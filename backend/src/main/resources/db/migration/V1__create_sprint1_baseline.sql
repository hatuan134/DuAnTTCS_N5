-- =========================================================
-- V1__create_sprint1_baseline.sql
-- HỆ THỐNG QUẢN LÝ MƯỢN / TRẢ SÁCH THƯ VIỆN
-- Sprint 1: Tài khoản, thẻ thư viện, chính sách mượn
-- PostgreSQL 15
-- =========================================================


-- =========================================================
-- 1. ROLES
-- 4 vai trò của hệ thống:
-- READER, LIBRARIAN, LIBRARY_MANAGER, ADMIN
-- =========================================================

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (code, name)
VALUES
    ('READER', 'Bạn đọc'),
    ('LIBRARIAN', 'Thủ thư'),
    ('LIBRARY_MANAGER', 'Quản lý thư viện'),
    ('ADMIN', 'Quản trị hệ thống');


-- =========================================================
-- 2. USERS
-- S1-01, S1-02, S1-03, S1-06
-- =========================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,

    role_id BIGINT NOT NULL,

    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    address VARCHAR(500),

    password_hash VARCHAR(255),

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,

    -- Tăng giá trị này khi cần vô hiệu hoá toàn bộ phiên cũ
    token_version INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id)
        REFERENCES roles(id),

    CONSTRAINT ck_users_status
        CHECK (
            status IN (
                'PENDING',
                'ACTIVE',
                'LOCKED',
                'DISABLED'
            )
        ),

    CONSTRAINT ck_failed_login_attempts
        CHECK (failed_login_attempts >= 0)
);

-- Email không phân biệt chữ hoa/chữ thường
CREATE UNIQUE INDEX ux_users_email_lower
    ON users (LOWER(email));

CREATE INDEX ix_users_role_id
    ON users(role_id);

CREATE INDEX ix_users_status
    ON users(status);


-- =========================================================
-- 3. READER PROFILES
-- Hồ sơ đăng ký bạn đọc
-- S1-03, S1-04
-- =========================================================

CREATE TABLE reader_profiles (
    user_id BIGINT PRIMARY KEY,

    date_of_birth DATE NOT NULL,

    -- Mã sinh viên hoặc mã cán bộ
    member_code VARCHAR(100) NOT NULL,

    registration_status VARCHAR(30)
        NOT NULL DEFAULT 'PENDING',

    rejection_reason VARCHAR(1000),

    submitted_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    reviewed_at TIMESTAMPTZ,

    reviewed_by BIGINT,

    CONSTRAINT fk_reader_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reader_profiles_reviewer
        FOREIGN KEY (reviewed_by)
        REFERENCES users(id),

    CONSTRAINT ck_reader_registration_status
        CHECK (
            registration_status IN (
                'PENDING',
                'APPROVED',
                'REJECTED'
            )
        )
);

CREATE UNIQUE INDEX ux_reader_profiles_member_code_lower
    ON reader_profiles (LOWER(member_code));

CREATE INDEX ix_reader_profiles_status
    ON reader_profiles(registration_status);


-- =========================================================
-- 4. PASSWORD HISTORY
-- S1-06:
-- mật khẩu mới không được trùng 3 mật khẩu gần nhất
-- =========================================================

CREATE TABLE password_history (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_history_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX ix_password_history_user_created
    ON password_history(user_id, created_at DESC);


-- =========================================================
-- 5. REFRESH TOKENS
-- S1-01
-- Access token: 30 phút
-- Refresh token: 7 ngày
-- =========================================================

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    token_hash VARCHAR(255) NOT NULL UNIQUE,

    expires_at TIMESTAMPTZ NOT NULL,

    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip_address INET,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX ix_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);


-- =========================================================
-- 6. PASSWORD RESET / INITIAL PASSWORD REQUESTS
-- S1-02:
-- Link đặt mật khẩu lần đầu hết hạn 24 giờ
--
-- S1-07:
-- Reset password hết hạn 30 phút
-- chỉ sử dụng một lần
-- giới hạn 3 yêu cầu/email/giờ sẽ kiểm tra ở Backend
-- =========================================================

