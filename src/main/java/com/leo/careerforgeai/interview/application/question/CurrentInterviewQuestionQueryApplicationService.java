package com.leo.careerforgeai.interview.application.question;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 从MySQL读取当前用户正在等待回答的最新模拟面试问题
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class CurrentInterviewQuestionQueryApplicationService {

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewSessionRepository sessionRepository;
    private final InterviewRoundRepository roundRepository;

    public CurrentInterviewQuestionQueryApplicationService(CurrentActorProvider currentActorProvider,
                                                           MockInterviewSessionRepository sessionRepository,
                                                           InterviewRoundRepository roundRepository) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.roundRepository = Objects.requireNonNull(roundRepository, "roundRepository不能为空");
    }

    @Transactional(readOnly = true)
    public CurrentQuestion getCurrent(UUID interviewId) {
        Objects.requireNonNull(interviewId, "interviewId不能为空");
        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");

        MockInterviewSession session = sessionRepository.findById(ownerId, interviewId)
                .orElseThrow(() -> new MockInterviewNotFoundException(interviewId));
        if (!ownerId.equals(session.ownerId())) throw new MockInterviewNotFoundException(interviewId);
        if (session.status() != InterviewStatus.WAITING_FOR_ANSWER) {
            throw new CurrentInterviewQuestionUnavailableException(interviewId, session.status());
        }

        List<InterviewQuestion> questions = roundRepository.findQuestions(ownerId, interviewId);
        if (questions.isEmpty()) {
            throw new IllegalStateException("WAITING_FOR_ANSWER面试缺少问题事实");
        }

        InterviewQuestion question = questions.getLast();
        InterviewRound round = roundRepository.findRound(ownerId, interviewId, question.roundId())
                .orElseThrow(() -> new IllegalStateException("当前问题缺少所属回合事实"));

        requireCurrentQuestionScope(ownerId, session, round, question, questions.size());
        return new CurrentQuestion(session, round, question);
    }

    private void requireCurrentQuestionScope(ActorId ownerId,
                                             MockInterviewSession session,
                                             InterviewRound round,
                                             InterviewQuestion question,
                                             int questionCount) {
        if (!ownerId.equals(round.ownerId())
                || !ownerId.equals(question.ownerId())
                || !session.interviewId().equals(round.interviewId())
                || !session.interviewId().equals(question.interviewId())
                || !round.roundId().equals(question.roundId())
                || round.roundNo() != questionCount
                || round.status() != InterviewRoundStatus.QUESTION_READY) {
            throw new IllegalStateException("当前问题、回合、面试状态或owner作用域不一致");
        }
    }

    /**
     * @program: CareerForge-AI
     * @description: 保存当前待回答问题对应的面试、回合和问题事实
     * @author: Miao Zheng
     * @date: 2026-08-30
     * @param session 当前模拟面试事实
     * @param round 当前待回答回合事实
     * @param question 当前待回答问题事实
     **/
    public record CurrentQuestion(
            MockInterviewSession session,
            InterviewRound round,
            InterviewQuestion question
    ) {

        public CurrentQuestion {
            Objects.requireNonNull(session, "session不能为空");
            Objects.requireNonNull(round, "round不能为空");
            Objects.requireNonNull(question, "question不能为空");
        }
    }
}