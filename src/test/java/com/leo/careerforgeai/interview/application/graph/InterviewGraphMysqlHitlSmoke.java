package com.leo.careerforgeai.interview.application.graph;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.leo.careerforgeai.interview.application.answer.InterviewAnswerSubmissionService;
import com.leo.careerforgeai.interview.application.model.contract.InterviewQuestionDraft;
import com.leo.careerforgeai.interview.application.port.InterviewRoleModelGateway;
import com.leo.careerforgeai.interview.application.port.InterviewRoundRepository;
import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionFactory;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionGenerationService;
import com.leo.careerforgeai.interview.application.question.InterviewQuestionPersistenceService;
import com.leo.careerforgeai.interview.application.session.MockInterviewNotFoundException;
import com.leo.careerforgeai.interview.config.InterviewGraphRuntimeConfiguration;
import com.leo.careerforgeai.interview.domain.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.InterviewMode;
import com.leo.careerforgeai.interview.domain.InterviewQuestion;
import com.leo.careerforgeai.interview.domain.InterviewQuestionType;
import com.leo.careerforgeai.interview.domain.InterviewRound;
import com.leo.careerforgeai.interview.domain.InterviewRouteDecision;
import com.leo.careerforgeai.interview.domain.InterviewStatus;
import com.leo.careerforgeai.interview.domain.InterviewWaitReason;
import com.leo.careerforgeai.interview.domain.MockInterviewSession;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisInterviewRoundAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisPlusMockInterviewSessionAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewRoundFactPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.MockInterviewSessionPersistenceConverter;
import com.leo.careerforgeai.interview.infrastructure.persistence.mapper.MockInterviewSessionMapper;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import com.leo.careerforgeai.interview.domain.InterviewReviewPlan;

import java.util.Map;

import static org.mockito.Mockito.when;
import com.leo.careerforgeai.interview.application.port.InterviewNodeExecutionRepository;
import com.leo.careerforgeai.interview.infrastructure.persistence.adapter.MyBatisInterviewNodeExecutionAdapter;
import com.leo.careerforgeai.interview.infrastructure.persistence.converter.InterviewNodeExecutionPersistenceConverter;

/**
 * @program: CareerForge-AI
 * @description: 使用真实MySQL业务事实和Checkpoint验证面试Graph同JVM、跨JVM、owner隔离及重复恢复
 * @author: Miao Zheng
 * @date: 2026-08-29
 **/
@SpringBootTest(
        classes = InterviewGraphMysqlHitlSmoke.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "careerforge.persistence.enabled=true",
                "spring.flyway.enabled=true",
                "spring.flyway.validate-on-migrate=true"
        }
)
@EnabledIfSystemProperty(named = "cp6.mysql.smoke", matches = "true")
class InterviewGraphMysqlHitlSmoke {

    private static final String ANSWER_TEXT = "虚拟线程适合大量阻塞任务，但不会提高CPU密集计算速度。";

    @Autowired
    private CurrentActorProvider currentActorProvider;

    @Autowired
    private MockInterviewSessionRepository sessionRepository;

    @Autowired
    private InterviewRoundRepository roundRepository;

    @Autowired
    private InterviewQuestionPersistenceService persistenceService;

    @Autowired
    private InterviewQuestionGenerationService generationService;

    @Autowired
    private InterviewGraphExecutionService executionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldVerifyMysqlHitlRecovery() {
        UUID interviewId = UUID.fromString(requiredSystemProperty("cp6.interview.id"));

        switch (requiredSystemProperty("cp6.mysql.mode")) {
            case "same-jvm" -> {
                start(interviewId);
                resume(interviewId);
            }
            case "start" -> start(interviewId);
            case "owner-isolation" -> ownerIsolation(interviewId);
            case "resume" -> resume(interviewId);
            default -> throw new IllegalArgumentException("不支持的cp6.mysql.mode");
        }
    }