CREATE TABLE password_reset_requests (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT,

    requested_email VARCHAR(255) NOT NULL,

    token_hash VARCHAR(255),

    request_type VARCHAR(30)
        NOT NULL DEFAULT 'RESET_PASSWORD',

    expires_at TIMESTAMPTZ,

    used_at TIMESTAMPTZ,

    requested_ip INET,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_password_request_type
        CHECK (
            request_type IN (
                'RESET_PASSWORD',
                'INITIAL_PASSWORD'
            )
        )
);

CREATE UNIQUE INDEX ux_password_reset_token_hash
    ON password_reset_requests(token_hash)
    WHERE token_hash IS NOT NULL;

CREATE INDEX ix_password_reset_email_created
    ON password_reset_requests(LOWER(requested_email), created_at DESC);

CREATE INDEX ix_password_reset_user_id
    ON password_reset_requests(user_id);


-- =========================================================
-- 7. CARD TYPES / BORROWING POLICY
-- S1-05
-- =========================================================

CREATE TABLE card_types (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,

    max_books INTEGER NOT NULL,
    loan_days INTEGER NOT NULL,
    max_renewals INTEGER NOT NULL,
    renewal_days INTEGER NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_card_types_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id),

    CONSTRAINT fk_card_types_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id),

    CONSTRAINT ck_card_types_max_books
        CHECK (max_books > 0 AND max_books <= 10),

    CONSTRAINT ck_card_types_loan_days
        CHECK (loan_days > 0 AND loan_days <= 60),

    CONSTRAINT ck_card_types_max_renewals
        CHECK (max_renewals > 0),

    CONSTRAINT ck_card_types_renewal_days
        CHECK (renewal_days > 0)
);

CREATE UNIQUE INDEX ux_card_types_name_lower
    ON card_types(LOWER(name));


-- =========================================================
-- 8. LIBRARY CARDS
-- S1-04, S1-05, S1-06
-- =========================================================

CREATE TABLE library_cards (
    id BIGSERIAL PRIMARY KEY,

    card_number VARCHAR(100) NOT NULL UNIQUE,

    user_id BIGINT NOT NULL UNIQUE,
    card_type_id BIGINT NOT NULL,

    issued_at DATE NOT NULL DEFAULT CURRENT_DATE,
    expires_at DATE NOT NULL,

    status VARCHAR(30)
        NOT NULL DEFAULT 'ACTIVE',

    created_by BIGINT,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_library_cards_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_library_cards_type
        FOREIGN KEY (card_type_id)
        REFERENCES card_types(id),

    CONSTRAINT fk_library_cards_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id),

    CONSTRAINT ck_library_card_status
        CHECK (
            status IN (
                'ACTIVE',
                'LOCKED',
                'EXPIRED',
                'DISABLED'
            )
        ),

    CONSTRAINT ck_library_card_dates
        CHECK (expires_at >= issued_at)
);

CREATE INDEX ix_library_cards_card_type
    ON library_cards(card_type_id);

CREATE INDEX ix_library_cards_status
    ON library_cards(status);


-- =========================================================
-- 9. AUTHORS
-- S1-08
-- =========================================================

CREATE TABLE authors (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_authors_name_lower
    ON authors(LOWER(name));


-- =========================================================
-- 10. CATEGORIES
-- S1-08
-- Cho phép tối đa 2 cấp
-- =========================================================

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    parent_id BIGINT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id)
        REFERENCES categories(id)
);

CREATE UNIQUE INDEX ux_categories_name_lower
    ON categories(LOWER(name));

CREATE INDEX ix_categories_parent
    ON categories(parent_id);


-- Chặn thể loại sâu hơn 2 cấp
CREATE OR REPLACE FUNCTION check_category_depth()
RETURNS TRIGGER AS $$
DECLARE
    parent_parent_id BIGINT;
BEGIN

    IF NEW.parent_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.id IS NOT NULL AND NEW.parent_id = NEW.id THEN
        RAISE EXCEPTION
            'Một thể loại không thể là cha của chính nó';
    END IF;

    SELECT parent_id
      INTO parent_parent_id
      FROM categories
     WHERE id = NEW.parent_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Thể loại cha không tồn tại';
    END IF;

    IF parent_parent_id IS NOT NULL THEN
        RAISE EXCEPTION
            'Thể loại chỉ được phép xếp lồng tối đa 2 cấp';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_category_depth
