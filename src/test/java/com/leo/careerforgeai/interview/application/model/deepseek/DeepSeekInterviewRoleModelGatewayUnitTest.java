package com.leo.careerforgeai.interview.application.model.deepseek;

import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.interview.infrastructure.model.deepseek.DeepSeekInterviewRoleModelGateway;
import com.leo.careerforgeai.model.application.ModelGateway;
import com.leo.careerforgeai.model.application.reliability.ModelCallBulkhead;
import com.leo.careerforgeai.model.application.reliability.ModelCircuitBreaker;
import com.leo.careerforgeai.model.application.reliability.ModelReliabilityMetrics;
import com.leo.careerforgeai.model.config.ModelCallBulkheadProperties;
import com.leo.careerforgeai.model.config.ModelReliabilityProperties;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.model.exception.ModelException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 确定性验证面试角色结构修复、业务校验和模型调用计数边界
 * @author: Miao Zheng
 * @date: 2026-09-01
 */
class DeepSeekInterviewRoleModelGatewayUnitTest {

    private ModelGateway modelGateway;
    private ModelReliabilityMetrics metrics;
    private InterviewRoleModelGateway gateway;
    private InterviewQuestionRoleContract contract;
    private ValidatorFactory validatorFactory;

    @BeforeEach
    void setUp() {
        modelGateway = mock(ModelGateway.class);
        metrics = new ModelReliabilityMetrics();

        ModelReliabilityProperties reliabilityProperties =
                new ModelReliabilityProperties(
                        1,
                        Duration.ofMillis(1),
                        Duration.ofMillis(1),
                        1.0,
                        Duration.ofSeconds(1),
                        10,
                        10,
                        100.0F,
                        Duration.ofSeconds(30),
                        1
                );

        validatorFactory = Validation.buildDefaultValidatorFactory();
        contract = new InterviewQuestionRoleContract(
                validatorFactory.getValidator()
        );

        gateway = new DeepSeekInterviewRoleModelGateway(
                modelGateway,
                JsonMapper.builder().build(),
                new ModelCircuitBreaker(
                        reliabilityProperties,
                        Clock.systemUTC(),
                        metrics
                ),
                new ModelCallBulkhead(
                        new ModelCallBulkheadProperties(1)
                ),
                reliabilityProperties,
                metrics
        );
    }

    @AfterEach
    void closeResources() {
        validatorFactory.close();
    }

    /** 验证首次结构失败后只执行一次修复并聚合两次调用用量。 */
    @Test
    void shouldRepairInitialStructureFailureOnce() {
        when(modelGateway.chat(any())).thenReturn(
                response(
                        "request-initial-invalid",
                        "{",
                        new ModelUsage(20, 5, 25)
                ),
                response(
                        "request-repaired",
                        output(2),
                        new ModelUsage(30, 10, 40)
                )
        );

        InterviewRoleModelGateway.Result<InterviewQuestionDraft> result =
                gateway.generate(
                        contract,
                        input(),
                        Duration.ofSeconds(5)
                );

        assertThat(result.requestId()).isEqualTo("request-repaired");
        assertThat(result.model()).isEqualTo("deepseek-v4-flash");
        assertThat(result.promptVersion()).isEqualTo("interviewer-v1");
        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(result.repaired()).isTrue();
        assertThat(result.usage()).isEqualTo(
                new ModelUsage(50, 15, 65)
        );
        assertThat(result.responseHash()).matches("[0-9a-f]{64}");
        assertThat(result.output().difficulty()).isEqualTo(2);

        ArgumentCaptor<ModelRequest> requestCaptor =
                ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelGateway, times(2)).chat(requestCaptor.capture());

        List<ModelRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.outputFormat())
                    .isEqualTo(ModelOutputFormat.JSON_OBJECT);
            assertThat(request.maxOutputTokens()).isEqualTo(1_200);
            assertThat(request.temperature()).isZero();
            assertThat(request.timeout()).isPositive();
        });
        assertThat(requests.get(1).messages().getFirst().content())
                .contains("唯一一次结构修复");

        assertThat(metrics.snapshot().logicalCalls()).isEqualTo(2);
        assertThat(metrics.snapshot().retryAttempts()).isZero();
    }

    /** 验证修复输出仍非法时失败关闭且不执行第三次调用。 */
    @Test
    void shouldFailClosedWhenRepairIsStillInvalid() {
        when(modelGateway.chat(any())).thenReturn(
                response(
                        "request-initial-invalid",
                        "{",
                        new ModelUsage(20, 5, 25)
                ),
                response(
                        "request-repair-invalid",
                        "not-json",
                        new ModelUsage(30, 10, 40)
                )
        );

        assertThatThrownBy(() -> gateway.generate(
                contract,
                input(),
                Duration.ofSeconds(5)
        )).isInstanceOfSatisfying(
                ModelException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(
                                ModelErrorType.STRUCTURED_OUTPUT_INVALID
                        )
        ).hasMessage("面试角色输出不是合法目标结构");

        verify(modelGateway, times(2)).chat(any(ModelRequest.class));
        assertThat(metrics.snapshot().logicalCalls()).isEqualTo(2);
        assertThat(metrics.snapshot().retryAttempts()).isZero();
    }

    /** 验证结构合法但违反业务契约时不得通过重新生成掩盖错误。 */
    @Test
    void shouldNotRepairBusinessContractViolation() {
        when(modelGateway.chat(any())).thenReturn(
                response(
                        "request-business-invalid",
                        output(3),
                        new ModelUsage(20, 5, 25)
                )
        );

        assertThatThrownBy(() -> gateway.generate(
                contract,
                input(),
                Duration.ofSeconds(5)
        )).isInstanceOfSatisfying(
                ModelException.class,
                exception -> assertThat(exception.getErrorType())
                        .isEqualTo(
                                ModelErrorType.STRUCTURED_OUTPUT_INVALID
                        )
        ).hasMessage("面试角色输出违反服务端业务契约");

        verify(modelGateway).chat(any(ModelRequest.class));
        assertThat(metrics.snapshot().logicalCalls()).isEqualTo(1);
        assertThat(metrics.snapshot().retryAttempts()).isZero();
    }

    private InterviewQuestionInput input() {
        return new InterviewQuestionInput(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000101"
                ),
                1,
                InterviewMode.TARGETED_MOCK,
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                2,
                "第一轮验证Java并发基础。",
                "Java后端工程师，需要掌握并发和可靠性。",
                Map.of(),
                List.of(),
                "验证候选人能否说明线程池资源清理边界。"
        );
    }

    private ModelResponse response(
            String requestId,
            String content,
            ModelUsage usage
    ) {
        return new ModelResponse(
                requestId,
                "deepseek-v4-flash",
                content,
                usage
        );
    }

    private String output(int difficulty) {
        return """
                {
                  "questionType":"TECHNICAL_KNOWLEDGE",
                  "question":"线程池复用线程时为什么需要清理ThreadLocal？",
                  "targetSkills":["JAVA_CONCURRENCY"],
                  "difficulty":%d,
                  "evaluationPoints":["说明线程复用和残留数据风险"],
                  "followUpAllowed":true,
                  "evidenceReferenceIds":[]
                }
                """.formatted(difficulty);
    }
}