    private void start(UUID interviewId) {
        createSession(interviewId);
        configureQuestionGeneration(interviewId);

        InterviewGraphState state = executionService.start(interviewId);
        MockInterviewSession session = sessionRepository
                .findById(owner(), interviewId)
                .orElseThrow();

        assertThat(session.status()).isEqualTo(InterviewStatus.WAITING_FOR_ANSWER);
        assertThat(session.version()).isEqualTo(2);
        assertThat(state.currentRound()).isEqualTo(1);
        assertThat(state.currentQuestionId()).isPresent();
        assertThat(state.waitReason()).contains(InterviewWaitReason.WAITING_FOR_ANSWER);
        assertThat(activeCheckpointCount(interviewId)).isPositive();

        System.out.printf(
                "mode=start, interviewId=%s, owner=%s, questionId=%s, checkpointCount=%d%n",
                interviewId,
                owner().value(),
                state.currentQuestionId().orElseThrow(),
                activeCheckpointCount(interviewId)
        );
    }

    private void resume(UUID interviewId) {
        MockInterviewSession waiting = sessionRepository
                .findById(owner(), interviewId)
                .orElseThrow();
        InterviewRound round = roundRepository
                .findRoundByNumber(owner(), interviewId, 1)
                .orElseThrow();
        InterviewQuestion question = roundRepository
                .findQuestionByRound(owner(), interviewId, round.roundId())
                .orElseThrow();
        UUID requestId = deterministicUuid("answer-request:" + interviewId);

        InterviewGraphState resumed = executionService.submitAnswerAndResume(
                interviewId,
                1,
                question.questionId(),
                requestId,
                waiting.version(),
                ANSWER_TEXT
        );
        InterviewGraphState replay = executionService.submitAnswerAndResume(
                interviewId,
                1,
                question.questionId(),
                requestId,
                waiting.version(),
                ANSWER_TEXT
        );

        MockInterviewSession reviewing = sessionRepository
                .findById(owner(), interviewId)
                .orElseThrow();

        assertThat(reviewing.status()).isEqualTo(InterviewStatus.REVIEWING);
        assertThat(reviewing.version()).isEqualTo(3);
        assertThat(resumed.answerId()).isPresent();
        assertThat(resumed.waitReason()).isEmpty();
        assertThat(replay.data()).isEqualTo(resumed.data());
        assertThat(count(
                "SELECT COUNT(*) FROM interview_answer WHERE interview_id = ? AND owner_id = ?",
                interviewId.toString(),
                owner().value()
        )).isEqualTo(1);
        assertThat(activeCheckpointCount(interviewId)).isPositive();

        System.out.printf(
                "mode=resume, interviewId=%s, owner=%s, answerId=%s, checkpointCount=%d%n",
                interviewId,
                owner().value(),
                resumed.answerId().orElseThrow(),
                activeCheckpointCount(interviewId)
        );
    }

    private void ownerIsolation(UUID interviewId) {
        assertThat(count(
                "SELECT COUNT(*) FROM mock_interview_session WHERE interview_id = ?",
                interviewId.toString()
        )).isEqualTo(1);

        assertThatThrownBy(() -> executionService.start(interviewId))
                .isInstanceOf(MockInterviewNotFoundException.class);

        System.out.printf(
                "mode=owner-isolation, interviewId=%s, rejectedOwner=%s%n",
                interviewId,
                owner().value()
        );
    }

    private void configureQuestionGeneration(UUID interviewId) {
        doAnswer(invocation -> {
            Optional<InterviewQuestion> existing =
                    persistenceService.startFirstQuestionGeneration(interviewId);
            if (existing.isPresent()) return existing.get();
            return persistenceService.persistFirstQuestion(interviewId, modelResult(interviewId));
        }).when(generationService).generateAndPersistQuestion(
                eq(interviewId),
                eq(1),
                isNull(),
                any(Duration.class)
        );
    }

