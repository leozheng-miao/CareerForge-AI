package com.leo.careerforgeai.career.application;

import com.leo.careerforgeai.career.application.port.CareerPlanningRepository;
import com.leo.careerforgeai.career.domain.TargetRoleDraft;
import com.leo.careerforgeai.shared.actor.ActorId;
import com.leo.careerforgeai.shared.actor.CurrentActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * @program: CareerForge-AI
 * @description: 使用当前Actor解析JD、保存PENDING目标岗位草案并执行受控查询
 * @author: Miao Zheng
 * @date: 2026-08-16
 */
@Service
@ConditionalOnBean(CareerPlanningRepository.class)
public class TargetRoleDraftApplicationService {

    public static final int MAX_JD_LENGTH = 12_000;

    private final CurrentActorProvider currentActorProvider;
    private final JobRequirementsParser parser;
    private final CareerPlanningRepository repository;
    private final Clock clock;

    public TargetRoleDraftApplicationService(
            CurrentActorProvider currentActorProvider,
            JobRequirementsParser parser,
            CareerPlanningRepository repository,
            Clock clock
    ) {
        this.currentActorProvider = Objects.requireNonNull(currentActorProvider, "currentActorProvider不能为空");
        this.parser = Objects.requireNonNull(parser, "parser不能为空");
        this.repository = Objects.requireNonNull(repository, "repository不能为空");
        this.clock = Objects.requireNonNull(clock, "clock不能为空");
    }

    /**
     * 模型调用必须发生在数据库写入前且不包裹长事务。
     * 只有完整解析成功后才写入一条PENDING草案。
     */
    public TargetRoleDraft createDraft(
            String sourceRef,
            String jdText
    ) {
        requireJdText(jdText);

        ActorId actorId = currentActor();
        JobRequirementsParseResult parseResult =
                parser.parseDetailed(jdText);

        TargetRoleDraft draft = TargetRoleDraft.createPending(
                UUID.randomUUID(),
                actorId,
                sourceRef,
                calculateSourceHash(jdText),
                parser.parserVersion(),
                parser.promptVersion(),
                parseResult.requirements(),
                clock.instant()
        );

        repository.insertTargetRoleDraft(draft);
        return draft;
    }

    /** 查询当前用户拥有的PENDING岗位草案。 */
    @Transactional(readOnly = true)
    public TargetRoleDraft getDraft(UUID draftId) {
        Objects.requireNonNull(draftId, "draftId不能为空");
        ActorId actorId = currentActor();

        TargetRoleDraft draft = repository.findTargetRoleDraft(actorId, draftId)
                .orElseThrow(() -> new IllegalArgumentException("目标岗位草案不存在或不属于当前用户"));

        if (!actorId.equals(draft.ownerId())) {
            throw new IllegalStateException("目标岗位草案查询结果违反owner边界");
        }
        return draft;
    }

    private ActorId currentActor() {
        return Objects.requireNonNull(currentActorProvider.currentActor(), "currentActor不能为空");
    }

    private static void requireJdText(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("jdText不能为空");
        }
        if (jdText.length() > MAX_JD_LENGTH) {
            throw new IllegalArgumentException("jdText超过长度限制");
        }
    }

    private static String calculateSourceHash(String jdText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(jdText.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256", exception);
        }
    }
}