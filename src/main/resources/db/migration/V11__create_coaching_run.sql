ALTER TABLE coaching_turn
    ADD CONSTRAINT uk_coaching_turn_id_owner
        UNIQUE (turn_id, owner_id);


CREATE TABLE coaching_run
(
    run_id                   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                 VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    session_id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id               CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint      CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_session_version BIGINT UNSIGNED                                NOT NULL,
    run_status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_turn_id             CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    assistant_turn_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_code             VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version                  BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    accepted_at              DATETIME(6)                                    NULL,
    started_at               DATETIME(6)                                    NULL,
    finished_at              DATETIME(6)                                    NULL,
    created_at               DATETIME(6)                                    NOT NULL,
    updated_at               DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (run_id),

    CONSTRAINT uk_coaching_run_id_owner
        UNIQUE (run_id, owner_id),

    CONSTRAINT uk_coaching_run_owner_request
        UNIQUE (owner_id, request_id),

    CONSTRAINT chk_coaching_run_status
        CHECK (
            run_status IN (
                           'RECEIVED',
                           'ACCEPTED',
                           'RUNNING',
                           'SUCCEEDED',
                           'FAILED',
                           'TIMED_OUT',
                           'REJECTED',
                           'INTERRUPTED'
                )
            ),

    CONSTRAINT chk_coaching_run_distinct_turns
        CHECK (
            assistant_turn_id IS NULL
                OR assistant_turn_id <> user_turn_id
            ),

    CONSTRAINT chk_coaching_run_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_coaching_run_time_order
        CHECK (
            (accepted_at IS NULL OR accepted_at >= created_at)
                AND (
                started_at IS NULL
                    OR (
                    accepted_at IS NOT NULL
                        AND started_at >= accepted_at
                    )
                )
                AND (
                finished_at IS NULL
                    OR finished_at >= COALESCE(
                        started_at,
                        accepted_at,
                        created_at
                                      )
                )
            ),

    CONSTRAINT chk_coaching_run_lifecycle
        CHECK (
            (
                run_status = 'RECEIVED'
                    AND user_turn_id IS NULL
                    AND assistant_turn_id IS NULL
                    AND failure_code IS NULL
                    AND accepted_at IS NULL
                    AND started_at IS NULL
                    AND finished_at IS NULL
                )
                OR
            (
                run_status = 'ACCEPTED'
                    AND user_turn_id IS NOT NULL
                    AND assistant_turn_id IS NULL
                    AND failure_code IS NULL
                    AND accepted_at IS NOT NULL
                    AND started_at IS NULL
                    AND finished_at IS NULL
                )
                OR
            (
                run_status = 'RUNNING'
                    AND user_turn_id IS NOT NULL
                    AND assistant_turn_id IS NULL
                    AND failure_code IS NULL
                    AND accepted_at IS NOT NULL
                    AND started_at IS NOT NULL
                    AND finished_at IS NULL
                )
                OR
            (
                run_status = 'SUCCEEDED'
                    AND user_turn_id IS NOT NULL
                    AND assistant_turn_id IS NOT NULL
                    AND failure_code IS NULL
                    AND accepted_at IS NOT NULL
                    AND started_at IS NOT NULL
                    AND finished_at IS NOT NULL
                )
                OR
            (
                run_status IN ('FAILED', 'TIMED_OUT')
                    AND user_turn_id IS NOT NULL
                    AND assistant_turn_id IS NOT NULL
                    AND failure_code IS NOT NULL
                    AND accepted_at IS NOT NULL
                    AND started_at IS NOT NULL
                    AND finished_at IS NOT NULL
                )
                OR
            (
                run_status = 'REJECTED'
                    AND assistant_turn_id IS NULL
                    AND failure_code IS NOT NULL
                    AND started_at IS NULL
                    AND finished_at IS NOT NULL
                    AND (
                    (
                        user_turn_id IS NULL
                            AND accepted_at IS NULL
                        )
                        OR
                    (
                        user_turn_id IS NOT NULL
                            AND accepted_at IS NOT NULL
                        )
                    )
                )
                OR
            (
                run_status = 'INTERRUPTED'
                    AND assistant_turn_id IS NULL
                    AND failure_code IS NOT NULL
                    AND finished_at IS NOT NULL
                    AND (
                    (
                        user_turn_id IS NULL
                            AND accepted_at IS NULL
                            AND started_at IS NULL
                        )
                        OR
                    (
                        user_turn_id IS NOT NULL
                            AND accepted_at IS NOT NULL
                        )
                    )
                )
            ),

    CONSTRAINT fk_coaching_run_session_owner
        FOREIGN KEY (session_id, owner_id)
            REFERENCES coaching_session (session_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_coaching_run_user_turn_owner
        FOREIGN KEY (user_turn_id, owner_id)
            REFERENCES coaching_turn (turn_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_coaching_run_assistant_turn_owner
        FOREIGN KEY (assistant_turn_id, owner_id)
            REFERENCES coaching_turn (turn_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_coaching_run_owner_session_created (
        owner_id,
        session_id,
        created_at,
        run_id
    ),

    INDEX idx_coaching_run_owner_status_updated (
        owner_id,
        run_status,
        updated_at,
        run_id
    ),

    INDEX idx_coaching_run_status_updated (
        run_status,
        updated_at,
        run_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;