BEFORE INSERT OR UPDATE OF parent_id
ON categories
FOR EACH ROW
EXECUTE FUNCTION check_category_depth();


-- =========================================================
-- 11. WAREHOUSES
-- S1-09
-- =========================================================

CREATE TABLE warehouses (
    id BIGSERIAL PRIMARY KEY,

    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,

    description VARCHAR(500),

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_warehouses_code_lower
    ON warehouses(LOWER(code));

CREATE UNIQUE INDEX ux_warehouses_name_lower
    ON warehouses(LOWER(name));


-- =========================================================
-- 12. SHELVES
-- Mã kệ duy nhất trong một kho
-- S1-09
-- =========================================================

CREATE TABLE shelves (
    id BIGSERIAL PRIMARY KEY,

    warehouse_id BIGINT NOT NULL,

    code VARCHAR(50) NOT NULL,
    name VARCHAR(150),

    description VARCHAR(500),

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shelves_warehouse
        FOREIGN KEY (warehouse_id)
        REFERENCES warehouses(id)
);

CREATE UNIQUE INDEX ux_shelves_warehouse_code
    ON shelves(warehouse_id, LOWER(code));

CREATE INDEX ix_shelves_warehouse
    ON shelves(warehouse_id);


-- =========================================================
-- 13. WEEKLY LIBRARY SCHEDULE
-- Lịch làm việc theo thứ trong tuần
-- S1-09
--
-- day_of_week:
-- 1 = Thứ Hai
-- ...
-- 7 = Chủ Nhật
-- =========================================================

CREATE TABLE library_weekly_schedule (
    id BIGSERIAL PRIMARY KEY,

    day_of_week SMALLINT NOT NULL UNIQUE,

    is_open BOOLEAN NOT NULL DEFAULT TRUE,

    open_time TIME,
    close_time TIME,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_by BIGINT,

    CONSTRAINT fk_weekly_schedule_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(id),

    CONSTRAINT ck_weekly_schedule_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT ck_weekly_schedule_time
        CHECK (
            (
                is_open = FALSE
                AND open_time IS NULL
                AND close_time IS NULL
            )
            OR
            (
                is_open = TRUE
                AND open_time IS NOT NULL
                AND close_time IS NOT NULL
                AND close_time > open_time
            )
        )
);


-- =========================================================
-- 14. CLOSED DATES
-- Ngày nghỉ lễ / ngày thư viện đóng cửa cụ thể
-- S1-09
-- =========================================================

CREATE TABLE library_closed_dates (
    id BIGSERIAL PRIMARY KEY,

    closed_date DATE NOT NULL UNIQUE,

    reason VARCHAR(500),

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_by BIGINT,

    CONSTRAINT fk_closed_dates_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
);

CREATE INDEX ix_library_closed_dates_date
    ON library_closed_dates(closed_date);


-- =========================================================
-- 15. AUDIT LOGS
-- S1-01, S1-02, S1-04, S1-05, S1-10
-- =========================================================

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,

    actor_user_id BIGINT,

    action VARCHAR(100) NOT NULL,

    entity_type VARCHAR(100),

    entity_id VARCHAR(100),

    before_data JSONB,
    after_data JSONB,

    ip_address INET,

    created_at TIMESTAMPTZ
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_user_id)
        REFERENCES users(id)
        ON DELETE SET NULL
);

CREATE INDEX ix_audit_logs_actor
    ON audit_logs(actor_user_id);

CREATE INDEX ix_audit_logs_action
    ON audit_logs(action);

CREATE INDEX ix_audit_logs_created_at
    ON audit_logs(created_at DESC);


-- =========================================================
-- 16. HÀM TỰ CẬP NHẬT updated_at
-- =========================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_card_types_updated_at
BEFORE UPDATE ON card_types
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_library_cards_updated_at
BEFORE UPDATE ON library_cards
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_authors_updated_at
BEFORE UPDATE ON authors
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_warehouses_updated_at
BEFORE UPDATE ON warehouses
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_shelves_updated_at
BEFORE UPDATE ON shelves
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_library_closed_dates_updated_at
BEFORE UPDATE ON library_closed_dates
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();