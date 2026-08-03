package com.leo.careerforgeai.model.infrastructure.ollama;

import com.leo.careerforgeai.model.domain.embedding.EmbeddingPurpose;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.ollama.dto.OllamaEmbedRequest;
import com.leo.careerforgeai.model.infrastructure.ollama.dto.OllamaEmbedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-08-03 09:36
 **/
class OllamaEmbeddingClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final JsonMapper jsonMapper = mock(JsonMapper.class);

    @Test
    void shouldFormatQueryAndReturnValidatedEmbeddingResult() throws Exception {
        stubSuccessfulResponse(new OllamaEmbedResponse("qwen3-embedding:0.6b", List.of(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F)), 2_000_000L, 1_000_000L, 20));

        EmbeddingResult result = createClient(2).embed(new EmbeddingRequest(EmbeddingPurpose.QUERY, List.of("岗位需要什么能力？", "面试考察什么？")));

        ArgumentCaptor<OllamaEmbedRequest> requestCaptor = ArgumentCaptor.forClass(OllamaEmbedRequest.class);
        verify(jsonMapper).writeValueAsString(requestCaptor.capture());
        assertThat(requestCaptor.getValue().truncate()).isFalse();
        assertThat(requestCaptor.getValue().input()).containsExactly(
                "Instruct: " + Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION + "\nQuery:岗位需要什么能力？",
                "Instruct: " + Qwen3EmbeddingInputFormatter.QUERY_INSTRUCTION + "\nQuery:面试考察什么？"
        );
        assertThat(result.model()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(result.dimensions()).isEqualTo(2);
        assertThat(result.vectors()).containsExactly(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F));
        assertThat(result.durationMs()).isNotNegative();
    }

    @ParameterizedTest
    @CsvSource({
            "401, AUTHENTICATION_ERROR",
            "403, PERMISSION_ERROR",
            "404, MODEL_NOT_FOUND",
            "408, TIMEOUT",
            "429, RATE_LIMITED",
            "500, PROVIDER_ERROR"
    })
    void shouldMapHttpStatus(int statusCode, ModelErrorType expectedType) throws Exception {
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        stubHttpResponse(statusCode, "");

        assertThatThrownBy(() -> createClient(2).embed(documentRequest()))
                .isInstanceOfSatisfying(ModelException.class, exception -> assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    @Test
    void shouldRejectInvalidJsonResponse() throws Exception {
        stubHttpResponse(200, "{invalid-json}");
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        when(jsonMapper.readValue(anyString(), eq(OllamaEmbedResponse.class))).thenThrow(mock(JacksonException.class));

        assertInvalidResponse(() -> createClient(2).embed(documentRequest()));
    }

    @Test
    void shouldRejectUnexpectedModel() throws Exception {
        stubSuccessfulResponse(new OllamaEmbedResponse("other-model", List.of(List.of(0.1F, 0.2F)), 1L, 1L, 1));

        assertInvalidResponse(() -> createClient(2).embed(documentRequest()));
    }

    @Test
    void shouldRejectUnexpectedVectorCount() throws Exception {
        stubSuccessfulResponse(new OllamaEmbedResponse("qwen3-embedding:0.6b", List.of(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F)), 1L, 1L, 1));

        assertInvalidResponse(() -> createClient(2).embed(documentRequest()));
    }

    @Test
    void shouldRejectUnexpectedVectorDimensions() throws Exception {
        stubSuccessfulResponse(new OllamaEmbedResponse("qwen3-embedding:0.6b", List.of(List.of(0.1F)), 1L, 1L, 1));

        assertInvalidResponse(() -> createClient(2).embed(documentRequest()));
    }

    @Test
    void shouldRejectNonFiniteVectorValue() throws Exception {
        stubSuccessfulResponse(new OllamaEmbedResponse("qwen3-embedding:0.6b", List.of(List.of(Float.NaN, 0.2F)), 1L, 1L, 1));

        assertInvalidResponse(() -> createClient(2).embed(documentRequest()));
    }

    @Test
    void shouldClassifyConnectTimeout() throws Exception {
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        stubSendFailure(new HttpConnectTimeoutException("connect timeout"));

        assertErrorType(() -> createClient(2).embed(documentRequest()), ModelErrorType.TIMEOUT);
    }

    @Test
    void shouldClassifyResponseTimeout() throws Exception {
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        stubSendFailure(new HttpTimeoutException("response timeout"));

        assertErrorType(() -> createClient(2).embed(documentRequest()), ModelErrorType.TIMEOUT);
    }

    @Test
    void shouldClassifyNetworkFailure() throws Exception {
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        stubSendFailure(new IOException("connection reset"));

        assertErrorType(() -> createClient(2).embed(documentRequest()), ModelErrorType.NETWORK_ERROR);
    }

    @Test
    void shouldClassifySerializationFailure() throws Exception {
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenThrow(mock(JacksonException.class));

        assertErrorType(() -> createClient(2).embed(documentRequest()), ModelErrorType.CONFIGURATION_ERROR);
    }

    private OllamaEmbeddingClient createClient(int dimensions) {
        OllamaEmbeddingProperties properties = new OllamaEmbeddingProperties(URI.create("http://ollama.test"), "qwen3-embedding:0.6b", dimensions);
        return new OllamaEmbeddingClient(properties, jsonMapper, httpClient);
    }

    private EmbeddingRequest documentRequest() {
        return new EmbeddingRequest(EmbeddingPurpose.DOCUMENT, List.of("文档正文"));
    }

    private void stubSuccessfulResponse(OllamaEmbedResponse providerResponse) throws Exception {
        stubHttpResponse(200, "{}");
        when(jsonMapper.writeValueAsString(any(OllamaEmbedRequest.class))).thenReturn("{}");
        when(jsonMapper.readValue(anyString(), eq(OllamaEmbedResponse.class))).thenReturn(providerResponse);
    }

    private void stubHttpResponse(int statusCode, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }

    private void stubSendFailure(IOException exception) throws Exception {
        when(httpClient.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(exception);
    }

    private void assertInvalidResponse(ThrowingCall call) {
        assertErrorType(call, ModelErrorType.INVALID_RESPONSE);
    }

    private void assertErrorType(ThrowingCall call, ModelErrorType expectedType) {
        assertThatThrownBy(call::invoke).isInstanceOfSatisfying(ModelException.class, exception -> assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke();
    }
}