package com.leo.careerforgeai.memory.infrastructure.persistence;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.memory.application.extraction.MemoryExtractionFingerprintGenerator;
import com.leo.careerforgeai.memory.application.port.conversation.CoachingConversationRepository;
import com.leo.careerforgeai.memory.application.port.extraction.MemoryExtractionReceiptRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.application.profile.MemoryDecisionApplicationService;
import com.leo.careerforgeai.memory.domain.conversation.CoachingSession;
import com.leo.careerforgeai.memory.domain.conversation.ConversationTurn;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionInputIdentity;
import com.leo.careerforgeai.memory.domain.extraction.MemoryExtractionReceipt;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryStatus;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
import com.leo.careerforgeai.memory.domain.profile.TimeConstraintKey;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用真实MySQL验证Memory、会话和成功提取凭证的迁移、隔离、约束、重启读取及事务回滚
 * @author: Miao Zheng
 * @date: 2026-08-14
 **/
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "careerforge.persistence.enabled=true",
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=mysql-smoke-placeholder",
        "careerforge.model.name=mysql-smoke-model",
        "spring.ai.mcp.server.enabled=false"
})
@Import(MySqlMemoryPersistenceSmoke.SmokeActorConfiguration.class)
class MySqlMemoryPersistenceSmoke {

    private static final ActorId SMOKE_ACTOR = new ActorId("mysql-smoke-actor");
    private static final ActorId OTHER_ACTOR = new ActorId("mysql-smoke-other");
    private static final Instant NOW = Instant.parse("2026-08-12T04:00:00Z");

    private final Flyway flyway;
    private final JdbcTemplate jdbcTemplate;
    private final MemoryRepository memoryRepository;
    private final MemoryDecisionRepository decisionRepository;
    private final MemoryDecisionApplicationService decisionService;
    private final CoachingConversationRepository conversationRepository;
    private final MemoryExtractionReceiptRepository receiptRepository;
    private final MemoryExtractionFingerprintGenerator fingerprintGenerator;

    @Autowired
    MySqlMemoryPersistenceSmoke(
            Flyway flyway,
            JdbcTemplate jdbcTemplate,
            MemoryRepository memoryRepository,
            MemoryDecisionRepository decisionRepository,
            MemoryDecisionApplicationService decisionService,
            CoachingConversationRepository conversationRepository,
            MemoryExtractionReceiptRepository receiptRepository,
            MemoryExtractionFingerprintGenerator fingerprintGenerator
    ) {
        this.flyway = flyway;
        this.jdbcTemplate = jdbcTemplate;
        this.memoryRepository = memoryRepository;
        this.decisionRepository = decisionRepository;
        this.decisionService = decisionService;
        this.conversationRepository = conversationRepository;
        this.receiptRepository = receiptRepository;
        this.fingerprintGenerator = fingerprintGenerator;
    }

    @BeforeEach
    void cleanBeforeTest() {
        cleanSmokeData();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanSmokeData();
    }

