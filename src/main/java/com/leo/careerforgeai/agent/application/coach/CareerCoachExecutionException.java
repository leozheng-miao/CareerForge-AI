package com.leo.careerforgeai.agent.application.coach;

import com.leo.careerforgeai.agent.domain.loop.AgentLoopResult;
import com.leo.careerforgeai.agent.domain.loop.AgentRunStatus;
import com.leo.careerforgeai.agent.domain.loop.trace.AgentRunTrace;
import com.leo.careerforgeai.agent.domain.loop.AgentTerminationReason;

import java.util.Objects;

/**
 * @program: CareerForge-AI
 * @description: 表示Agent Loop因超时、预算、限制或模型故障未能生成最终回答。
 * @author: Miao Zheng
 * @date: 2026-08-07 05:30
 **/
public final class CareerCoachExecutionException extends RuntimeException {

    private final AgentRunStatus runStatus;
    private final AgentTerminationReason terminationReason;
    private final AgentRunTrace trace;

    /** 从未正常完成的Agent Loop结果创建安全执行异常，不保留原始Tool Result。 */
    public CareerCoachExecutionException(AgentLoopResult loopResult) {
        super("Career Coach未能完成本次请求");
        Objects.requireNonNull(loopResult, "loopResult不能为空");
        if (loopResult.status() == AgentRunStatus.COMPLETED) {
            throw new IllegalArgumentException("正常完成结果不能转换为执行异常");
        }

        this.runStatus = loopResult.status();
        this.terminationReason = loopResult.terminationReason();
        this.trace = loopResult.trace();
    }

    /** 返回Agent对上层暴露的终态。 */
    public AgentRunStatus getRunStatus() {
        return runStatus;
    }

    /** 返回不包含底层异常详情的确定性终止原因。 */
    public AgentTerminationReason getTerminationReason() {
        return terminationReason;
    }

    /** 返回不包含消息正文和Tool Result的脱敏Trace。 */
    public AgentRunTrace getTrace() {
        return trace;
    }
}