DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM vehicles
        GROUP BY upper(regexp_replace(license_plate, '[[:space:]]+', '', 'g'))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot normalize vehicles: duplicate normalized license plates exist';
    END IF;
END $$;

UPDATE vehicles
SET license_plate = upper(regexp_replace(license_plate, '[[:space:]]+', '', 'g')),
    vehicle_type = regexp_replace(btrim(vehicle_type), '[[:space:]]+', ' ', 'g');

ALTER TABLE vehicles
    ADD CONSTRAINT ck_vehicles_license_plate_normalized
        CHECK (license_plate <> ''
            AND license_plate = upper(regexp_replace(license_plate, '[[:space:]]+', '', 'g'))),
    ADD CONSTRAINT ck_vehicles_type_normalized
        CHECK (vehicle_type <> ''
            AND vehicle_type = regexp_replace(btrim(vehicle_type), '[[:space:]]+', ' ', 'g'));

CREATE INDEX idx_vehicles_operator_id ON vehicles (operator_id);
CREATE INDEX idx_trips_vehicle_id ON trips (vehicle_id);
