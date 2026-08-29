package com.leo.careerforgeai.interview.application.graph;

import com.leo.careerforgeai.interview.application.answer.InterviewAnswerSubmissionService;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.bsc.langgraph4j.StateGraph.END;

/**
 * @program: CareerForge-AI
 * @description: 使用固定threadId启动面试Graph并在每次答案提交后恢复到下一次interrupt或终态
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
public class InterviewGraphExecutionService {

    private static final String THREAD_PREFIX = "interview:";

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewAnswerSubmissionService answerSubmissionService;
    private final CompiledGraph<InterviewGraphState> graph;
    private final Executor reviewExecutor;

    public InterviewGraphExecutionService(
            CurrentActorProvider currentActorProvider,
            MockInterviewSessionRepository sessionRepository,
            InterviewAnswerSubmissionService answerSubmissionService,
            CompiledGraph<InterviewGraphState> graph,
            Executor reviewExecutor
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.answerSubmissionService = Objects.requireNonNull(answerSubmissionService, "answerSubmissionService不能为空");
        this.graph = Objects.requireNonNull(graph, "graph不能为空");
        this.reviewExecutor = Objects.requireNonNull(reviewExecutor, "reviewExecutor不能为空");
    }

    public InterviewGraphState start(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        MockInterviewSession session = requireSession(interviewId);
        RunnableConfig config = config(interviewId);

        var existing = graph.lastStateOf(config);
        if (existing.isPresent()) return requireStateScope(existing.get().state(), session);

        var interrupted = graph.invokeFinal(
                GraphInput.args(InterviewGraphState.initialData(interviewId, session.mode(), session.inputSnapshotHash())),
                config
        ).orElseThrow(() -> new IllegalStateException("面试Graph未返回首题中断结果"));

        if (!InterviewGraphWorkflow.GENERATE_AND_PERSIST_QUESTION.equals(interrupted.node())) {
            throw new IllegalStateException("面试Graph没有在首题持久化后暂停");
        }

        var checkpoint = graph.lastStateOf(config)
                .orElseThrow(() -> new IllegalStateException("首题生成后缺少Checkpoint"));
        InterviewGraphState state = requireStateScope(checkpoint.state(), session);
        requireWaitingForAnswer(state, 1);
        if (!InterviewGraphWorkflow.VALIDATE_ANSWER_RESUME.equals(checkpoint.next())) {
            throw new IllegalStateException("首题Checkpoint的下一节点不是答案恢复校验");
        }
        return state;
    }

    public InterviewGraphState submitAnswerAndResume(
            UUID interviewId,
            int roundNo,
            UUID questionId,
            UUID requestId,
            long expectedInterviewVersion,
            String answerText
    ) {
        InterviewAnswer answer = answerSubmissionService.submit(
                interviewId, roundNo, questionId, requestId, expectedInterviewVersion, answerText
        );

        MockInterviewSession session = requireSession(interviewId);
        InterviewGraphState current = graph.lastStateOf(config(interviewId))
                .map(checkpoint -> requireStateScope(checkpoint.state(), session))
                .orElseThrow(() -> new IllegalStateException("当前面试不存在可恢复的Checkpoint"));

        if (current.currentRound() > roundNo) return current;
        if (current.currentRound() != roundNo
                || current.currentQuestionId().filter(questionId::equals).isEmpty()) {
            throw new IllegalStateException("答案提交目标与当前Checkpoint问题不一致");
        }
        return resumeAfterAnswer(interviewId, answer.answerId());
    }

    public InterviewGraphState resumeAfterAnswer(UUID interviewId, UUID answerId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        Objects.requireNonNull(answerId, "answerId不能为空");
        MockInterviewSession session = requireSession(interviewId);
        RunnableConfig config = config(interviewId);

        var checkpoint = graph.lastStateOf(config)
                .orElseThrow(() -> new IllegalStateException("当前面试不存在可恢复的Checkpoint"));
        InterviewGraphState current = requireStateScope(checkpoint.state(), session);

        if (END.equals(checkpoint.next())) return current;
        if (current.answerId().isPresent() && !current.answerId().get().equals(answerId)) {
            throw new IllegalStateException("Checkpoint已经绑定其他答案");
        }

        int answeredRound = current.currentRound();
        GraphInput input;
        if (current.waitReason().orElse(null) == InterviewWaitReason.WAITING_FOR_ANSWER) {
            input = GraphInput.resume(InterviewGraphState.answerResumeUpdate(answerId));
        } else if (current.answerId().filter(answerId::equals).isPresent() && current.waitReason().isEmpty()) {
            input = GraphInput.resume();
        } else {
            throw new IllegalStateException("当前Checkpoint不在可恢复状态");
        }

        graph.invoke(input, config)
                .orElseThrow(() -> new IllegalStateException("面试Graph恢复后没有返回State"));

        var resumedCheckpoint = graph.lastStateOf(config)
                .orElseThrow(() -> new IllegalStateException("面试Graph恢复后缺少Checkpoint"));
        InterviewGraphState resumed = requireStateScope(resumedCheckpoint.state(), requireSession(interviewId));

        if (END.equals(resumedCheckpoint.next())) {
            if (resumed.waitReason().isPresent()) {
                throw new IllegalStateException("终态Checkpoint不能继续等待答案");
            }
            return resumed;
        }

        if (!InterviewGraphWorkflow.VALIDATE_ANSWER_RESUME.equals(resumedCheckpoint.next())) {
            throw new IllegalStateException("面试Graph没有结束或暂停在下一题答案校验前");
        }
        requireWaitingForAnswer(resumed, answeredRound + 1);
        if (resumed.answerId().isPresent() || resumed.routeDecision().isPresent()) {
            throw new IllegalStateException("下一回合Checkpoint仍残留上一回合答案或路由");
        }
        return resumed;
    }

    static String threadId(UUID interviewId) {
        return THREAD_PREFIX + interviewId;
    }

    private void requireWaitingForAnswer(InterviewGraphState state, int expectedRound) {
        if (state.currentRound() != expectedRound
                || state.currentQuestionId().isEmpty()
                || state.waitReason().orElse(null) != InterviewWaitReason.WAITING_FOR_ANSWER) {
            throw new IllegalStateException("Checkpoint没有进入预期回合的WAITING_FOR_ANSWER");
        }
    }

    private RunnableConfig config(UUID interviewId) {
        return RunnableConfig.builder()
                .threadId(threadId(interviewId))
                .addParallelNodeExecutor(InterviewGraphWorkflow.PREPARE_REVIEWS, reviewExecutor)
                .build();
    }

    private MockInterviewSession requireSession(UUID interviewId) {
        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        return sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
    }

    private InterviewGraphState requireStateScope(InterviewGraphState state, MockInterviewSession session) {
        if (!state.interviewId().equals(session.interviewId())
                || state.mode() != session.mode()
                || !state.inputSnapshotHash().equals(session.inputSnapshotHash())) {
            throw new IllegalStateException("Checkpoint与当前MySQL面试事实不一致");
        }
        return state;
    }
}