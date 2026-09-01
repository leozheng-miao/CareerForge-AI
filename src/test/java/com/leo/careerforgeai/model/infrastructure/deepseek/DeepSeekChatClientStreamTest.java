package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.config.ModelProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEvent;
import com.leo.careerforgeai.model.domain.stream.ModelStreamEventType;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatClientStreamTest {

    private HttpServer server;

    private final AtomicReference<String> receivedRequestBody =
            new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldEmitStartDeltasAndCompletedWithUsage()
            throws Exception {
        String responseBody = """
                data: {"id":"provider-1","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"你"},"finish_reason":null}]}

                data: {"id":"provider-1","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":null,"content":"好"},"finish_reason":"stop"}]}

                data: {"id":"provider-1","model":"deepseek-v4-flash","choices":[],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}

                data: [DONE]

                """;

        startServer(responseBody);

        DeepSeekChatClient client = createClient();
        List<ModelStreamEvent> events = new ArrayList<>();

        client.stream(createRequest(), events::add);

        assertThat(events)
                .extracting(ModelStreamEvent::type)
                .containsExactly(
                        ModelStreamEventType.START,
                        ModelStreamEventType.DELTA,
                        ModelStreamEventType.DELTA,
                        ModelStreamEventType.COMPLETED
                );

        String fullContent = events.stream()
                .filter(event ->
                        event.type() == ModelStreamEventType.DELTA
                )
                .map(ModelStreamEvent::content)
                .collect(Collectors.joining());

        assertThat(fullContent).isEqualTo("你好");

        ModelStreamEvent completed = events.getLast();

        assertThat(completed.usage()).isNotNull();
        assertThat(completed.usage().inputTokens()).isEqualTo(2);
        assertThat(completed.usage().outputTokens()).isEqualTo(2);
        assertThat(completed.usage().totalTokens()).isEqualTo(4);

        assertThat(events)
                .extracting(ModelStreamEvent::requestId)
                .containsOnly(events.getFirst().requestId());

        assertThat(receivedRequestBody.get())
                .contains("\"stream\":true")
                .contains("\"include_usage\":true");
    }

    @Test
    void shouldEmitErrorWithoutCompletedWhenStreamDisconnects()
            throws Exception {
        String responseBody = """
                data: {"id":"provider-2","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"部分内容"},"finish_reason":null}]}

                """;

        startServer(responseBody);

        DeepSeekChatClient client = createClient();
        List<ModelStreamEvent> events = new ArrayList<>();

        client.stream(createRequest(), events::add);

        assertThat(events)
                .extracting(ModelStreamEvent::type)
                .containsExactly(
                        ModelStreamEventType.START,
                        ModelStreamEventType.DELTA,
                        ModelStreamEventType.ERROR
                );

        assertThat(events.stream().noneMatch(event ->
                event.type() == ModelStreamEventType.COMPLETED
        )).isTrue();

        assertThat(events.get(1).content())
                .isEqualTo("部分内容");

        assertThat(events.getLast().content())
                .isEqualTo("大模型流式网络调用失败");
        assertThat(events.getLast().errorType()).isEqualTo(ModelErrorType.NETWORK_ERROR);
    }

    @Test
    void shouldEmitIncompleteErrorWhenStreamReachesTokenLimit()
            throws Exception {
        String responseBody = """
            data: {"id":"provider-length","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"部分输出"},"finish_reason":null}]}

            data: {"id":"provider-length","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":null,"content":""},"finish_reason":"length"}]}

            data: {"id":"provider-length","model":"deepseek-v4-flash","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":3,"total_tokens":13}}

            data: [DONE]

            """;

        startServer(responseBody);

        DeepSeekChatClient client = createClient();
        List<ModelStreamEvent> events = new ArrayList<>();

        client.stream(createRequest(), events::add);

        assertThat(events)
                .extracting(ModelStreamEvent::type)
                .containsExactly(
                        ModelStreamEventType.START,
                        ModelStreamEventType.DELTA,
                        ModelStreamEventType.ERROR
                );
        assertThat(events.get(1).content()).isEqualTo("部分输出");
        assertThat(events.getLast().errorType())
                .isEqualTo(ModelErrorType.PROVIDER_INCOMPLETE);
        assertThat(events.getLast().content())
                .isEqualTo("模型供应商输出未完整结束");
        assertThat(events)
                .noneMatch(event ->
                        event.type() == ModelStreamEventType.COMPLETED
                );
    }

    private void startServer(String responseBody)
            throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/chat/completions", exchange -> {
            try {
                receivedRequestBody.set(
                        new String(
                                exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        )
                );

                exchange.getResponseHeaders().set(
                        "Content-Type",
                        "text/event-stream"
                );

                exchange.sendResponseHeaders(200, 0);

                try (OutputStream output =
                             exchange.getResponseBody()) {
                    output.write(
                            responseBody.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            } finally {
                exchange.close();
            }
        });

        server.start();
    }

    private DeepSeekChatClient createClient() {
        JsonMapper jsonMapper =
                JsonMapper.builder().build();

        ModelProperties properties = new ModelProperties(
                URI.create(
                        "http://127.0.0.1:"
                                + server.getAddress().getPort()
                ),
                "test-api-key",
                "deepseek-v4-flash"
        );

        return new DeepSeekChatClient(
                properties,
                jsonMapper,
                new DeepSeekSseParser(jsonMapper),
                HttpClient.newHttpClient()
        );
    }

    private ModelRequest createRequest() {
        return new ModelRequest(
                List.of(
                        new ModelMessage(
                                ModelRole.USER,
                                "请输出你好"
                        )
                ),
                ModelOutputFormat.TEXT
        );
    }
}