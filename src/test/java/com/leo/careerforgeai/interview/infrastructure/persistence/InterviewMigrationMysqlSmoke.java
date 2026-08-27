package com.leo.careerforgeai.interview.infrastructure.persistence;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 验证隔离MySQL中V1至V15迁移、唯一约束、CAS及LangGraph4j Saver真实兼容性
 * @author: Miao Zheng
 * @date: 2026-08-27
 */
class InterviewMigrationMysqlSmoke {

    private static final Set<String> STAGE_SIX_TABLES = Set.of(
            "PERSONAL_EVIDENCE_ARTIFACT",
            "PERSONAL_EVIDENCE_CHUNK",
            "MOCK_INTERVIEW_INPUT_SNAPSHOT",
            "MOCK_INTERVIEW_INPUT_ARTIFACT",
            "MOCK_INTERVIEW_SESSION",
            "INTERVIEW_ROUND",
            "INTERVIEW_QUESTION",
            "INTERVIEW_ANSWER",
            "INTERVIEW_TECHNICAL_REVIEW",
            "INTERVIEW_EVIDENCE_REVIEW",
            "INTERVIEW_REPORT",
            "INTERVIEW_REPORT_SUGGESTION",
            "INTERVIEW_REPORT_CONFIRMATION",
            "INTERVIEW_REPORT_DECISION",
            "INTERVIEW_NODE_EXECUTION",
            "LANGRAPH4J_THREAD",
            "LANGRAPH4J_CHECKPOINT"
    );

    @Test
    @EnabledIfSystemProperty(named = "cp2.mysql.smoke", matches = "true")
    void shouldMigrateStageSixTablesAndUseFlywayOwnedCheckpointSchema() throws Exception {
        MysqlDataSource dataSource = mysqlDataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();

        flyway.migrate();

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
        assertThat(stageSixTables(dataSource)).containsExactlyInAnyOrderElementsOf(STAGE_SIX_TABLES);

        MysqlSaver saver = MysqlSaver.builder()
                .dataSource(dataSource)
                .createOption(CreateOption.CREATE_NONE)
                .build();
        CompiledGraph<AgentState> graph = compileWorkflow(saver);
        String threadId = "cp2-migration-smoke-" + UUID.randomUUID();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        var interrupted = graph.invokeFinal(GraphInput.args(Map.of("schemaVersion", 1)), config).orElseThrow();
        assertThat(interrupted.node()).isEqualTo("persist_probe");
        assertThat(graph.lastStateOf(config).orElseThrow().next()).isEqualTo("resume_probe");

        AgentState completed = graph.invoke(GraphInput.resume(Map.of("resume", true)), config).orElseThrow();
        assertThat(completed.<String>value("migrationResult")).contains("resumed");
        assertThat(isReleased(dataSource, threadId)).isTrue();
        verifySessionUniqueConstraintAndCas(dataSource);
    }

