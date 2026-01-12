-- 1. Users Table
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       name VARCHAR(255),
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Social Accounts Table
CREATE TABLE social_accounts (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 platform VARCHAR(50) NOT NULL,
                                 platform_user_id VARCHAR(100),
                                 username VARCHAR(100),
                                 auth_data JSONB NOT NULL,
                                 expires_at TIMESTAMP WITH TIME ZONE,
                                 is_active BOOLEAN DEFAULT TRUE,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Posts Table
CREATE TABLE posts (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       social_account_id UUID NOT NULL REFERENCES social_accounts(id) ON DELETE CASCADE,
                       platform VARCHAR(50) NOT NULL,
                       content TEXT,
                       media_urls TEXT[],
                       scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
                       published_at TIMESTAMP WITH TIME ZONE,
                       status VARCHAR(20) DEFAULT 'scheduled'
                           CHECK (status IN ('draft', 'scheduled', 'publishing', 'published', 'failed')),
                       api_payload JSONB NOT NULL,
                       error_message TEXT,
                       retry_count INTEGER DEFAULT 0,
                       max_retries INTEGER DEFAULT 3,
                       created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                       updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ================================================================
-- INDEXES
-- ================================================================

-- Posts table indexes
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_scheduled_at ON posts(scheduled_at);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_social_account_id ON posts(social_account_id);
CREATE INDEX idx_posts_scheduled_status ON posts(scheduled_at, status);
CREATE INDEX idx_posts_user_platform ON posts(user_id, platform);

-- Social accounts table indexes
CREATE INDEX idx_social_accounts_user_id ON social_accounts(user_id);
CREATE INDEX idx_social_accounts_platform ON social_accounts(platform);
CREATE INDEX idx_social_accounts_expires_at ON social_accounts(expires_at);

-- ================================================================
-- Altering for the sake of enhancing authorization process
-- 21/08/2025-5:05 PM
-- ================================================================
ALTER TABLE social_accounts
    ADD COLUMN oauth_state VARCHAR(255),
ADD COLUMN expected_username VARCHAR(100),
ADD COLUMN state_expires_at TIMESTAMP WITH TIME ZONE;

-- Index for OAuth state lookups
CREATE INDEX idx_social_accounts_oauth_state ON social_accounts(oauth_state, state_expires_at);
CREATE INDEX idx_social_accounts_user_platform ON social_accounts(user_id, platform);


-- ================================================================
-- Altering for the sake of development
-- 30/08/2025-12:04 PM
-- ================================================================
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;


ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ALTER COLUMN name DROP NOT NULL;

-- ================================================================
-- Altering for encrypting the tokens instead of storing them as map
-- 05/09/2025-10:57 AM
-- ================================================================

ALTER TABLE social_accounts
    ALTER COLUMN auth_data TYPE TEXT USING auth_data::text;

-- ================================================================
-- Altering for users table to add enabled column for user verification
-- 05/09/2025-10:57 AM
-- ================================================================
ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- ================================================================
-- Creating an EmailVerificationToken Entity to enhance the verification process
--
-- ================================================================
CREATE TABLE email_verification_tokens (
                                           id UUID PRIMARY KEY,
                                           user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           token_hash VARCHAR(64) NOT NULL UNIQUE,
                                           created_at TIMESTAMP NOT NULL,
                                           expires_at TIMESTAMP NOT NULL,
                                           used BOOLEAN NOT NULL DEFAULT FALSE,
                                           used_at TIMESTAMP NULL,
                                           ip_address VARCHAR(45) NULL
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens(user_id);

-- ================================================================
--
--25/11/2025 2:32 pm
-- ================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id UUID PRIMARY KEY,
                                              user_id UUID NOT NULL,
                                              token TEXT NOT NULL,
                                              created_at TIMESTAMP NOT NULL,
                                              expires_at TIMESTAMP NOT NULL,
                                              revoked BOOLEAN DEFAULT FALSE,

                                              CONSTRAINT fk_user
                                                  FOREIGN KEY (user_id) REFERENCES users(id)
                                                      ON DELETE CASCADE
);
ALTER TABLE email_verification_tokens
ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE refresh_tokens
    ALTER COLUMN id SET DEFAULT gen_random_uuid();


-- ================================================================
-- 12/8/2024 7:53
--25/11/2025 2:32 pm
-- ================================================================
-- =========================================
-- USERS FIXES
-- =========================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_not_null
    ON users(email)
    WHERE email IS NOT NULL;

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;


-- =========================================
-- SOCIAL ACCOUNTS FIXES
-- =========================================

-- Make platform_user_id required
ALTER TABLE social_accounts
    ALTER COLUMN platform_user_id SET NOT NULL;

-- Add missing uniqueness constraint
ALTER TABLE social_accounts
    ADD CONSTRAINT uq_social_platform_user
        UNIQUE (platform, platform_user_id);

-- Remove useless index
DROP INDEX IF EXISTS idx_social_accounts_user_platform;


-- =========================================
-- DONE
-- =========================================
CREATE TABLE IF NOT EXISTS oauth_states (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            user_id UUID NOT NULL,
                                            platform VARCHAR(50) NOT NULL,
                                            oauth_token VARCHAR(255) NOT NULL UNIQUE,
                                            token_secret VARCHAR(255) NOT NULL,
                                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                            consumed BOOLEAN NOT NULL DEFAULT FALSE,

                                            CONSTRAINT fk_oauth_states_user FOREIGN KEY (user_id)
                                                REFERENCES users(id) ON DELETE CASCADE
);

-- Index for quick lookup by oauth_token
CREATE INDEX idx_oauth_states_token ON oauth_states(oauth_token);

-- Index for cleanup of expired states
CREATE INDEX idx_oauth_states_expires_at ON oauth_states(expires_at);

-- Index for finding unconsumed states
CREATE INDEX idx_oauth_states_consumed ON oauth_states(consumed) WHERE consumed = FALSE;


UPDATE refresh_tokens
SET revoked = FALSE
WHERE revoked IS NULL;

-- ================================================================
-- 17/12/2024 10:46 am
-- created the auth req table to persist state and code verifier
-- for oauth2.0 X authorization flow
-- ================================================================
CREATE TABLE oauth2_authorization_requests (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                               provider VARCHAR(32) NOT NULL,

                                               user_id UUID NOT NULL,

                                               state VARCHAR(512) NOT NULL,
                                               code_verifier VARCHAR(512) NOT NULL,

                                               created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                               expires_at TIMESTAMPTZ NOT NULL,

                                               consumed BOOLEAN NOT NULL DEFAULT FALSE,

                                               CONSTRAINT fk_oauth2_user
                                                   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Prevent replay and cross-user abuse
CREATE UNIQUE INDEX uq_oauth2_provider_state
    ON oauth2_authorization_requests (provider, state);

-- User-specific cleanup and debugging
CREATE INDEX idx_oauth2_user_active
    ON oauth2_authorization_requests (user_id)
    WHERE consumed = FALSE;

-- Expiry cleanup
CREATE INDEX idx_oauth2_expires
    ON oauth2_authorization_requests (expires_at);

