package com.leo.careerforgeai.agent.infrastructure.springai.coach;

import com.leo.careerforgeai.agent.application.tool.career.search.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.domain.coach.CareerCoachAnswerStatus;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionResult;
import com.leo.careerforgeai.agent.domain.tool.ToolExecutionStatus;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @program: CareerForge-AI
 * @description: 使用真实DeepSeek验证Spring AI Career Coach完整Tool Calling链路。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
@SpringBootTest
@ActiveProfiles("spring-ai-smoke")
class SpringAiCareerCoachSmoke {

    @Autowired
    private SpringAiCareerCoachService careerCoachService;

    @MockitoBean
    private KnowledgeEvidenceSearchService evidenceSearchService;

    @Test
    void shouldCallParseJobRequirementsAndGenerateFinalAnswer() {
        String message = """
                请先调用 parse_job_requirements 工具解析下面的岗位JD，
                再根据工具返回的结构化要求给出学习重点和面试准备建议。
                不需要搜索项目知识库。

                岗位：AI应用开发工程师
                要求：
                1. 熟悉Java 21、Spring Boot和Spring AI。
                2. 理解Tool Calling、Agent Loop、RAG和向量检索。
                3. 能够处理模型超时、重复工具调用和结构化输出校验。
                4. 具备JUnit 5和Mockito自动化测试经验。
                """;

        SpringAiCareerCoachResult result = careerCoachService.coach(message);

        assertThat(result.runId()).isNotBlank();
        assertThat(result.totalDurationMs()).isPositive();
        assertThat(result.answer().status()).isEqualTo(CareerCoachAnswerStatus.ANSWERED);
        assertThat(result.answer().answer()).isNotBlank();
        assertThat(result.answer().citedChunkIds()).isEmpty();

        assertThat(result.toolResults()).hasSize(1);
        ToolExecutionResult parseResult = result.toolResults().getFirst();
        assertThat(parseResult.toolName()).isEqualTo(ParseJobRequirementsTool.NAME);
        assertThat(parseResult.status()).isEqualTo(ToolExecutionStatus.SUCCESS);
        assertThat(parseResult.resultCount()).isNotNull().isPositive();
        assertThat(parseResult.modelUsage()).isNotNull();
        assertThat(parseResult.modelUsage().totalTokens()).isPositive();
        assertThat(parseResult.modelDurationMs()).isNotNull().isPositive();

        verifyNoInteractions(evidenceSearchService);

        System.out.printf(
                "runId=%s, answerStatus=%s, toolName=%s, toolStatus=%s, resultCount=%d, toolModelTokens=%d, toolModelDurationMs=%d, totalDurationMs=%d%n",
                result.runId(),
                result.answer().status(),
                parseResult.toolName(),
                parseResult.status(),
                parseResult.resultCount(),
                parseResult.modelUsage().totalTokens(),
                parseResult.modelDurationMs(),
                result.totalDurationMs()
        );
    }
}