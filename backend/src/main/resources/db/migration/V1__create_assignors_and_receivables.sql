CREATE TABLE assignors (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT assignors_name_valid CHECK (char_length(btrim(name)) BETWEEN 1 AND 200)
);

CREATE TABLE receivables (
    id UUID PRIMARY KEY,
    assignor_id UUID NOT NULL REFERENCES assignors(id),
    face_value NUMERIC(18,2) NOT NULL,
    face_currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    due_date DATE NOT NULL,
    type VARCHAR(16) NOT NULL,
    payment_currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT receivables_face_positive CHECK (face_value > 0),
    CONSTRAINT receivables_face_brl CHECK (face_currency = 'BRL'),
    CONSTRAINT receivables_type_known CHECK (type IN ('DUPLICATA', 'CHEQUE')),
    CONSTRAINT receivables_payment_currency_known CHECK (payment_currency IN ('BRL', 'USD')),
    CONSTRAINT receivables_status_known CHECK (status IN ('AVAILABLE', 'SETTLED')),
    CONSTRAINT receivables_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX receivables_assignor_idx ON receivables (assignor_id);
CREATE INDEX receivables_created_idx ON receivables (created_at DESC, id DESC);
CREATE INDEX receivables_status_created_idx ON receivables (status, created_at DESC, id DESC);
