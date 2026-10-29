CREATE TABLE fx_rates (
    id UUID PRIMARY KEY,
    pair VARCHAR(7) NOT NULL CHECK (pair = 'USD/BRL'),
    rate NUMERIC(16,8) NOT NULL CHECK (rate > 0),
    origin VARCHAR(100) NOT NULL CHECK (length(trim(origin)) > 0),
    effective_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fx_rates_pair_effective UNIQUE (pair, effective_at)
);
CREATE INDEX idx_fx_rates_selection ON fx_rates (pair, effective_at DESC, recorded_at);
