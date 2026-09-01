package com.leo.careerforgeai.interview.application.port;

import com.leo.careerforgeai.interview.domain.round.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.round.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.round.InterviewRound;
import com.leo.careerforgeai.shared.actor.ActorId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 定义面试回合、不可变问题、幂等答案和回合CAS的持久化边界
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
public interface InterviewRoundRepository {

    InterviewQuestion claimQuestionReadyRound(InterviewRound round, InterviewQuestion question);

    Optional<InterviewRound> findRound(ActorId ownerId, UUID interviewId, UUID roundId);

    Optional<InterviewQuestion> findQuestionByRound(
            ActorId ownerId,
            UUID interviewId,
            UUID roundId
    );

    InterviewAnswer claimAnswer(InterviewAnswer candidate);

    Optional<InterviewAnswer> findAnswerByQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    );

    Optional<InterviewAnswer> findAnswerByRequest(ActorId ownerId, UUID requestId);

    boolean updateRoundIfVersionMatches(
            ActorId ownerId,
            InterviewRound updatedRound,
            long expectedVersion
    );

    Optional<InterviewRound> findRoundByNumber(ActorId ownerId, UUID interviewId, int roundNo);

    int countQuestions(ActorId ownerId, UUID interviewId);

    int countFollowUps(ActorId ownerId, UUID interviewId);

    List<InterviewQuestion> findQuestions(ActorId ownerId, UUID interviewId);
}