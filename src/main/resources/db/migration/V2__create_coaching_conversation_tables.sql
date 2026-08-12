CREATE TABLE coaching_session
(
    session_id         CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id           VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    title              VARCHAR(120)                                  NOT NULL,
    session_status     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    next_turn_sequence BIGINT UNSIGNED                                NOT NULL DEFAULT 1,
    version            BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at         DATETIME(6)                                    NOT NULL,
    updated_at         DATETIME(6)                                    NOT NULL,
    closed_at          DATETIME(6)                                    NULL,

    PRIMARY KEY (session_id),

    CONSTRAINT uk_coaching_session_id_owner
        UNIQUE (session_id, owner_id),

    CONSTRAINT chk_coaching_session_status
        CHECK (session_status IN ('ACTIVE', 'CLOSED')),

    CONSTRAINT chk_coaching_session_next_sequence
        CHECK (next_turn_sequence >= 1),

    CONSTRAINT chk_coaching_session_closed_at
        CHECK (
            (session_status = 'ACTIVE' AND closed_at IS NULL)
                OR
            (session_status = 'CLOSED' AND closed_at IS NOT NULL)
            ),

    INDEX idx_coaching_session_owner_status_updated (
        owner_id,
        session_status,
        updated_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE coaching_turn
(
    turn_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exchange_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id       VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    turn_sequence  BIGINT UNSIGNED                                NOT NULL,
    turn_role      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    turn_status    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content        VARCHAR(8000)                                  NULL,
    content_hash   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    agent_run_id   VARCHAR(128) COLLATE utf8mb4_bin               NULL,
    failure_code   VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at     DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (turn_id),

    CONSTRAINT uk_coaching_turn_session_sequence
        UNIQUE (session_id, turn_sequence),

    CONSTRAINT uk_coaching_turn_exchange_role
        UNIQUE (session_id, exchange_id, turn_role),

    CONSTRAINT chk_coaching_turn_sequence
        CHECK (turn_sequence >= 1),

    CONSTRAINT chk_coaching_turn_role
        CHECK (turn_role IN ('USER', 'ASSISTANT')),

    CONSTRAINT chk_coaching_turn_status
        CHECK (turn_status IN ('COMPLETED', 'FAILED')),

    CONSTRAINT chk_coaching_turn_content_boundary
        CHECK (
            (
                turn_role = 'USER'
                    AND turn_status = 'COMPLETED'
                    AND content IS NOT NULL
                    AND content_hash IS NOT NULL
                    AND agent_run_id IS NULL
                    AND failure_code IS NULL
                )
                OR
            (
                turn_role = 'ASSISTANT'
                    AND turn_status = 'COMPLETED'
                    AND content IS NOT NULL
                    AND content_hash IS NOT NULL
                    AND agent_run_id IS NOT NULL
                    AND failure_code IS NULL
                )
                OR
            (
                turn_role = 'ASSISTANT'
                    AND turn_status = 'FAILED'
                    AND content IS NULL
                    AND content_hash IS NULL
                    AND agent_run_id IS NOT NULL
                    AND failure_code IS NOT NULL
                )
            ),

    CONSTRAINT fk_coaching_turn_session_owner
        FOREIGN KEY (session_id, owner_id)
            REFERENCES coaching_session (session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_coaching_turn_owner_session_sequence (
        owner_id,
        session_id,
        turn_sequence
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;