package com.leo.careerforgeai.agent.evaluation.compatibility;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @program: CareerForge-AI
 * @description: 验证LangGraph4j 1.8.24通过真实MySQL Saver完成跨JVM Checkpoint恢复。
 * @author: Miao Zheng
 * @date: 2026-08-27
 */
class LangGraph4jMysqlCrossJvmSmoke {

    @Test
    @EnabledIfSystemProperty(named = "cp0.phase", matches = "pause")
    void shouldPersistInterruptedCheckpointToMysql() throws Exception {
        MysqlSaver saver = MysqlSaver.builder()
                .dataSource(mysqlDataSource())
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();
        CompiledGraph<AgentState> workflow = compileWorkflow(saver);
        RunnableConfig config = runnableConfig();

        var interrupted = workflow.invokeFinal(
                GraphInput.args(Map.of(
                        "schemaVersion", 1,
                        "runId", requiredEnvironment("CP0_THREAD_ID"),
                        "skills", List.of("JAVA", "SPRING_AI"),
                        "frozenContext", Map.of(
                                "ownerId", "owner-cp0",
                                "version", 1
                        )
                )),
                config
        ).orElseThrow();

        assertThat(interrupted.node()).isEqualTo("question");

        var checkpoint = workflow.lastStateOf(config).orElseThrow();
        assertThat(checkpoint.next()).isEqualTo("finish");
        assertThat(checkpoint.state().<Integer>value("schemaVersion")).contains(1);
        assertThat(checkpoint.state().value("result")).isEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = "cp0.phase", matches = "resume")
    void shouldLoadCheckpointInNewJvmAndResume() throws Exception {
        MysqlSaver saver = MysqlSaver.builder()
                .dataSource(mysqlDataSource())
                .createOption(CreateOption.CREATE_NONE)
                .build();
        CompiledGraph<AgentState> workflow = compileWorkflow(saver);
        RunnableConfig config = runnableConfig();

        var checkpoint = workflow.lastStateOf(config).orElseThrow();
        assertThat(checkpoint.next()).isEqualTo("finish");
        assertThat(checkpoint.state().<Integer>value("schemaVersion")).contains(1);

        Map<String, Object> frozenContext = checkpoint.state()
                .<Map<String, Object>>value("frozenContext")
                .orElseThrow();
        assertThat(frozenContext)
                .containsEntry("ownerId", "owner-cp0")
                .containsEntry("version", 1);

        AgentState completed = workflow.invoke(
                GraphInput.resume(Map.of("answerId", "answer-cp0-001")),
                config
        ).orElseThrow();

        assertThat(completed.<String>value("result"))
                .contains("resumed:answer-cp0-001");
    }

    private CompiledGraph<AgentState> compileWorkflow(MysqlSaver saver) throws Exception {
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        graph.addNode("question", node_async(state -> Map.of(
                "question",
                "解释Checkpoint为什么不能作为业务真相源"
        )));
        graph.addNode("finish", node_async(state -> Map.of(
                "result",
                "resumed:" + state.<String>value("answerId").orElseThrow()
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

    private RunnableConfig runnableConfig() {
        return RunnableConfig.builder()
                .threadId(requiredEnvironment("CP0_THREAD_ID"))
                .build();
    }

    private MysqlDataSource mysqlDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(requiredEnvironment("CP0_MYSQL_URL"));
        dataSource.setUser(requiredEnvironment("CP0_MYSQL_USER"));
        dataSource.setPassword(requiredEnvironment("CP0_MYSQL_PASSWORD"));
        return dataSource;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}