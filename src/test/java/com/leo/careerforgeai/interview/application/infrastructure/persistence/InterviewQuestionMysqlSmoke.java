package com.leo.careerforgeai.interview.application.infrastructure.persistence;

import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionPersistenceService;
import com.leo.careerforgeai.interview.application.session.MockInterviewVersionConflictException;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionFactory;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisInterviewRoundAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisPlusMockInterviewSessionAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewRoundFactPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.MockInterviewSessionPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.MockInterviewSessionMapper;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.time.ZoneOffset;

/**
 * @program: CareerForge-AI
 * @description: 在隔离MySQL中验证首题正常提交、幂等重放及Session CAS失败时事务回滚
 * @author: Miao Zheng
 * @date: 2026-08-28
 **/
@SpringBootTest(
        classes = InterviewQuestionMysqlSmoke.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "careerforge.persistence.enabled=true",
                "spring.flyway.enabled=true"
        }
)
@EnabledIfSystemProperty(named = "cp5.mysql.smoke", matches = "true")
class InterviewQuestionMysqlSmoke {

    private static final ActorId OWNER =
            new ActorId("cp5-mysql-" + UUID.randomUUID());
    private static final Instant NOW =
            Instant.parse("2026-08-28T00:00:00Z");

    @Autowired
    private InterviewQuestionPersistenceService persistenceService;

