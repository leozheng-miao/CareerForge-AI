package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.blueprint.InterviewBlueprintApplicationService;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.session.InterviewFailureCode;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.execution.InterviewRouteDecision;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证任意回合问题生成、幂等重放和模型失败收敛链路
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
class InterviewQuestionGenerationServiceTest {

    private static final UUID INTERVIEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String CHUNK_ID = "a".repeat(64);

    private InterviewBlueprintApplicationService blueprintService;
    private InterviewQuestionRoleContract questionContract;
    private InterviewRoleModelGateway modelGateway;
    private InterviewQuestionPersistenceService persistenceService;
    private InterviewQuestionGenerationService service;

    @BeforeEach
    void setUp() {
        blueprintService = mock(InterviewBlueprintApplicationService.class);
        questionContract = mock(InterviewQuestionRoleContract.class);
        modelGateway = mock(InterviewRoleModelGateway.class);
        persistenceService = mock(InterviewQuestionPersistenceService.class);
        service = new InterviewQuestionGenerationService(
                blueprintService, questionContract, modelGateway, persistenceService
        );
    }

    @Test
    void shouldGenerateAndPersistFollowUpInRequiredOrder() {
        InterviewQuestionInput input = input(2);
        InterviewRoleModelGateway.Result<InterviewQuestionDraft> modelResult = modelResult();
        InterviewQuestion stored = mock(InterviewQuestion.class);

        when(persistenceService.startQuestionGeneration(INTERVIEW_ID, 2)).thenReturn(Optional.empty());
        when(blueprintService.prepareNextQuestion(INTERVIEW_ID, InterviewRouteDecision.FOLLOW_UP))
                .thenReturn(input);
        when(modelGateway.generate(questionContract, input, TIMEOUT)).thenReturn(modelResult);
        when(persistenceService.persistQuestion(
                INTERVIEW_ID, input, InterviewRouteDecision.FOLLOW_UP, modelResult
        )).thenReturn(stored);

        InterviewQuestion result = service.generateAndPersistQuestion(
                INTERVIEW_ID, 2, InterviewRouteDecision.FOLLOW_UP, TIMEOUT
        );

        assertThat(result).isSameAs(stored);
        InOrder order = inOrder(persistenceService, blueprintService, modelGateway);
        order.verify(persistenceService).startQuestionGeneration(INTERVIEW_ID, 2);
        order.verify(blueprintService).prepareNextQuestion(INTERVIEW_ID, InterviewRouteDecision.FOLLOW_UP);
        order.verify(modelGateway).generate(questionContract, input, TIMEOUT);
        order.verify(persistenceService).persistQuestion(
                INTERVIEW_ID, input, InterviewRouteDecision.FOLLOW_UP, modelResult
        );
    }

    @Test
    void shouldReturnExistingFirstQuestionWithoutCallingModelAgain() {
        InterviewQuestion existing = mock(InterviewQuestion.class);
        when(persistenceService.startQuestionGeneration(INTERVIEW_ID, 1))
                .thenReturn(Optional.of(existing));

        InterviewQuestion result = service.generateAndPersistFirstQuestion(INTERVIEW_ID, TIMEOUT);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(blueprintService, questionContract, modelGateway);
        verify(persistenceService, never()).persistQuestion(
                INTERVIEW_ID, input(1), null, modelResult()
        );
    }

    @Test
    void shouldFailSessionWhenModelOutputRemainsInvalid() {
        InterviewQuestionInput input = input(1);
        ModelException failure = new ModelException(
                ModelErrorType.STRUCTURED_OUTPUT_INVALID,
                "模型输出结构非法"
        );

        when(persistenceService.startQuestionGeneration(INTERVIEW_ID, 1)).thenReturn(Optional.empty());
        when(blueprintService.prepareFirstQuestion(INTERVIEW_ID)).thenReturn(input);
        when(modelGateway.generate(questionContract, input, TIMEOUT)).thenThrow(failure);

        assertThatThrownBy(() -> service.generateAndPersistFirstQuestion(INTERVIEW_ID, TIMEOUT))
                .isSameAs(failure);

        verify(persistenceService).failQuestionGeneration(
                INTERVIEW_ID, InterviewFailureCode.MODEL_OUTPUT_INVALID
        );
        verify(persistenceService, never()).persistQuestion(
                INTERVIEW_ID, input, null, modelResult()
        );
    }

    private InterviewQuestionInput input(int roundNo) {
        return new InterviewQuestionInput(
                INTERVIEW_ID,
                roundNo,
                InterviewMode.TARGETED_MOCK,
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                2,
                "模式=TARGETED_MOCK；第1题=TECHNICAL_KNOWLEDGE/难度2/技能Java并发",
                "岗位=Java AI应用开发工程师；核心要求=Java并发、Agent可靠性",
                Map.of(CHUNK_ID, "候选人简历记录了Java并发和虚拟线程实践。"),
                roundNo == 1 ? List.of() : List.of("第1题：虚拟线程的适用边界"),
                "验证候选人对Java并发原理、适用边界和失败场景的理解。"
        );
    }

    private InterviewRoleModelGateway.Result<InterviewQuestionDraft> modelResult() {
        InterviewQuestionDraft draft = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                "请结合你的实践说明虚拟线程与平台线程的主要区别。",
                List.of("Java并发"),
                2,
                List.of("说明调度模型", "说明适用边界"),
                true,
                List.of(CHUNK_ID)
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
                "b".repeat(64)
        );
    }
}