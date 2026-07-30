package com.leo.careerforgeai.model.infrastructure.deepseek;

import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.dto.DeepSeekStreamChunk;
import com.leo.careerforgeai.model.infrastructure.deepseek.stream.DeepSeekSseParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-30 13:30
 **/
class DeepSeekSseParserTest {
    private final DeepSeekSseParser parser =
            new DeepSeekSseParser(JsonMapper.builder().build());

    @Test
    void shouldParseNormalStreamUntilDone() throws Exception {
        String sse = """
                data: {"id":"req-1","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"你"},"finish_reason":null}]}
                
                data: {"id":"req-1","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":null,"content":"好"},"finish_reason":"stop"}]}
                
                data: [DONE]
                
                """;

        List<DeepSeekStreamChunk> chunks = new ArrayList<>();

        parser.parse(
                new ByteArrayInputStream(
                        sse.getBytes(StandardCharsets.UTF_8)
                ),
                chunks::add
        );

        assertThat(chunks).hasSize(2);

        assertThat(
                chunks.get(0)
                        .choices()
                        .getFirst()
                        .delta()
                        .content()
        ).isEqualTo("你");

        assertThat(
                chunks.get(1)
                        .choices()
                        .getFirst()
                        .delta()
                        .content()
        ).isEqualTo("好");

        assertThat(
                chunks.get(1)
                        .choices()
                        .getFirst()
                        .finishReason()
        ).isEqualTo("stop");
    }

    @Test
    void shouldParseEventSplitAcrossInputStreamReads() throws Exception {
        String sse = """
            data: {"id":"req-2","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"跨块读取"},"finish_reason":"stop"}]}

            data: [DONE]

            """;

        byte[] responseBytes = sse.getBytes(StandardCharsets.UTF_8);

        List<DeepSeekStreamChunk> chunks = new ArrayList<>();

        parser.parse(
                new ChunkedInputStream(responseBytes, 2),
                chunks::add
        );

        assertThat(chunks).hasSize(1);

        assertThat(
                chunks.getFirst()
                        .choices()
                        .getFirst()
                        .delta()
                        .content()
        ).isEqualTo("跨块读取");
    }

    @Test
    void shouldIgnoreCommentsAndBlankEvents() throws Exception {
        String sse = """
            : heartbeat


            data: {"id":"req-3","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"有效内容"},"finish_reason":"stop"}]}

            : another-heartbeat

            data: [DONE]

            """;

        List<DeepSeekStreamChunk> chunks = new ArrayList<>();

        parser.parse(
                new ByteArrayInputStream(
                        sse.getBytes(StandardCharsets.UTF_8)
                ),
                chunks::add
        );

        assertThat(chunks).hasSize(1);
        assertThat(
                chunks.getFirst()
                        .choices()
                        .getFirst()
                        .delta()
                        .content()
        ).isEqualTo("有效内容");
    }

    @Test
    void shouldJoinMultipleDataLinesIntoOneEvent() throws Exception {
        String sse = """
            data: {"id":"req-4","model":"deepseek-v4-flash",
            data: "choices":[{"index":0,"delta":{"role":"assistant","content":"多行事件"},"finish_reason":"stop"}]}

            data: [DONE]

            """;

        List<DeepSeekStreamChunk> chunks = new ArrayList<>();

        parser.parse(
                new ByteArrayInputStream(
                        sse.getBytes(StandardCharsets.UTF_8)
                ),
                chunks::add
        );

        assertThat(chunks).hasSize(1);
        assertThat(
                chunks.getFirst()
                        .choices()
                        .getFirst()
                        .delta()
                        .content()
        ).isEqualTo("多行事件");
    }

    @Test
    void shouldFailWhenConnectionClosesBeforeDone() {
        String sse = """
            data: {"id":"req-5","model":"deepseek-v4-flash","choices":[{"index":0,"delta":{"role":"assistant","content":"已有内容"},"finish_reason":"stop"}]}

            """;

        List<DeepSeekStreamChunk> chunks = new ArrayList<>();

        assertThatThrownBy(() ->
                parser.parse(
                        new ByteArrayInputStream(
                                sse.getBytes(StandardCharsets.UTF_8)
                        ),
                        chunks::add
                )
        )
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("未收到[DONE]");

        assertThat(chunks).hasSize(1);
    }

    @Test
    void shouldFailWhenConnectionClosesInsideEvent() {
        String sse = "data: {\"id\":\"req-6\"";

        assertThatThrownBy(() ->
                parser.parse(
                        new ByteArrayInputStream(
                                sse.getBytes(StandardCharsets.UTF_8)
                        ),
                        chunk -> {
                        }
                )
        )
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("尚未结束");
    }

    @Test
    void shouldFailWhenEventJsonIsInvalid() {
        String sse = """
            data: {not-json}

            data: [DONE]

            """;

        assertThatThrownBy(() ->
                parser.parse(
                        new ByteArrayInputStream(
                                sse.getBytes(StandardCharsets.UTF_8)
                        ),
                        chunk -> {
                        }
                )
        ).isInstanceOfSatisfying(
                ModelException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ModelErrorType.INVALID_RESPONSE)
        );
    }

    private static final class ChunkedInputStream
            extends ByteArrayInputStream {

        private final int maxChunkSize;

        private ChunkedInputStream(
                byte[] data,
                int maxChunkSize
        ) {
            super(data);
            this.maxChunkSize = maxChunkSize;
        }

        @Override
        public synchronized int read(
                byte[] buffer,
                int offset,
                int length
        ) {
            return super.read(
                    buffer,
                    offset,
                    Math.min(length, maxChunkSize)
            );
        }
    }
}