package com.leo.careerforgeai.agent.evaluation.compatibility;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @program: CareerForge-AI
 * @description: 验证LangGraph4j 1.8.24最小Graph的中断、Checkpoint与同thread恢复能力。
 * @author: Miao Zheng
 * @date: 2026-08-27
 */
class LangGraph4jInterruptResumeCompatibilityTest {

    @Test
    void shouldInterruptAfterQuestionAndResumeSameThread() throws Exception {
        MemorySaver saver = new MemorySaver();
        CompiledGraph<AgentState> workflow = compileWorkflow(saver);
        RunnableConfig config = RunnableConfig.builder()
                .threadId("cp0-interrupt-resume")
                .build();

        var interrupted = workflow.invokeFinal(
                GraphInput.args(Map.of("schemaVersion", 1)),
                config
        ).orElseThrow();

        assertThat(interrupted.node()).isEqualTo("question");

        var checkpoint = workflow.lastStateOf(config).orElseThrow();
        assertThat(checkpoint.next()).isEqualTo("finish");

        String question = checkpoint.state()
                .<String>value("question")
                .orElseThrow();
        assertThat(question).contains("MySQL");
        assertThat(checkpoint.state().value("result")).isEmpty();

        AgentState completed = workflow.invoke(
                GraphInput.resume(Map.of(
                        "answer",
                        "业务事实必须可审计，不能依赖Checkpoint作为真相源"
                )),
                config
        ).orElseThrow();

        String answer = completed.<String>value("answer").orElseThrow();
        String result = completed.<String>value("result").orElseThrow();
        assertThat(answer).contains("不能依赖Checkpoint");
        assertThat(result).contains("不能依赖Checkpoint");
    }
    @Test
    void shouldRejectResumeWithDifferentThreadId() throws Exception {
        MemorySaver saver = new MemorySaver();
        CompiledGraph<AgentState> workflow = compileWorkflow(saver);
        RunnableConfig waitingConfig = RunnableConfig.builder()
                .threadId("cp0-owner-thread")
                .build();

        workflow.invoke(GraphInput.args(Map.of()), waitingConfig);

        RunnableConfig wrongConfig = RunnableConfig.builder()
                .threadId("cp0-other-thread")
                .build();

        assertThatThrownBy(() -> workflow.invoke(
                GraphInput.resume(Map.of("answer", "越权恢复")),
                wrongConfig
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid checkpoint");
    }

    private CompiledGraph<AgentState> compileWorkflow(MemorySaver saver) throws Exception {
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        graph.addNode("question", node_async(state -> Map.of(
                "question",
                "解释MySQL为何是业务真相源"
        )));
        graph.addNode("finish", node_async(state -> Map.of(
                "result",
                state.<String>value("answer").orElseThrow()
        )));

        graph.addEdge(START, "question");
        graph.addEdge("question", "finish");
        graph.addEdge("finish", END);

        return graph.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter("question")
                .releaseThread(false)
                .build());
    }
}