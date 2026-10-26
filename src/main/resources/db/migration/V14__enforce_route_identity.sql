DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM routes
        GROUP BY origin_id, destination_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce route identity: duplicate origin/destination pairs exist';
    END IF;
END $$;

ALTER TABLE routes
    ADD CONSTRAINT uk_routes_origin_destination UNIQUE (origin_id, destination_id);

CREATE INDEX idx_routes_destination_id ON routes (destination_id);
CREATE INDEX idx_trips_route_id ON trips (route_id);
