UPDATE bus_operators
SET name = regexp_replace(btrim(name), '[[:space:]]+', ' ', 'g'),
    address = regexp_replace(btrim(address), '[[:space:]]+', ' ', 'g'),
    email = lower(btrim(email)),
    phone = CASE
        WHEN btrim(phone) ~ '^\+84[0-9]{9,10}$' THEN '0' || substring(btrim(phone) FROM 4)
        ELSE btrim(phone)
    END;

ALTER TABLE bus_operators
    ADD CONSTRAINT ck_bus_operators_name_normalized
        CHECK (name = regexp_replace(btrim(name), '[[:space:]]+', ' ', 'g')),
    ADD CONSTRAINT ck_bus_operators_address_normalized
        CHECK (address <> '' AND address = regexp_replace(btrim(address), '[[:space:]]+', ' ', 'g')),
    ADD CONSTRAINT ck_bus_operators_email_normalized
        CHECK (email <> '' AND email = lower(btrim(email))),
    ADD CONSTRAINT ck_bus_operators_phone_normalized
        CHECK (phone ~ '^0([35789][0-9]{8}|2[0-9]{9})$');

CREATE UNIQUE INDEX uk_bus_operators_normalized_name ON bus_operators (lower(name));
CREATE UNIQUE INDEX uk_bus_operators_normalized_email ON bus_operators (email);
