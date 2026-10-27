--
-- PostgreSQL database dump
--

\restrict sNQLcOxecJTrhGUH07zszXauyRwrY2isiM8vXOMFHV0do1VCq5e84nnQxS6bJPX

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: bus_operators; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.bus_operators (id, name, address, phone, email, status, created_at) VALUES (2, 'Hoang Long Express', '123 Trần Duy Hưng, Hà Nội', '0901234567', 'contact@hoanglong.example', 'ACTIVE', '2026-10-27 02:09:09.612448');
INSERT INTO public.bus_operators (id, name, address, phone, email, status, created_at) VALUES (1, 'api bus test', 'Ha Noi', '0912256789', 'bustest@example.com', 'ACTIVE', '2026-10-23 11:50:02.206894');


--
-- Data for Name: locations; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.locations (id, name, province, address) VALUES (1, 'Ben xe My Dinh Test', 'Ha Noi', 'My Dinh');
INSERT INTO public.locations (id, name, province, address) VALUES (2, 'Ben xe Hai Phong Test', 'Hai Phong', 'Hai Phong');
INSERT INTO public.locations (id, name, province, address) VALUES (4, 'Ben xe Thanh Hoa test', 'Thanh Hoa', 'Thanh Hoa');
INSERT INTO public.locations (id, name, province, address) VALUES (3, 'Ben xe Giap Bat test', 'Ha Noi', 'Giap Bat');


--
-- Data for Name: routes; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.routes (id, origin_id, destination_id, estimated_duration_minutes, status) VALUES (1, 1, 2, 120, 'ACTIVE');
INSERT INTO public.routes (id, origin_id, destination_id, estimated_duration_minutes, status) VALUES (2, 3, 4, 361, 'ACTIVE');


--
-- Data for Name: vehicles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.vehicles (id, operator_id, license_plate, vehicle_type, seat_capacity, status, created_at) VALUES (1, 1, 'TEST-1', 'BUS', 4, 'ACTIVE', '2026-10-23 11:50:02.206894');
INSERT INTO public.vehicles (id, operator_id, license_plate, vehicle_type, seat_capacity, status, created_at) VALUES (2, 1, '29B13346', 'BUS', 36, 'ACTIVE', '2026-10-27 02:18:44.272099');
INSERT INTO public.vehicles (id, operator_id, license_plate, vehicle_type, seat_capacity, status, created_at) VALUES (3, 2, '29B12345', 'BUS', 20, 'ACTIVE', '2026-10-27 02:26:24.988336');


--
-- Data for Name: trips; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.trips (id, route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status, created_at) VALUES (2, 1, 1, '2026-11-10 02:00:00', '2026-11-10 14:00:00', 180000.00, 4, 'CANCELLED', '2026-10-24 15:49:42.927142');
INSERT INTO public.trips (id, route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status, created_at) VALUES (1, 1, 1, '2026-10-30 11:50:02.206894', '2026-10-30 13:50:02.206894', 150000.00, 3, 'SCHEDULED', '2026-10-23 11:50:02.206894');
INSERT INTO public.trips (id, route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status, created_at) VALUES (3, 2, 2, '2026-10-29 01:00:00', '2026-10-29 04:30:00', 300000.00, 36, 'CANCELLED', '2026-10-27 09:59:53.225713');
INSERT INTO public.trips (id, route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status, created_at) VALUES (4, 2, 3, '2026-10-29 02:00:00', '2026-10-29 03:35:00', 234000.00, 20, 'CANCELLED', '2026-10-27 10:11:23.634929');
INSERT INTO public.trips (id, route_id, vehicle_id, departure_time, arrival_time, price, available_seats, status, created_at) VALUES (5, 2, 3, '2026-10-29 02:10:00', '2026-10-29 03:35:00', 235000.00, 18, 'SCHEDULED', '2026-10-27 10:14:19.457445');


--
-- Name: bus_operators_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.bus_operators_id_seq', 2, true);


--
-- Name: locations_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.locations_id_seq', 4, true);


--
-- Name: routes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.routes_id_seq', 2, true);


--
-- Name: trips_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.trips_id_seq', 5, true);


--
-- Name: vehicles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.vehicles_id_seq', 3, true);


--
-- PostgreSQL database dump complete
--

\unrestrict sNQLcOxecJTrhGUH07zszXauyRwrY2isiM8vXOMFHV0do1VCq5e84nnQxS6bJPX

