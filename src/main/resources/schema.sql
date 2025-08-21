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
