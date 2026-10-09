package com.leo.careerforgeai.interviewflow.infrastructure.persistence;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用隔离MySQL验证新面试七表、约束、索引和单活动面试不变量
 * @author: Miao Zheng
 * @date: 2026-09-30
 */
class InterviewFlowMigrationMysqlSmoke {

    private static final Set<String> INTERVIEW_FLOW_TABLES = Set.of(
            "INTERVIEW_FLOW_SESSION",
            "INTERVIEW_FLOW_INPUT_SNAPSHOT",
            "INTERVIEW_FLOW_TOPIC",
            "INTERVIEW_FLOW_QUESTION",
            "INTERVIEW_FLOW_ANSWER",
            "INTERVIEW_FLOW_TASK",
            "INTERVIEW_FLOW_REPORT"
    );

    @Test
    @EnabledIfSystemProperty(
            named = "interviewflow.mysql.smoke",
            matches = "true"
    )
    void shouldCreateSevenTablesAndEnforcePersistenceInvariants()
            throws Exception {
        MysqlDataSource dataSource = mysqlDataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();

        flyway.migrate();

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion())
                .isEqualTo("20");
        assertThat(interviewFlowTables(dataSource))
                .containsExactlyInAnyOrderElementsOf(INTERVIEW_FLOW_TABLES);

        assertThat(foreignKeyCount(dataSource)).isGreaterThanOrEqualTo(7);
        assertThat(cascadeDeleteCount(dataSource)).isZero();
        assertThat(hasGeneratedActiveOwnerColumn(dataSource)).isTrue();

        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_session",
                "active_owner_id"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_input_snapshot",
                "session_id"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_answer",
                "question_id"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_task",
                "business_key"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_report",
                "session_id"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_topic",
                "session_id",
                "sequence_no"
        )).isTrue();
        assertThat(hasUniqueIndex(
                dataSource,
                "interview_flow_question",
                "session_id",
                "display_order"
        )).isTrue();

        verifyOnlyOneInterviewingSessionPerOwner(dataSource);
    }

    private Set<String> interviewFlowTables(DataSource dataSource)
            throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """;
        Set<String> actual = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.createStatement()
                     .executeQuery(sql)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(1)
                        .toUpperCase(Locale.ROOT);
                if (tableName.startsWith("INTERVIEW_FLOW_")) {
                    actual.add(tableName);
                }
            }
        }
        return actual;
    }

    private int foreignKeyCount(DataSource dataSource) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name LIKE 'interview\\_flow\\_%'
                """;
        return queryForInt(dataSource, sql);
    }

    private int cascadeDeleteCount(DataSource dataSource)
            throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name LIKE 'interview\\_flow\\_%'
                  AND delete_rule = 'CASCADE'
                """;
        return queryForInt(dataSource, sql);
    }

    private boolean hasGeneratedActiveOwnerColumn(DataSource dataSource)
            throws SQLException {
        String sql = """
                SELECT generation_expression
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'interview_flow_session'
                  AND column_name = 'active_owner_id'
                """;
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.createStatement()
                     .executeQuery(sql)) {
            if (!resultSet.next()) {
                return false;
            }
            String expression = resultSet.getString(1);
            return expression != null
                    && expression.toUpperCase(Locale.ROOT)
                    .contains("INTERVIEWING");
        }
    }

    private boolean hasUniqueIndex(
            DataSource dataSource,
            String tableName,
            String... columnNames
    ) throws SQLException {
        String sql = """
                SELECT index_name,
                       GROUP_CONCAT(
                           column_name
                           ORDER BY seq_in_index
                           SEPARATOR ','
                       ) AS index_columns
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND non_unique = 0
                GROUP BY index_name
                """;
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                String expectedColumns = String.join(",", columnNames);
                while (resultSet.next()) {
                    if (expectedColumns.equals(resultSet.getString(2))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private void verifyOnlyOneInterviewingSessionPerOwner(
            DataSource dataSource
    ) throws SQLException {
        String ownerId = "interview-flow-smoke-" + UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertSession(connection, ownerId, "READY");
                insertSession(connection, ownerId, "READY");
                insertSession(connection, ownerId, "INTERVIEWING");

                assertThatThrownBy(() ->
                        insertSession(connection, ownerId, "INTERVIEWING")
                ).isInstanceOf(SQLException.class);
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertSession(
            Connection connection,
            String ownerId,
            String sessionStage
    ) throws SQLException {
        String sql = """
                INSERT INTO interview_flow_session (
                    interview_id, owner_id, interview_mode,
                    session_stage, timer_state,
                    duration_seconds, accumulated_answer_seconds,
                    follow_up_count, consecutive_follow_up_count,
                    input_version, plan_version, version,
                    created_at, updated_at
                )
                VALUES (
                    ?, ?, 'QUICK',
                    ?, 'NOT_STARTED',
                    3600, 0,
                    0, 0,
                    1, 1, 0,
                    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, ownerId);
            statement.setString(3, sessionStage);
            statement.executeUpdate();
        }
    }

    private int queryForInt(DataSource dataSource, String sql)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.createStatement()
                     .executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("数据库统计查询没有返回结果");
            }
            return resultSet.getInt(1);
        }
    }

    private MysqlDataSource mysqlDataSource() {
        String url = requiredEnvironment("INTERVIEW_FLOW_MYSQL_URL");
        if (!url.matches(
                "^jdbc:mysql://[^/]+/careerforge_interview_flow_smoke(?:\\?.*)?$"
        )) {
            throw new IllegalStateException(
                    "INTERVIEW_FLOW_MYSQL_URL必须指向"
                            + "careerforge_interview_flow_smoke隔离库"
            );
        }
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(url);
        dataSource.setUser(requiredEnvironment("INTERVIEW_FLOW_MYSQL_USER"));
        dataSource.setPassword(requiredEnvironment(
                "INTERVIEW_FLOW_MYSQL_PASSWORD"
        ));
        return dataSource;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }
        return value;
    }
}
