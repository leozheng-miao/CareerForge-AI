package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.domain.InterviewAnswer;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRoundStatus;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewRoundFactPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewAnswerEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewQuestionEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.entity.InterviewRoundEntity;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewRoundFactMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 事务保存问题就绪回合、幂等答案并执行owner受控回合CAS
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisInterviewRoundAdapter implements InterviewRoundRepository {

    private final InterviewRoundFactMapper mapper;
    private final InterviewRoundFactPersistenceConverter converter;

    public MyBatisInterviewRoundAdapter(
            InterviewRoundFactMapper mapper,
            InterviewRoundFactPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    @Transactional
    public InterviewQuestion claimQuestionReadyRound(
            InterviewRound round,
            InterviewQuestion question
    ) {
        requireQuestionScope(round, question);
        if (round.status() != InterviewRoundStatus.QUESTION_READY || round.version() != 0) {
            throw new IllegalArgumentException("新回合必须处于QUESTION_READY且version为0");
        }

        mapper.claimRound(converter.toEntity(round));
        InterviewRoundEntity storedRound = mapper.findRoundByNumber(
                round.ownerId().value(),
                round.interviewId().toString(),
                round.roundNo()
        );
        if (storedRound == null) {
            throw new IllegalStateException("回合认领后无法按逻辑回合号读取");
        }

        if (!storedRound.getRoundId().equals(round.roundId().toString())) {
            return Optional.ofNullable(mapper.findQuestionByRound(
                            storedRound.getOwnerId(),
                            storedRound.getInterviewId(),
                            storedRound.getRoundId()
                    ))
                    .map(converter::toDomain)
                    .orElseThrow(() -> new IllegalStateException("已存在的逻辑回合缺少问题事实"));
        }

        mapper.claimQuestion(converter.toEntity(question));
        return Optional.ofNullable(mapper.findQuestionByRound(
                        round.ownerId().value(),
                        round.interviewId().toString(),
                        round.roundId().toString()
                ))
                .map(converter::toDomain)
                .orElseThrow(() -> new IllegalStateException("问题认领后无法读取"));
    }

    @Override
    public Optional<InterviewRound> findRound(
            ActorId ownerId,
            UUID interviewId,
            UUID roundId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(roundId, "roundId不能为空");
        return Optional.ofNullable(mapper.findRound(
                ownerId.value(),
                interviewId.toString(),
                roundId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<InterviewRound> findRoundByNumber(
            ActorId ownerId,
            UUID interviewId,
            int roundNo
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        if (roundNo < 1) throw new IllegalArgumentException("roundNo必须从1开始");
        return Optional.ofNullable(mapper.findRoundByNumber(
                ownerId.value(),
                interviewId.toString(),
                roundNo
        )).map(converter::toDomain);
    }

    @Override
    public Optional<InterviewQuestion> findQuestionByRound(
            ActorId ownerId,
            UUID interviewId,
            UUID roundId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(roundId, "roundId不能为空");
        return Optional.ofNullable(mapper.findQuestionByRound(
                ownerId.value(),
                interviewId.toString(),
                roundId.toString()
        )).map(converter::toDomain);
    }

    @Override
    @Transactional
    public InterviewAnswer claimAnswer(InterviewAnswer candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        mapper.claimAnswer(converter.toEntity(candidate));

        InterviewAnswerEntity stored = mapper.findAnswerByRequest(
                candidate.ownerId().value(),
                candidate.requestId().toString()
        );
        if (stored == null) {
            stored = mapper.findAnswerByQuestion(
                    candidate.ownerId().value(),
                    candidate.interviewId().toString(),
                    candidate.questionId().toString()
            );
        }
        if (stored == null) {
            throw new IllegalStateException("答案认领后无法按请求或问题读取，可能发生answerId冲突");
        }
        return converter.toDomain(stored);
    }

    @Override
    public Optional<InterviewAnswer> findAnswerByQuestion(
            ActorId ownerId,
            UUID interviewId,
            UUID questionId
    ) {
        requireOwnerAndId(ownerId, interviewId, "interviewId");
        Objects.requireNonNull(questionId, "questionId不能为空");
        return Optional.ofNullable(mapper.findAnswerByQuestion(
                ownerId.value(),
                interviewId.toString(),
                questionId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<InterviewAnswer> findAnswerByRequest(ActorId ownerId, UUID requestId) {
        requireOwnerAndId(ownerId, requestId, "requestId");
        return Optional.ofNullable(mapper.findAnswerByRequest(
                ownerId.value(),
                requestId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public boolean updateRoundIfVersionMatches(
            ActorId ownerId,
            InterviewRound updatedRound,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedRound, "updatedRound不能为空");
        if (!ownerId.equals(updatedRound.ownerId())) {
            throw new IllegalArgumentException("ownerId与回合归属不一致");
        }
        if (expectedVersion < 0 || updatedRound.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("回合version不符合CAS递增规则");
        }

        int affectedRows = mapper.updateRoundIfVersionMatches(
                updatedRound.roundId().toString(),
                updatedRound.interviewId().toString(),
                ownerId.value(),
                updatedRound.status().name(),
                updatedRound.version(),
                updatedRound.updatedAt(),
                updatedRound.answeredAt(),
                updatedRound.reviewedAt(),
                expectedVersion
        );
        if (affectedRows > 1) throw new IllegalStateException("回合CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    private static void requireQuestionScope(
            InterviewRound round,
            InterviewQuestion question
    ) {
        Objects.requireNonNull(round, "round不能为空");
        Objects.requireNonNull(question, "question不能为空");
        if (!round.roundId().equals(question.roundId())
                || !round.interviewId().equals(question.interviewId())
                || !round.ownerId().equals(question.ownerId())) {
            throw new IllegalArgumentException("问题与回合的round、interview或owner不一致");
        }
    }

    private static void requireOwnerAndId(ActorId ownerId, UUID id, String fieldName) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(id, fieldName + "不能为空");
    }
}