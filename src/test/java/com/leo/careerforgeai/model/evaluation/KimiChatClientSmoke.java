package com.leo.careerforgeai.model.evaluation;

import com.leo.careerforgeai.model.config.ModelRoutingProperties;
import com.leo.careerforgeai.model.domain.ModelMessage;
import com.leo.careerforgeai.model.domain.ModelOutputFormat;
import com.leo.careerforgeai.model.domain.ModelRequest;
import com.leo.careerforgeai.model.domain.ModelResponse;
import com.leo.careerforgeai.model.domain.ModelRole;
import com.leo.careerforgeai.model.domain.routing.ModelCapability;
import com.leo.careerforgeai.model.domain.routing.ModelExecutionProfile;
import com.leo.careerforgeai.model.domain.routing.ModelTaskType;
import com.leo.careerforgeai.model.domain.routing.ReasoningMode;
import com.leo.careerforgeai.model.infrastructure.kimi.KimiChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 通过正式Kimi ProviderModelClient验证真实非流式模型调用。
 * @author: Miao Zheng
 * @date: 2026-09-02
 */
@EnabledIfEnvironmentVariable(named = "MOONSHOT_API_KEY", matches = ".+")
class KimiChatClientSmoke {

    @Test
    void shouldCallKimiThroughProviderModelClient() {
        String model = System.getenv().getOrDefault("KIMI_MODEL", "kimi-k2.6");
        URI baseUrl = URI.create(System.getenv()
                .getOrDefault("KIMI_BASE_URL", "https://api.moonshot.cn/v1"));
        ModelRoutingProperties properties = new ModelRoutingProperties(
                "routing-v1",
                Map.of("kimi", new ModelRoutingProperties.Provider(
                        baseUrl, System.getenv("MOONSHOT_API_KEY"), true)),
                Map.of("kimi-standard", new ModelRoutingProperties.Profile(
                        "kimi", model, Set.of(ModelCapability.CHAT),
                        ReasoningMode.DISABLED, null, 1_000,
                        Duration.ofSeconds(60), "kimi-2026-09-01", true)),
                Map.of(ModelTaskType.CAREER_COACH, List.of("kimi-standard")));
        KimiChatClient client = new KimiChatClient(properties,
                JsonMapper.builder().build(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        ModelExecutionProfile profile = properties.executionRoutes()
                .get(ModelTaskType.CAREER_COACH).getFirst();

        ModelResponse response = client.chat(profile,
                new ModelRequest(
                        List.of(new ModelMessage(ModelRole.USER, "只回复：OK")),
                        ModelOutputFormat.TEXT, 32, 1.0, Duration.ofSeconds(60)));

        assertThat(response.content()).isNotBlank();
        assertThat(response.model()).isEqualTo(model);
        assertThat(response.usage()).isNotNull();
        System.out.printf(
                "provider=KIMI, mode=PRODUCTION_ADAPTER, model=%s, requestId=%s, totalTokens=%d%n",
                response.model(), response.requestId(), response.usage().totalTokens());
    }
}