-- Recheck in a fresh statement after waiting for the account lock: refunds append
-- ledger rows without updating the immutable account row itself.
CREATE OR REPLACE FUNCTION attach_funded_deposit_at_handover() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy BIGINT; reader BIGINT; account BIGINT; required BIGINT; held BIGINT;
BEGIN
 PERFORM id FROM library_overdue_fee_settings WHERE id=1 FOR SHARE;
 SELECT id INTO policy FROM deposit_policies WHERE effective_at<=clock_timestamp() ORDER BY effective_at DESC,id DESC LIMIT 1;
 IF policy IS NULL THEN RETURN NEW; END IF;
 SELECT borrower_user_id INTO reader FROM loans WHERE id=NEW.loan_id;
 SELECT a.id,a.required_vnd INTO account,required FROM deposit_accounts a
 WHERE a.reader_user_id=reader AND a.copy_id=NEW.book_copy_id
   AND NOT EXISTS(SELECT 1 FROM deposit_handovers h WHERE h.account_id=a.id)
   AND a.required_vnd=(SELECT COALESCE(sum(CASE WHEN t.kind='COLLECT' THEN e.amount_vnd ELSE -e.amount_vnd END),0)
        FROM deposit_entries e JOIN deposit_transactions t ON t.id=e.transaction_id WHERE e.account_id=a.id)
 ORDER BY a.id LIMIT 1 FOR UPDATE OF a;
 IF account IS NULL THEN RAISE EXCEPTION 'DEPOSIT_REQUIRED: Confirm full deposit collection before handover'; END IF;
 SELECT COALESCE(sum(CASE WHEN t.kind='COLLECT' THEN e.amount_vnd ELSE -e.amount_vnd END),0) INTO held
 FROM deposit_entries e JOIN deposit_transactions t ON t.id=e.transaction_id WHERE e.account_id=account;
 IF held<>required OR EXISTS(SELECT 1 FROM deposit_handovers WHERE account_id=account) THEN
  RAISE EXCEPTION 'DEPOSIT_CHANGED: Refresh deposit before handover';
 END IF;
 INSERT INTO deposit_handovers(account_id,item_id) VALUES(account,NEW.id);
 RETURN NEW;
END; $$;
