package com.leo.careerforgeai.career.application.training;

import com.leo.careerforgeai.career.domain.TrainingPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.leo.careerforgeai.career.application.training.TrainingPlanGenerationException.ErrorType.MODEL_CALL_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证训练计划生成按照输入读取、事务外模型调用和短事务写入的顺序执行
 * @author: Miao Zheng
 * @date: 2026-08-18
 */
@ExtendWith(MockitoExtension.class)
class TrainingPlanGenerationApplicationServiceTest {

    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Mock
    private TrainingPlanGenerationInputReader inputReader;

    @Mock
    private TrainingPlanGenerator generator;

    @Mock
    private PendingTrainingPlanWriter writer;

    private TrainingPlanGenerationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanGenerationApplicationService(inputReader, generator, writer);
    }

    @Test
    void shouldGenerateBeforeInvokingTransactionalWriter() {
        TrainingPlanGenerationInputReader.FixedInput input =
                mock(TrainingPlanGenerationInputReader.FixedInput.class);
        TrainingPlanGenerator.GeneratedPlan generatedPlan =
                mock(TrainingPlanGenerator.GeneratedPlan.class);
        TrainingPlan savedPlan = mock(TrainingPlan.class);

        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(generator.generate(input)).thenReturn(generatedPlan);
        when(writer.save(input, generatedPlan)).thenReturn(savedPlan);

        TrainingPlan result = service.generate(SNAPSHOT_ID);

        assertThat(result).isSameAs(savedPlan);
        InOrder order = inOrder(inputReader, generator, writer);
        order.verify(inputReader).read(SNAPSHOT_ID);
        order.verify(generator).generate(input);
        order.verify(writer).save(input, generatedPlan);
        order.verifyNoMoreInteractions();
    }

    @Test
    void shouldNotInvokeWriterWhenModelFails() {
        TrainingPlanGenerationInputReader.FixedInput input =
                mock(TrainingPlanGenerationInputReader.FixedInput.class);

        when(inputReader.read(SNAPSHOT_ID)).thenReturn(input);
        when(generator.generate(input)).thenThrow(
                new TrainingPlanGenerationException(MODEL_CALL_FAILED, "训练计划模型调用失败")
        );

        assertThatThrownBy(() -> service.generate(SNAPSHOT_ID))
                .isInstanceOfSatisfying(
                        TrainingPlanGenerationException.class,
                        exception -> assertThat(exception.getErrorType()).isEqualTo(MODEL_CALL_FAILED)
                );

        verify(inputReader).read(SNAPSHOT_ID);
        verify(generator).generate(input);
        verifyNoInteractions(writer);
    }

    @Test
    void shouldRejectNullSnapshotIdBeforeReadingInput() {
        assertThatThrownBy(() -> service.generate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("snapshotId不能为空");

        verifyNoInteractions(inputReader, generator, writer);
    }
}