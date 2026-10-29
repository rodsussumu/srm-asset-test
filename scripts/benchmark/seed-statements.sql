-- Synthetic statement workload. Use a disposable migrated database, never production.
-- Stable UUID namespaces make reruns append-only and idempotent. Does not exercise the confirmation API.
BEGIN;
INSERT INTO assignors (id, name, created_at)
SELECT md5('statement-benchmark-assignor-' || g)::uuid, 'Benchmark assignor ' || g,
       TIMESTAMPTZ '2025-12-31T12:00:00Z'
FROM generate_series(1,10) AS g ON CONFLICT (id) DO NOTHING;

INSERT INTO fx_rates (id, pair, rate, origin, effective_at, recorded_at)
SELECT md5('statement-benchmark-fx-' || g)::uuid, 'USD/BRL', 5.4321, 'benchmark-mock',
       TIMESTAMPTZ '2026-01-01T11:00:00Z' + g * INTERVAL '1 day',
       TIMESTAMPTZ '2026-01-01T11:00:00Z' + g * INTERVAL '1 day'
FROM generate_series(0,179) AS g ON CONFLICT DO NOTHING;

WITH source AS (
    SELECT g, TIMESTAMPTZ '2026-01-01T12:00:00Z' + ((g-1)%180) * INTERVAL '1 day'
           + ((g-1)/180) * INTERVAL '1 minute' AS at
    FROM generate_series(1,10000) AS g
)
INSERT INTO receivables (id,assignor_id,face_value,due_date,type,payment_currency,status,version,created_at)
SELECT md5('statement-benchmark-receivable-' || g)::uuid,
       md5('statement-benchmark-assignor-' || (((g-1)%10)+1))::uuid,
       100000.00, (at AT TIME ZONE 'America/Sao_Paulo')::date + 90, 'DUPLICATA',
       CASE WHEN g%2 = 0 THEN 'BRL' ELSE 'USD' END,
       CASE WHEN g<=5000 THEN 'SETTLED' ELSE 'AVAILABLE' END,
       CASE WHEN g<=5000 THEN 1 ELSE 0 END, at - INTERVAL '1 hour'
FROM source ON CONFLICT (id) DO NOTHING;

WITH source AS (
    SELECT g, TIMESTAMPTZ '2026-01-01T12:00:00Z' + ((g-1)%180) * INTERVAL '1 day'
           + ((g-1)/180) * INTERVAL '1 minute' AS at
    FROM generate_series(1,5000) AS g
)
INSERT INTO settlements (id,receivable_id,assignor_id,assignor_name,actor_id,face_value,due_date,type,months,
    operation_date,operation_at,base_rate,spread,present_value_brl,discount_brl,payment_currency,payment_amount,
    fx_rate_id,fx_rate,fx_origin,fx_effective_at,fx_recorded_at,policy_version,correlation_id,settled_at,receivable_version)
SELECT md5('statement-benchmark-settlement-' || g)::uuid, r.id, r.assignor_id, a.name, 'benchmark-operator',
       r.face_value, r.due_date, r.type, 3, (source.at AT TIME ZONE 'America/Sao_Paulo')::date,
       source.at, 0.0100, 0.0150, 92859.94, 7140.06, r.payment_currency,
       CASE WHEN r.payment_currency='BRL' THEN 92859.94 ELSE 17094.67 END,
       f.id, f.rate, f.origin, f.effective_at, f.recorded_at, 'monthly-ceil-30d-v1',
       md5('statement-benchmark-correlation-' || g)::uuid, source.at, 1
FROM source
JOIN receivables r ON r.id=md5('statement-benchmark-receivable-' || g)::uuid
JOIN assignors a ON a.id=r.assignor_id
LEFT JOIN fx_rates f ON r.payment_currency='USD' AND f.id=md5('statement-benchmark-fx-' || ((g-1)%180))::uuid
ON CONFLICT (id) DO NOTHING;
COMMIT;
