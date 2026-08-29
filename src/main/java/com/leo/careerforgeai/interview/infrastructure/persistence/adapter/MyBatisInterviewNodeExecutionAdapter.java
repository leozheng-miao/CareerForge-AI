package com.leo.careerforgeai.interview.infrastructure.persistence.adapter;

import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecution;
import com.leo.careerforgeai.interview.domain.InterviewNodeExecutionStatus;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewNodeExecutionPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.InterviewNodeExecutionMapper;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 认领、owner隔离查询并CAS更新Graph节点执行记录
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Repository
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MyBatisInterviewNodeExecutionAdapter
        implements InterviewNodeExecutionRepository {

    private final InterviewNodeExecutionMapper mapper;
    private final InterviewNodeExecutionPersistenceConverter converter;

    public MyBatisInterviewNodeExecutionAdapter(
            InterviewNodeExecutionMapper mapper,
            InterviewNodeExecutionPersistenceConverter converter
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper不能为空");
        this.converter = Objects.requireNonNull(converter, "converter不能为空");
    }

    @Override
    public InterviewNodeExecution claim(InterviewNodeExecution candidate) {
        Objects.requireNonNull(candidate, "candidate不能为空");
        if (candidate.status() != InterviewNodeExecutionStatus.RUNNING || candidate.version() != 0) {
            throw new IllegalArgumentException("新节点执行必须处于RUNNING且version为0");
        }

        mapper.claim(converter.toEntity(candidate));
        return findByIdentity(
                candidate.ownerId(),
                candidate.interviewId(),
                candidate.roundNo(),
                candidate.nodeName(),
                candidate.inputHash()
        ).orElseThrow(() -> new IllegalStateException(
                "节点执行认领后无法按逻辑身份读取，可能发生executionId冲突"
        ));
    }

    @Override
    public Optional<InterviewNodeExecution> findById(
            ActorId ownerId,
            UUID interviewId,
            UUID executionId
    ) {
        requireScope(ownerId, interviewId);
        Objects.requireNonNull(executionId, "executionId不能为空");
        return Optional.ofNullable(mapper.findById(
                ownerId.value(),
                interviewId.toString(),
                executionId.toString()
        )).map(converter::toDomain);
    }

    @Override
    public Optional<InterviewNodeExecution> findByIdentity(
            ActorId ownerId,
            UUID interviewId,
            int roundNo,
            String nodeName,
            String inputHash
    ) {
        requireScope(ownerId, interviewId);
        if (roundNo < 0) throw new IllegalArgumentException("roundNo不能小于0");
        if (nodeName == null || nodeName.isBlank()) {
            throw new IllegalArgumentException("nodeName不能为空");
        }
        if (inputHash == null || inputHash.isBlank()) {
            throw new IllegalArgumentException("inputHash不能为空");
        }

        return Optional.ofNullable(mapper.findByIdentity(
                ownerId.value(),
                interviewId.toString(),
                roundNo,
                nodeName,
                inputHash
        )).map(converter::toDomain);
    }

    @Override
    public boolean updateIfVersionMatches(
            ActorId ownerId,
            InterviewNodeExecution updatedExecution,
            long expectedVersion
    ) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(updatedExecution, "updatedExecution不能为空");
        if (!ownerId.equals(updatedExecution.ownerId())) {
            throw new IllegalArgumentException("ownerId与节点执行归属不一致");
        }
        if (expectedVersion < 0 || updatedExecution.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("节点执行version不符合CAS递增规则");
        }

        var usage = updatedExecution.modelUsage();
        int affectedRows = mapper.updateIfVersionMatches(
                updatedExecution.executionId().toString(),
                updatedExecution.interviewId().toString(),
                ownerId.value(),
                updatedExecution.status().name(),
                updatedExecution.outputReferenceId(),
                updatedExecution.modelRequestId(),
                updatedExecution.attemptCount(),
                updatedExecution.modelCallCount(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                updatedExecution.modelDurationMs(),
                updatedExecution.failureCode(),
                updatedExecution.version(),
                updatedExecution.startedAt(),
                updatedExecution.finishedAt(),
                updatedExecution.updatedAt(),
                expectedVersion
        );
        if (affectedRows > 1) throw new IllegalStateException("节点执行CAS更新影响了多行数据");
        return affectedRows == 1;
    }

    @Override
    public int sumModelCallCount(ActorId ownerId, UUID interviewId) {
        requireScope(ownerId, interviewId);
        return Math.toIntExact(mapper.sumModelCallCount(ownerId.value(), interviewId.toString()));
    }

    @Override
    public long sumTotalTokens(ActorId ownerId, UUID interviewId) {
        requireScope(ownerId, interviewId);
        return mapper.sumTotalTokens(ownerId.value(), interviewId.toString());
    }

    private static void requireScope(ActorId ownerId, UUID interviewId) {
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(interviewId, "interviewId不能为空");
    }
}