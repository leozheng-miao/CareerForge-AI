package com.leo.careerforgeai;

import com.leo.careerforgeai.agent.application.loop.AgentLoop;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.application.tool.career.parse.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.config.AgentLoopProperties;
import com.leo.careerforgeai.agent.infrastructure.springai.coach.SpringAiCareerCoachService;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.adapter.SpringAiToolCallbackCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import com.leo.careerforgeai.agent.infrastructure.mcp.server.tool.McpSearchCareerMaterialsToolProvider;
import io.modelcontextprotocol.server.McpSyncServer;

import static org.assertj.core.api.Assertions.assertThat;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @program: CareerForge-AI
 * @description: 验证应用上下文和阶段三Java工具白名单能够正确装配。
 * @author: Miao Zheng
 * @date: 2026-08-07 02:10
 **/
@SpringBootTest(properties = {
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=test-placeholder",
        "spring.ai.mcp.server.enabled=false",
        "careerforge.model.name=test-model"
})
class CareerForgeAiApplicationTests {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentLoop agentLoop;

    @Autowired
    private SafeToolExecutor safeToolExecutor;

    @Autowired
    private AgentLoopProperties agentLoopProperties;

    @Autowired
    private SpringAiToolCallbackCatalog springAiToolCallbackCatalog;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoadsWithExplicitAgentToolWhitelist() {
        assertThat(toolRegistry.contracts())
                .extracting(contract -> contract.name())
                .containsExactly(
                        SearchCareerMaterialsTool.NAME,
                        ParseJobRequirementsTool.NAME
                );
        assertThat(agentLoop).isNotNull();
        assertThat(safeToolExecutor).isNotNull();
        assertThat(agentLoopProperties.toPolicy().maxModelIterations()).isPositive();
        assertThat(springAiToolCallbackCatalog.callbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(
                        SearchCareerMaterialsTool.NAME,
                        ParseJobRequirementsTool.NAME
                );
        assertThat(applicationContext.getBeansOfType(ToolCallback.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SpringAiCareerCoachService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ChatClient.Builder.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(McpSearchCareerMaterialsToolProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(McpSyncServer.class)).isEmpty();
        assertThat(applicationContext.containsBean("webMvcStreamableServerRouterFunction")).isFalse();
    }

    @Test
    void springAiToolDefinitionsShouldMatchPublicToolContracts() throws Exception {
        assertThat(springAiToolCallbackCatalog.callbacks())
                .hasSameSizeAs(toolRegistry.definitions());

        for (var nativeDefinition : toolRegistry.definitions()) {
            var springDefinition = springAiToolCallbackCatalog.callbacks().stream()
                    .map(ToolCallback::getToolDefinition)
                    .filter(definition -> definition.name().equals(nativeDefinition.name()))
                    .findFirst()
                    .orElseThrow();

            assertThat(springDefinition.name())
                    .isEqualTo(nativeDefinition.name());
            assertThat(springDefinition.description())
                    .isEqualTo(nativeDefinition.description());
            assertThat(springDefinition.inputSchema())
                    .isEqualTo(nativeDefinition.inputSchemaJson());

            assertThat(jsonMapper.readTree(springDefinition.inputSchema()))
                    .isEqualTo(jsonMapper.readTree(nativeDefinition.inputSchemaJson()));
        }
    }

    @Test
    void ordinaryTestContextShouldNotCreateDatabaseInfrastructure() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(SqlSessionFactory.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Flyway.class)).isEmpty();
    }

    @Test
    void redisInfrastructureShouldUseOnlyStringSerialization() {
        assertThat(stringRedisTemplate.isEnableDefaultSerializer()).isFalse();
        assertThat(stringRedisTemplate.getKeySerializer())
                .isInstanceOf(StringRedisSerializer.class);
        assertThat(stringRedisTemplate.getValueSerializer())
                .isInstanceOf(StringRedisSerializer.class);
        assertThat(stringRedisTemplate.getHashKeySerializer())
                .isInstanceOf(StringRedisSerializer.class);
        assertThat(stringRedisTemplate.getHashValueSerializer())
                .isInstanceOf(StringRedisSerializer.class);
    }
}