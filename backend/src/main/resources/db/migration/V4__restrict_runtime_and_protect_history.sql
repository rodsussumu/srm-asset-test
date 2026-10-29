DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'srm_runtime') THEN
        CREATE ROLE srm_runtime NOLOGIN;
    END IF;
END;
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON settlements, fx_rates, idempotency_records FROM PUBLIC, srm_runtime;
GRANT USAGE ON SCHEMA public TO srm_runtime;
GRANT SELECT, INSERT ON assignors, receivables, fx_rates, settlements, idempotency_records TO srm_runtime;
GRANT UPDATE (status, version) ON receivables TO srm_runtime;
GRANT UPDATE (status_code, response_body, location, settlement_id, completed_at) ON idempotency_records TO srm_runtime;

CREATE FUNCTION protect_confirmed_idempotency() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Idempotency history is immutable' USING ERRCODE = '42501';
    END IF;
    IF OLD.status_code IS NOT NULL
        OR (NEW.actor_id, NEW.route, NEW.key, NEW.request_hash, NEW.created_at)
            IS DISTINCT FROM (OLD.actor_id, OLD.route, OLD.key, OLD.request_hash, OLD.created_at) THEN
        RAISE EXCEPTION 'Confirmed idempotency result is immutable' USING ERRCODE = '42501';
    END IF;
    IF NEW.status_code IS DISTINCT FROM 201 THEN
        RAISE EXCEPTION 'Reservation must be completed' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER idempotency_immutable_result BEFORE UPDATE OR DELETE ON idempotency_records
FOR EACH ROW EXECUTE FUNCTION protect_confirmed_idempotency();
