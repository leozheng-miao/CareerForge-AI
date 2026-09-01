package com.leo.careerforgeai.interview.application.session;

import com.leo.careerforgeai.interview.application.port.MockInterviewSessionRepository;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputSelection;
import com.leo.careerforgeai.interview.application.snapshot.MockInterviewInputSnapshotApplicationService;
import com.leo.careerforgeai.interview.domain.session.InterviewBudgetPolicy;
import com.leo.careerforgeai.interview.domain.session.InterviewMode;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.session.MockInterviewSession;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 冻结可信输入并以owner和requestId幂等创建模拟面试
 * @author: Miao Zheng
 * @date: 2026-08-30
 **/
@Service
@ConditionalOnProperty(prefix = "careerforge.persistence", name = "enabled", havingValue = "true")
public class MockInterviewCreationApplicationService {

    private static final InterviewBudgetPolicy TARGETED_MOCK_POLICY = new InterviewBudgetPolicy(5, 2, 20, 20_000);
    private static final InterviewBudgetPolicy GAP_DRILL_POLICY = new InterviewBudgetPolicy(4, 1, 16, 16_000);

    private final CurrentActorProvider currentActorProvider;
    private final MockInterviewInputSnapshotApplicationService snapshotService;
    private final MockInterviewSessionRepository sessionRepository;
    private final Clock clock;

    public MockInterviewCreationApplicationService(CurrentActorProvider currentActorProvider,
                                                   MockInterviewInputSnapshotApplicationService snapshotService,
                                                   MockInterviewSessionRepository sessionRepository,
                                                   Clock clock) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService不能为空");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    @Transactional
    public MockInterviewSession create(UUID requestId, InterviewMode mode, MockInterviewInputSelection selection) {
        Objects.requireNonNull(requestId, "requestId不能为空");
        Objects.requireNonNull(mode, "mode不能为空");
        Objects.requireNonNull(selection, "selection不能为空");
        requireModeInput(mode, selection);

        ActorId ownerId = Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
        String fingerprint = requestFingerprint(mode, selection);

        MockInterviewSession existing = sessionRepository.findByRequestId(ownerId, requestId).orElse(null);
        if (existing != null) return requireReplay(existing, fingerprint);

        MockInterviewInputSnapshot snapshot = snapshotService.create(selection);
        if (!ownerId.equals(snapshot.ownerId())) throw new IllegalStateException("输入快照不属于当前用户");

        MockInterviewSession candidate = MockInterviewSession.create(
                UUID.randomUUID(),
                ownerId,
                requestId,
                fingerprint,
                mode,
                snapshot.inputSnapshotId(),
                snapshot.snapshotHash(),
                budgetPolicy(mode),
                clock.instant()
        );
        MockInterviewSession claimed = sessionRepository.claim(candidate);
        requireClaimIdentity(ownerId, requestId, claimed);
        return requireReplay(claimed, fingerprint);
    }

    private MockInterviewSession requireReplay(MockInterviewSession session, String fingerprint) {
        if (!fingerprint.equals(session.requestFingerprint())) {
            throw new MockInterviewRequestConflictException(session.interviewId());
        }
        return session;
    }

    private void requireClaimIdentity(ActorId ownerId, UUID requestId, MockInterviewSession session) {
        if (!ownerId.equals(session.ownerId()) || !requestId.equals(session.requestId())) {
            throw new IllegalStateException("Repository返回了错误请求身份的模拟面试");
        }
    }

    private void requireModeInput(InterviewMode mode, MockInterviewInputSelection selection) {
        if (mode == InterviewMode.GAP_DRILL && selection.skillGapSnapshotId() == null) {
            throw new IllegalArgumentException("GAP_DRILL模式必须选择能力差距快照");
        }
    }

    private InterviewBudgetPolicy budgetPolicy(InterviewMode mode) {
        return switch (mode) {
            case TARGETED_MOCK -> TARGETED_MOCK_POLICY;
            case GAP_DRILL -> GAP_DRILL_POLICY;
        };
    }

    private String requestFingerprint(InterviewMode mode, MockInterviewInputSelection selection) {
        StringBuilder canonical = new StringBuilder()
                .append("mode=").append(mode)
                .append("\ntargetRoleId=").append(selection.targetRoleId())
                .append("\ntargetRoleVersion=").append(selection.targetRoleVersion())
                .append("\nskillGapSnapshotId=").append(selection.skillGapSnapshotId())
                .append("\ntrainingPlanId=").append(selection.trainingPlanId())
                .append("\ntrainingPlanVersion=").append(selection.trainingPlanVersion());

        selection.artifactVersions().stream()
                .sorted(Comparator.comparing((MockInterviewInputSelection.ArtifactVersion item) -> item.artifactId().toString())
                        .thenComparingLong(MockInterviewInputSelection.ArtifactVersion::artifactVersion))
                .forEach(item -> canonical.append("\nartifact=")
                        .append(item.artifactId()).append(':').append(item.artifactVersion()));

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}