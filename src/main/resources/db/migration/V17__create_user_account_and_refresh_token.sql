CREATE TABLE user_account
(
    user_id        VARCHAR(128) COLLATE utf8mb4_bin                   NOT NULL,
    email          VARCHAR(254) COLLATE utf8mb4_bin                   NOT NULL,
    display_name   VARCHAR(80)                                        NOT NULL,
    password_hash  VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL,
    version        BIGINT UNSIGNED                                    NOT NULL DEFAULT 0,
    created_at     DATETIME(6)                                        NOT NULL,
    updated_at     DATETIME(6)                                        NOT NULL,
    last_login_at  DATETIME(6)                                        NULL,
    disabled_at    DATETIME(6)                                        NULL,

    PRIMARY KEY (user_id),

    CONSTRAINT uk_user_account_email
        UNIQUE (email),

    CONSTRAINT chk_user_account_email
        CHECK (
            CHAR_LENGTH(email) BETWEEN 3 AND 254
                AND email = LOWER(TRIM(email))
            ),

    CONSTRAINT chk_user_account_display_name
        CHECK (
            CHAR_LENGTH(TRIM(display_name)) BETWEEN 1 AND 80
            ),

    CONSTRAINT chk_user_account_status
        CHECK (account_status IN ('ACTIVE', 'DISABLED')),

    CONSTRAINT chk_user_account_status_time
        CHECK (
            (account_status = 'ACTIVE' AND disabled_at IS NULL)
                OR
            (account_status = 'DISABLED' AND disabled_at IS NOT NULL)
            ),

    CONSTRAINT chk_user_account_updated_at
        CHECK (updated_at >= created_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;


CREATE TABLE auth_refresh_token
(
    refresh_token_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    user_id          VARCHAR(128) COLLATE utf8mb4_bin                  NOT NULL,
    family_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    parent_token_id  CHAR(36) CHARACTER SET ascii COLLATE ascii_bin    NULL,
    token_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL,
    token_status     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at       DATETIME(6)                                       NOT NULL,
    created_at       DATETIME(6)                                       NOT NULL,
    rotated_at       DATETIME(6)                                       NULL,
    revoked_at       DATETIME(6)                                       NULL,
    version          BIGINT UNSIGNED                                   NOT NULL DEFAULT 0,

    PRIMARY KEY (refresh_token_id),

    CONSTRAINT uk_refresh_token_id_user
        UNIQUE (refresh_token_id, user_id),

    CONSTRAINT uk_refresh_token_hash
        UNIQUE (token_hash),

    CONSTRAINT chk_refresh_token_status
        CHECK (token_status IN ('ACTIVE', 'ROTATED', 'REVOKED')),

    CONSTRAINT chk_refresh_token_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT uk_refresh_token_parent
        UNIQUE (parent_token_id),

    CONSTRAINT chk_refresh_token_lifecycle
        CHECK (
            (
                token_status = 'ACTIVE'
                    AND rotated_at IS NULL
                    AND revoked_at IS NULL
                )
                OR
            (
                token_status = 'ROTATED'
                    AND rotated_at IS NOT NULL
                    AND revoked_at IS NULL
                )
                OR
            (
                token_status = 'REVOKED'
                    AND revoked_at IS NOT NULL
                )
            ),

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
            REFERENCES user_account (user_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    CONSTRAINT fk_refresh_token_parent_user
        FOREIGN KEY (parent_token_id, user_id)
            REFERENCES auth_refresh_token (refresh_token_id, user_id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT,

    INDEX idx_refresh_token_user_status_expiry (
                                                user_id,
                                                token_status,
                                                expires_at
        ),

    INDEX idx_refresh_token_user_family_status (
                                                user_id,
                                                family_id,
                                                token_status
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;