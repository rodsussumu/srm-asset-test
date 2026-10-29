CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    receivable_id UUID NOT NULL REFERENCES receivables(id),
    assignor_id UUID NOT NULL REFERENCES assignors(id),
    assignor_name VARCHAR(200) NOT NULL,
    actor_id VARCHAR(200) NOT NULL CHECK (length(btrim(actor_id)) > 0),
    face_value NUMERIC(18,2) NOT NULL CHECK (face_value > 0),
    due_date DATE NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('DUPLICATA', 'CHEQUE')),
    months INTEGER NOT NULL CHECK (months BETWEEN 1 AND 36),
    operation_date DATE NOT NULL,
    operation_at TIMESTAMPTZ NOT NULL,
    base_rate NUMERIC(10,8) NOT NULL CHECK (base_rate >= 0),
    spread NUMERIC(10,8) NOT NULL CHECK (spread >= 0),
    present_value_brl NUMERIC(18,2) NOT NULL CHECK (present_value_brl >= 0),
    discount_brl NUMERIC(18,2) NOT NULL CHECK (discount_brl >= 0),
    payment_currency VARCHAR(3) NOT NULL CHECK (payment_currency IN ('BRL', 'USD')),
    payment_amount NUMERIC(30,2) NOT NULL CHECK (payment_amount >= 0),
    fx_rate_id UUID REFERENCES fx_rates(id),
    fx_rate NUMERIC(16,8),
    fx_origin VARCHAR(100),
    fx_effective_at TIMESTAMPTZ,
    fx_recorded_at TIMESTAMPTZ,
    policy_version VARCHAR(100) NOT NULL,
    correlation_id UUID NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL,
    receivable_version BIGINT NOT NULL CHECK (receivable_version > 0),
    CONSTRAINT uk_settlements_receivable UNIQUE (receivable_id),
    CONSTRAINT settlements_brl_balance CHECK (face_value = present_value_brl + discount_brl),
    CONSTRAINT settlements_fx_snapshot CHECK (
        (payment_currency = 'BRL' AND payment_amount = present_value_brl AND fx_rate_id IS NULL
            AND fx_rate IS NULL AND fx_origin IS NULL AND fx_effective_at IS NULL AND fx_recorded_at IS NULL)
        OR
        (payment_currency = 'USD' AND fx_rate_id IS NOT NULL AND fx_rate > 0 AND fx_rate IS NOT NULL
            AND fx_origin IS NOT NULL AND fx_effective_at IS NOT NULL AND fx_recorded_at IS NOT NULL)
    )
);

CREATE TABLE idempotency_records (
    actor_id VARCHAR(200) NOT NULL CHECK (length(btrim(actor_id)) > 0),
    route VARCHAR(100) NOT NULL,
    key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    status_code INTEGER,
    response_body TEXT,
    location VARCHAR(200),
    settlement_id UUID REFERENCES settlements(id),
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (actor_id, route, key),
    CONSTRAINT idempotency_result_complete CHECK (
        (status_code IS NULL AND response_body IS NULL AND location IS NULL AND settlement_id IS NULL AND completed_at IS NULL)
        OR
        (status_code = 201 AND status_code IS NOT NULL AND response_body IS NOT NULL AND location IS NOT NULL
            AND settlement_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT idempotency_json_object CHECK (response_body IS NULL OR jsonb_typeof(response_body::jsonb) = 'object')
);

-- A pending reservation may exist only inside its transaction, never after commit.
CREATE FUNCTION require_completed_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM idempotency_records
        WHERE actor_id = NEW.actor_id AND route = NEW.route AND key = NEW.key AND status_code IS NULL) THEN
        RAISE EXCEPTION 'Incomplete idempotency reservation' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER idempotency_must_complete
AFTER INSERT OR UPDATE ON idempotency_records DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION require_completed_idempotency();
