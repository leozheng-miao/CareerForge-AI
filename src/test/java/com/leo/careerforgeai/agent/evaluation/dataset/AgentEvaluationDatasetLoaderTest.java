package com.leo.careerforgeai.agent.evaluation.dataset;

import com.leo.careerforgeai.agent.application.tool.career.parse.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.application.tool.career.search.ParseJobRequirementsTool;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证固定Agent评测集的版本、覆盖范围和严格标注约束。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
class AgentEvaluationDatasetLoaderTest {

    private final AgentEvaluationDatasetLoader loader =
            new AgentEvaluationDatasetLoader(JsonMapper.builder().build());

    @Test
    void shouldLoadFrozenAgentEvaluationDataset() {
        AgentEvaluationDataset dataset = loader.load();

        assertThat(dataset.cases()).hasSize(13);
        assertThat(dataset.realRunsPerCase()).isEqualTo(3);
        assertThat(dataset.cases()).extracting(AgentEvaluationDataset.EvaluationCase::caseId).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(AgentEvaluationDataset.EvaluationCase::userMessage).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(AgentEvaluationDataset.EvaluationCase::scenarioType)
                .contains(AgentEvaluationDataset.ScenarioType.values());
        assertThat(dataset.cases()).allSatisfy(evaluationCase -> {
            Set<String> classifiedTools = new HashSet<>(evaluationCase.expectedTools());
            classifiedTools.addAll(evaluationCase.forbiddenTools());

            assertThat(Collections.disjoint(
                    evaluationCase.expectedTools(),
                    evaluationCase.forbiddenTools()
            )).isTrue();
            assertThat(classifiedTools)
                    .containsExactlyInAnyOrderElementsOf(AgentEvaluationDataset.SUPPORTED_TOOLS);
        });
    }

    @Test
    void shouldRejectUnknownJsonProperty() throws IOException {
        String validJson;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(AgentEvaluationDatasetLoader.DATASET_RESOURCE)) {
            if (input == null) throw new IllegalStateException("测试资源不存在");
            validJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String invalidJson = validJson.replace(
                "\"schemaVersion\": \"agent-evaluation-v1\"",
                "\"unknownField\": true,\n  \"schemaVersion\": \"agent-evaluation-v1\""
        );

        assertThatThrownBy(() -> loader.read(
                new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AgentEvaluationDatasetException.class)
                .hasMessageContaining("解析或校验失败");
    }

    @Test
    void shouldRejectCitationWithoutSearchEvidence() {
        assertThatThrownBy(() -> new AgentEvaluationDataset.EvaluationCase(
                "agent-eval-999",
                AgentEvaluationDataset.ScenarioType.JD_PARSE,
                "解析这份测试JD",
                List.of(ParseJobRequirementsTool.NAME),
                List.of(SearchCareerMaterialsTool.NAME),
                AgentEvaluationDataset.SequenceMode.EXACT_ORDER,
                List.of(ParseJobRequirementsTool.NAME),
                1,
                true,
                true,
                AgentEvaluationDataset.FaultMode.NONE,
                com.leo.careerforgeai.agent.domain.loop.AgentRunStatus.COMPLETED,
                com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason.FINAL_ANSWER,
                com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus.ANSWERED,
                "测试错误引用标注"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCitation");
    }

    @Test
    void shouldRejectInvalidAnyOrderDefinition() {
        assertThatThrownBy(() -> new AgentEvaluationDataset.EvaluationCase(
                "agent-eval-998",
                AgentEvaluationDataset.ScenarioType.KNOWLEDGE_SEARCH,
                "查询测试材料",
                List.of(SearchCareerMaterialsTool.NAME),
                List.of(ParseJobRequirementsTool.NAME),
                AgentEvaluationDataset.SequenceMode.ANY_ORDER,
                List.of(),
                1,
                true,
                true,
                AgentEvaluationDataset.FaultMode.NONE,
                com.leo.careerforgeai.agent.domain.loop.AgentRunStatus.COMPLETED,
                com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason.FINAL_ANSWER,
                com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus.ANSWERED,
                "测试错误顺序标注"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ANY_ORDER");
    }
}