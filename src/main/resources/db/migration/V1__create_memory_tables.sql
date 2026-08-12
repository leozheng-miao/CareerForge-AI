CREATE TABLE memory_item
(
    memory_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id             VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    memory_type          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    normalized_key       VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    normalization_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content              VARCHAR(2000)                                  NOT NULL,
    content_hash         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    memory_status        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id            VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    source_hash          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_refs_json   JSON                                           NOT NULL,
    supersedes_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version              BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at           DATETIME(6)                                    NOT NULL,
    updated_at           DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (memory_id),

    CONSTRAINT uk_memory_item_id_owner
        UNIQUE (memory_id, owner_id),

    CONSTRAINT uk_memory_item_exact_duplicate
        UNIQUE (
                owner_id,
                memory_type,
                normalized_key,
                source_id,
                content_hash
            ),

    CONSTRAINT chk_memory_item_type
        CHECK (
            memory_type IN (
                            'CAREER_GOAL',
                            'SKILL_EVIDENCE',
                            'LEARNING_PREFERENCE',
                            'TIME_CONSTRAINT'
                )
            ),

    CONSTRAINT chk_memory_item_status
        CHECK (
            memory_status IN (
                              'PENDING',
                              'CONFIRMED',
                              'REJECTED',
                              'SUPERSEDED',
                              'REVOKED'
                )
            ),

    CONSTRAINT chk_memory_item_source_type
        CHECK (
            source_type IN (
                            'CONVERSATION_TURN',
                            'AGENT_RUN',
                            'JOB_DOCUMENT',
                            'PROJECT_EVIDENCE'
                )
            ),

    CONSTRAINT chk_memory_item_not_self_superseding
        CHECK (
            supersedes_id IS NULL
                OR supersedes_id <> memory_id
            ),

    CONSTRAINT fk_memory_item_supersedes
        FOREIGN KEY (supersedes_id, owner_id)
            REFERENCES memory_item (memory_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_memory_item_owner_status_updated (
        owner_id,
        memory_status,
        updated_at
    ),

    INDEX idx_memory_item_owner_type_key (
        owner_id,
        memory_type,
        normalized_key,
        normalization_version,
        updated_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE memory_decision
(
    decision_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    memory_id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id               VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    decision_type          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_status            VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    to_status              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_memory_version BIGINT UNSIGNED                               NOT NULL,
    replacement_memory_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    note                   VARCHAR(500)                                   NOT NULL DEFAULT '',
    decided_at             DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (decision_id),

    CONSTRAINT uk_memory_decision_version
        UNIQUE (memory_id, expected_memory_version),

    CONSTRAINT chk_memory_decision_transition
        CHECK (
            (
                decision_type = 'CONFIRM'
                    AND from_status = 'PENDING'
                    AND to_status = 'CONFIRMED'
                    AND replacement_memory_id IS NULL
                )
                OR
            (
                decision_type = 'REJECT'
                    AND from_status = 'PENDING'
                    AND to_status = 'REJECTED'
                    AND replacement_memory_id IS NULL
                )
                OR
            (
                decision_type = 'SUPERSEDE'
                    AND from_status = 'CONFIRMED'
                    AND to_status = 'SUPERSEDED'
                    AND replacement_memory_id IS NOT NULL
                )
                OR
            (
                decision_type = 'REVOKE'
                    AND from_status = 'CONFIRMED'
                    AND to_status = 'REVOKED'
                    AND replacement_memory_id IS NULL
                )
            ),

    CONSTRAINT chk_memory_decision_not_self_replacement
        CHECK (
            replacement_memory_id IS NULL
                OR replacement_memory_id <> memory_id
            ),

    CONSTRAINT fk_memory_decision_memory_owner
        FOREIGN KEY (memory_id, owner_id)
            REFERENCES memory_item (memory_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_memory_decision_replacement_owner
        FOREIGN KEY (replacement_memory_id, owner_id)
            REFERENCES memory_item (memory_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_memory_decision_owner_memory_time (
        owner_id,
        memory_id,
        decided_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;