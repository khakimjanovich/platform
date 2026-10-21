-- As ecom-infra's postgres/init.sql does it: one schema and one user per module, each user owning
-- only its own schema.
DO $$
DECLARE m text;
BEGIN
  FOREACH m IN ARRAY ARRAY['a', 'b', 'c'] LOOP
    EXECUTE format('CREATE USER %I WITH PASSWORD %L', m || '_user', 'test');
    EXECUTE format('CREATE SCHEMA %I AUTHORIZATION %I', m, m || '_user');
    EXECUTE format('ALTER ROLE %I SET search_path = %I', m || '_user', m);
  END LOOP;
END $$;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
