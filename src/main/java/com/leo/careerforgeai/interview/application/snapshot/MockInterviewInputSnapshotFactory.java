package com.leo.careerforgeai.interview.application.snapshot;

import com.leo.careerforgeai.career.domain.SkillGapSnapshot;
import com.leo.careerforgeai.career.domain.TargetRole;
import com.leo.careerforgeai.career.domain.TrainingPlan;
import com.leo.careerforgeai.interview.domain.session.MockInterviewInputSnapshot;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceArtifact;
import com.leo.careerforgeai.interview.domain.evidence.PersonalEvidenceStatus;
import com.leo.careerforgeai.shared.actor.ActorId;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 将岗位、可选Gap、可选训练计划和个人证据版本冻结为确定性面试输入快照
 * @author: Miao Zheng
 * @date: 2026-08-27
 **/
@Component
public class MockInterviewInputSnapshotFactory {

    private static final int MAX_ARTIFACTS = 20;

    private final JsonMapper jsonMapper;

    public MockInterviewInputSnapshotFactory(JsonMapper jsonMapper) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper不能为空");
    }

    public MockInterviewInputSnapshot create(
            UUID inputSnapshotId,
            ActorId ownerId,
            TargetRole targetRole,
            SkillGapSnapshot skillGapSnapshot,
            TrainingPlan trainingPlan,
            List<PersonalEvidenceArtifact> artifacts,
            Instant createdAt
    ) {
        Objects.requireNonNull(inputSnapshotId, "inputSnapshotId不能为空");
        Objects.requireNonNull(ownerId, "ownerId不能为空");
        Objects.requireNonNull(targetRole, "targetRole不能为空");
        Objects.requireNonNull(createdAt, "createdAt不能为空");

        requireOwner(ownerId, targetRole.ownerId(), "目标岗位不属于当前用户");
        validateGap(ownerId, targetRole, skillGapSnapshot);
        validateTrainingPlan(ownerId, skillGapSnapshot, trainingPlan);

        List<PersonalEvidenceArtifact> orderedArtifacts = validateAndOrderArtifacts(ownerId, artifacts);
        List<MockInterviewInputSnapshot.ArtifactReference> references = createReferences(orderedArtifacts);
        String contextJson = canonicalJson(createContext(
                targetRole,
                skillGapSnapshot,
                trainingPlan,
                orderedArtifacts
        ));

        return new MockInterviewInputSnapshot(
                inputSnapshotId,
                ownerId,
                MockInterviewInputSnapshot.CURRENT_SCHEMA_VERSION,
                targetRole.targetRoleId(),
                targetRole.targetRoleVersion(),
                skillGapSnapshot == null ? null : skillGapSnapshot.snapshotId(),
                trainingPlan == null ? null : trainingPlan.planId(),
                trainingPlan == null ? null : trainingPlan.planVersion(),
                contextJson,
                references,
                sha256(contextJson),
                createdAt
        );
    }

    private void validateGap(
            ActorId ownerId,
            TargetRole targetRole,
            SkillGapSnapshot skillGapSnapshot
    ) {
        if (skillGapSnapshot == null) return;

        requireOwner(ownerId, skillGapSnapshot.ownerId(), "能力差距快照不属于当前用户");
        if (!skillGapSnapshot.targetRoleId().equals(targetRole.targetRoleId())
                || skillGapSnapshot.targetRoleVersion() != targetRole.targetRoleVersion()) {
            throw new IllegalArgumentException("能力差距快照与目标岗位版本不匹配");
        }
    }

    private void validateTrainingPlan(
            ActorId ownerId,
            SkillGapSnapshot skillGapSnapshot,
            TrainingPlan trainingPlan
    ) {
        if (trainingPlan == null) return;
        if (skillGapSnapshot == null) {
            throw new IllegalArgumentException("选择训练计划时必须同时选择其能力差距快照");
        }

        requireOwner(ownerId, trainingPlan.ownerId(), "训练计划不属于当前用户");
        if (!trainingPlan.gapSnapshotId().equals(skillGapSnapshot.snapshotId())) {
            throw new IllegalArgumentException("训练计划与能力差距快照不匹配");
        }
        if (trainingPlan.status() != TrainingPlan.PlanStatus.ACTIVE
                && trainingPlan.status() != TrainingPlan.PlanStatus.COMPLETED) {
            throw new IllegalArgumentException("只能使用已确认的ACTIVE或COMPLETED训练计划");
        }
    }

    private List<PersonalEvidenceArtifact> validateAndOrderArtifacts(
            ActorId ownerId,
            List<PersonalEvidenceArtifact> artifacts
    ) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一份个人证据，简历即可");
        }
        if (artifacts.size() > MAX_ARTIFACTS) {
            throw new IllegalArgumentException("个人证据数量不能超过" + MAX_ARTIFACTS);
        }

        List<PersonalEvidenceArtifact> ordered = artifacts.stream()
                .map(artifact -> Objects.requireNonNull(artifact, "个人证据不能为空"))
                .sorted(Comparator.comparing(PersonalEvidenceArtifact::type)
                        .thenComparing(PersonalEvidenceArtifact::artifactId)
                        .thenComparingLong(PersonalEvidenceArtifact::artifactVersion))
                .toList();

        String previousIdentity = null;
        for (PersonalEvidenceArtifact artifact : ordered) {
            requireOwner(ownerId, artifact.ownerId(), "个人证据不属于当前用户");
            if (artifact.status() == PersonalEvidenceStatus.REVOKED) {
                throw new IllegalArgumentException("已撤销的个人证据不能创建新面试");
            }

            String identity = artifact.artifactId() + ":" + artifact.artifactVersion();
            if (identity.equals(previousIdentity)) {
                throw new IllegalArgumentException("同一个人证据版本不能重复选择");
            }
            previousIdentity = identity;
        }
        return ordered;
    }

    private List<MockInterviewInputSnapshot.ArtifactReference> createReferences(
            List<PersonalEvidenceArtifact> artifacts
    ) {
        List<MockInterviewInputSnapshot.ArtifactReference> references = new ArrayList<>();
        for (int index = 0; index < artifacts.size(); index++) {
            PersonalEvidenceArtifact artifact = artifacts.get(index);
            references.add(new MockInterviewInputSnapshot.ArtifactReference(
                    artifact.artifactId(),
                    artifact.artifactVersion(),
                    artifact.sourceHash(),
                    index + 1
            ));
        }
        return List.copyOf(references);
    }

    private Map<String, Object> createContext(
            TargetRole targetRole,
            SkillGapSnapshot skillGapSnapshot,
            TrainingPlan trainingPlan,
            List<PersonalEvidenceArtifact> artifacts
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schemaVersion", MockInterviewInputSnapshot.CURRENT_SCHEMA_VERSION);
        context.put("targetRole", targetRoleContext(targetRole));
        context.put("skillGap", skillGapSnapshot == null ? null : skillGapContext(skillGapSnapshot));
        context.put("trainingPlan", trainingPlan == null ? null : trainingPlanContext(trainingPlan));
        context.put("artifacts", artifacts.stream().map(this::artifactContext).toList());
        return context;
    }

    private Map<String, Object> targetRoleContext(TargetRole targetRole) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("targetRoleId", targetRole.targetRoleId());
        context.put("targetRoleVersion", targetRole.targetRoleVersion());
        context.put("sourceHash", targetRole.sourceHash());
        context.put("requirements", targetRole.requirementsSnapshot());
        return context;
    }

    private Map<String, Object> skillGapContext(SkillGapSnapshot snapshot) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("snapshotId", snapshot.snapshotId());
        context.put("targetRoleId", snapshot.targetRoleId());
        context.put("targetRoleVersion", snapshot.targetRoleVersion());
        context.put("profileVersion", snapshot.profileVersion());
        context.put("algorithmVersion", snapshot.algorithmVersion());
        context.put("items", snapshot.items());
        return context;
    }

    private Map<String, Object> trainingPlanContext(TrainingPlan trainingPlan) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("planId", trainingPlan.planId());
        context.put("planVersion", trainingPlan.planVersion());
        context.put("gapSnapshotId", trainingPlan.gapSnapshotId());
        context.put("title", trainingPlan.title());
        context.put("status", trainingPlan.status());
        context.put("version", trainingPlan.version());
        context.put("items", trainingPlan.items());
        return context;
    }

    private Map<String, Object> artifactContext(PersonalEvidenceArtifact artifact) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("artifactId", artifact.artifactId());
        context.put("artifactVersion", artifact.artifactVersion());
        context.put("type", artifact.type());
        context.put("sourceName", artifact.sourceName());
        context.put("sourceHash", artifact.sourceHash());
        context.put("chunkIds", artifact.chunks().stream()
                .map(PersonalEvidenceArtifact.Chunk::evidenceChunkId)
                .toList());
        return context;
    }

    private String canonicalJson(Object value) {
        try {
            String json = jsonMapper.writeValueAsString(value);
            Object parsed = jsonMapper.readValue(json, Object.class);
            return jsonMapper.writeValueAsString(normalize(parsed));
        } catch (JacksonException exception) {
            throw new IllegalStateException("序列化面试输入快照失败", exception);
        }
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private static void requireOwner(ActorId expected, ActorId actual, String message) {
        if (!expected.equals(actual)) throw new IllegalArgumentException(message);
    }
}