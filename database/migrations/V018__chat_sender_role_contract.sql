-- The merchant service persists the public conversation roles used by the
-- BFF projection. Keep the historical roles valid for already migrated rows.
ALTER TABLE chat_message DROP CHECK ck_chat_sender_role;
ALTER TABLE chat_message ADD CONSTRAINT ck_chat_sender_role
  CHECK (sender_role IN ('USER', 'MERCHANT', 'MERCHANT_AI', 'MERCHANT_ADMIN', 'PLATFORM_ADMIN', 'ASSISTANT'));
