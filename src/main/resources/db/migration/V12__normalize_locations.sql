UPDATE locations
SET name = regexp_replace(btrim(name), '[[:space:]]+', ' ', 'g'),
    province = regexp_replace(btrim(province), '[[:space:]]+', ' ', 'g'),
    address = regexp_replace(btrim(address), '[[:space:]]+', ' ', 'g');

ALTER TABLE locations
    ADD CONSTRAINT ck_locations_name_normalized
        CHECK (name = regexp_replace(btrim(name), '[[:space:]]+', ' ', 'g')),
    ADD CONSTRAINT ck_locations_province_normalized
        CHECK (province = regexp_replace(btrim(province), '[[:space:]]+', ' ', 'g')),
    ADD CONSTRAINT ck_locations_address_normalized
        CHECK (address = regexp_replace(btrim(address), '[[:space:]]+', ' ', 'g'));

CREATE UNIQUE INDEX uk_locations_normalized_identity
    ON locations (lower(name), lower(province), lower(address));
