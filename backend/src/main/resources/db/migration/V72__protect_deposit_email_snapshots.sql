CREATE FUNCTION protect_deposit_email_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.financial_snapshot IS NOT NULL THEN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Financial email evidence cannot be deleted'; END IF;
  IF NEW.financial_snapshot IS DISTINCT FROM OLD.financial_snapshot
     OR NEW.domain_event_key IS DISTINCT FROM OLD.domain_event_key
     OR NEW.reader_id IS DISTINCT FROM OLD.reader_id
     OR NEW.notification_type IS DISTINCT FROM OLD.notification_type THEN
   RAISE EXCEPTION 'Financial email snapshot is immutable';
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER protect_financial_email_evidence BEFORE UPDATE OR DELETE ON ready_pickup_notifications
 FOR EACH ROW EXECUTE FUNCTION protect_deposit_email_snapshot();
