ALTER TABLE skill_gap_snapshot
    ADD COLUMN algorithm_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER profile_version;

UPDATE skill_gap_snapshot
SET algorithm_version = 'legacy-unknown-v0'
WHERE algorithm_version IS NULL;

ALTER TABLE skill_gap_snapshot
    MODIFY COLUMN algorithm_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
DROP INDEX uk_skill_gap_snapshot_input_versions,
    ADD CONSTRAINT uk_skill_gap_snapshot_input_versions
        UNIQUE (
            owner_id,
            target_role_id,
            target_role_version,
            profile_version,
            algorithm_version
        );