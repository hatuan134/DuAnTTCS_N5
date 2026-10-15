-- Preserve all issued codes. A single non-cycling sequence serves every creation path.
CREATE SEQUENCE loan_number_seq AS BIGINT MINVALUE 1 NO CYCLE;
-- Also tolerate short numeric codes already imported by a demo or an earlier installation.
SELECT setval('loan_number_seq', GREATEST(1, COALESCE(MAX(
    CASE WHEN loan_number ~ '^PM-[0-9]{1,18}$'
         THEN substring(loan_number FROM 4)::bigint END), 0) + 1), false)
FROM loans;
