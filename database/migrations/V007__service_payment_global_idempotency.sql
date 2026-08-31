-- service_payment is owned by order-service. A request key is unique per user,
-- even when a client accidentally targets a different order on retry.
ALTER TABLE service_payment
  ADD UNIQUE KEY uk_service_payment_request (user_id, client_request_id);
