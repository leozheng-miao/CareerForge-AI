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
 * @description: 使用固定threadId启动面试Graph并在答案提交后恢复并行评审流程
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

    public InterviewGraphExecutionService(CurrentActorProvider currentActorProvider,
                                          MockInterviewSessionRepository sessionRepository,
                                          InterviewAnswerSubmissionService answerSubmissionService,
                                          CompiledGraph<InterviewGraphState> graph,
                                          Executor reviewExecutor) {
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

        InterviewGraphState state = graph.lastStateOf(config)
                .orElseThrow(() -> new IllegalStateException("首题生成后缺少Checkpoint"))
                .state();
        requireStateScope(state, session);
        if (state.waitReason().orElse(null) != InterviewWaitReason.WAITING_FOR_ANSWER || state.currentQuestionId().isEmpty()) {
            throw new IllegalStateException("首题Checkpoint没有进入WAITING_FOR_ANSWER");
        }
        return state;
    }

    public InterviewGraphState submitAnswerAndResume(UUID interviewId, int roundNo, UUID questionId, UUID requestId,
                                                     long expectedInterviewVersion, String answerText) {
        InterviewAnswer answer = answerSubmissionService.submit(
                interviewId, roundNo, questionId, requestId, expectedInterviewVersion, answerText
        );
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

        if (current.answerId().isPresent() && !current.answerId().get().equals(answerId)) {
            throw new IllegalStateException("Checkpoint已经绑定其他答案");
        }
        if (current.answerId().filter(answerId::equals).isPresent() && END.equals(checkpoint.next())) return current;

        GraphInput input;
        if (current.waitReason().orElse(null) == InterviewWaitReason.WAITING_FOR_ANSWER) {
            input = GraphInput.resume(InterviewGraphState.answerResumeUpdate(answerId));
        } else if (current.answerId().filter(answerId::equals).isPresent() && current.waitReason().isEmpty()) {
            input = GraphInput.resume();
        } else {
            throw new IllegalStateException("当前Checkpoint不在可恢复状态");
        }

        InterviewGraphState resumed = graph.invoke(input, config)
                .orElseThrow(() -> new IllegalStateException("面试Graph恢复后没有返回State"));
        requireStateScope(resumed, session);

        var completedCheckpoint = graph.lastStateOf(config)
                .orElseThrow(() -> new IllegalStateException("面试Graph恢复后缺少Checkpoint"));
        if (!END.equals(completedCheckpoint.next())) {
            throw new IllegalStateException("并行评审尚未完整汇合");
        }
        if (!resumed.answerId().filter(answerId::equals).isPresent() || resumed.waitReason().isPresent()) {
            throw new IllegalStateException("答案恢复结果不完整");
        }
        return resumed;
    }

    static String threadId(UUID interviewId) {
        return THREAD_PREFIX + interviewId;
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