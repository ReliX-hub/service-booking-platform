-- R__seed_dev_data.sql

TRUNCATE reviews, settlements, orders, time_slots, services, providers, users RESTART IDENTITY CASCADE;

INSERT INTO users (email, password_hash, name, phone, role, status) VALUES
    ('admin@servicebooking.com', '$2a$10$dummyhash', 'System Admin', '1234567890', 'ADMIN', 'ACTIVE'),
    ('john@example.com', '$2a$10$dummyhash', 'John Customer', '1111111111', 'CUSTOMER', 'ACTIVE'),
    ('jane@example.com', '$2a$10$dummyhash', 'Jane Customer', '2222222222', 'CUSTOMER', 'ACTIVE'),
    ('bob@hairsalon.com', '$2a$10$dummyhash', 'Bob Provider', '3333333333', 'PROVIDER', 'ACTIVE'),
    ('alice@spa.com', '$2a$10$dummyhash', 'Alice Provider', '4444444444', 'PROVIDER', 'ACTIVE');

INSERT INTO providers (user_id, business_name, description, address, rating, review_count, verified) VALUES
    (4, 'Bob''s Hair Salon', 'Professional haircuts and styling', '123 Main St, City', 4.50, 10, TRUE),
    (5, 'Alice''s Spa', 'Relaxing spa treatments', '456 Oak Ave, Town', 4.80, 25, TRUE);

INSERT INTO services (provider_id, name, description, duration_minutes, price, status) VALUES
    (1, 'Men''s Haircut', 'Classic men''s haircut with styling', 30, 25.00, 'ACTIVE'),
    (1, 'Women''s Haircut', 'Women''s haircut with blow dry', 45, 45.00, 'ACTIVE'),
    (2, 'Swedish Massage', '60-minute relaxing massage', 60, 90.00, 'ACTIVE'),
    (2, 'Facial Treatment', 'Rejuvenating facial', 45, 75.00, 'ACTIVE');

INSERT INTO time_slots (provider_id, start_time, end_time, status)
SELECT 
    p.id,
    (CURRENT_DATE + (d || ' days')::INTERVAL + (h || ' hours')::INTERVAL)::TIMESTAMP WITH TIME ZONE,
    (CURRENT_DATE + (d || ' days')::INTERVAL + ((h + 1) || ' hours')::INTERVAL)::TIMESTAMP WITH TIME ZONE,
    'AVAILABLE'
FROM providers p
CROSS JOIN generate_series(1, 7) AS d
CROSS JOIN generate_series(9, 17) AS h;

INSERT INTO orders (customer_id, provider_id, service_id, status, total_price, notes) VALUES
    (2, 1, 1, 'COMPLETED', 25.00, 'Demo completed order');

INSERT INTO settlements (order_id, total_price, platform_fee, provider_payout, status) VALUES
    (1, 25.00, 2.50, 22.50, 'COMPLETED');
