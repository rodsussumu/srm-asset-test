\set ON_ERROR_STOP on
\getenv app_user DATABASE_USER
\getenv app_password DATABASE_PASSWORD

-- Refuse to turn the database owner or an existing privileged account into the runtime account.
SELECT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'app_user'
        AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
) OR :'app_user' = current_user OR :'app_user' = 'srm_runtime'
  OR EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_roles r ON r.oid = c.relowner WHERE n.nspname = 'public' AND r.rolname = :'app_user'
  ) AS unsafe \gset
\if :unsafe
  \echo 'DATABASE_USER must be a separate unprivileged runtime account.'
  \quit 1
\endif

SELECT 'CREATE ROLE srm_runtime NOLOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'srm_runtime') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'app_user', :'app_password') \gexec
SELECT format('GRANT srm_runtime TO %I', :'app_user') \gexec
