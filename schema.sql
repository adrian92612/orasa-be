-- Orasa Full High-Performance Schema
-- Optimized for: PostgreSQL / Supabase
-- Target: Sub-100ms Query Times

-- 0. Enable Performance Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "citext"; -- native case-insensitivity

-- 0. Cleanup (Reverse Dependency Order)
DROP TABLE IF EXISTS scheduled_sms_tasks CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS sms_logs CASCADE;
DROP TABLE IF EXISTS activity_logs CASCADE;
DROP TABLE IF EXISTS appointment_reminders CASCADE;
DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS business_reminder_configs CASCADE;
DROP TABLE IF EXISTS branch_services CASCADE;
DROP TABLE IF EXISTS services CASCADE;
DROP TABLE IF EXISTS user_branches CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS branches CASCADE;
DROP TABLE IF EXISTS businesses CASCADE;

-- 1. Businesses
CREATE TABLE businesses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    slug CITEXT UNIQUE, -- Instant lookups regardless of casing
    free_sms_credits INTEGER NOT NULL DEFAULT 100,
    paid_sms_credits INTEGER NOT NULL DEFAULT 0,
    subscription_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    subscription_start_date TIMESTAMP WITH TIME ZONE,
    subscription_end_date TIMESTAMP WITH TIME ZONE,
    next_credit_reset_date TIMESTAMP WITH TIME ZONE,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 2. Branches
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    phone_number VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 3. Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID REFERENCES businesses(id) ON DELETE CASCADE,
    username CITEXT NOT NULL,
    email CITEXT,
    password_hash VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Unique indexes that only care about active rows (Faster than global indexes)
CREATE UNIQUE INDEX uq_users_username_active ON users(username) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uq_users_email_active ON users(email) WHERE is_deleted = FALSE;

-- 4. User Branches (Many-to-Many)
CREATE TABLE user_branches (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, branch_id)
);

-- 5. Services
CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    base_price DECIMAL(10, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 6. Branch Services
CREATE TABLE branch_services (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    custom_price DECIMAL(10, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(branch_id, service_id)
);

-- 7. Appointments (Performance Core)
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(50) NOT NULL,
    service_id UUID REFERENCES services(id) ON DELETE SET NULL,
    start_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    additional_reminder_minutes INTEGER,
    additional_reminder_template TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- SPEED INDEX 1: The Dashboard Query
-- Targets: business_id + status + start_date_time (Matches your Controller/Service logic)
CREATE INDEX idx_appt_dashboard_perf 
ON appointments (business_id, status, start_date_time DESC) 
WHERE is_deleted = FALSE;

-- SPEED INDEX 2: Trigram Indexes for ILIKE Search
-- Separate per-column GIN indexes; PostgreSQL combines via bitmap scans
CREATE INDEX idx_appt_trgm_name
ON appointments USING gin (customer_name gin_trgm_ops)
WHERE is_deleted = FALSE;

CREATE INDEX idx_appt_trgm_phone
ON appointments USING gin (customer_phone gin_trgm_ops)
WHERE is_deleted = FALSE;

CREATE INDEX idx_appt_trgm_notes
ON appointments USING gin (notes gin_trgm_ops)
WHERE is_deleted = FALSE;

-- SPEED INDEX 3: Business Date Range (New)
-- Targets: Queries filtering by business and date (e.g. "All" tab)
CREATE INDEX idx_appt_biz_date
ON appointments (business_id, start_date_time)
WHERE is_deleted = FALSE;

-- Add to your schema
CREATE INDEX idx_appt_biz_search 
ON appointments (business_id, start_date_time) 
INCLUDE (customer_name, customer_phone, status, type)
WHERE is_deleted = FALSE;

-- 8. Business Reminder Configs
CREATE TABLE business_reminder_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    lead_time_minutes INTEGER NOT NULL CHECK (lead_time_minutes > 0),
    message_template TEXT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_biz_leadtime_active ON business_reminder_configs (business_id, lead_time_minutes) WHERE is_deleted = FALSE;

-- 9. Activity Logs
CREATE TABLE activity_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    branch_id UUID REFERENCES branches(id) ON DELETE CASCADE,
    action VARCHAR(255) NOT NULL,
    description TEXT,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 10. SMS Logs
CREATE TABLE sms_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
    recipient_phone VARCHAR(50) NOT NULL,
    message_body TEXT,
    status VARCHAR(50),
    provider_id VARCHAR(255),
    provider_response TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 11. Scheduled SMS Tasks
CREATE TABLE scheduled_sms_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    lead_time_minutes INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- SPEED INDEX 3: Background Queue
CREATE INDEX idx_sms_queue_active ON scheduled_sms_tasks (scheduled_at ASC) 
WHERE status = 'PENDING' AND is_deleted = FALSE;

-- 12. Appointment Reminders (Join Table)
CREATE TABLE appointment_reminders (
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    reminder_config_id UUID NOT NULL REFERENCES business_reminder_configs(id) ON DELETE CASCADE,
    PRIMARY KEY (appointment_id, reminder_config_id)
);

-- 13. Payments
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    merchant_order_no VARCHAR(255) NOT NULL,
    plat_order_no VARCHAR(255),
    amount DECIMAL(10, 2) NOT NULL,
    description TEXT NOT NULL,
    method VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_link TEXT,
    payment_image TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_biz ON payments(business_id);
CREATE INDEX idx_payments_order ON payments(merchant_order_no);

-- 13. System Admin Insert
INSERT INTO users (business_id, username, email, role, created_by)
VALUES (NULL, 'adrvil.code@gmail.com', 'adrvil.code@gmail.com', 'ADMIN', 'SYSTEM_INIT');