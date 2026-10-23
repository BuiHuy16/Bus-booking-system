CREATE TABLE email_verifications (
    user_id BIGINT PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_email_verifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_email_verification_hash CHECK (btrim(code_hash) <> ''),
    CONSTRAINT ck_email_verification_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_email_verification_expiry CHECK (expires_at > sent_at)
);