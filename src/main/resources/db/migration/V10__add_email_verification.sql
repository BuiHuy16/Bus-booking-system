ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE email_verifications (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_email_verification_hash CHECK (code_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_email_verification_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_email_verification_expiry CHECK (expires_at > sent_at)
);
