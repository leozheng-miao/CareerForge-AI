package com.leo.careerforgeai.model.application.routing;

import com.leo.careerforgeai.model.config.ModelTaskPolicyProperties;
import com.leo.careerforgeai.model.domain.*;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @program: CareerForge-AI
 * @description: 验证统一任务策略覆盖旧ModelRequest默认参数后再进入模型执行器
 * @author: Miao Zheng
 * @date: 2026-09-04
 */
class RoutingModelGatewayTest {
    @Test
    void shouldApplyBackendTaskPolicyBeforeRouting() {
        ModelCallExecutor executor = mock(ModelCallExecutor.class);
        ModelTaskPolicyProperties properties = new ModelTaskPolicyProperties(Map.of(
                ModelTaskType.TRAINING_PLAN_GENERATION,
                new ModelTaskPolicyProperties.Policy(4000, 0.2, Duration.ofSeconds(60), true)
        ));
        RoutingModelGateway gateway = new RoutingModelGateway(executor, properties);
        ModelResponse expected = new ModelResponse("request-1", "deepseek-v4-flash", "{}",
                new ModelUsage(10, 20, 30));
        when(executor.chat(eq(ModelTaskType.TRAINING_PLAN_GENERATION), eq(Set.of()),
                any(ModelRequest.class), eq(4000), eq(true))).thenReturn(expected);

        ModelResponse actual = gateway.chat(ModelTaskType.TRAINING_PLAN_GENERATION,
                new ModelRequest(List.of(new ModelMessage(ModelRole.USER, "生成计划")),
                        ModelOutputFormat.JSON_OBJECT));

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(executor).chat(eq(ModelTaskType.TRAINING_PLAN_GENERATION), eq(Set.of()),
                captor.capture(), eq(4000), eq(true));
        assertThat(actual).isSameAs(expected);
        assertThat(captor.getValue().maxOutputTokens()).isEqualTo(4000);
        assertThat(captor.getValue().temperature()).isEqualTo(0.2);
        assertThat(captor.getValue().timeout()).isEqualTo(Duration.ofSeconds(60));
    }
}