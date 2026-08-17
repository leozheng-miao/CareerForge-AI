CREATE TABLE target_role_draft
(
    draft_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id          VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    source_ref        VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    source_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parser_version    VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_version    VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requirements_json JSON                                           NOT NULL,
    draft_status      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version           BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at        DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (draft_id),

    CONSTRAINT uk_target_role_draft_id_owner
        UNIQUE (draft_id, owner_id),

    CONSTRAINT chk_target_role_draft_status
        CHECK (draft_status = 'PENDING'),

    CONSTRAINT chk_target_role_draft_version
        CHECK (version = 0),

    CONSTRAINT chk_target_role_draft_requirements
        CHECK (JSON_TYPE(requirements_json) = 'OBJECT'),

    INDEX idx_target_role_draft_owner_created (
        owner_id,
        created_at,
        draft_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;