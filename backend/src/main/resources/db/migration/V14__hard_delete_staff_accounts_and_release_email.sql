-- SuaLoiTaiKhoan
-- Cho phép xóa vật lý tài khoản nhân viên mà không phá dữ liệu nghiệp vụ/lịch sử,
-- đồng thời dọn các tài khoản đã từng bị "xóa mềm" bằng status = DISABLED.

-- Các cột chỉ lưu người thao tác/reviewer: khi tài khoản bị xóa thì giữ bản ghi nghiệp vụ
-- nhưng bỏ liên kết tới tài khoản đã xóa.
ALTER TABLE reader_profiles
    DROP CONSTRAINT IF EXISTS fk_reader_profiles_reviewer;
ALTER TABLE reader_profiles
    ADD CONSTRAINT fk_reader_profiles_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE card_types
    DROP CONSTRAINT IF EXISTS fk_card_types_created_by;
ALTER TABLE card_types
    ADD CONSTRAINT fk_card_types_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE card_types
    DROP CONSTRAINT IF EXISTS fk_card_types_updated_by;
ALTER TABLE card_types
    ADD CONSTRAINT fk_card_types_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

-- Thẻ là dữ liệu thuộc về tài khoản chủ thẻ; nếu user thật sự bị xóa thì thẻ cũng được xóa.
ALTER TABLE library_cards
    DROP CONSTRAINT IF EXISTS fk_library_cards_user;
ALTER TABLE library_cards
    ADD CONSTRAINT fk_library_cards_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE library_cards
    DROP CONSTRAINT IF EXISTS fk_library_cards_created_by;
ALTER TABLE library_cards
    ADD CONSTRAINT fk_library_cards_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE library_weekly_schedule
    DROP CONSTRAINT IF EXISTS fk_weekly_schedule_updated_by;
ALTER TABLE library_weekly_schedule
    ADD CONSTRAINT fk_weekly_schedule_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE library_closed_dates
    DROP CONSTRAINT IF EXISTS fk_closed_dates_created_by;
ALTER TABLE library_closed_dates
    ADD CONSTRAINT fk_closed_dates_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

-- V13 tạo created_by NOT NULL + ON DELETE RESTRICT. Đây chỉ là metadata người lập phiếu,
-- vì vậy cho phép giữ phiếu mượn nhưng tách khỏi tài khoản nhân viên đã bị xóa.
ALTER TABLE loans
    DROP CONSTRAINT IF EXISTS loans_created_by_fkey;
ALTER TABLE loans
    DROP CONSTRAINT IF EXISTS fk_loans_created_by;
ALTER TABLE loans
    ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE loans
    ADD CONSTRAINT fk_loans_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

-- Lịch sử trạng thái bản sao phải bất biến, nhưng actor account có thể bị xóa.
-- Chỉ cho phép một thay đổi duy nhất trên bản ghi lịch sử: FK tự đặt actor_user_id = NULL.
CREATE OR REPLACE FUNCTION protect_copy_status_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.actor_user_id IS NOT NULL
       AND NEW.actor_user_id IS NULL
       AND ROW(
            NEW.id,
            NEW.book_copy_id,
            NEW.previous_status,
            NEW.new_status,
            NEW.actor_name,
            NEW.changed_at,
            NEW.reason
       ) IS NOT DISTINCT FROM ROW(
            OLD.id,
            OLD.book_copy_id,
            OLD.previous_status,
            OLD.new_status,
            OLD.actor_name,
            OLD.changed_at,
            OLD.reason
       ) THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'Không được sửa hoặc xóa lịch sử thay đổi trạng thái bản sao.';
END;
$$;

ALTER TABLE book_copy_status_history
    DROP CONSTRAINT IF EXISTS book_copy_status_history_actor_user_id_fkey;
ALTER TABLE book_copy_status_history
    DROP CONSTRAINT IF EXISTS fk_book_copy_status_history_actor;
ALTER TABLE book_copy_status_history
    ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE book_copy_status_history
    ADD CONSTRAINT fk_book_copy_status_history_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Dọn các tài khoản nhân viên đã bị xóa mềm bởi phiên bản cũ.
-- Đồng thời xóa các audit log chứa email của đúng tài khoản đó để email không còn tồn tại
-- trong dữ liệu hệ thống và có thể được đăng ký lại bình thường.
CREATE TEMP TABLE tmp_disabled_staff_users ON COMMIT DROP AS
SELECT u.id, u.email
FROM users u
JOIN roles r ON r.id = u.role_id
WHERE u.status = 'DISABLED'
  AND r.code IN ('ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN');

DELETE FROM audit_logs al
USING tmp_disabled_staff_users deleted_user
WHERE (al.entity_type = 'USER' AND al.entity_id = deleted_user.id::text)
   OR LOWER(COALESCE(al.entity_id, '')) = LOWER(deleted_user.email)
   OR LOWER(COALESCE(al.before_data::text, '')) LIKE '%' || LOWER(deleted_user.email) || '%'
   OR LOWER(COALESCE(al.after_data::text, '')) LIKE '%' || LOWER(deleted_user.email) || '%';

DELETE FROM users u
USING tmp_disabled_staff_users deleted_user
WHERE u.id = deleted_user.id;
