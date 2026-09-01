package com.leo.careerforgeai.interview.application.model.deepseek;

import com.leo.careerforgeai.interview.application.model.review.EvidenceReviewInput;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionInput;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportDraft;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportInput;
import com.leo.careerforgeai.interview.application.model.common.InterviewRoleContract;
import com.leo.careerforgeai.interview.application.model.review.TechnicalReviewInput;
import com.leo.careerforgeai.interview.application.model.review.EvidenceReviewRoleContract;
import com.leo.careerforgeai.interview.application.model.question.InterviewQuestionRoleContract;
import com.leo.careerforgeai.interview.application.model.report.InterviewReportRoleContract;
import com.leo.careerforgeai.interview.application.model.review.TechnicalReviewRoleContract;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestionType;
import com.leo.careerforgeai.model.exception.ModelException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 使用固定合成输入重复验证四个面试角色的真实DeepSeek结构化输出稳定性
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@SpringBootTest(properties = {
        "careerforge.persistence.enabled=false",
        "spring.flyway.enabled=false",
        "spring.ai.chat.client.enabled=false",
        "spring.ai.model.chat=none"
})
class InterviewRoleModelStabilitySmoke {

    private static final int REPEATS_PER_INPUT = 3;
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final String CHUNK_A = "a".repeat(64);
    private static final String CHUNK_B = "b".repeat(64);

    @Autowired
    private InterviewRoleModelGateway modelGateway;

    @Autowired
    private InterviewQuestionRoleContract questionContract;

    @Autowired
    private TechnicalReviewRoleContract technicalContract;

    @Autowired
    private EvidenceReviewRoleContract evidenceContract;

    @Autowired
    private InterviewReportRoleContract reportContract;

    @Test
    void shouldMeasureFourRoleStructuredOutputStability() {
        int failures = 0;
        failures += runRole("CP4-INTERVIEWER", questionContract, questionInputs());
        failures += runRole("CP4-TECHNICAL", technicalContract, technicalInputs());
        failures += runRole("CP4-EVIDENCE", evidenceContract, evidenceInputs());
        failures += runRole("CP4-REPORT", reportContract, reportInputs());

        assertThat(failures)
                .as("四角色共36次固定真实调用的最终失败次数")
                .isZero();
    }

    @Test
    void shouldGenerateResumeGroundedFirstQuestionOnce() {
        InterviewQuestionInput input = new InterviewQuestionInput(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                1,
                InterviewMode.TARGETED_MOCK,
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                2,
                "模式=TARGETED_MOCK；最大问题数=3；问题计划="
                        + "第1题=TECHNICAL_KNOWLEDGE/难度2/技能Java并发；"
                        + "第2题=SYSTEM_DESIGN/难度2/技能MySQL；"
                        + "第3题=TECHNICAL_KNOWLEDGE/难度3/技能Agent可靠性",
                "岗位=Java AI应用开发工程师；核心要求=Java并发、MySQL、Agent可靠性",
                Map.of(
                        CHUNK_A,
                        "候选人简历记录：在Java项目中使用虚拟线程执行可阻塞任务，"
                                + "通过超时、并发准入和结构化日志处理资源耗尽与故障定位。"
                ),
                List.of(),
                "根据冻结简历内容验证候选人对Java并发原理、适用边界和失败场景的理解。"
        );

        InterviewRoleModelGateway.Result<InterviewQuestionDraft> result =
                modelGateway.generate(questionContract, input, CALL_TIMEOUT);

        assertThat(result.output().questionType())
                .isEqualTo(InterviewQuestionType.TECHNICAL_KNOWLEDGE);
        assertThat(result.output().difficulty()).isEqualTo(2);
        assertThat(result.output().question()).isNotBlank();
        assertThat(result.output().evidenceReferenceIds())
                .allMatch(input.evidenceByChunkId()::containsKey);

        System.out.printf(
                Locale.ROOT,
                "caseId=CP5-RESUME-FIRST-QUESTION, status=SUCCEEDED, "
                        + "model=%s, promptVersion=%s, modelCallCount=%d, repaired=%s, "
                        + "totalTokens=%d, durationMs=%d, responseHash=%s%n",
                result.model(),
                result.promptVersion(),
                result.modelCallCount(),
                result.repaired(),
                result.usage().totalTokens(),
                result.durationMs(),
                result.responseHash()
        );
    }

