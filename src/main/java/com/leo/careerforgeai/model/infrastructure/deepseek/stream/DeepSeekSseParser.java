package com.leo.careerforgeai.model.infrastructure.deepseek.stream;

import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import com.leo.careerforgeai.model.infrastructure.deepseek.dto.DeepSeekStreamChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * @program: CareerForge-AI
 * @description:
 * @author: Miao Zheng
 * @date: 2026-07-29 16:59
 **/
@Component
@RequiredArgsConstructor
public class DeepSeekSseParser {
    private final JsonMapper jsonMapper;

    public void parse(InputStream inputStream,
                      Consumer<DeepSeekStreamChunk> chunkConsumer
    ) throws IOException {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            while (true) {
                String eventData = readNextEventData(reader);

                if (eventData == null) {
                    throw new EOFException("SSE连接已关闭，但未收到[DONE]");
                }

                if ("[DONE]".equals(eventData)) return;
                DeepSeekStreamChunk chunk = null;
                try {
                    chunk = jsonMapper.readValue(eventData, DeepSeekStreamChunk.class);
                } catch (JacksonException e) {
                    throw new ModelException(ModelErrorType.INVALID_RESPONSE, "DeepSeek SSE事件JSON解析失败", e);
                }

                chunkConsumer.accept(chunk);
            }
        }
    }

    /**
     * 从流中读取一个完整的SSE 事件
     *
     * @param reader
     * @return
     * @throws IOException readNextEventData() 按行读取 SSE 流，
     *                     忽略心跳、空事件和非 data: 字段，将同一个 SSE 事件中的一个或多个 data:
     *                     行组合为完整事件数据，并在连接中途断开时抛出异常。
     *                     它只负责识别和组装 SSE 事件，不负责 JSON 解析或完整模型回答的拼接。
     */
    private String readNextEventData(BufferedReader reader) throws IOException {
        StringBuilder dataBuffer = new StringBuilder();
        boolean hasDataLine = false;
        String line;
        while ((line = reader.readLine()) != null) {
            // 空行表示 一个 SSE事件 结束
            if (line.isEmpty()) {
                if (hasDataLine) {
                    return dataBuffer.toString();
                }
                continue;
            }
            //SSE 注释或心跳
            if (line.startsWith(":")) continue;
            //当前只处理 data 字段
            if (!line.startsWith("data:")) continue;

            String data = line.substring("data:".length());
            if (data.startsWith(" ")) data = data.substring(1);
            //SSE 允许一个事件包含多个 data 行
            if (hasDataLine) dataBuffer.append('\n');
            dataBuffer.append(data);
            hasDataLine = true;
        }

        if (hasDataLine) throw new EOFException("SSE事件尚未结束，连接已关闭");
        return null;
    }
}