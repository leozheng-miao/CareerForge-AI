package com.leo.careerforgeai.model.infrastructure.ollama;

import com.leo.careerforgeai.model.application.EmbeddingGateway;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingRequest;
import com.leo.careerforgeai.model.domain.embedding.EmbeddingResult;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.ollama.dto.OllamaEmbedRequest;
import com.leo.careerforgeai.model.infrastructure.ollama.dto.OllamaEmbedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 调用 Ollama 批量生成 Document 或 Query Embedding，并将供应商响应严格校验后转换为统一结果
 * @author: Miao Zheng
 * @date: 2026-08-02 23:54
 **/
@Component
@Slf4j
public class OllamaEmbeddingClient implements EmbeddingGateway {

    private final OllamaEmbeddingProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final Qwen3EmbeddingInputFormatter inputFormatter = new Qwen3EmbeddingInputFormatter();

    public OllamaEmbeddingClient(OllamaEmbeddingProperties properties, JsonMapper jsonMapper, HttpClient httpClient) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        List<String> formattedInputs = inputFormatter.format(request);
        OllamaEmbedRequest providerRequest = new OllamaEmbedRequest(properties.getModel(), formattedInputs, false);
        long startNanos = System.nanoTime();

        try {
            OllamaEmbedResponse providerResponse = execute(providerRequest);
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            EmbeddingResult result = validateAndMap(providerResponse, formattedInputs.size(), durationMs);
            Long providerDurationMs = providerResponse.totalDuration() == null ? null : Duration.ofNanos(providerResponse.totalDuration()).toMillis();
            log.info("Ollama Embedding调用完成，model={}, purpose={}, batchSize={}, dimensions={}, durationMs={}, providerDurationMs={}, promptEvalCount={}",
                    result.model(), request.purpose(), formattedInputs.size(), result.dimensions(), result.durationMs(), providerDurationMs, providerResponse.promptEvalCount());
            return result;
        } catch (HttpConnectTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "连接 Ollama Embedding 服务超时", e);
        } catch (HttpTimeoutException e) {
            throw new ModelException(ModelErrorType.TIMEOUT, "等待 Ollama Embedding 响应超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Ollama Embedding 调用被中断", e);
        } catch (IOException e) {
            throw new ModelException(ModelErrorType.NETWORK_ERROR, "Ollama Embedding 网络调用失败", e);
        }
    }

    private OllamaEmbedResponse execute(OllamaEmbedRequest providerRequest) throws IOException, InterruptedException {
        String jsonBody = serializeRequest(providerRequest);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(properties.getBaseUrl().resolve("/api/embed"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() > 299) throw mapHttpError(response.statusCode());

        try {
            return jsonMapper.readValue(response.body(), OllamaEmbedResponse.class);
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 响应不是合法 JSON", e);
        }
    }

    /**
     * 不能只判断 HTTP 200，因为它只说明服务器处理了请求，不证明数据满足索引要求。
     * 必须继续验证：
     * 模型是否还是 qwen3-embedding:0.6b；
     * 两个输入是否返回两个向量；
     * 每个向量是否为 1024 维；
     * 是否存在 null、NaN、Infinity。
     * 任何一项错误都要停止入库。否则 Elasticsearch 索引中可能混入错误模型或错误维度的数据。
     * @param response
     * @param expectedCount
     * @param durationMs
     * @return
     */
    private EmbeddingResult validateAndMap(OllamaEmbedResponse response, int expectedCount, long durationMs) {
        if (response == null) throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 响应为空");
        if (!properties.getModel().equals(response.model())) throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 返回模型不匹配，expected=" + properties.getModel() + ", actual=" + response.model());

        List<List<Float>> vectors = response.embeddings();
        if (vectors == null || vectors.size() != expectedCount) {
            int actualCount = vectors == null ? 0 : vectors.size();
            throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 返回向量数量不匹配，expected=" + expectedCount + ", actual=" + actualCount);
        }

        for (int index = 0; index < vectors.size(); index++) {
            List<Float> vector = vectors.get(index);
            if (vector == null || vector.size() != properties.getDimensions()) {
                int actualDimensions = vector == null ? 0 : vector.size();
                throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 向量维度不匹配，index=" + index + ", expected=" + properties.getDimensions() + ", actual=" + actualDimensions);
            }
            if (vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) throw new ModelException(ModelErrorType.INVALID_RESPONSE, "Ollama Embedding 向量包含非法浮点值，index=" + index);
        }

        return new EmbeddingResult(response.model(), properties.getDimensions(), vectors, durationMs);
    }

    private String serializeRequest(OllamaEmbedRequest request) {
        try {
            return jsonMapper.writeValueAsString(request);
        } catch (JacksonException e) {
            throw new ModelException(ModelErrorType.CONFIGURATION_ERROR, "Ollama Embedding 请求序列化失败", e);
        }
    }

    private ModelException mapHttpError(int statusCode) {
        return switch (statusCode) {
            case 401 -> new ModelException(ModelErrorType.AUTHENTICATION_ERROR, "Ollama Embedding 鉴权失败");
            case 403 -> new ModelException(ModelErrorType.PERMISSION_ERROR, "Ollama Embedding 拒绝访问");
            case 404 -> new ModelException(ModelErrorType.MODEL_NOT_FOUND, "Ollama Embedding 模型或接口不存在");
            case 408, 504 -> new ModelException(ModelErrorType.TIMEOUT, "Ollama Embedding 服务响应超时");
            case 429 -> new ModelException(ModelErrorType.RATE_LIMITED, "Ollama Embedding 请求频率受限");
            default -> statusCode >= 500
                    ? new ModelException(ModelErrorType.PROVIDER_ERROR, "Ollama Embedding 服务异常，statusCode=" + statusCode)
                    : new ModelException(ModelErrorType.PROVIDER_ERROR, "Ollama Embedding 拒绝请求，statusCode=" + statusCode);
        };
    }

}