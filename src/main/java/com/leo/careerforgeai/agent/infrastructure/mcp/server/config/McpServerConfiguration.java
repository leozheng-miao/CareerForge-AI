package com.leo.careerforgeai.agent.infrastructure.mcp.server.config;

import com.leo.careerforgeai.agent.application.coach.CareerCoachScopeProvider;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.career.parse.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.infrastructure.mcp.server.tool.McpSearchCareerMaterialsToolProvider;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @program: CareerForge-AI
 * @description: 在MCP Server显式开启时装配唯一的职业材料搜索Tool Provider。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "enabled",
        havingValue = "true"
)
public class McpServerConfiguration {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]",
            "0:0:0:0:0:0:0:1"
    );

    /** MCP开启时拒绝缺失地址、0.0.0.0和其他非本机监听地址。 */
    @Bean
    public InitializingBean mcpServerLocalBindingGuard(
            @Value("${server.address:}") String serverAddress
    ) {
        return () -> {
            String normalizedAddress = serverAddress == null
                    ? ""
                    : serverAddress.trim().toLowerCase(Locale.ROOT);

            if (!LOOPBACK_ADDRESSES.contains(normalizedAddress)) {
                throw new IllegalStateException(
                        "MCP Server启用时server.address必须显式配置为本机回环地址"
                );
            }
        };
    }

    /** 创建只暴露search_career_materials的MCP Tool Provider。 */
    @Bean
    public McpSearchCareerMaterialsToolProvider mcpSearchCareerMaterialsToolProvider(
            SearchCareerMaterialsTool searchTool,
            SafeToolExecutor safeToolExecutor,
            CareerCoachScopeProvider scopeProvider,
            Clock agentClock,
            JsonMapper jsonMapper
    ) {
        return new McpSearchCareerMaterialsToolProvider(
                searchTool,
                safeToolExecutor,
                scopeProvider,
                agentClock,
                jsonMapper
        );
    }

    /** 显式向MCP Server注册唯一的同步工具规范。 */
    @Bean
    public List<McpServerFeatures.SyncToolSpecification> mcpSearchCareerMaterialsToolSpecifications(
            McpSearchCareerMaterialsToolProvider provider
    ) {
        return provider.specifications();
    }
}