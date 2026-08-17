package com.leo.careerforgeai.agent.infrastructure.mcp.server;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.agent.application.coach.CareerCoachService;
import com.leo.careerforgeai.agent.application.tool.career.search.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.infrastructure.mcp.server.tool.McpSearchCareerMaterialsToolProvider;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证MCP开启时只装配职业材料Tool且不暴露其他协议能力。
 * @author: Miao Zheng
 * @date: 2026-08-10
 **/
@SpringBootTest(
        classes = CareerForgeAiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.model.chat=none",
                "spring.ai.mcp.server.enabled=true",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.type=SYNC",
                "spring.ai.mcp.server.name=careerforge-test-mcp",
                "spring.ai.mcp.server.version=test",
                "server.address=127.0.0.1",
                "careerforge.model.base-url=http://localhost",
                "careerforge.model.api-key=test-placeholder",
                "careerforge.model.name=test-model"
        }
)
class McpServerConfigurationTest {

    @MockitoBean
    private KnowledgeEvidenceSearchService evidenceSearchService;

    @Autowired
    private McpSyncServer mcpServer;

    @Autowired
    private McpSearchCareerMaterialsToolProvider toolProvider;

    @Autowired
    private CareerCoachService nativeCareerCoachService;

    @Test
    @DisplayName("MCP Server只注册搜索工具并保留原生Career Coach")
    void shouldRegisterOnlySearchToolAndKeepNativeCareerCoach() {
        assertThat(toolProvider.specifications()).hasSize(1);
        assertThat(nativeCareerCoachService).isNotNull();

        assertThat(mcpServer.listTools())
                .extracting(tool -> tool.name())
                .containsExactly(SearchCareerMaterialsTool.NAME);

        assertThat(mcpServer.listResources()).isEmpty();
        assertThat(mcpServer.listResourceTemplates()).isEmpty();
        assertThat(mcpServer.listPrompts()).isEmpty();

        var capabilities = mcpServer.getServerCapabilities();
        assertThat(capabilities.tools()).isNotNull();
        assertThat(capabilities.resources()).isNull();
        assertThat(capabilities.prompts()).isNull();
        assertThat(capabilities.completions()).isNull();

        assertThat(mcpServer.getServerInfo().name())
                .isEqualTo("careerforge-test-mcp");
        assertThat(mcpServer.getServerInfo().version())
                .isEqualTo("test");
    }
}