CREATE TABLE personal_evidence_artifact
(
    artifact_id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_version       BIGINT UNSIGNED                                NOT NULL,
    owner_id               VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    artifact_type          VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_name            VARCHAR(255)                                   NOT NULL,
    source_hash            CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content                MEDIUMTEXT                                     NOT NULL,
    artifact_status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    superseded_by_version  BIGINT UNSIGNED                                NULL,
    created_at             DATETIME(6)                                    NOT NULL,
    updated_at             DATETIME(6)                                    NOT NULL,
    superseded_at          DATETIME(6)                                    NULL,
    revoked_at             DATETIME(6)                                    NULL,

    PRIMARY KEY (artifact_id, artifact_version),

    CONSTRAINT uk_personal_evidence_artifact_owner_version
        UNIQUE (artifact_id, owner_id, artifact_version),

    CONSTRAINT chk_personal_evidence_artifact_version
        CHECK (artifact_version >= 1),

    CONSTRAINT chk_personal_evidence_artifact_type
        CHECK (artifact_type IN ('RESUME', 'PROJECT', 'TEST_REPORT')),

    CONSTRAINT chk_personal_evidence_artifact_source_name
        CHECK (CHAR_LENGTH(TRIM(source_name)) BETWEEN 1 AND 255),

    CONSTRAINT chk_personal_evidence_artifact_content
        CHECK (CHAR_LENGTH(content) BETWEEN 1 AND 100000),

    CONSTRAINT chk_personal_evidence_artifact_status
        CHECK (artifact_status IN ('ACTIVE', 'SUPERSEDED', 'REVOKED')),

    CONSTRAINT chk_personal_evidence_artifact_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_personal_evidence_artifact_lifecycle
        CHECK (
            (
                artifact_status = 'ACTIVE'
                    AND superseded_by_version IS NULL
                    AND superseded_at IS NULL
                    AND revoked_at IS NULL
                )
                OR
            (
                artifact_status = 'SUPERSEDED'
                    AND superseded_by_version IS NOT NULL
                    AND superseded_by_version > artifact_version
                    AND superseded_at IS NOT NULL
                    AND superseded_at >= created_at
                    AND revoked_at IS NULL
                )
                OR
            (
                artifact_status = 'REVOKED'
                    AND superseded_by_version IS NULL
                    AND superseded_at IS NULL
                    AND revoked_at IS NOT NULL
                    AND revoked_at >= created_at
                )
            ),

    CONSTRAINT fk_personal_evidence_artifact_superseded_version
        FOREIGN KEY (artifact_id, owner_id, superseded_by_version)
            REFERENCES personal_evidence_artifact (artifact_id, owner_id, artifact_version)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_personal_evidence_owner_type_status_updated (
        owner_id,
        artifact_type,
        artifact_status,
        updated_at,
        artifact_id
    ),

    INDEX idx_personal_evidence_owner_source_hash (
        owner_id,
        source_hash,
        artifact_id,
        artifact_version
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE personal_evidence_chunk
(
    evidence_chunk_id  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    artifact_version   BIGINT UNSIGNED                                NOT NULL,
    owner_id           VARCHAR(128) COLLATE utf8mb4_bin               NOT NULL,
    chunk_index        INT UNSIGNED                                   NOT NULL,
    start_offset       INT UNSIGNED                                   NOT NULL,
    end_offset         INT UNSIGNED                                   NOT NULL,
    chunk_content      VARCHAR(4000)                                  NOT NULL,
    content_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at         DATETIME(6)                                    NOT NULL,

    PRIMARY KEY (evidence_chunk_id),

    CONSTRAINT uk_personal_evidence_chunk_id_owner
        UNIQUE (evidence_chunk_id, owner_id),

    CONSTRAINT uk_personal_evidence_chunk_artifact_index
        UNIQUE (artifact_id, owner_id, artifact_version, chunk_index),

    CONSTRAINT chk_personal_evidence_chunk_index
        CHECK (chunk_index >= 1),

    CONSTRAINT chk_personal_evidence_chunk_offsets
        CHECK (
            end_offset > start_offset
                AND CHAR_LENGTH(chunk_content) = end_offset - start_offset
            ),

    CONSTRAINT chk_personal_evidence_chunk_content
        CHECK (CHAR_LENGTH(chunk_content) BETWEEN 1 AND 4000),

    CONSTRAINT fk_personal_evidence_chunk_artifact
        FOREIGN KEY (artifact_id, owner_id, artifact_version)
            REFERENCES personal_evidence_artifact (artifact_id, owner_id, artifact_version)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_personal_evidence_chunk_owner_artifact (
        owner_id,
        artifact_id,
        artifact_version,
        chunk_index
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;