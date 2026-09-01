package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证问题事实映射、可信模型元数据和完整内容Hash
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
class InterviewQuestionFactoryTest {

    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ActorId OWNER = new ActorId("question-owner");
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private final InterviewQuestionFactory factory =
            new InterviewQuestionFactory(JsonMapper.builder().build());

    @Test
    void shouldCreateQuestionAndHashAllBusinessContent() {
        InterviewRoleModelGateway.Result<InterviewQuestionDraft> result = result(
                List.of("说明锁语义", "比较适用边界")
        );

        InterviewQuestion first = factory.createFirstQuestion(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                INTERVIEW_ID,
                ROUND_ID,
                OWNER,
                result,
                NOW
        );
        InterviewQuestion sameContent = factory.createFirstQuestion(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                INTERVIEW_ID,
                ROUND_ID,
                OWNER,
                result,
                NOW.plusSeconds(1)
        );
        InterviewQuestion changedEvaluation = factory.createFirstQuestion(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                INTERVIEW_ID,
                ROUND_ID,
                OWNER,
                result(List.of("说明锁语义", "分析中断响应")),
                NOW
        );

        assertThat(first.questionText()).isEqualTo("synchronized与ReentrantLock有什么区别？");
        assertThat(first.modelRequestId()).isEqualTo("request-1");
        assertThat(first.promptVersion()).isEqualTo("interviewer-v1");
        assertThat(first.followUp()).isFalse();
        assertThat(first.parentQuestionId()).isNull();
        assertThat(first.contentHash()).isEqualTo(sameContent.contentHash());
        assertThat(first.contentHash()).isNotEqualTo(changedEvaluation.contentHash());
    }

    private InterviewRoleModelGateway.Result<InterviewQuestionDraft> result(
            List<String> evaluationPoints
    ) {
        InterviewQuestionDraft draft = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                "synchronized与ReentrantLock有什么区别？",
                List.of("Java并发"),
                2,
                evaluationPoints,
                true,
                List.of()
        );
        return new InterviewRoleModelGateway.Result<>(
                draft,
                "request-1",
                "deepseek-chat",
                "interviewer-v1",
                new ModelUsage(300, 100, 400),
                1200,
                1,
                false,
                "a".repeat(64)
        );
    }
}