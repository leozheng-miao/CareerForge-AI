ALTER TABLE target_role_draft
DROP CHECK chk_target_role_draft_status,
    DROP CHECK chk_target_role_draft_version,

    ADD COLUMN confirmed_target_role_id
        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER created_at,

    ADD COLUMN confirmed_target_role_version
        BIGINT UNSIGNED NULL
        AFTER confirmed_target_role_id,

    ADD COLUMN confirmed_at
        DATETIME(6) NULL
        AFTER confirmed_target_role_version,

    ADD CONSTRAINT chk_target_role_draft_status
        CHECK (draft_status IN ('PENDING', 'CONFIRMED')),

    ADD CONSTRAINT chk_target_role_draft_lifecycle
        CHECK (
            (
                draft_status = 'PENDING'
                    AND version = 0
                    AND confirmed_target_role_id IS NULL
                    AND confirmed_target_role_version IS NULL
                    AND confirmed_at IS NULL
            )
            OR
            (
                draft_status = 'CONFIRMED'
                    AND version = 1
                    AND confirmed_target_role_id IS NOT NULL
                    AND confirmed_target_role_version >= 1
                    AND confirmed_at IS NOT NULL
                    AND confirmed_at >= created_at
            )
        ),

    ADD CONSTRAINT fk_target_role_draft_confirmed_role
        FOREIGN KEY (
            confirmed_target_role_id,
            owner_id,
            confirmed_target_role_version
        )
        REFERENCES target_role (
            target_role_id,
            owner_id,
            target_role_version
        )
        ON UPDATE RESTRICT
           ON DELETE RESTRICT,

    ADD INDEX idx_target_role_draft_owner_status_created (
        owner_id,
        draft_status,
        created_at,
        draft_id
    );