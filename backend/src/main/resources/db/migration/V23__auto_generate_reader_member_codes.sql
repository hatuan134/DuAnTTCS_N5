-- S3-00.3: Bạn đọc không còn tự nhập mã SV/cán bộ.
-- Mã bạn đọc mới được cấp tuần tự theo dãy BD000001, BD000002, ...
-- Giữ nguyên toàn bộ member_code cũ để không phá hồ sơ/lịch sử hiện có.
CREATE SEQUENCE reader_member_code_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

DO $$
DECLARE
    existing_count BIGINT;
    max_generated_number BIGINT;
    initial_value BIGINT;
BEGIN
    SELECT COUNT(*) INTO existing_count FROM reader_profiles;

    SELECT COALESCE(MAX(SUBSTRING(member_code FROM 3)::BIGINT), 0)
    INTO max_generated_number
    FROM reader_profiles
    WHERE member_code ~ '^BD[0-9]+$';

    initial_value := GREATEST(existing_count, max_generated_number);

    IF initial_value > 0 THEN
        PERFORM setval('reader_member_code_seq', initial_value, TRUE);
    ELSE
        PERFORM setval('reader_member_code_seq', 1, FALSE);
    END IF;
END;
$$;
