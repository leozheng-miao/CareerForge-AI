package com.leo.careerforgeai.agent.config;

import com.leo.careerforgeai.agent.application.tool.ToolRegistry;
import com.leo.careerforgeai.agent.application.tool.career.CareerMaterialScopePolicy;
import com.leo.careerforgeai.agent.application.tool.career.SearchCareerMaterialsTool;
import com.leo.careerforgeai.knowledge.application.evidence.KnowledgeEvidenceSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @program: CareerForge-AI
 * @description: 显式装配阶段三允许 Agent 调用的 Java 工具及安全白名单。
 * @author: Miao Zheng
 * @date: 2026-08-06 22:30
 **/
@Configuration(proxyBeanMethods = false)
public class AgentToolConfiguration {

    /** 注册负责收窄模型文档类型请求的服务端Scope策略。 */
    @Bean
    public CareerMaterialScopePolicy careerMaterialScopePolicy() {
        return new CareerMaterialScopePolicy();
    }

    /** 注册只返回受控职业材料证据的Java工具。 */
    @Bean
    public SearchCareerMaterialsTool searchCareerMaterialsTool(KnowledgeEvidenceSearchService evidenceSearchService, CareerMaterialScopePolicy scopePolicy) {
        return new SearchCareerMaterialsTool(evidenceSearchService, scopePolicy);
    }

    /** 使用明确列出的工具创建Agent调用白名单。 */
    @Bean
    public ToolRegistry toolRegistry(SearchCareerMaterialsTool searchCareerMaterialsTool) {
        return new ToolRegistry(List.of(searchCareerMaterialsTool));
    }
}