    private CompiledGraph<AgentState> compileWorkflow(MysqlSaver saver) throws Exception {
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);
        graph.addNode("persist_probe", node_async(state -> Map.of("migrationProbe", "saved")));
        graph.addNode("resume_probe", node_async(state -> Map.of("migrationResult", "resumed")));
        graph.addEdge(START, "persist_probe");
        graph.addEdge("persist_probe", "resume_probe");
        graph.addEdge("resume_probe", END);
        return graph.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter("persist_probe")
                .releaseThread(true)
                .build());
    }

    private Set<String> stageSixTables(DataSource dataSource) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """;
        Set<String> actual = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(1).toUpperCase(Locale.ROOT);
                if (STAGE_SIX_TABLES.contains(tableName)) {
                    actual.add(tableName);
                }
            }
        }
        return actual;
    }

    private boolean isReleased(DataSource dataSource, String threadId) throws SQLException {
        String sql = """
                SELECT is_released
                FROM LANGRAPH4J_THREAD
                WHERE thread_name = ?
                """;
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, threadId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private MysqlDataSource mysqlDataSource() {
        String url = requiredEnvironment("CP2_MYSQL_URL");
        if (!url.matches("^jdbc:mysql://[^/]+/careerforge_cp2_smoke(?:\\?.*)?$")) {
            throw new IllegalStateException("CP2_MYSQL_URL必须指向careerforge_cp2_smoke隔离库");
        }
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(url);
        dataSource.setUser(requiredEnvironment("CP2_MYSQL_USER"));
        dataSource.setPassword(requiredEnvironment("CP2_MYSQL_PASSWORD"));
        return dataSource;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private void verifySessionUniqueConstraintAndCas(DataSource dataSource) throws SQLException {
        String ownerId = "cp2-smoke-" + UUID.randomUUID();
        String targetRoleId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        String interviewId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String snapshotHash = "b".repeat(64);
        String requestFingerprint = "c".repeat(64);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertTargetRole(connection, targetRoleId, ownerId);
                insertInputSnapshot(connection, snapshotId, ownerId, targetRoleId, snapshotHash);
                insertSession(
                        connection,
                        interviewId,
                        ownerId,
                        requestId,
                        requestFingerprint,
                        snapshotId,
                        snapshotHash
                );
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }

        assertThatThrownBy(() -> {
            try (Connection connection = dataSource.getConnection()) {
                insertSession(
                        connection,
                        UUID.randomUUID().toString(),
                        ownerId,
                        requestId,
                        requestFingerprint,
                        snapshotId,
                        snapshotHash
                );
            }
        }).isInstanceOf(SQLException.class);

        assertThat(updateSessionIfVersionMatches(
                dataSource,
                interviewId,
                "other-owner",
                0,
                1
        )).isZero();

        assertThat(updateSessionIfVersionMatches(
                dataSource,
                interviewId,
                ownerId,
                0,
                1
        )).isEqualTo(1);

        assertThat(updateSessionIfVersionMatches(
                dataSource,
                interviewId,
                ownerId,
                0,
                1
        )).isZero();
    }

    private void insertTargetRole(
            Connection connection,
            String targetRoleId,
            String ownerId
    ) throws SQLException {
        String sql = """
            INSERT INTO target_role (
                target_role_id, owner_id, target_role_version,
                source_ref, source_hash, parser_version,
                prompt_version, requirements_json, confirmed_at
            )
            VALUES (?, ?, 1, ?, ?, ?, ?, JSON_OBJECT(), CURRENT_TIMESTAMP(6))
            """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetRoleId);
            statement.setString(2, ownerId);
            statement.setString(3, "cp2-smoke-source");
            statement.setString(4, "a".repeat(64));
            statement.setString(5, "cp2-smoke-parser-v1");
            statement.setString(6, "cp2-smoke-prompt-v1");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertInputSnapshot(
            Connection connection,
            String snapshotId,
            String ownerId,
            String targetRoleId,
            String snapshotHash
    ) throws SQLException {
        String sql = """
            INSERT INTO mock_interview_input_snapshot (
                input_snapshot_id, owner_id, schema_version,
                target_role_id, target_role_version,
                skill_gap_snapshot_id, training_plan_id,
                training_plan_version, snapshot_context_json,
                snapshot_hash, created_at
            )
            VALUES (?, ?, 1, ?, 1, NULL, NULL, NULL, JSON_OBJECT(), ?, CURRENT_TIMESTAMP(6))
            """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshotId);
            statement.setString(2, ownerId);
            statement.setString(3, targetRoleId);
            statement.setString(4, snapshotHash);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertSession(
            Connection connection,
            String interviewId,
            String ownerId,
            String requestId,
            String requestFingerprint,
            String snapshotId,
            String snapshotHash
    ) throws SQLException {
        String sql = """
            INSERT INTO mock_interview_session (
                interview_id, owner_id, request_id, request_fingerprint,
                input_snapshot_id, input_snapshot_hash,
                interview_mode, interview_status,
                max_questions, max_follow_ups,
                max_model_calls, max_total_tokens,
                failure_code, version,
                created_at, updated_at, finished_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?,
                'TARGETED_MOCK', 'CREATED',
                5, 2, 20, 20000,
                NULL, 0,
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL
            )
            """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, interviewId);
            statement.setString(2, ownerId);
            statement.setString(3, requestId);
            statement.setString(4, requestFingerprint);
            statement.setString(5, snapshotId);
            statement.setString(6, snapshotHash);
            statement.executeUpdate();
        }
    }

    private int updateSessionIfVersionMatches(
            DataSource dataSource,
            String interviewId,
            String ownerId,
            long expectedVersion,
            long newVersion
    ) throws SQLException {
        String sql = """
            UPDATE mock_interview_session
            SET interview_status = 'GENERATING_QUESTION',
                version = ?,
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE interview_id = ?
              AND owner_id = ?
              AND version = ?
            """;
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, newVersion);
            statement.setString(2, interviewId);
            statement.setString(3, ownerId);
            statement.setLong(4, expectedVersion);
            return statement.executeUpdate();
        }
    }
}