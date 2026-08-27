-- Payment idempotency keys are unique within a user, regardless of order.
-- V001 is immutable; this migration aligns the executable schema with the
-- service contract and adds the PROCESSING state required by the payment Saga.

ALTER TABLE payment_record
  DROP INDEX uk_payment_request,
  ADD UNIQUE KEY uk_payment_request (user_id, client_request_id);

ALTER TABLE payment_record
  DROP CHECK ck_payment_status,
  ADD CONSTRAINT ck_payment_status CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'));
