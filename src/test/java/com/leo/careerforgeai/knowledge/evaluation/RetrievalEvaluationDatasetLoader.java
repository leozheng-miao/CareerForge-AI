package com.leo.careerforgeai.knowledge.evaluation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

public final class RetrievalEvaluationDatasetLoader {

    public static final String DATASET_RESOURCE = "rag/evaluation/retrieval-cases.json";

    private final JsonMapper jsonMapper;

    public RetrievalEvaluationDatasetLoader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 读取仓库中冻结的固定检索评测集。 */
    public RetrievalEvaluationDataset load() {
        InputStream resource = RetrievalEvaluationDatasetLoader.class.getClassLoader().getResourceAsStream(DATASET_RESOURCE);
        if (resource == null) throw new EvaluationDatasetException("固定检索评测集不存在：" + DATASET_RESOURCE);

        try (InputStream input = resource) {
            return read(input);
        } catch (IOException e) {
            throw new EvaluationDatasetException("固定检索评测集关闭失败", e);
        }
    }

    /** 严格解析输入流并触发评测数据业务约束。 */
    RetrievalEvaluationDataset read(InputStream input) {
        if (input == null) throw new IllegalArgumentException("input 不能为空");

        try {
            RetrievalEvaluationDataset dataset = jsonMapper.readerFor(RetrievalEvaluationDataset.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(input);
            if (dataset == null) throw new EvaluationDatasetException("固定检索评测集内容为空");
            return dataset;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new EvaluationDatasetException("固定检索评测集解析或校验失败", e);
        }
    }
}