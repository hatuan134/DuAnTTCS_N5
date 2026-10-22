-- Reuse the pickup outbox/worker/SMTP. No historical email backfill.
ALTER TABLE ready_pickup_notifications ALTER COLUMN reservation_id DROP NOT NULL;
ALTER TABLE ready_pickup_notifications ADD COLUMN domain_event_key TEXT UNIQUE,
 ADD COLUMN financial_snapshot JSONB;
ALTER TABLE ready_pickup_notifications DROP CONSTRAINT ck_pickup_notification_window;
ALTER TABLE ready_pickup_notifications ADD CONSTRAINT ck_pickup_notification_window CHECK (
 (notification_type='READY_FOR_PICKUP' AND reservation_id IS NOT NULL AND send_before IS NOT NULL AND send_before=event_at+INTERVAL '5 minutes')
 OR (notification_type='LAST_DAY_REMINDER' AND reservation_id IS NOT NULL AND pickup_deadline IS NOT NULL AND send_before IS NOT NULL AND send_before=pickup_deadline AND event_at<send_before)
 OR (notification_type='PICKUP_EXPIRED_CANCELLATION' AND reservation_id IS NOT NULL AND send_before IS NULL AND book_id IS NOT NULL
     AND cancellation_reason='Đã huỷ do quá hạn nhận' AND cancelled_at IS NOT NULL AND pickup_deadline IS NOT NULL AND cancelled_at>pickup_deadline)
 OR (notification_type IN ('DEPOSIT_RESERVATION','DEPOSIT_LOAN','DEPOSIT_RETURN','DEPOSIT_TRANSACTION')
     AND financial_snapshot IS NOT NULL AND domain_event_key IS NOT NULL AND send_before=event_at+INTERVAL '24 hours')
);
CREATE FUNCTION deposit_email_account(account BIGINT) RETURNS JSONB LANGUAGE sql STABLE AS $$
 SELECT jsonb_build_object('bookTitle',b.title,'barcode',c.barcode,'coverPrice',a.cover_price,'percentage',a.percentage,
  'requiredVnd',a.required_vnd,'collectedVnd',COALESCE(e.collected,0),'refundedVnd',COALESCE(e.refunded,0),
  'deductedVnd',COALESCE(e.deducted,0),'heldVnd',COALESCE(e.collected,0)-COALESCE(e.refunded,0)-COALESCE(e.deducted,0),
  'loanNumber',l.loan_number,'borrowedAt',li.borrowed_at,'dueAt',li.due_date,'returnedAt',li.returned_at,
  'overdueVnd',COALESCE(f.overdue,0),'compensationVnd',COALESCE(f.compensation,0),'outstandingVnd',COALESCE(f.outstanding,0),
  'transactions',COALESCE(e.codes,''))
 FROM deposit_accounts a JOIN book_copies c ON c.id=a.copy_id JOIN books b ON b.id=c.book_id
 LEFT JOIN deposit_handovers h ON h.account_id=a.id LEFT JOIN loan_items li ON li.id=h.item_id LEFT JOIN loans l ON l.id=li.loan_id
 LEFT JOIN LATERAL(SELECT sum(x.amount_vnd) FILTER(WHERE t.kind='COLLECT') collected,sum(x.amount_vnd) FILTER(WHERE t.kind='REFUND') refunded,
  sum(x.amount_vnd) FILTER(WHERE t.kind='DEDUCT') deducted,string_agg(t.transaction_number,', ' ORDER BY t.id) codes
  FROM deposit_entries x JOIN deposit_transactions t ON t.id=x.transaction_id WHERE x.account_id=a.id) e ON true
 LEFT JOIN LATERAL(SELECT sum(amount_vnd) FILTER(WHERE return_item_id=li.id) overdue,
  sum(amount_vnd) FILTER(WHERE compensation_item_id=li.id) compensation,
  sum(amount_vnd-paid_amount_vnd-waived_amount_vnd) outstanding FROM reader_fees f WHERE (return_item_id=li.id OR compensation_item_id=li.id)
  AND NOT EXISTS(SELECT 1 FROM book_incidents i JOIN book_incident_adjustments x ON x.incident_id=i.id WHERE i.compensation_fee_id=f.id AND x.status='CONFIRMED')) f ON true
 WHERE a.id=account;