    @Test
    void shouldNotKeepTechnicalGapResolvedByLaterRound() {
        InterviewReportInput input = new InterviewReportInput(
                id("cp12-report-cross-round-resolution"),
                "Java后端工程师，重点考察Java集合与并发实现。",
                List.of(
                        """
                        回合：1
                        目标技能：HashMap、ConcurrentHashMap
                        回答：能够说明HashMap数组、链表和红黑树结构，但没有说明树化阈值原因和并发扩容。
                        已覆盖要点：HashMap基本结构
                        错误或遗漏：未说明树化阈值8的设计原因；未说明ConcurrentHashMap协助扩容
                        """,
                        """
                        回合：2
                        是否追问：true
                        目标技能：HashMap、ConcurrentHashMap
                        回答：树化阈值8来自哈希均匀条件下的泊松分布低概率设计；ConcurrentHashMap扩容时线程通过CAS认领区间，遇到ForwardingNode可以协助迁移，并使用synchronized锁桶头保证桶迁移安全。
                        已覆盖要点：树化阈值8及泊松分布原因；CAS认领迁移区间；ForwardingNode协助迁移；synchronized锁桶头
                        错误或遗漏：无
                        """
                ),
                List.of("能够说明树化阈值8及其概率设计依据"),
                List.of(),
                false
        );

        InterviewRoleModelGateway.Result<InterviewReportDraft> result =
                modelGateway.generate(reportContract, input, CALL_TIMEOUT);

        assertThat(result.promptVersion()).isEqualTo("report-coach-v7");
        assertThat(result.output().technicalGaps())
                .noneMatch(gap -> gap.contains("树化阈值8")
                        || gap.contains("泊松分布")
                        || gap.contains("协助扩容")
                        || gap.contains("ForwardingNode"));
        assertThat(result.output().proposedTrainingPlanAdjustments()).isEmpty();

        System.out.printf(
                Locale.ROOT,
                "caseId=CP12-REPORT-CROSS-ROUND, status=SUCCEEDED, promptVersion=%s, "
                        + "modelCallCount=%d, totalTokens=%d, durationMs=%d, responseHash=%s%n",
                result.promptVersion(),
                result.modelCallCount(),
                result.usage().totalTokens(),
                result.durationMs(),
                result.responseHash()
        );
    }

    private <I, O> int runRole(
            String casePrefix,
            InterviewRoleContract<I, O> contract,
            List<I> inputs
    ) {
        int totalRuns = inputs.size() * REPEATS_PER_INPUT;
        int successes = 0;
        int failures = 0;
        int repaired = 0;
        long successfulTotalTokens = 0;
        List<Long> successfulDurations = new ArrayList<>();

        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            I input = inputs.get(inputIndex);
            String caseId = "%s-%02d".formatted(casePrefix, inputIndex + 1);

            for (int repeat = 1; repeat <= REPEATS_PER_INPUT; repeat++) {
                long startedNanos = System.nanoTime();
                try {
                    InterviewRoleModelGateway.Result<O> result =
                            modelGateway.generate(contract, input, CALL_TIMEOUT);

                    successes++;
                    if (result.repaired()) repaired++;
                    successfulTotalTokens += result.usage().totalTokens();
                    successfulDurations.add(result.durationMs());

                    System.out.printf(
                            Locale.ROOT,
                            "caseId=%s, repeat=%d/%d, status=SUCCEEDED, model=%s, promptVersion=%s, requestId=%s, modelCallCount=%d, repaired=%s, inputTokens=%d, outputTokens=%d, totalTokens=%d, durationMs=%d, responseHash=%s%n",
                            caseId,
                            repeat,
                            REPEATS_PER_INPUT,
                            result.model(),
                            result.promptVersion(),
                            result.requestId(),
                            result.modelCallCount(),
                            result.repaired(),
                            result.usage().inputTokens(),
                            result.usage().outputTokens(),
                            result.usage().totalTokens(),
                            result.durationMs(),
                            result.responseHash()
                    );
                } catch (RuntimeException exception) {
                    if (Thread.currentThread().isInterrupted()) throw exception;
                    failures++;
                    long durationMs =
                            Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

                    System.out.printf(
                            Locale.ROOT,
                            "caseId=%s, repeat=%d/%d, status=FAILED, errorType=%s, durationMs=%d, tokenObservation=see-safe-gateway-log%n",
                            caseId,
                            repeat,
                            REPEATS_PER_INPUT,
                            errorType(exception),
                            Math.max(0, durationMs)
                    );
                }
            }
        }

