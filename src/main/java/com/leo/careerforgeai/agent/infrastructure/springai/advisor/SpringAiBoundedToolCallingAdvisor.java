package com.leo.careerforgeai.agent.infrastructure.springai.advisor;

import com.leo.careerforgeai.agent.domain.loop.AgentLoopPolicy;
import com.leo.careerforgeai.agent.infrastructure.springai.tool.SpringAiToolRunContext;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;

import java.time.Clock;
import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 在Spring AI默认Tool Calling生命周期前增加服务端迭代和Deadline限制。
 * @author: Miao Zheng
 * @date: 2026-08-10 05:20
 **/
public final class SpringAiBoundedToolCallingAdvisor extends ToolCallingAdvisor {

    private final AgentLoopPolicy policy;
    private final Clock clock;

    private SpringAiBoundedToolCallingAdvisor(
            ToolCallingManager toolCallingManager,
            ToolExecutionEligibilityChecker eligibilityChecker,
            int advisorOrder,
            boolean conversationHistoryEnabled,
            AgentLoopPolicy policy,
            Clock clock
    ) {
        super(toolCallingManager, eligibilityChecker, advisorOrder, conversationHistoryEnabled);
        this.policy = Objects.requireNonNull(policy, "policy不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    /** 在父类发起每一次模型调用前执行请求级边界检查。 */
    @Override
    protected ChatClientRequest doBeforeCall(
            ChatClientRequest request,
            CallAdvisorChain callAdvisorChain
    ) {
        if (!(request.prompt().getOptions() instanceof ToolCallingChatOptions options)) {
            return request;
        }

        SpringAiToolRunContext runContext = SpringAiToolRunContext.requireFrom(
                new ToolContext(options.getToolContext())
        );
        runContext.startModelIteration(policy.maxModelIterations(), clock.instant());
        return request;
    }

    public static Builder builder(AgentLoopPolicy policy, Clock clock) {
        return new Builder(policy, clock);
    }

    /**
     * @program: CareerForge-AI
     * @description: 保留Spring AI默认Advisor配置并构造带项目运行边界的Advisor。
     * @author: Miao Zheng
     * @date: 2026-08-10 05:20
     **/
    public static final class Builder extends ToolCallingAdvisor.Builder<Builder> {

        private final AgentLoopPolicy policy;
        private final Clock clock;

        private Builder(AgentLoopPolicy policy, Clock clock) {
            this.policy = Objects.requireNonNull(policy, "policy不能为空");
            this.clock = Objects.requireNonNull(clock, "clock不能为空");
        }

        @Override
        protected ToolCallingAdvisor.Builder<?> newCopy() {
            return new Builder(policy, clock);
        }

        @Override
        public SpringAiBoundedToolCallingAdvisor build() {
            return new SpringAiBoundedToolCallingAdvisor(
                    getToolCallingManager(),
                    getToolExecutionEligibilityChecker(),
                    getAdvisorOrder(),
                    isConversationHistoryEnabled(),
                    policy,
                    clock
            );
        }
    }
}