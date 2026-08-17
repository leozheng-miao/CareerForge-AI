package com.leo.careerforgeai.career.infrastructure.persistence;

import com.leo.careerforgeai.CareerForgeAiApplication;
import com.leo.careerforgeai.career.application.DeterministicSkillGapMatcher;
import com.leo.careerforgeai.career.application.SkillGapSnapshotApplicationService;
import com.leo.careerforgeai.career.application.TrainingPlanApplicationService;
import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.career.domain.TrainingPlanItem;
import com.leo.careerforgeai.memory.application.port.profile.MemoryDecisionRepository;
import com.leo.careerforgeai.memory.application.port.profile.MemoryRepository;
import com.leo.careerforgeai.memory.application.profile.ConfirmedSkillProfile;
import com.leo.careerforgeai.memory.application.profile.MemoryProfileQueryApplicationService;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecision;
import com.leo.careerforgeai.memory.domain.profile.MemoryDecisionType;
import com.leo.careerforgeai.memory.domain.profile.MemoryItem;
import com.leo.careerforgeai.memory.domain.profile.MemoryNormalizedKey;
import com.leo.careerforgeai.memory.domain.profile.MemorySource;
import com.leo.careerforgeai.memory.domain.profile.MemorySourceType;
import com.leo.careerforgeai.memory.domain.profile.MemoryType;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @program: CareerForge-AI
 * @description: 使用真实MySQL验证求职规划迁移、CRUD、owner隔离、重启读取和事务回滚
 * @author: Miao Zheng
 * @date: 2026-08-12
 **/
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "careerforge.persistence.enabled=true",
        "careerforge.model.base-url=http://localhost",
        "careerforge.model.api-key=mysql-smoke-placeholder",
        "careerforge.model.name=mysql-smoke-model",
        "spring.ai.mcp.server.enabled=false"
})
@Import(MySqlCareerPlanningPersistenceSmoke.SmokeActorConfiguration.class)
class MySqlCareerPlanningPersistenceSmoke {

    private static final ActorId SMOKE_ACTOR =
            new ActorId("mysql-career-smoke-actor");

    private static final ActorId OTHER_ACTOR =
            new ActorId("mysql-career-smoke-other");

    private static final Instant CREATED_AT =
            Instant.parse("2026-01-01T00:00:00Z");

    private final Flyway flyway;
    private final JdbcTemplate jdbcTemplate;
    private final CareerPlanningRepository repository;
    private final TrainingPlanApplicationService trainingPlanService;
    private final SkillGapSnapshotApplicationService skillGapService;
    private final MemoryProfileQueryApplicationService memoryProfileQueryService;
    private final MemoryRepository memoryRepository;
    private final MemoryDecisionRepository memoryDecisionRepository;

