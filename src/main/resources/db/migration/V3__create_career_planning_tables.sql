CREATE TABLE target_role
(
    target_role_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    target_role_version BIGINT UNSIGNED                                NOT NULL,
    source_ref          VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    source_hash         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parser_version      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_version      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requirements_json   JSON                                           NOT NULL,
    confirmed_at        DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (target_role_id),

    CONSTRAINT uk_target_role_id_owner_version
        UNIQUE (
                target_role_id,
                owner_id,
                target_role_version
            ),

    CONSTRAINT uk_target_role_owner_version
        UNIQUE (
                owner_id,
                target_role_version
            ),

    CONSTRAINT chk_target_role_version
        CHECK (target_role_version >= 1),

    CONSTRAINT chk_target_role_requirements_json
        CHECK (JSON_TYPE(requirements_json) = 'OBJECT'),

    INDEX idx_target_role_owner_confirmed (
        owner_id,
        confirmed_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE skill_gap_snapshot
(
    snapshot_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    target_role_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_role_version BIGINT UNSIGNED                                NOT NULL,
    profile_version     BIGINT UNSIGNED                                NOT NULL,
    items_json          JSON                                           NOT NULL,
    created_at          DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (snapshot_id),

    CONSTRAINT uk_skill_gap_snapshot_id_owner
        UNIQUE (
                snapshot_id,
                owner_id
            ),

    CONSTRAINT uk_skill_gap_snapshot_input_versions
        UNIQUE (
                owner_id,
                target_role_id,
                target_role_version,
                profile_version
            ),

    CONSTRAINT chk_skill_gap_target_version
        CHECK (target_role_version >= 1),

    CONSTRAINT chk_skill_gap_items_json
        CHECK (
            JSON_TYPE(items_json) = 'ARRAY'
                AND JSON_LENGTH(items_json) >= 1
                AND JSON_LENGTH(items_json) <= 100
            ),

    CONSTRAINT fk_skill_gap_snapshot_target_role
        FOREIGN KEY (
                     target_role_id,
                     owner_id,
                     target_role_version
            )
            REFERENCES target_role (
                                    target_role_id,
                                    owner_id,
                                    target_role_version
                )
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_skill_gap_snapshot_owner_created (
        owner_id,
        created_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE training_plan
(
    plan_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id         VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    plan_version     BIGINT UNSIGNED                                NOT NULL,
    gap_snapshot_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title            VARCHAR(120)                                  NOT NULL,
    plan_status      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version          BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at       DATETIME(6)                                    NOT NULL,
    updated_at       DATETIME(6)                                    NOT NULL,
    activated_at     DATETIME(6)                                    NULL,
    completed_at     DATETIME(6)                                    NULL,
    cancelled_at     DATETIME(6)                                    NULL,

    PRIMARY KEY (plan_id),

    CONSTRAINT uk_training_plan_id_owner
        UNIQUE (
                plan_id,
                owner_id
            ),

    CONSTRAINT uk_training_plan_owner_version
        UNIQUE (
                owner_id,
                plan_version
            ),

    CONSTRAINT chk_training_plan_business_version
        CHECK (plan_version >= 1),

    CONSTRAINT chk_training_plan_status
        CHECK (
            plan_status IN (
                            'DRAFT',
                            'PENDING_CONFIRMATION',
                            'ACTIVE',
                            'COMPLETED',
                            'CANCELLED'
                )
            ),

    CONSTRAINT chk_training_plan_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_training_plan_status_times
        CHECK (
            (
                plan_status IN ('DRAFT', 'PENDING_CONFIRMATION')
                    AND activated_at IS NULL
                    AND completed_at IS NULL
                    AND cancelled_at IS NULL
                )
                OR
            (
                plan_status = 'ACTIVE'
                    AND activated_at IS NOT NULL
                    AND completed_at IS NULL
                    AND cancelled_at IS NULL
                )
                OR
            (
                plan_status = 'COMPLETED'
                    AND activated_at IS NOT NULL
                    AND completed_at IS NOT NULL
                    AND completed_at >= activated_at
                    AND cancelled_at IS NULL
                )
                OR
            (
                plan_status = 'CANCELLED'
                    AND completed_at IS NULL
                    AND cancelled_at IS NOT NULL
                )
            ),

    CONSTRAINT fk_training_plan_gap_snapshot
        FOREIGN KEY (
                     gap_snapshot_id,
                     owner_id
            )
            REFERENCES skill_gap_snapshot (
                                           snapshot_id,
                                           owner_id
                )
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_training_plan_owner_status_updated (
        owner_id,
        plan_status,
        updated_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE training_plan_item
(
    item_id                     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_id                     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                    VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    week_number                 SMALLINT UNSIGNED                              NOT NULL,
    title                       VARCHAR(120)                                   NOT NULL,
    task_description            VARCHAR(2000)                                  NOT NULL,
    estimated_minutes           INT UNSIGNED                                   NOT NULL,
    completion_criteria         VARCHAR(1000)                                  NOT NULL,
    evidence_requirement        VARCHAR(1000)                                  NOT NULL,
    gap_item_ids_json            JSON                                           NOT NULL,
    foundation_goal             VARCHAR(500)                                   NULL,
    resource_refs_json          JSON                                           NOT NULL,
    item_status                 VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    completion_evidence_refs_json JSON                                         NOT NULL,
    version                     BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)                                    NOT NULL,
    updated_at                  DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (item_id),

    CONSTRAINT uk_training_plan_item_id_owner
        UNIQUE (
                item_id,
                owner_id
            ),

    CONSTRAINT uk_training_plan_item_title
        UNIQUE (
                plan_id,
                title
            ),

    CONSTRAINT chk_training_plan_item_week
        CHECK (
            week_number >= 1
                AND week_number <= 52
            ),

    CONSTRAINT chk_training_plan_item_minutes
        CHECK (
            estimated_minutes >= 1
                AND estimated_minutes <= 10080
            ),

    CONSTRAINT chk_training_plan_item_status
        CHECK (
            item_status IN (
                            'NOT_STARTED',
                            'IN_PROGRESS',
                            'COMPLETED'
                )
            ),

    CONSTRAINT chk_training_plan_item_gap_refs
        CHECK (
            JSON_TYPE(gap_item_ids_json) = 'ARRAY'
                AND JSON_LENGTH(gap_item_ids_json) <= 20
                AND (
                JSON_LENGTH(gap_item_ids_json) >= 1
                    OR foundation_goal IS NOT NULL
                )
            ),

    CONSTRAINT chk_training_plan_item_resource_refs
        CHECK (
            JSON_TYPE(resource_refs_json) = 'ARRAY'
                AND JSON_LENGTH(resource_refs_json) <= 20
            ),

    CONSTRAINT chk_training_plan_item_evidence_refs
        CHECK (
            JSON_TYPE(completion_evidence_refs_json) = 'ARRAY'
                AND JSON_LENGTH(completion_evidence_refs_json) <= 20
                AND (
                (
                    item_status = 'COMPLETED'
                        AND JSON_LENGTH(completion_evidence_refs_json) >= 1
                    )
                    OR
                (
                    item_status IN ('NOT_STARTED', 'IN_PROGRESS')
                        AND JSON_LENGTH(completion_evidence_refs_json) = 0
                    )
                )
            ),

    CONSTRAINT chk_training_plan_item_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT fk_training_plan_item_plan
        FOREIGN KEY (
                     plan_id,
                     owner_id
            )
            REFERENCES training_plan (
                                      plan_id,
                                      owner_id
                )
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_training_plan_item_owner_plan_week (
        owner_id,
        plan_id,
        week_number,
        item_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;