
package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseException;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParseResult;
import com.leo.careerforgeai.career.application.requirement.JobRequirementsParser;
import com.leo.careerforgeai.career.application.targetrole.TargetRoleDraftApplicationService;
import com.leo.careerforgeai.career.domain.JobRequirements;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
import com.leo.careerforgeai.model.domain.ModelUsage;
import com.leo.careerforgeai.model.exception.ModelErrorType;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.testsupport.MutableCurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @program: CareerForge-AI
 * @description: 验证目标岗位草案创建、来源Hash、owner隔离和模型失败不落库
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@ExtendWith(MockitoExtension.class)
class TargetRoleDraftApplicationServiceTest {

    private static final ActorId ACTOR_A =
            new ActorId("actor-a");
    private static final ActorId ACTOR_B =
            new ActorId("actor-b");
    private static final Instant NOW =
            Instant.parse("2026-08-16T02:00:00Z");
    private static final UUID DRAFT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001"
            );
    private static final String JD_TEXT =
            "AI Agent开发工程师，要求掌握Java和Spring Boot";

    @Mock
    private JobRequirementsParser parser;

    @Mock
    private CareerPlanningRepository repository;

    private MutableCurrentActorProvider actorProvider;
    private TargetRoleDraftApplicationService service;

    @BeforeEach
    void setUp() {
        actorProvider = new MutableCurrentActorProvider(ACTOR_A);
        service = new TargetRoleDraftApplicationService(
                actorProvider,
                parser,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldParseAndSavePendingDraftWithServerFields() {
        when(parser.parseDetailed(JD_TEXT)).thenReturn(
                new JobRequirementsParseResult(
                        requirements(),
                        new ModelUsage(100, 30, 130),
                        25
                )
        );
        when(parser.parserVersion()).thenReturn(
                "job-requirements-parser-v1"
        );
        when(parser.promptVersion()).thenReturn(
                "job-requirements-prompt-v1"
        );

        TargetRoleDraft result = service.createDraft(
                "jd-upload-1",
                JD_TEXT
        );

        ArgumentCaptor<TargetRoleDraft> captor =
                ArgumentCaptor.forClass(TargetRoleDraft.class);

        verify(repository).insertTargetRoleDraft(
                captor.capture()
        );

        TargetRoleDraft saved = captor.getValue();

        assertThat(result).isEqualTo(saved);
        assertThat(saved.ownerId()).isEqualTo(ACTOR_A);
        assertThat(saved.status())
                .isEqualTo(TargetRoleDraft.Status.PENDING);
        assertThat(saved.version()).isZero();
        assertThat(saved.sourceHash())
                .matches("[0-9a-f]{64}");
        assertThat(saved.parserVersion())
                .isEqualTo("job-requirements-parser-v1");
        assertThat(saved.promptVersion())
                .isEqualTo("job-requirements-prompt-v1");
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void shouldNotWriteDraftWhenModelParsingFails() {
        when(parser.parseDetailed(JD_TEXT)).thenThrow(
                new JobRequirementsParseException(
                        ModelErrorType.TIMEOUT,
                        "岗位要求模型调用失败",
                        null,
                        null,
                        50
                )
        );

        assertThatThrownBy(() -> service.createDraft(
                "jd-upload-1",
                JD_TEXT
        )).isInstanceOf(JobRequirementsParseException.class);

        verify(repository, never())
                .insertTargetRoleDraft(any());
    }

    @Test
    void shouldHideDraftFromAnotherActor() {
        TargetRoleDraft draft = draft(ACTOR_A);
        actorProvider.switchTo(ACTOR_B);

        when(repository.findTargetRoleDraft(
                ACTOR_B,
                DRAFT_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDraft(DRAFT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "目标岗位草案不存在或不属于当前用户"
                );
    }

    @Test
    void shouldFailClosedWhenRepositoryReturnsAnotherOwnersDraft() {
        TargetRoleDraft leaked = draft(ACTOR_B);

        when(repository.findTargetRoleDraft(
                ACTOR_A,
                DRAFT_ID
        )).thenReturn(Optional.of(leaked));

        assertThatThrownBy(() -> service.getDraft(DRAFT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "目标岗位草案查询结果违反owner或状态边界"
                );
    }

    private TargetRoleDraft draft(ActorId ownerId) {
        return TargetRoleDraft.createPending(
                DRAFT_ID,
                ownerId,
                "jd-upload-1",
                "a".repeat(64),
                "job-requirements-parser-v1",
                "job-requirements-prompt-v1",
                requirements(),
                NOW
        );
    }

    private JobRequirements requirements() {
        return new JobRequirements(
                "AI Agent开发工程师",
                List.of("Java"),
                List.of("Spring Boot"),
                List.of("Agent工作流"),
                List.of(),
                List.of("自动化测试"),
                List.of(),
                List.of("开发Agent应用"),
                List.of("Java基础")
        );
    }
}