package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.model.domain.ModelUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.INPUT_INTEGRITY_VIOLATION;
import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.INPUT_VERSION_CONFLICT;
import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.MODEL_OUTPUT_INVALID;
import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.PERSISTENCE_FAILED;

/**
 * @program: CareerForge-AI
 * @description: 在短事务内最终复核训练计划输入并幂等保存PENDING_CONFIRMATION计划版本
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@Service
@ConditionalOnBean(CareerPlanningRepository.class)
public class PendingTrainingPlanWriter {

    public static final String CONTEXT_SCHEMA_VERSION = "training-plan-generation-context-v1";

    private final TrainingPlanGenerationInputReader inputReader;
    private final CareerPlanningRepository repository;
    private final Clock clock;

    public PendingTrainingPlanWriter(
            TrainingPlanGenerationInputReader inputReader,
            CareerPlanningRepository repository,
            Clock clock
    ) {
        this.inputReader = Objects.requireNonNull(inputReader, "inputReader不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public TrainingPlan save(
            TrainingPlanGenerationInputReader.FixedInput expectedInput,
            TrainingPlanGenerator.GeneratedPlan generatedPlan
    ) {
        return save(expectedInput, generatedPlan, UUID.randomUUID());
    }

    /**
     * 模型调用已经结束。本事务只执行幂等检查、最终输入复核、版本分配和计划写入。
     */
    @Transactional
    public TrainingPlan save(
            TrainingPlanGenerationInputReader.FixedInput expectedInput,
            TrainingPlanGenerator.GeneratedPlan generatedPlan,
            UUID planId
    ) {
        Objects.requireNonNull(expectedInput, "expectedInput不能为空");
        Objects.requireNonNull(generatedPlan, "generatedPlan不能为空");
        Objects.requireNonNull(planId, "planId不能为空");

        Optional<TrainingPlan> replay = findPlan(expectedInput, planId);
        if (replay.isPresent()) return requireReplay(replay.get(), expectedInput, planId);

        TrainingPlanGenerationInputReader.FixedInput currentInput = readCurrentInput(
                expectedInput.gapSnapshot().snapshotId()
        );
        if (!expectedInput.equals(currentInput)) {
            throw failure(INPUT_VERSION_CONFLICT, "训练计划生成期间输入已经变化，请重新生成");
        }

        validateGeneratedPlan(generatedPlan);
        long planVersion = nextPlanVersion(expectedInput);
        Instant now = clock.instant();
        ModelUsage usage = generatedPlan.modelUsage();

        TrainingPlan.GenerationContext context = new TrainingPlan.GenerationContext(
                CONTEXT_SCHEMA_VERSION,
                expectedInput.inputPolicyVersion(),
                expectedInput.weeklyAvailableMinutes(),
                expectedInput.memoryRefs(),
                expectedInput.resourceRefs(),
                TrainingPlanGenerator.GENERATOR_VERSION,
                TrainingPlanGenerator.PROMPT_VERSION,
                generatedPlan.modelRequestId(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                generatedPlan.modelDurationMs()
        );

        TrainingPlan pendingPlan = TrainingPlan.createGeneratedDraft(
                planId,
                expectedInput.ownerId(),
                planVersion,
                expectedInput.gapSnapshot().snapshotId(),
                context,
                generatedPlan.title(),
                generatedPlan.items(),
                now
        ).submitForConfirmation(now);

        try {
            repository.insertTrainingPlan(pendingPlan);
        } catch (RuntimeException exception) {
            throw failure(PERSISTENCE_FAILED, "训练计划持久化失败", exception);
        }
        return pendingPlan;
    }

    private Optional<TrainingPlan> findPlan(
            TrainingPlanGenerationInputReader.FixedInput input,
            UUID planId
    ) {
        try {
            return repository.findTrainingPlan(input.ownerId(), planId);
        } catch (RuntimeException exception) {
            throw failure(PERSISTENCE_FAILED, "训练计划幂等记录读取失败", exception);
        }
    }

    private TrainingPlan requireReplay(
            TrainingPlan existing,
            TrainingPlanGenerationInputReader.FixedInput input,
            UUID planId
    ) {
        if (!existing.planId().equals(planId)
                || !existing.ownerId().equals(input.ownerId())
                || !existing.gapSnapshotId().equals(input.gapSnapshot().snapshotId())
                || existing.generationContext() == null
                || existing.status() == TrainingPlan.PlanStatus.DRAFT) {
            throw failure(INPUT_INTEGRITY_VIOLATION, "稳定planId已被不同训练计划占用");
        }
        return existing;
    }

    private TrainingPlanGenerationInputReader.FixedInput readCurrentInput(UUID snapshotId) {
        try {
            return inputReader.read(snapshotId);
        } catch (TrainingPlanGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(PERSISTENCE_FAILED, "训练计划最终输入复核失败", exception);
        }
    }

    private long nextPlanVersion(TrainingPlanGenerationInputReader.FixedInput input) {
        Optional<TrainingPlan> latest;
        try {
            latest = repository.findLatestTrainingPlan(input.ownerId());
        } catch (RuntimeException exception) {
            throw failure(PERSISTENCE_FAILED, "训练计划版本读取失败", exception);
        }
        if (latest.isEmpty()) return 1L;

        TrainingPlan latestPlan = latest.get();
        if (!input.ownerId().equals(latestPlan.ownerId())) {
            throw failure(INPUT_INTEGRITY_VIOLATION, "最新训练计划违反owner边界");
        }

        try {
            return Math.addExact(latestPlan.planVersion(), 1L);
        } catch (ArithmeticException exception) {
            throw failure(PERSISTENCE_FAILED, "训练计划业务版本已经耗尽", exception);
        }
    }

    private void validateGeneratedPlan(
            TrainingPlanGenerator.GeneratedPlan generatedPlan
    ) {
        if (generatedPlan.items().stream().anyMatch(item ->
                item.status() != TrainingPlanItem.ItemStatus.NOT_STARTED
                        || !item.completionEvidenceRefs().isEmpty())) {
            throw failure(MODEL_OUTPUT_INVALID, "待确认计划项不能包含进度或完成证据");
        }

        int actualDuration = generatedPlan.items().stream()
                .mapToInt(TrainingPlanItem::weekNumber)
                .max()
                .orElseThrow(() -> failure(MODEL_OUTPUT_INVALID, "训练计划任务不能为空"));
        if (actualDuration != generatedPlan.durationWeeks()) {
            throw failure(MODEL_OUTPUT_INVALID, "模型计划周期与任务周次不一致");
        }
    }

    private static TrainingPlanGenerationException failure(
            TrainingPlanGenerationException.ErrorType errorType,
            String message
    ) {
        return new TrainingPlanGenerationException(errorType, message);
    }

    private static TrainingPlanGenerationException failure(
            TrainingPlanGenerationException.ErrorType errorType,
            String message,
            Throwable cause
    ) {
        return new TrainingPlanGenerationException(errorType, message, cause);
    }
}