    @MockitoSpyBean
    private MockInterviewSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", InterviewQuestionMysqlSmoke::mysqlUrl);
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("CP2_MYSQL_USER"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("CP2_MYSQL_PASSWORD"));
    }

    @Test
    void shouldCommitRoundQuestionAndWaitingStatusExactlyOnce() {
        UUID interviewId = createSession(1, 'c');

        Optional<InterviewQuestion> existing =
                persistenceService.startFirstQuestionGeneration(interviewId);
        InterviewQuestion stored =
                persistenceService.persistFirstQuestion(interviewId, result());
        Optional<InterviewQuestion> replay =
                persistenceService.startFirstQuestionGeneration(interviewId);

        assertThat(existing).isEmpty();
        assertThat(replay).contains(stored);
        assertThat(count(
                "SELECT COUNT(*) FROM interview_round WHERE interview_id = ? AND owner_id = ?",
                interviewId.toString(),
                OWNER.value()
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM interview_question WHERE interview_id = ? AND owner_id = ?",
                interviewId.toString(),
                OWNER.value()
        )).isEqualTo(1);

        MockInterviewSession session = sessionRepository
                .findById(OWNER, interviewId)
                .orElseThrow();
        assertThat(session.status()).isEqualTo(InterviewStatus.WAITING_FOR_ANSWER);
        assertThat(session.version()).isEqualTo(2);
    }

    @Test
    void shouldRollbackRoundAndQuestionWhenSessionCasFails() {
        UUID interviewId = createSession(2, 'd');
        assertThat(persistenceService.startFirstQuestionGeneration(interviewId))
                .isEmpty();

        doReturn(false).when(sessionRepository).updateIfVersionMatches(
                eq(OWNER),
                argThat(session ->
                        session.status() == InterviewStatus.WAITING_FOR_ANSWER),
                eq(1L)
        );

        assertThatThrownBy(() ->
                persistenceService.persistFirstQuestion(interviewId, result())
        ).isInstanceOf(MockInterviewVersionConflictException.class);

        assertThat(count(
                "SELECT COUNT(*) FROM interview_round WHERE interview_id = ? AND owner_id = ?",
                interviewId.toString(),
                OWNER.value()
        )).isZero();
        assertThat(count(
                "SELECT COUNT(*) FROM interview_question WHERE interview_id = ? AND owner_id = ?",
                interviewId.toString(),
                OWNER.value()
        )).isZero();

        MockInterviewSession session = sessionRepository
                .findById(OWNER, interviewId)
                .orElseThrow();
        assertThat(session.status()).isEqualTo(InterviewStatus.GENERATING_QUESTION);
        assertThat(session.version()).isEqualTo(1);
    }

    private UUID createSession(long targetRoleVersion, char hashSeed) {
        UUID targetRoleId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        String sourceHash = String.valueOf(hashSeed).repeat(64);
        String snapshotHash = String.valueOf((char) (hashSeed + 1)).repeat(64);

        jdbcTemplate.update("""
                INSERT INTO target_role (
                    target_role_id, owner_id, target_role_version,
                    source_ref, source_hash, parser_version,
                    prompt_version, requirements_json, confirmed_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, JSON_OBJECT(), CURRENT_TIMESTAMP(6))
                """,
                targetRoleId.toString(),
                OWNER.value(),
                targetRoleVersion,
                "cp5-mysql-smoke-" + targetRoleVersion,
                sourceHash,
                "cp5-parser-v1",
                "cp5-prompt-v1"
        );

        jdbcTemplate.update("""
                INSERT INTO mock_interview_input_snapshot (
                    input_snapshot_id, owner_id, schema_version,
                    target_role_id, target_role_version,
                    skill_gap_snapshot_id, training_plan_id,
                    training_plan_version, snapshot_context_json,
                    snapshot_hash, created_at
                )
                VALUES (
                    ?, ?, 1, ?, ?,
                    NULL, NULL, NULL, JSON_OBJECT(), ?, CURRENT_TIMESTAMP(6)
                )
                """,
                snapshotId.toString(),
                OWNER.value(),
                targetRoleId.toString(),
                targetRoleVersion,
                snapshotHash
        );

        MockInterviewSession session = MockInterviewSession.create(
                interviewId,
                OWNER,
                UUID.randomUUID(),
                "f".repeat(64),
                InterviewMode.TARGETED_MOCK,
                snapshotId,
                snapshotHash,
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                NOW
        );
        MockInterviewSession stored = sessionRepository.claim(session);
        assertThat(stored.interviewId()).isEqualTo(interviewId);
        return interviewId;
    }

    private InterviewRoleModelGateway.Result<InterviewQuestionDraft> result() {
        InterviewQuestionDraft draft = new InterviewQuestionDraft(
                InterviewQuestionType.TECHNICAL_KNOWLEDGE,
                "请说明虚拟线程适合解决什么问题，以及它不适合解决什么问题。",
                List.of("Java并发"),
                2,
                List.of("说明阻塞任务适用性", "说明CPU密集任务边界"),
                true,
                List.of()
        );
        return new InterviewRoleModelGateway.Result<>(
                draft,
                "cp5-mysql-request",
                "stub-model",
                "interviewer-v1",
                new ModelUsage(300, 100, 400),
                1200,
                1,
                false,
                "a".repeat(64)
        );
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private static String mysqlUrl() {
        String url = requiredEnvironment("CP2_MYSQL_URL");
        if (!url.matches("^jdbc:mysql://[^/]+/careerforge_cp2_smoke(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "CP2_MYSQL_URL必须指向careerforge_cp2_smoke隔离库"
            );
        }
        return url;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }
        return value;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = MockInterviewSessionMapper.class)
    @Import({
            MyBatisPlusMockInterviewSessionAdapter.class,
            MyBatisInterviewRoundAdapter.class,
            MockInterviewSessionPersistenceConverter.class,
            InterviewRoundFactPersistenceConverter.class
    })
    static class TestApplication {

        @Bean
        DataSource dataSource() {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setURL(mysqlUrl());
            dataSource.setUser(requiredEnvironment("CP2_MYSQL_USER"));
            dataSource.setPassword(requiredEnvironment("CP2_MYSQL_PASSWORD"));
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        CurrentActorProvider currentActorProvider() {
            return () -> OWNER;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        InterviewQuestionFactory interviewQuestionFactory(JsonMapper jsonMapper) {
            return new InterviewQuestionFactory(jsonMapper);
        }

        @Bean
        InterviewQuestionPersistenceService interviewQuestionPersistenceService(
                CurrentActorProvider currentActorProvider,
                MockInterviewSessionRepository sessionRepository,
                InterviewRoundRepository roundRepository,
                InterviewQuestionFactory questionFactory,
                Clock clock
        ) {
            return new InterviewQuestionPersistenceService(
                    currentActorProvider,
                    sessionRepository,
                    roundRepository,
                    questionFactory,
                    clock
            );
        }
    }
}