$$;
CREATE FUNCTION enqueue_deposit_email(reader BIGINT,kind TEXT,key TEXT,payload JSONB,reservation BIGINT DEFAULT NULL) RETURNS void LANGUAGE plpgsql AS $$
DECLARE happened TIMESTAMPTZ:=clock_timestamp();
BEGIN
 INSERT INTO ready_pickup_notifications(reservation_id,notification_type,reader_id,recipient_email,reader_name,book_title,
  event_at,send_before,next_attempt_at,domain_event_key,financial_snapshot)
 SELECT reservation,kind,u.id,u.email,u.full_name,'Thông tin mượn trả và tiền cọc',happened,happened+INTERVAL '24 hours',happened,key,payload
 FROM users u WHERE u.id=reader ON CONFLICT(domain_event_key) DO NOTHING;
END; $$;
CREATE FUNCTION enqueue_deposit_loan_email() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE reader BIGINT; payload JSONB; number TEXT;
BEGIN
 IF TG_OP='INSERT' THEN
  SELECT borrower_user_id,loan_number INTO reader,number FROM loans WHERE id=NEW.loan_id;
  SELECT jsonb_agg(deposit_email_account(h.account_id) ORDER BY h.item_id) INTO payload FROM deposit_handovers h JOIN loan_items li ON li.id=h.item_id WHERE li.loan_id=NEW.loan_id;
  IF payload IS NOT NULL THEN PERFORM enqueue_deposit_email(reader,'DEPOSIT_LOAN','loan:'||NEW.loan_id,
   jsonb_build_object('loanNumber',number,'items',payload)); END IF;
 ELSE
  IF OLD.returned_at IS NULL AND NEW.returned_at IS NOT NULL THEN
   SELECT borrower_user_id INTO reader FROM loans WHERE id=NEW.loan_id;
   SELECT deposit_email_account(account_id) INTO payload FROM deposit_handovers WHERE item_id=NEW.id;
   IF payload IS NOT NULL THEN PERFORM enqueue_deposit_email(reader,'DEPOSIT_RETURN','return:'||NEW.id,
    jsonb_build_object('items',jsonb_build_array(payload))); END IF;
  END IF;
 END IF;
 RETURN NEW;
END; $$;
-- Deferred snapshots see all items/fees recorded in the enclosing transaction.
CREATE CONSTRAINT TRIGGER deposit_loan_email AFTER INSERT OR UPDATE ON loan_items
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enqueue_deposit_loan_email();
CREATE FUNCTION enqueue_deposit_transaction_email() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE items JSONB;
BEGIN
 SELECT jsonb_agg(deposit_email_account(account_id) ORDER BY account_id) INTO items FROM deposit_entries WHERE transaction_id=NEW.id;
 PERFORM enqueue_deposit_email(NEW.reader_user_id,'DEPOSIT_TRANSACTION','deposit:'||NEW.id,
   jsonb_build_object('kind',NEW.kind,'transactionNumber',NEW.transaction_number,'totalVnd',NEW.total_vnd,'recordedAt',NEW.recorded_at,'items',items));
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER deposit_transaction_email AFTER INSERT ON deposit_transactions
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enqueue_deposit_transaction_email();
CREATE FUNCTION deposit_reservation_estimate(reservation BIGINT) RETURNS JSONB LANGUAGE sql STABLE AS $$
 SELECT jsonb_build_object('bookTitle',b.title,'barcode',c.barcode,'coverPrice',c.cover_price,'percentage',p.percentage,
  'requiredVnd',CASE WHEN c.cover_price>0 AND p.percentage>0 THEN round(c.cover_price*p.percentage/100,0) END,
  'policyActive',p.id IS NOT NULL)
 FROM book_reservations r JOIN books b ON b.id=r.book_id LEFT JOIN book_copies c ON c.id=r.book_copy_id
 LEFT JOIN LATERAL(SELECT id,percentage FROM deposit_policies WHERE effective_at<=statement_timestamp() ORDER BY effective_at DESC,id DESC LIMIT 1) p ON true
 WHERE r.id=reservation;
$$;
CREATE FUNCTION enqueue_deposit_reservation_email() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE item JSONB;
BEGIN
 item:=deposit_reservation_estimate(NEW.id);
 IF (item->>'policyActive')::boolean THEN
  PERFORM enqueue_deposit_email(NEW.reader_id,'DEPOSIT_RESERVATION','reservation:'||NEW.id,
   jsonb_build_object('items',jsonb_build_array(item)),NEW.id);
 END IF;
 RETURN NEW;
END; $$;
CREATE CONSTRAINT TRIGGER deposit_reservation_email AFTER INSERT ON book_reservations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enqueue_deposit_reservation_email();
