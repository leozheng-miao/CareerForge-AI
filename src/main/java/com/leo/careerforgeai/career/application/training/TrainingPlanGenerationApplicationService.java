package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.domain.TrainingPlan;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 在数据库写事务外编排固定输入读取、模型生成和待确认计划持久化
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@Service
public class TrainingPlanGenerationApplicationService {

    private final TrainingPlanGenerationInputReader inputReader;
    private final TrainingPlanGenerator generator;
    private final PendingTrainingPlanWriter writer;

    public TrainingPlanGenerationApplicationService(
            TrainingPlanGenerationInputReader inputReader,
            TrainingPlanGenerator generator,
            PendingTrainingPlanWriter writer
    ) {
        this.inputReader = Objects.requireNonNull(inputReader, "inputReader不能为空");
        this.generator = Objects.requireNonNull(generator, "generator不能为空");
        this.writer = Objects.requireNonNull(writer, "writer不能为空");
    }

    /**
     * 本方法不能添加@Transactional，确保模型调用不占用数据库事务。
     */
    public TrainingPlan generate(UUID snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId不能为空");
        TrainingPlanGenerationInputReader.FixedInput input = inputReader.read(snapshotId);
        TrainingPlanGenerator.GeneratedPlan generatedPlan = generator.generate(input);
        return writer.save(input, generatedPlan);
    }
}