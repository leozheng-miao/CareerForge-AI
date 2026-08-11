package com.leo.careerforgeai.agent.evaluation.dataset;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * @program: CareerForge-AI
 * @description: 严格读取和校验仓库中的固定Agent评测集。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
public final class AgentEvaluationDatasetLoader {

    public static final String DATASET_RESOURCE = "agent/evaluation/agent-cases.json";

    private final JsonMapper jsonMapper;

    public AgentEvaluationDatasetLoader(JsonMapper jsonMapper) {
        if (jsonMapper == null) throw new IllegalArgumentException("jsonMapper不能为空");
        this.jsonMapper = jsonMapper;
    }

    public AgentEvaluationDataset load() {
        InputStream resource = AgentEvaluationDatasetLoader.class.getClassLoader().getResourceAsStream(DATASET_RESOURCE);
        if (resource == null) throw new AgentEvaluationDatasetException("固定Agent评测集不存在：" + DATASET_RESOURCE);
        try (InputStream input = resource) {
            return read(input);
        } catch (IOException exception) {
            throw new AgentEvaluationDatasetException("固定Agent评测集关闭失败", exception);
        }
    }

    AgentEvaluationDataset read(InputStream input) {
        if (input == null) throw new IllegalArgumentException("input不能为空");
        try {
            AgentEvaluationDataset dataset = jsonMapper.readerFor(AgentEvaluationDataset.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(input);
            if (dataset == null) throw new AgentEvaluationDatasetException("固定Agent评测集内容为空");
            return dataset;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new AgentEvaluationDatasetException("固定Agent评测集解析或校验失败", exception);
        }
    }
}