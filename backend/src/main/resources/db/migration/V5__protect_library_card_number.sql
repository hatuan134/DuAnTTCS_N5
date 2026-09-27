-- S1-04: Mã thẻ được sinh tự động và tuyệt đối không được sửa sau khi cấp.

CREATE OR REPLACE FUNCTION prevent_library_card_number_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.card_number IS DISTINCT FROM OLD.card_number THEN
        RAISE EXCEPTION 'Library card number cannot be changed after issuance';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_library_cards_immutable_number ON library_cards;

CREATE TRIGGER trg_library_cards_immutable_number
BEFORE UPDATE OF card_number ON library_cards
FOR EACH ROW
EXECUTE FUNCTION prevent_library_card_number_change();