    @Test
    void shouldMigratePersistFilterByOwnerAndReadAfterContextRestart() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'memory_item',
                      'memory_decision',
                      'coaching_session',
                      'coaching_turn',
                      'memory_extraction_receipt'
                  )
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(5);

        MemoryItem candidate = pendingMemory(UUID.randomUUID(), "我每周可以学习10小时");
        memoryRepository.insert(candidate);

        assertThat(memoryRepository.findById(SMOKE_ACTOR, candidate.memoryId())).contains(candidate);
        assertThat(memoryRepository.findById(OTHER_ACTOR, candidate.memoryId())).isEmpty();

        MemoryItem confirmed = decisionService.confirm(candidate.memoryId(), 0, "MySQL Smoke确认");

        assertThat(confirmed.status()).isEqualTo(MemoryStatus.CONFIRMED);
        assertThat(confirmed.version()).isEqualTo(1);
        assertThat(decisionRepository.findByMemoryId(SMOKE_ACTOR, candidate.memoryId())).hasSize(1);

        try (ConfigurableApplicationContext restartedContext = startFreshApplicationContext()) {
            MemoryRepository restartedRepository = restartedContext.getBean(MemoryRepository.class);

            assertThat(restartedRepository.findById(SMOKE_ACTOR, candidate.memoryId()))
                    .get()
                    .extracting(MemoryItem::status)
                    .isEqualTo(MemoryStatus.CONFIRMED);

            assertThat(restartedRepository.findById(SMOKE_ACTOR, candidate.memoryId()))
                    .get()
                    .extracting(MemoryItem::version)
                    .isEqualTo(1L);
        }
    }

    @Test
    void shouldPersistEmptyReceiptFilterByOwnerRejectDuplicateAndReadAfterRestart() {
        UUID sessionId = UUID.randomUUID();
        CoachingSession session = CoachingSession.create(
                sessionId,
                SMOKE_ACTOR,
                "Memory提取凭证Smoke",
                NOW
        );
        ConversationTurn sourceTurn = ConversationTurn.completedUser(
                UUID.randomUUID(),
                sessionId,
                UUID.randomUUID(),
                SMOKE_ACTOR,
                1,
                "你好，谢谢",
                NOW
        );

        conversationRepository.insertSession(session);
        conversationRepository.insertTurn(sourceTurn);

        MemoryExtractionInputIdentity identity = fingerprintGenerator.generate(List.of(sourceTurn));
        MemoryExtractionReceipt receipt = new MemoryExtractionReceipt(
                UUID.randomUUID(),
                SMOKE_ACTOR,
                identity,
                List.of(),
                "mysql-smoke-model-request",
                new ModelUsage(100, 20, 120),
                350,
                1,
                NOW
        );

        receiptRepository.insert(receipt);

        assertThat(receiptRepository.findByIdentity(
                SMOKE_ACTOR,
                identity.extractorVersion(),
                identity.inputFingerprint()
        )).contains(receipt);

        assertThat(receiptRepository.findByIdentity(
                OTHER_ACTOR,
                identity.extractorVersion(),
                identity.inputFingerprint()
        )).isEmpty();

        Integer memoryIdCount = jdbcTemplate.queryForObject(
                """
                SELECT JSON_LENGTH(memory_ids_json)
                FROM memory_extraction_receipt
                WHERE receipt_id = ?
                """,
                Integer.class,
                receipt.receiptId().toString()
        );

        assertThat(memoryIdCount).isZero();

        MemoryExtractionReceipt duplicate = new MemoryExtractionReceipt(
                UUID.randomUUID(),
                SMOKE_ACTOR,
                identity,
                List.of(),
                "mysql-smoke-duplicate-request",
                new ModelUsage(110, 25, 135),
                400,
                1,
                NOW
        );

        assertThatThrownBy(() -> receiptRepository.insert(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        try (ConfigurableApplicationContext restartedContext = startFreshApplicationContext()) {
            MemoryExtractionReceiptRepository restartedRepository =
                    restartedContext.getBean(MemoryExtractionReceiptRepository.class);

            assertThat(restartedRepository.findByIdentity(
                    SMOKE_ACTOR,
                    identity.extractorVersion(),
                    identity.inputFingerprint()
            )).contains(receipt);
        }
    }

    @Test
    void shouldRollbackMemoryUpdateWhenDecisionInsertFails() {
        MemoryItem candidate = pendingMemory(UUID.randomUUID(), "我每周可以学习6小时");
        memoryRepository.insert(candidate);

        MemoryDecision occupiedDecisionVersion = new MemoryDecision(
                UUID.randomUUID(),
                candidate.memoryId(),
                SMOKE_ACTOR,
                MemoryDecisionType.CONFIRM,
                MemoryStatus.PENDING,
                MemoryStatus.CONFIRMED,
                0,
                null,
                "故障注入：提前占用同一Memory版本的决策唯一键",
                NOW
        );

        decisionRepository.insert(occupiedDecisionVersion);

        assertThatThrownBy(() -> decisionService.confirm(candidate.memoryId(), 0, "本次写入应当回滚"))
                .isInstanceOf(DataIntegrityViolationException.class);

        MemoryItem reloaded = memoryRepository.findById(
                SMOKE_ACTOR,
                candidate.memoryId()
        ).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(MemoryStatus.PENDING);
        assertThat(reloaded.version()).isZero();
        assertThat(decisionRepository.findByMemoryId(SMOKE_ACTOR, candidate.memoryId()))
                .containsExactly(occupiedDecisionVersion);
    }

    private MemoryItem pendingMemory(UUID memoryId, String content) {
        return MemoryItem.createPending(
                memoryId,
                SMOKE_ACTOR,
                MemoryType.TIME_CONSTRAINT,
                MemoryNormalizedKey.timeConstraint(TimeConstraintKey.WEEKLY_HOURS),
                content,
                new MemorySource(
                        MemorySourceType.CONVERSATION_TURN,
                        "mysql-smoke-turn-" + memoryId,
                        "a".repeat(64)
                ),
                List.of("mysql-smoke-turn-" + memoryId),
                NOW
        );
    }

    private ConfigurableApplicationContext startFreshApplicationContext() {
        return new SpringApplicationBuilder(CareerForgeAiApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.autoconfigure.exclude=",
                        "--careerforge.persistence.enabled=true",
                        "--careerforge.model.base-url=http://localhost",
                        "--careerforge.model.api-key=mysql-smoke-placeholder",
                        "--careerforge.model.name=mysql-smoke-model",
                        "--spring.ai.mcp.server.enabled=false"
                );
    }

    private void cleanSmokeData() {
        jdbcTemplate.update(
                "DELETE FROM memory_extraction_receipt WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );
        jdbcTemplate.update(
                "DELETE FROM memory_decision WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );
        jdbcTemplate.update(
                "DELETE FROM memory_item WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );
        jdbcTemplate.update(
                "DELETE FROM coaching_turn WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );
        jdbcTemplate.update(
                "DELETE FROM coaching_session WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );
    }

    /**
     * 仅在当前Smoke中覆盖固定开发Actor，不影响生产配置。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class SmokeActorConfiguration {

        @Bean
        @Primary
        CurrentActorProvider smokeCurrentActorProvider() {
            return () -> SMOKE_ACTOR;
        }
    }
}