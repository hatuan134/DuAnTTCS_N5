-- Additive indexes only; existing debts, payments and receipts are unchanged.
CREATE INDEX ix_fee_payment_items_fee ON fee_payment_items(reader_fee_id, payment_id DESC);
CREATE INDEX ix_reader_fees_created ON reader_fees(created_at DESC, id DESC);