    private void createSession(UUID interviewId) {
        if (sessionRepository.findById(owner(), interviewId).isPresent()) return;

        UUID targetRoleId = deterministicUuid("target-role:" + interviewId);
        UUID snapshotId = deterministicUuid("snapshot:" + interviewId);

        jdbcTemplate.update("""
                INSERT INTO target_role (
                    target_role_id, owner_id, target_role_version,
                    source_ref, source_hash, parser_version,
                    prompt_version, requirements_json, confirmed_at
                )
                VALUES (?, ?, 1, ?, ?, ?, ?, JSON_OBJECT(), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE target_role_id = target_role_id
                """,
                targetRoleId.toString(),
                owner().value(),
                "cp6-graph-smoke-" + interviewId,
                "a".repeat(64),
                "cp6-parser-v1",
                "cp6-prompt-v1"
        );

        jdbcTemplate.update("""
                INSERT INTO mock_interview_input_snapshot (
                    input_snapshot_id, owner_id, schema_version,
                    target_role_id, target_role_version,
                    skill_gap_snapshot_id, training_plan_id,
                    training_plan_version, snapshot_context_json,
                    snapshot_hash, created_at
                )
                VALUES (?, ?, 1, ?, 1, NULL, NULL, NULL, JSON_OBJECT(), ?, CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE input_snapshot_id = input_snapshot_id
                """,
                snapshotId.toString(),
                owner().value(),
                targetRoleId.toString(),
                "b".repeat(64)
        );

        MockInterviewSession candidate = MockInterviewSession.create(
                interviewId,
                owner(),
                deterministicUuid("session-request:" + interviewId),
                "c".repeat(64),
                InterviewMode.TARGETED_MOCK,
                snapshotId,
                "b".repeat(64),
                new InterviewBudgetPolicy(3, 1, 12, 12_000),
                Clock.systemUTC().instant()
        );
        assertThat(sessionRepository.claim(candidate).interviewId()).isEqualTo(interviewId);
    }