    @Autowired
    MySqlCareerPlanningPersistenceSmoke(
            Flyway flyway,
            JdbcTemplate jdbcTemplate,
            CareerPlanningRepository repository,
            TrainingPlanApplicationService trainingPlanService,
            SkillGapSnapshotApplicationService skillGapService,
            MemoryProfileQueryApplicationService memoryProfileQueryService,
            MemoryRepository memoryRepository,
            MemoryDecisionRepository memoryDecisionRepository
    ) {
        this.flyway = flyway;
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.trainingPlanService = trainingPlanService;
        this.skillGapService = skillGapService;
        this.memoryProfileQueryService = memoryProfileQueryService;
        this.memoryRepository = memoryRepository;
        this.memoryDecisionRepository = memoryDecisionRepository;
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
    void shouldMigratePersistFilterByOwnerAndReadPlanAfterRestart() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion())
                .isEqualTo("9");

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
                      'target_role',
                      'skill_gap_snapshot',
                      'training_plan',
                      'training_plan_item'
                  )
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(8);

        UUID targetRoleId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID gapItemId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        TargetRole targetRole = targetRole(targetRoleId);
        SkillGapSnapshot snapshot = gapSnapshot(
                snapshotId,
                targetRoleId,
                gapItemId
        );
        TrainingPlan pendingPlan = pendingPlan(
                planId,
                1,
                snapshotId,
                gapItemId,
                itemId
        );

        repository.insertTargetRole(targetRole);
        repository.insertSkillGapSnapshot(snapshot);
        repository.insertTrainingPlan(pendingPlan);

        assertThat(repository.findTargetRole(
                SMOKE_ACTOR,
                targetRoleId
        )).contains(targetRole);

        assertThat(repository.findSkillGapSnapshot(
                SMOKE_ACTOR,
                snapshotId
        )).contains(snapshot);

        assertThat(repository.findTrainingPlan(
                OTHER_ACTOR,
                planId
        )).isEmpty();

        TrainingPlan activePlan = trainingPlanService.activate(
                planId,
                pendingPlan.version()
        );

        assertThat(activePlan.status())
                .isEqualTo(TrainingPlan.PlanStatus.ACTIVE);

        try (ConfigurableApplicationContext restartedContext =
                     startFreshApplicationContext()) {
            CareerPlanningRepository restartedRepository =
                    restartedContext.getBean(
                            CareerPlanningRepository.class
                    );

            TrainingPlan restartedPlan =
                    restartedRepository.findTrainingPlan(
                            SMOKE_ACTOR,
                            planId
                    ).orElseThrow();

            assertThat(restartedPlan.status())
                    .isEqualTo(TrainingPlan.PlanStatus.ACTIVE);

            assertThat(restartedPlan.version())
                    .isEqualTo(activePlan.version());

            assertThat(restartedPlan.items())
                    .containsExactlyElementsOf(activePlan.items());
        }
    }

    @Test
    void shouldRollbackPlanInsertWhenPlanItemPrimaryKeyConflicts() {
        UUID targetRoleId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID gapItemId = UUID.randomUUID();
        UUID occupiedItemId = UUID.randomUUID();

        repository.insertTargetRole(
                targetRole(targetRoleId)
        );

        repository.insertSkillGapSnapshot(
                gapSnapshot(
                        snapshotId,
                        targetRoleId,
                        gapItemId
                )
        );

        TrainingPlan existingPlan = pendingPlan(
                UUID.randomUUID(),
                1,
                snapshotId,
                gapItemId,
                occupiedItemId
        );

        repository.insertTrainingPlan(existingPlan);

        TrainingPlan failedPlan = pendingPlan(
                UUID.randomUUID(),
                2,
                snapshotId,
                gapItemId,
                occupiedItemId
        );

        assertThatThrownBy(() ->
                repository.insertTrainingPlan(failedPlan)
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.findTrainingPlan(
                SMOKE_ACTOR,
                failedPlan.planId()
        )).isEmpty();

        assertThat(repository.findTrainingPlan(
                SMOKE_ACTOR,
                existingPlan.planId()
        )).contains(existingPlan);
    }

    @Test
    void shouldGenerateReplayHistoricalGapAfterProfileChangesAndReloadByOwner() {
        UUID targetRoleId = UUID.randomUUID();
        repository.insertTargetRole(targetRole(targetRoleId));

        MemoryItem javaProject = insertConfirmedSkill(
                UUID.randomUUID(),
                "Java",
                MemorySourceType.PROJECT_EVIDENCE,
                CREATED_AT.plusSeconds(10)
        );
        MemoryItem springSelfReport = insertConfirmedSkill(
                UUID.randomUUID(),
                "Spring Boot",
                MemorySourceType.CONVERSATION_TURN,
                CREATED_AT.plusSeconds(20)
        );

        ConfirmedSkillProfile profile =
                memoryProfileQueryService.findConfirmedSkillProfile();

        assertThat(profile.ownerId()).isEqualTo(SMOKE_ACTOR);
        assertThat(profile.profileVersion()).isEqualTo(2);
        assertThat(profile.skillEvidence())
                .extracting(MemoryItem::memoryId)
                .containsExactly(javaProject.memoryId(), springSelfReport.memoryId());

        SkillGapSnapshot first = skillGapService.generate(
                targetRoleId,
                1,
                profile.profileVersion()
        );

        Map<String, SkillGapSnapshot.GapItem> itemsByRef =
                first.items().stream().collect(Collectors.toMap(
                        SkillGapSnapshot.GapItem::requirementRef,
                        Function.identity()
                ));

        assertThat(first.ownerId()).isEqualTo(SMOKE_ACTOR);
        assertThat(first.targetRoleId()).isEqualTo(targetRoleId);
        assertThat(first.targetRoleVersion()).isEqualTo(1);
        assertThat(first.profileVersion()).isEqualTo(2);
        assertThat(first.algorithmVersion())
                .isEqualTo(DeterministicSkillGapMatcher.ALGORITHM_VERSION);
        assertThat(first.items()).hasSize(5);

        assertThat(itemsByRef.get("programmingLanguages[0]").status())
                .isEqualTo(SkillGapSnapshot.GapStatus.MATCHED);
        assertThat(itemsByRef.get("programmingLanguages[0]").evidenceMemoryIds())
                .containsExactly(javaProject.memoryId());

        assertThat(itemsByRef.get("backendAndInfrastructureRequirements[0]").status())
                .isEqualTo(SkillGapSnapshot.GapStatus.UNVERIFIED);
        assertThat(itemsByRef.get("backendAndInfrastructureRequirements[0]").evidenceMemoryIds())
                .containsExactly(springSelfReport.memoryId());

        assertThat(itemsByRef.get("agentRequirements[0]").status())
                .isEqualTo(SkillGapSnapshot.GapStatus.MISSING);
        assertThat(itemsByRef.get("ragRequirements[0]").status())
                .isEqualTo(SkillGapSnapshot.GapStatus.MISSING);
        assertThat(itemsByRef.get("engineeringRequirements[0]").status())
                .isEqualTo(SkillGapSnapshot.GapStatus.MISSING);

        MemoryItem laterSkill = insertConfirmedSkill(
                UUID.randomUUID(),
                "Docker",
                MemorySourceType.PROJECT_EVIDENCE,
                CREATED_AT.plusSeconds(30)
        );
        ConfirmedSkillProfile changedProfile =
                memoryProfileQueryService.findConfirmedSkillProfile();

        assertThat(changedProfile.profileVersion()).isEqualTo(3);
        assertThat(changedProfile.skillEvidence())
                .extracting(MemoryItem::memoryId)
                .contains(laterSkill.memoryId());

        SkillGapSnapshot replay = skillGapService.generate(
                targetRoleId,
                1,
                profile.profileVersion()
        );

        assertThat(replay).isEqualTo(first);
        assertThat(repository.findSkillGapSnapshot(
                SMOKE_ACTOR,
                first.snapshotId()
        )).contains(first);
        assertThat(repository.findSkillGapSnapshotByInputVersions(
                SMOKE_ACTOR,
                targetRoleId,
                1,
                2,
                DeterministicSkillGapMatcher.ALGORITHM_VERSION
        )).contains(first);
        assertThat(repository.findSkillGapSnapshot(
                OTHER_ACTOR,
                first.snapshotId()
        )).isEmpty();

        Integer snapshotCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM skill_gap_snapshot
                WHERE owner_id = ?
                  AND target_role_id = ?
                  AND target_role_version = 1
                  AND profile_version = 2
                  AND algorithm_version = ?
                """,
                Integer.class,
                SMOKE_ACTOR.value(),
                targetRoleId.toString(),
                DeterministicSkillGapMatcher.ALGORITHM_VERSION
        );

        assertThat(snapshotCount).isEqualTo(1);
    }

    private TargetRole targetRole(UUID targetRoleId) {
        return TargetRole.createConfirmed(
                targetRoleId,
                SMOKE_ACTOR,
                1,
                "mysql-smoke-jd",
                "a".repeat(64),
                "job-parser-v1",
                "job-prompt-v1",
                requirements(),
                CREATED_AT
        );
    }

    private SkillGapSnapshot gapSnapshot(
            UUID snapshotId,
            UUID targetRoleId,
            UUID gapItemId
    ) {
        SkillGapSnapshot.GapItem gapItem =
                new SkillGapSnapshot.GapItem(
                        gapItemId,
                        "agentRequirements[0]",
                        "掌握Agent状态管理与上下文管理",
                        SkillGapSnapshot.GapStatus.MISSING,
                        List.of(),
                        "当前确认画像中没有相关技能证据"
                );

        return SkillGapSnapshot.create(
                snapshotId,
                SMOKE_ACTOR,
                targetRoleId,
                1,
                0,
                "deterministic-skill-gap-v1",
                List.of(gapItem),
                CREATED_AT.plusSeconds(1)
        );
    }

    private TrainingPlan pendingPlan(
            UUID planId,
            long planVersion,
            UUID snapshotId,
            UUID gapItemId,
            UUID itemId
    ) {
        TrainingPlanItem item =
                TrainingPlanItem.createDraft(
                        itemId,
                        1,
                        "实现Memory确认流",
                        "实现候选确认、拒绝和撤销状态转换",
                        180,
                        "状态机和owner隔离测试全部通过",
                        "提交测试报告引用",
                        List.of(gapItemId),
                        null,
                        List.of(),
                        CREATED_AT.plusSeconds(2)
                );

        return TrainingPlan.createDraft(
                planId,
                SMOKE_ACTOR,
                planVersion,
                snapshotId,
                "AI Agent开发能力训练计划",
                List.of(item),
                CREATED_AT.plusSeconds(2)
        ).submitForConfirmation(
                CREATED_AT.plusSeconds(3)
        );
    }

    private JobRequirements requirements() {
        return new JobRequirements(
                "AI Agent开发工程师",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Agent状态管理"),
                List.of("RAG"),
                List.of("自动化测试"),
                List.of(),
                List.of("开发AI Agent应用"),
                List.of("Memory状态机")
        );
    }

    private ConfigurableApplicationContext startFreshApplicationContext() {
        return new SpringApplicationBuilder(
                CareerForgeAiApplication.class
        ).web(
                WebApplicationType.NONE
        ).run(
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
                "DELETE FROM training_plan_item WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );

        jdbcTemplate.update(
                "DELETE FROM training_plan WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );

        jdbcTemplate.update(
                "DELETE FROM skill_gap_snapshot WHERE owner_id = ?",
                SMOKE_ACTOR.value()
        );

        jdbcTemplate.update(
                "DELETE FROM target_role WHERE owner_id = ?",
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
    }

    private MemoryItem insertConfirmedSkill(
            UUID memoryId,
            String skill,
            MemorySourceType sourceType,
            Instant createdAt
    ) {
        String sourceId = "mysql-gap-smoke-" + memoryId;
        MemoryItem pending = MemoryItem.createPending(
                memoryId,
                SMOKE_ACTOR,
                MemoryType.SKILL_EVIDENCE,
                MemoryNormalizedKey.skillEvidence(skill),
                "已使用" + skill + "完成受控项目开发",
                new MemorySource(
                        sourceType,
                        sourceId,
                        "b".repeat(64)
                ),
                List.of(sourceId),
                createdAt
        );
        memoryRepository.insert(pending);

        MemoryDecision decision = MemoryDecision.create(
                UUID.randomUUID(),
                pending,
                SMOKE_ACTOR,
                MemoryDecisionType.CONFIRM,
                null,
                "MySQL Gap Smoke受控确认",
                createdAt.plusSeconds(1)
        );
        MemoryItem confirmed = pending.applyDecision(decision);

        assertThat(memoryRepository.updateIfVersionMatches(
                SMOKE_ACTOR,
                confirmed,
                pending.version()
        )).isTrue();

        memoryDecisionRepository.insert(decision);
        return confirmed;
    }

    /**
     * @program: CareerForge-AI
     * @description: 仅在当前MySQL Smoke中提供固定Actor
     * @author: Miao Zheng
     * @date: 2026-08-12
     **/
    @TestConfiguration(proxyBeanMethods = false)
    static class SmokeActorConfiguration {

        @Bean
        @Primary
        CurrentActorProvider smokeCurrentActorProvider() {
            return () -> SMOKE_ACTOR;
        }
    }
}