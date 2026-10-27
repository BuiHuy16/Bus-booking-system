-- Phase 1 keeps all integrity constraints and business uniqueness rules.
-- Only the four query acceleration indexes introduced by V14/V15 are deferred.
-- Refuse to remove an index repurposed for uniqueness or replication by schema drift.
DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relnamespace = 'public'::regnamespace
          AND c.relname IN (
              'idx_routes_destination_id', 'idx_trips_route_id',
              'idx_vehicles_operator_id', 'idx_trips_vehicle_id'
          )
          AND (i.indisunique OR i.indisprimary OR i.indisreplident OR i.indisclustered)
    ) THEN
        RAISE EXCEPTION 'Cannot defer catalog indexes: an index has an integrity, replication or clustering role';
    END IF;
END;
$migration$;

-- Flyway runs this migration transactionally. Fail rather than mask missing indexes
-- or remove dependent objects; do not use IF EXISTS or CASCADE.
SET LOCAL lock_timeout = '5s';
DROP INDEX public.idx_routes_destination_id;
DROP INDEX public.idx_trips_route_id;
DROP INDEX public.idx_vehicles_operator_id;
DROP INDEX public.idx_trips_vehicle_id;