    private InterviewRoleModelGateway.Result<InterviewQuestionDraft> modelResult(UUID interviewId) {
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
                "cp6-smoke-" + interviewId,
                "stub-model",
                "interviewer-v1",
                new ModelUsage(300, 100, 400),
                10,
                1,
                false,
                "d".repeat(64)
        );
    }

    private long activeCheckpointCount(UUID interviewId) {
        return count("""
                SELECT COUNT(*)
                FROM LANGRAPH4J_CHECKPOINT checkpoint_record
                JOIN LANGRAPH4J_THREAD thread_record
                  ON thread_record.thread_id = checkpoint_record.thread_id
                WHERE thread_record.thread_name = ?
                  AND thread_record.is_released = FALSE
                """, InterviewGraphExecutionService.threadId(interviewId));
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }

    private ActorId owner() {
        return currentActorProvider.currentActor();
    }

    private static UUID deterministicUuid(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static String mysqlUrl() {
        String url = requiredEnvironment("CP2_MYSQL_URL");
        if (!url.matches("^jdbc:mysql://[^/]+/careerforge_cp2_smoke(?:\\?.*)?$")) {
            throw new IllegalStateException("CP2_MYSQL_URL必须指向careerforge_cp2_smoke隔离库");
        }
        return url;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property: " + name);
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
            InterviewRoundFactPersistenceConverter.class,
            MyBatisInterviewNodeExecutionAdapter.class,
            InterviewNodeExecutionPersistenceConverter.class,
            InterviewGraphRuntimeConfiguration.class
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
            ActorId actorId = new ActorId(requiredSystemProperty("cp6.owner.id"));
            return () -> actorId;
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        InterviewQuestionFactory interviewQuestionFactory(JsonMapper jsonMapper) {
            return new InterviewQuestionFactory(jsonMapper);
        }

        @Bean
        InterviewQuestionPersistenceService interviewQuestionPersistenceService(
                CurrentActorProvider actorProvider,
                MockInterviewSessionRepository sessionRepository,
                InterviewRoundRepository roundRepository,
                InterviewNodeExecutionRepository executionRepository,
                InterviewQuestionFactory questionFactory,
                JsonMapper jsonMapper,
                Clock clock
        ) {
            return new InterviewQuestionPersistenceService(
                    actorProvider,
                    sessionRepository,
                    roundRepository,
                    executionRepository,
                    questionFactory,
                    jsonMapper,
                    clock
            );
        }

        @Bean
        InterviewQuestionGenerationService interviewQuestionGenerationService() {
            return mock(InterviewQuestionGenerationService.class);
        }

        @Bean
        InterviewAnswerSubmissionService interviewAnswerSubmissionService(
                CurrentActorProvider actorProvider,
                MockInterviewSessionRepository sessionRepository,
                InterviewRoundRepository roundRepository,
                JsonMapper jsonMapper,
                Clock clock
        ) {
            return new InterviewAnswerSubmissionService(
                    actorProvider,
                    sessionRepository,
                    roundRepository,
                    jsonMapper,
                    clock
            );
        }

        @Bean
        InterviewGraphNodes interviewGraphNodes(
                CurrentActorProvider actorProvider,
                MockInterviewSessionRepository sessionRepository,
                InterviewRoundRepository roundRepository,
                InterviewQuestionGenerationService generationService
        ) {
            return new InterviewGraphNodes(
                    actorProvider,
                    sessionRepository,
                    roundRepository,
                    generationService,
                    Duration.ofSeconds(30)
            );
        }

        @Bean
        InterviewReviewGraphNodes interviewReviewGraphNodes() {
            InterviewReviewGraphNodes reviewNodes = mock(InterviewReviewGraphNodes.class);
            when(reviewNodes.prepareReviews(any(InterviewGraphState.class)))
                    .thenReturn(Map.of(
                            InterviewGraphState.REVIEW_PLAN,
                            InterviewReviewPlan.TECHNICAL_ONLY.name()
                    ));
            when(reviewNodes.technicalReview(any(InterviewGraphState.class)))
                    .thenReturn(Map.of(
                            InterviewGraphState.TECHNICAL_REVIEW_ID,
                            "00000000-0000-0000-0000-000000000091"
                    ));
            when(reviewNodes.evidenceReview(any(InterviewGraphState.class)))
                    .thenReturn(Map.of(
                            InterviewGraphState.EVIDENCE_REVIEW_ID,
                            "00000000-0000-0000-0000-000000000092"
                    ));
            when(reviewNodes.joinReviews(any(InterviewGraphState.class))).thenReturn(Map.of());
            return reviewNodes;
        }

        @Bean
        InterviewGraphWorkflow interviewGraphWorkflow(
                InterviewGraphNodes nodes,
                InterviewReviewGraphNodes reviewNodes,
                InterviewSupervisionGraphNode supervisionNode,
                InterviewRouteGraphNodes routeNodes
        ) {
            return new InterviewGraphWorkflow(nodes, reviewNodes, supervisionNode, routeNodes);
        }

        @Bean
        InterviewSupervisionGraphNode interviewSupervisionGraphNode() {
            InterviewSupervisionGraphNode node = mock(InterviewSupervisionGraphNode.class);
            when(node.superviseRound(any(InterviewGraphState.class)))
                    .thenReturn(InterviewGraphState.routeDecisionUpdate(InterviewRouteDecision.GENERATE_REPORT));
            return node;
        }

        @Bean
        InterviewRouteGraphNodes interviewRouteGraphNodes() {
            InterviewRouteGraphNodes nodes = mock(InterviewRouteGraphNodes.class);
            when(nodes.startReportGeneration(any(InterviewGraphState.class))).thenReturn(Map.of());
            return nodes;
        }
    }
}