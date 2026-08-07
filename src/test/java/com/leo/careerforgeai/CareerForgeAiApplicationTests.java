package com.leo.careerforgeai;

import com.leo.careerforgeai.agent.application.loop.AgentLoop;
import com.leo.careerforgeai.agent.application.tool.SafeToolExecutor;
import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.application.tool.career.ParseJobRequirementsTool;
import com.leo.careerforgeai.agent.application.tool.career.SearchCareerMaterialsTool;
import com.leo.careerforgeai.agent.config.AgentLoopProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @program: CareerForge-AI
 * @description: 验证应用上下文和阶段三Java工具白名单能够正确装配。
 * @author: Miao Zheng
 * @date: 2026-08-07 02:10
 **/
@SpringBootTest(properties = {
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=test-placeholder",
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
    }
}