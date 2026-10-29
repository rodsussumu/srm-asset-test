CREATE INDEX settlements_settled_idx ON settlements (settled_at DESC, id DESC);
CREATE INDEX settlements_assignor_settled_idx ON settlements (assignor_id, settled_at DESC, id DESC);
CREATE INDEX settlements_currency_settled_idx ON settlements (payment_currency, settled_at DESC, id DESC);
