-- Empty policies preserve all existing lending behaviour. Never backfill old loan items.
CREATE TABLE deposit_policies (
 id BIGSERIAL PRIMARY KEY,
 percentage NUMERIC(9,4) NOT NULL CHECK(percentage>0 AND percentage<=100),
 effective_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
 created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 previous_policy_id BIGINT REFERENCES deposit_policies(id),
 CHECK(effective_at>=created_at)
);
CREATE INDEX ix_deposit_policy_effective ON deposit_policies(effective_at DESC,id DESC);
CREATE TABLE deposit_accounts (
 id BIGSERIAL PRIMARY KEY,
 reader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 copy_id BIGINT NOT NULL REFERENCES book_copies(id) ON DELETE RESTRICT,
 policy_id BIGINT NOT NULL REFERENCES deposit_policies(id) ON DELETE RESTRICT,
 cover_price NUMERIC(12,2) NOT NULL CHECK(cover_price>0),
 percentage NUMERIC(9,4) NOT NULL CHECK(percentage>0 AND percentage<=100),
 required_vnd BIGINT NOT NULL CHECK(required_vnd BETWEEN 1 AND 9007199254740991),
 created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 CHECK(required_vnd=round(cover_price*percentage/100,0))
);
CREATE INDEX ix_deposit_account_reader ON deposit_accounts(reader_user_id,id DESC);
CREATE INDEX ix_deposit_account_copy ON deposit_accounts(reader_user_id,copy_id,id DESC);
CREATE TABLE deposit_handovers (
 account_id BIGINT PRIMARY KEY REFERENCES deposit_accounts(id) ON DELETE RESTRICT,
 item_id BIGINT NOT NULL UNIQUE REFERENCES loan_items(id) ON DELETE RESTRICT,
 attached_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE deposit_transactions (
 id BIGSERIAL PRIMARY KEY,
 transaction_number TEXT GENERATED ALWAYS AS ('COC-'||id::text) STORED UNIQUE,
 reader_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 request_key UUID NOT NULL,
 fingerprint CHAR(64) NOT NULL,
 kind VARCHAR(12) NOT NULL CHECK(kind IN ('COLLECT','REFUND','DEDUCT')),
 method VARCHAR(20) NOT NULL CHECK(method IN ('CASH','DEPOSIT_OFFSET')),
 total_vnd BIGINT NOT NULL CHECK(total_vnd BETWEEN 1 AND 9007199254740991),
 actor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
 actor_name VARCHAR(255) NOT NULL,
 recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 created_txid BIGINT NOT NULL DEFAULT txid_current(),
 fee_payment_id BIGINT UNIQUE REFERENCES fee_payments(id) ON DELETE RESTRICT,
 UNIQUE(reader_user_id,request_key),
 CHECK((kind='DEDUCT' AND method='DEPOSIT_OFFSET' AND fee_payment_id IS NOT NULL)
    OR (kind IN ('COLLECT','REFUND') AND method='CASH' AND fee_payment_id IS NULL))
);
CREATE INDEX ix_deposit_transaction_reader ON deposit_transactions(reader_user_id,id DESC);
CREATE TABLE deposit_entries (
 transaction_id BIGINT NOT NULL REFERENCES deposit_transactions(id) ON DELETE RESTRICT,
 account_id BIGINT NOT NULL REFERENCES deposit_accounts(id) ON DELETE RESTRICT,
 amount_vnd BIGINT NOT NULL CHECK(amount_vnd>0),
 PRIMARY KEY(transaction_id,account_id)
);
CREATE INDEX ix_deposit_entry_account ON deposit_entries(account_id,transaction_id);
-- A real fee receipt distinguishes an offset from cash; old receipts remain CASH.
ALTER TABLE fee_payments ADD COLUMN funding_source VARCHAR(20) NOT NULL DEFAULT 'CASH'
 CHECK(funding_source IN ('CASH','DEPOSIT_OFFSET'));

CREATE FUNCTION guard_deposit_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Deposit history is immutable; use a recorded adjustment'; END; $$;
DO $$ DECLARE name TEXT; BEGIN
 FOREACH name IN ARRAY ARRAY['deposit_policies','deposit_accounts','deposit_handovers','deposit_transactions','deposit_entries'] LOOP
  EXECUTE format('CREATE TRIGGER protect_deposit_history BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION guard_deposit_history()',name);
  EXECUTE format('CREATE TRIGGER protect_deposit_truncate BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION guard_deposit_history()',name);
 END LOOP;
END; $$;

CREATE FUNCTION guard_deposit_entry() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE a deposit_accounts%ROWTYPE; t deposit_transactions%ROWTYPE; collected BIGINT; spent BIGINT; item BIGINT;
BEGIN
 SELECT * INTO a FROM deposit_accounts WHERE id=NEW.account_id FOR UPDATE;
 SELECT * INTO t FROM deposit_transactions WHERE id=NEW.transaction_id;
 IF t.created_txid<>txid_current() THEN RAISE EXCEPTION 'Cannot extend a completed deposit transaction'; END IF;
 IF a.reader_user_id<>t.reader_user_id THEN RAISE EXCEPTION 'Deposit owner mismatch'; END IF;
 SELECT COALESCE(sum(e.amount_vnd) FILTER(WHERE x.kind='COLLECT'),0),
        COALESCE(sum(e.amount_vnd) FILTER(WHERE x.kind<>'COLLECT'),0) INTO collected,spent
 FROM deposit_entries e JOIN deposit_transactions x ON x.id=e.transaction_id WHERE e.account_id=a.id;
 SELECT item_id INTO item FROM deposit_handovers WHERE account_id=a.id;
 IF t.kind='COLLECT' THEN
  IF collected<>0 OR NEW.amount_vnd<>a.required_vnd OR item IS NOT NULL THEN RAISE EXCEPTION 'Deposit already collected or amount changed'; END IF;
 ELSE
  IF NEW.amount_vnd>collected-spent THEN RAISE EXCEPTION 'Deposit balance exceeded'; END IF;
  IF item IS NOT NULL AND NOT EXISTS(SELECT 1 FROM loan_items WHERE id=item AND returned_at IS NOT NULL) THEN
   RAISE EXCEPTION 'Deposit remains held until this copy is returned or resolved';
  END IF;
  IF t.kind='DEDUCT' AND (item IS NULL OR NOT EXISTS(
   SELECT 1 FROM fee_payments p WHERE p.id=t.fee_payment_id AND p.reader_user_id=a.reader_user_id
     AND p.funding_source='DEPOSIT_OFFSET'
  )) THEN RAISE EXCEPTION 'Deposit deduction requires its fee receipt'; END IF;
  IF t.kind='DEDUCT' AND NEW.amount_vnd<>(SELECT COALESCE(sum(pi.collected_amount_vnd),0)
      FROM fee_payment_items pi JOIN reader_fees f ON f.id=pi.reader_fee_id
      WHERE pi.payment_id=t.fee_payment_id AND (f.return_item_id=item OR f.compensation_item_id=item)) THEN
   RAISE EXCEPTION 'Deposit deduction must match this copy fee allocations';
  END IF;
  IF t.kind='REFUND' AND item IS NOT NULL AND EXISTS(SELECT 1 FROM reader_fees f
      WHERE (f.return_item_id=item OR f.compensation_item_id=item)
      AND f.amount_vnd>f.paid_amount_vnd+f.waived_amount_vnd
      AND NOT EXISTS(SELECT 1 FROM book_incidents i JOIN book_incident_adjustments x ON x.incident_id=i.id
          WHERE i.compensation_fee_id=f.id AND x.status='CONFIRMED')) THEN
   RAISE EXCEPTION 'Settle confirmed obligations before refund';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER validate_deposit_entry BEFORE INSERT ON deposit_entries FOR EACH ROW EXECUTE FUNCTION guard_deposit_entry();

CREATE FUNCTION validate_deposit_transaction_total() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE total BIGINT;
BEGIN
 SELECT COALESCE(sum(amount_vnd),0) INTO total FROM deposit_entries WHERE transaction_id=NEW.id;
 IF total<>NEW.total_vnd THEN RAISE EXCEPTION 'Deposit transaction allocations do not match total'; END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER deposit_transaction_total AFTER INSERT ON deposit_transactions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_deposit_transaction_total();

CREATE FUNCTION attach_funded_deposit_at_handover() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy BIGINT; reader BIGINT; account BIGINT;
BEGIN
 -- Same lock as policy activation, and no retrospective requirement for existing items.
 PERFORM id FROM library_overdue_fee_settings WHERE id=1 FOR SHARE;
 SELECT id INTO policy FROM deposit_policies WHERE effective_at<=clock_timestamp() ORDER BY effective_at DESC,id DESC LIMIT 1;
 IF policy IS NULL THEN RETURN NEW; END IF;
 SELECT borrower_user_id INTO reader FROM loans WHERE id=NEW.loan_id;
 SELECT a.id INTO account FROM deposit_accounts a
 WHERE a.reader_user_id=reader AND a.copy_id=NEW.book_copy_id
   AND NOT EXISTS(SELECT 1 FROM deposit_handovers h WHERE h.account_id=a.id)
   AND a.required_vnd=(SELECT COALESCE(sum(CASE WHEN t.kind='COLLECT' THEN e.amount_vnd ELSE -e.amount_vnd END),0)
        FROM deposit_entries e JOIN deposit_transactions t ON t.id=e.transaction_id WHERE e.account_id=a.id)
 ORDER BY a.id LIMIT 1 FOR UPDATE OF a;
 IF account IS NULL THEN RAISE EXCEPTION 'DEPOSIT_REQUIRED: Confirm full deposit collection before handover'; END IF;
 INSERT INTO deposit_handovers(account_id,item_id) VALUES(account,NEW.id);
 RETURN NEW;
END; $$;
CREATE TRIGGER require_funded_deposit AFTER INSERT ON loan_items FOR EACH ROW EXECUTE FUNCTION attach_funded_deposit_at_handover();
