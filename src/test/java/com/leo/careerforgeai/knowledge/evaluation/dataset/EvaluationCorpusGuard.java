package com.leo.careerforgeai.knowledge.evaluation.dataset;

import com.leo.careerforgeai.knowledge.domain.document.SourceDocument;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvaluationCorpusGuard {

    public static final String MANIFEST_RESOURCE = "rag/evaluation/corpus-manifest.json";
    public static final String SUPPORTED_SCHEMA_VERSION = "rag-evaluation-corpus-v1";

    private final JsonMapper jsonMapper;

    public EvaluationCorpusGuard(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 从测试资源中严格读取固定语料快照。 */
    public EvaluationCorpusManifest loadManifest() {
        InputStream resource = EvaluationCorpusGuard.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE);
        if (resource == null) throw new CorpusDriftException("评测语料 manifest 不存在：" + MANIFEST_RESOURCE);

        try (InputStream input = resource) {
            EvaluationCorpusManifest manifest = jsonMapper.readerFor(EvaluationCorpusManifest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(input);
            if (manifest == null) throw new CorpusDriftException("评测语料 manifest 内容为空");
            return manifest;
        } catch (JacksonException | IOException e) {
            throw new CorpusDriftException("评测语料 manifest 读取失败", e);
        }
    }

    /** 在评测运行前确认当前语料和处理版本与固定快照完全一致。 */
    public void verify(EvaluationCorpusManifest manifest, List<SourceDocument> currentDocuments, String currentCleaningVersion, String currentChunkerVersion) {
        if (manifest == null) throw new IllegalArgumentException("manifest 不能为空");
        if (currentDocuments == null) throw new IllegalArgumentException("currentDocuments 不能为空");

        requireEqual("schemaVersion", manifest.schemaVersion(), SUPPORTED_SCHEMA_VERSION);
        requireEqual("cleaningVersion", manifest.cleaningVersion(), currentCleaningVersion);
        requireEqual("chunkerVersion", manifest.chunkerVersion(), currentChunkerVersion);

        Map<String, SourceDocument> currentById = new LinkedHashMap<>();
        for (SourceDocument document : currentDocuments) {
            if (document == null) throw new CorpusDriftException("当前语料包含空文档");
            if (currentById.putIfAbsent(document.documentId(), document) != null) throw new CorpusDriftException("当前语料包含重复 documentId=" + document.documentId());
            requireEqual("knowledgeBaseId", manifest.knowledgeBaseId(), document.knowledgeBaseId());
        }

        Map<String, EvaluationCorpusManifest.DocumentSnapshot> expectedById = new LinkedHashMap<>();
        manifest.documents().forEach(document -> expectedById.put(document.documentId(), document));
        if (!currentById.keySet().equals(expectedById.keySet())) throw new CorpusDriftException("语料文档集合不一致，expected=" + expectedById.keySet() + ", actual=" + currentById.keySet());

        expectedById.forEach((documentId, expected) -> verifyDocument(expected, currentById.get(documentId)));
    }

    private void verifyDocument(EvaluationCorpusManifest.DocumentSnapshot expected, SourceDocument actual) {
        requireEqual("documentName", expected.documentName(), actual.documentName());
        requireEqual("documentType", expected.documentType(), actual.documentType());
        requireEqual("sourcePath", expected.sourcePath(), actual.sourcePath());
        requireEqual("sourceHash", expected.sourceHash(), actual.sourceHash());
    }

    private void requireEqual(String field, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new CorpusDriftException(field + " 不一致，expected=" + expected + ", actual=" + actual);
    }
}