        double successRate = percentage(successes, totalRuns);
        double repairRate = percentage(repaired, successes);
        long averageTokens = successes == 0 ? 0 : successfulTotalTokens / successes;

        System.out.printf(
                Locale.ROOT,
                "role=%s, totalRuns=%d, successes=%d, failures=%d, repaired=%d, successRate=%.2f%%, repairRateAmongSuccesses=%.2f%%, successfulLatencySamples=%d, p50DurationMs=%d, p95DurationMs=%d, averageSuccessfulTokens=%d, successfulTotalTokens=%d%n",
                contract.role(),
                totalRuns,
                successes,
                failures,
                repaired,
                successRate,
                repairRate,
                successfulDurations.size(),
                percentile(successfulDurations, 0.50),
                percentile(successfulDurations, 0.95),
                averageTokens,
                successfulTotalTokens
        );
        return failures;
    }

    private List<InterviewQuestionInput> questionInputs() {
        return List.of(
                new InterviewQuestionInput(
                        id("cp4-question-01"),
                        1,
                        InterviewMode.TARGETED_MOCK,
                        InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                        2,
                        "固定三轮面试，第一轮验证Java并发基础。",
                        "Java后端与AI应用开发工程师，需要掌握并发、数据库和Agent可靠性。",
                        Map.of(),
                        List.of(),
                        "验证候选人是否理解synchronized与ReentrantLock的主要差异。"
                ),
                new InterviewQuestionInput(
                        id("cp4-question-02"),
                        2,
                        InterviewMode.TARGETED_MOCK,
                        InterviewQuestionType.PROJECT_DEEP_DIVE,
                        3,
                        "第二轮深挖候选项目中的Redis限流设计，允许引用唯一冻结证据片段。",
                        "Java后端与AI应用开发工程师，需要说明项目决策、失败处理和验证证据。",
                        Map.of(CHUNK_A, "项目材料记录：Redis限流支持失败降级，并通过固定场景验证限流边界。"),
                        List.of("第一轮已询问Java锁机制。"),
                        "验证候选人能否解释Redis限流方案的边界和降级策略。"
                ),
                new InterviewQuestionInput(
                        id("cp4-question-03"),
                        3,
                        InterviewMode.GAP_DRILL,
                        InterviewQuestionType.SYSTEM_DESIGN,
                        4,
                        "专项训练高并发任务系统设计，重点覆盖幂等、超时和故障恢复。",
                        "Java后端与AI应用开发工程师，需要设计可恢复的异步任务系统。",
                        Map.of(),
                        List.of("已询问锁机制。", "已询问Redis限流项目。"),
                        "验证候选人能否权衡MySQL、Redis、消息投递和状态机的一致性。"
                )
        );
    }

    private List<TechnicalReviewInput> technicalInputs() {
        return List.of(
                new TechnicalReviewInput(
                        id("cp4-technical-interview-01"),
                        1,
                        id("cp4-technical-question-01"),
                        id("cp4-technical-answer-01"),
                        "synchronized与ReentrantLock的主要差异是什么？",
                        "两者都能提供互斥。synchronized由JVM管理锁释放；ReentrantLock需要finally显式释放，并支持可中断获取、公平锁和多个Condition。",
                        List.of("理解Java并发同步机制", "能够说明异常情况下的资源释放"),
                        List.of("CORRECTNESS", "DEPTH", "CLARITY"),
                        List.of(
                                "CORRECTNESS：事实正确且不存在关键错误。",
                                "DEPTH：能够说明能力差异和适用边界。",
                                "CLARITY：表达有结构且直接回答问题。"
                        )
                ),
                new TechnicalReviewInput(
                        id("cp4-technical-interview-02"),
                        2,
                        id("cp4-technical-question-02"),
                        id("cp4-technical-answer-02"),
                        "为什么接口幂等不能只依赖Redis短期去重键？",
                        "Redis键过期或数据丢失后请求可能再次进入，因此业务真相源还需要唯一约束、请求指纹和状态校验。Redis适合快速拦截，但不能替代MySQL事实。",
                        List.of("理解接口幂等", "能够区分缓存和业务真相源"),
                        List.of("CORRECTNESS", "TRADE_OFF", "FAILURE_HANDLING"),
                        List.of(
                                "CORRECTNESS：说明Redis不能替代持久化唯一约束。",
                                "TRADE_OFF：说明Redis快速拦截的收益。",
                                "FAILURE_HANDLING：覆盖过期、丢失和重复请求。"
                        )
                ),
                new TechnicalReviewInput(
                        id("cp4-technical-interview-03"),
                        3,
                        id("cp4-technical-question-03"),
                        id("cp4-technical-answer-03"),
                        "如何设计可暂停恢复的多Agent模拟面试？",
                        "MySQL保存问题、答案、评审和报告事实；Checkpoint只保存流程位置。每个有副作用节点使用稳定幂等键，恢复前重读MySQL。等待用户回答时中断执行，不占用线程和并发许可。",
                        List.of("理解工作流恢复", "理解业务事实与Checkpoint边界"),
                        List.of("CONSISTENCY", "RECOVERY", "RESOURCE_SAFETY"),
                        List.of(
                                "CONSISTENCY：业务事实以MySQL为准。",
                                "RECOVERY：说明Checkpoint和节点幂等。",
                                "RESOURCE_SAFETY：等待期间不占用执行资源。"
                        )
                )
        );
    }

    private List<EvidenceReviewInput> evidenceInputs() {
        return List.of(
                new EvidenceReviewInput(
                        id("cp4-evidence-interview-01"),
                        1,
                        id("cp4-evidence-question-01"),
                        id("cp4-evidence-answer-01"),
                        "解释Java volatile的可见性语义。",
                        "volatile写与后续读之间建立happens-before关系，但volatile不能让复合操作自动具备原子性。",
                        Map.of()
                ),
                new EvidenceReviewInput(
                        id("cp4-evidence-interview-02"),
                        2,
                        id("cp4-evidence-question-02"),
                        id("cp4-evidence-answer-02"),
                        "你在项目中如何限制同一用户的并发任务？",
                        "项目使用owner级Semaphore限制同一用户最多同时执行两个任务，并通过自动测试验证许可最终释放。",
                        Map.of(
                                CHUNK_A,
                                "项目记录：owner级Semaphore的最大并发数为2，并验证成功、异常和取消路径最终释放许可。"
                        )
                ),
                new EvidenceReviewInput(
                        id("cp4-evidence-interview-03"),
                        3,
                        id("cp4-evidence-question-03"),
                        id("cp4-evidence-answer-03"),
                        "项目压测达到过怎样的稳定吞吐量？",
                        "项目已经稳定达到每秒5000个请求。",
                        Map.of(
                                CHUNK_B,
                                "压测记录：当前固定环境的稳定吞吐量约为每秒120个请求，尚未验证每秒5000个请求。"
                        )
                )
        );
    }

    private List<InterviewReportInput> reportInputs() {
        return List.of(
                new InterviewReportInput(
                        id("cp4-report-01"),
                        "Java后端与AI应用开发工程师，重点考察并发、数据库和Agent可靠性。",
                        List.of(
                                "第一轮：锁机制回答事实正确，能够说明ReentrantLock的可中断和Condition能力。",
                                "第二轮：幂等回答区分了Redis快速拦截和MySQL唯一约束。",
                                "第三轮：系统设计覆盖MySQL事实、Checkpoint进度和恢复前重读。"
                        )
                ),
                new InterviewReportInput(
                        id("cp4-report-02"),
                        "Java Agent应用开发工程师，重点考察结构化输出、工具调用和安全边界。",
                        List.of(
                                "第一轮：能够解释模型输出必须经过Java结构和业务校验。",
                                "第二轮：能够区分网络重试与结构修复，但没有量化额外Token成本。",
                                "第三轮：能够说明模型不能直接写Memory或训练计划。"
                        )
                ),
                new InterviewReportInput(
                        id("cp4-report-03"),
                        "Java高并发后端工程师，重点考察可靠性、可观测性和故障恢复。",
                        List.of(
                                "第一轮：说明了超时、有限重试、Bulkhead和熔断器的职责。",
                                "第二轮：回答缺少熔断半开状态的具体恢复过程。",
                                "第三轮：能够使用请求ID、错误分类、响应Hash和Token定位模型失败。"
                        )
                )
        );
    }

    private UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String errorType(RuntimeException exception) {
        return exception instanceof ModelException modelException
                ? modelException.getErrorType().name()
                : exception.getClass().getSimpleName();
    }

    private double percentage(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private long percentile(List<Long> samples, double percentile) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = samples.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }
}