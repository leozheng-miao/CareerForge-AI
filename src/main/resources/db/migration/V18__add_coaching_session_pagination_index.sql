CREATE INDEX idx_coaching_session_owner_updated_id
    ON coaching_session (owner_id, updated_at, session_id);