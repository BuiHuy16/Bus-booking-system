ALTER TABLE roles DROP CONSTRAINT ck_roles_name;
ALTER TABLE roles ADD CONSTRAINT ck_roles_name
    CHECK (name IN ('USER', 'ADMIN', 'SUPER_ADMIN'));

INSERT INTO roles (name) VALUES ('SUPER_ADMIN');

DO $migration$
DECLARE
    super_role_id BIGINT;
BEGIN
    SELECT id INTO STRICT super_role_id
    FROM roles
    WHERE name = 'SUPER_ADMIN';

    EXECUTE format(
        'CREATE UNIQUE INDEX uk_users_single_super_admin
         ON users (role_id) WHERE role_id = %s',
        super_role_id
    );
END;
$migration$;