ALTER TABLE training_plan
    ADD CONSTRAINT uk_training_plan_id_owner_business_version
        UNIQUE (plan_id, owner_id, plan_version);


CREATE TABLE mock_interview_input_snapshot
(
    input_snapshot_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    schema_version          INT UNSIGNED                                   NOT NULL,
    target_role_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_role_version     BIGINT UNSIGNED                                NOT NULL,
    skill_gap_snapshot_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    training_plan_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    training_plan_version   BIGINT UNSIGNED                                NULL,
    snapshot_context_json   JSON                                           NOT NULL,
    snapshot_hash           CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at              DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (input_snapshot_id),

    CONSTRAINT uk_mock_interview_snapshot_id_owner
        UNIQUE (input_snapshot_id, owner_id),

    CONSTRAINT uk_mock_interview_snapshot_identity
        UNIQUE (input_snapshot_id, owner_id, snapshot_hash),

    CONSTRAINT uk_mock_interview_snapshot_owner_hash
        UNIQUE (owner_id, snapshot_hash),

    CONSTRAINT chk_mock_interview_snapshot_schema_version
        CHECK (schema_version >= 1),

    CONSTRAINT chk_mock_interview_snapshot_context
        CHECK (JSON_TYPE(snapshot_context_json) = 'OBJECT'),

    CONSTRAINT chk_mock_interview_snapshot_training_plan
        CHECK (
            (training_plan_id IS NULL AND training_plan_version IS NULL)
                OR
            (training_plan_id IS NOT NULL AND training_plan_version >= 1)
            ),

    CONSTRAINT fk_mock_interview_snapshot_target_role
        FOREIGN KEY (target_role_id, owner_id, target_role_version)
            REFERENCES target_role (target_role_id, owner_id, target_role_version)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_mock_interview_snapshot_skill_gap
        FOREIGN KEY (skill_gap_snapshot_id, owner_id)
            REFERENCES skill_gap_snapshot (snapshot_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_mock_interview_snapshot_training_plan
        FOREIGN KEY (training_plan_id, owner_id, training_plan_version)
            REFERENCES training_plan (plan_id, owner_id, plan_version)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_mock_interview_snapshot_owner_created (
        owner_id,
        created_at,
        input_snapshot_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE mock_interview_input_artifact
(
    input_snapshot_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id           VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    artifact_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_version   BIGINT UNSIGNED                                NOT NULL,
    artifact_source_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_order     SMALLINT UNSIGNED                              NOT NULL,
    created_at         DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (input_snapshot_id, artifact_id, artifact_version),

    CONSTRAINT uk_mock_interview_snapshot_artifact_order
        UNIQUE (input_snapshot_id, artifact_order),

    CONSTRAINT chk_mock_interview_snapshot_artifact_order
        CHECK (artifact_order >= 1),

    CONSTRAINT fk_mock_interview_input_artifact_snapshot
        FOREIGN KEY (input_snapshot_id, owner_id)
            REFERENCES mock_interview_input_snapshot (input_snapshot_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_mock_interview_input_artifact_version
        FOREIGN KEY (artifact_id, owner_id, artifact_version)
            REFERENCES personal_evidence_artifact (artifact_id, owner_id, artifact_version)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_mock_interview_input_artifact_owner (
        owner_id,
        input_snapshot_id,
        artifact_order
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE mock_interview_session
(
    interview_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id              VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    request_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_snapshot_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_snapshot_hash   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_mode        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_status      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_questions         INT UNSIGNED                                   NOT NULL,
    max_follow_ups        INT UNSIGNED                                   NOT NULL,
    max_model_calls       INT UNSIGNED                                   NOT NULL,
    max_total_tokens      BIGINT UNSIGNED                                NOT NULL,
    failure_code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version               BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at            DATETIME(6)                                    NOT NULL,
    updated_at            DATETIME(6)                                    NOT NULL,
    finished_at           DATETIME(6)                                    NULL,

    PRIMARY KEY (interview_id),

    CONSTRAINT uk_mock_interview_session_id_owner
        UNIQUE (interview_id, owner_id),

    CONSTRAINT uk_mock_interview_session_owner_request
        UNIQUE (owner_id, request_id),

    CONSTRAINT chk_mock_interview_session_mode
        CHECK (interview_mode IN ('TARGETED_MOCK', 'GAP_DRILL')),

    CONSTRAINT chk_mock_interview_session_status
        CHECK (
            interview_status IN (
                                 'CREATED',
                                 'GENERATING_QUESTION',
                                 'WAITING_FOR_ANSWER',
                                 'REVIEWING',
                                 'GENERATING_REPORT',
                                 'AWAITING_CONFIRMATION',
                                 'COMPLETED',
                                 'FAILED',
                                 'INTERRUPTED',
                                 'CANCELLED'
                )
            ),

    CONSTRAINT chk_mock_interview_session_budget
        CHECK (
            max_questions >= 1
                AND max_follow_ups < max_questions
                AND max_model_calls >= 1
                AND max_total_tokens >= 1
            ),

    CONSTRAINT chk_mock_interview_session_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_mock_interview_session_lifecycle
        CHECK (
            (
                interview_status IN (
                                     'CREATED',
                                     'GENERATING_QUESTION',
                                     'WAITING_FOR_ANSWER',
                                     'REVIEWING',
                                     'GENERATING_REPORT',
                                     'AWAITING_CONFIRMATION'
                    )
                    AND failure_code IS NULL
                    AND finished_at IS NULL
                )
                OR
            (
                interview_status = 'COMPLETED'
                    AND failure_code IS NULL
                    AND finished_at IS NOT NULL
                    AND finished_at >= created_at
                )
                OR
            (
                interview_status IN ('FAILED', 'INTERRUPTED')
                    AND failure_code IS NOT NULL
                    AND finished_at IS NOT NULL
                    AND finished_at >= created_at
                )
                OR
            (
                interview_status = 'CANCELLED'
                    AND failure_code IS NULL
                    AND finished_at IS NOT NULL
                    AND finished_at >= created_at
                )
            ),

    CONSTRAINT fk_mock_interview_session_snapshot
        FOREIGN KEY (input_snapshot_id, owner_id, input_snapshot_hash)
            REFERENCES mock_interview_input_snapshot (input_snapshot_id, owner_id, snapshot_hash)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_mock_interview_owner_status_updated (
        owner_id,
        interview_status,
        updated_at,
        interview_id
    ),

    INDEX idx_mock_interview_status_updated (
        interview_status,
        updated_at,
        interview_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_round
(
    round_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id       VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    round_no       INT UNSIGNED                                   NOT NULL,
    round_status   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version        BIGINT UNSIGNED                                NOT NULL DEFAULT 0,
    created_at     DATETIME(6)                                    NOT NULL,
    updated_at     DATETIME(6)                                    NOT NULL,
    answered_at    DATETIME(6)                                    NULL,
    reviewed_at    DATETIME(6)                                    NULL,

    PRIMARY KEY (round_id),

    CONSTRAINT uk_interview_round_id_interview_owner
        UNIQUE (round_id, interview_id, owner_id),

    CONSTRAINT uk_interview_round_interview_number
        UNIQUE (interview_id, round_no),

    CONSTRAINT chk_interview_round_number
        CHECK (round_no >= 1),

    CONSTRAINT chk_interview_round_status
        CHECK (round_status IN ('QUESTION_READY', 'ANSWERED', 'REVIEWED')),

    CONSTRAINT chk_interview_round_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_interview_round_lifecycle
        CHECK (
            (
                round_status = 'QUESTION_READY'
                    AND answered_at IS NULL
                    AND reviewed_at IS NULL
                )
                OR
            (
                round_status = 'ANSWERED'
                    AND answered_at IS NOT NULL
                    AND answered_at >= created_at
                    AND reviewed_at IS NULL
                )
                OR
            (
                round_status = 'REVIEWED'
                    AND answered_at IS NOT NULL
                    AND reviewed_at IS NOT NULL
                    AND answered_at >= created_at
                    AND reviewed_at >= answered_at
                )
            ),

    CONSTRAINT fk_interview_round_session
        FOREIGN KEY (interview_id, owner_id)
            REFERENCES mock_interview_session (interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_round_owner_interview_number (
        owner_id,
        interview_id,
        round_no
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_question
(
    question_id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    round_id              CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id              VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    parent_question_id    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    question_type         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_text         VARCHAR(2000)                                  NOT NULL,
    difficulty            TINYINT UNSIGNED                               NOT NULL,
    target_skills_json    JSON                                           NOT NULL,
    evaluation_points_json JSON                                          NOT NULL,
    follow_up_allowed     BOOLEAN                                        NOT NULL DEFAULT FALSE,
    is_follow_up          BOOLEAN                                        NOT NULL DEFAULT FALSE,
    evidence_refs_json    JSON                                           NOT NULL,
    model_request_id      VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    prompt_version        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_hash          CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at            DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (question_id),

    CONSTRAINT uk_interview_question_id_owner
        UNIQUE (question_id, owner_id),

    CONSTRAINT uk_interview_question_parent_scope
        UNIQUE (question_id, interview_id, owner_id),

    CONSTRAINT uk_interview_question_answer_scope
        UNIQUE (question_id, interview_id, round_id, owner_id),

    CONSTRAINT uk_interview_question_round
        UNIQUE (round_id),

    CONSTRAINT chk_interview_question_type
        CHECK (
            question_type IN (
                              'TECHNICAL_KNOWLEDGE',
                              'PROJECT_DEEP_DIVE',
                              'SYSTEM_DESIGN'
                )
            ),

    CONSTRAINT chk_interview_question_text
        CHECK (CHAR_LENGTH(TRIM(question_text)) BETWEEN 1 AND 2000),

    CONSTRAINT chk_interview_question_difficulty
        CHECK (difficulty BETWEEN 1 AND 5),

    CONSTRAINT chk_interview_question_target_skills
        CHECK (
            JSON_TYPE(target_skills_json) = 'ARRAY'
                AND JSON_LENGTH(target_skills_json) BETWEEN 1 AND 10
            ),

    CONSTRAINT chk_interview_question_evaluation_points
        CHECK (
            JSON_TYPE(evaluation_points_json) = 'ARRAY'
                AND JSON_LENGTH(evaluation_points_json) BETWEEN 1 AND 10
            ),

    CONSTRAINT chk_interview_question_evidence_refs
        CHECK (
            JSON_TYPE(evidence_refs_json) = 'ARRAY'
                AND JSON_LENGTH(evidence_refs_json) <= 10
            ),

    CONSTRAINT chk_interview_question_follow_up
        CHECK (
            (is_follow_up = FALSE AND parent_question_id IS NULL)
                OR
            (is_follow_up = TRUE AND parent_question_id IS NOT NULL)
            ),

    CONSTRAINT fk_interview_question_round
        FOREIGN KEY (round_id, interview_id, owner_id)
            REFERENCES interview_round (round_id, interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_interview_question_parent
        FOREIGN KEY (parent_question_id, interview_id, owner_id)
            REFERENCES interview_question (question_id, interview_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_question_owner_interview_created (
        owner_id,
        interview_id,
        created_at,
        question_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE interview_answer
(
    answer_id                   CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    interview_id                CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    round_id                    CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id                 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id                    VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    request_id                  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_interview_version  BIGINT UNSIGNED                                NOT NULL,
    answer_text                 TEXT                                           NOT NULL,
    content_hash                CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submitted_at                DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (answer_id),

    CONSTRAINT uk_interview_answer_id_owner
        UNIQUE (answer_id, owner_id),

    CONSTRAINT uk_interview_answer_question
        UNIQUE (question_id),

    CONSTRAINT uk_interview_answer_owner_request
        UNIQUE (owner_id, request_id),

    CONSTRAINT chk_interview_answer_text
        CHECK (CHAR_LENGTH(TRIM(answer_text)) BETWEEN 1 AND 12000),

    CONSTRAINT fk_interview_answer_question
        FOREIGN KEY (question_id, interview_id, round_id, owner_id)
            REFERENCES interview_question (question_id, interview_id, round_id, owner_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_interview_answer_owner_interview_submitted (
        owner_id,
        interview_id,
        submitted_at,
        answer_id
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;