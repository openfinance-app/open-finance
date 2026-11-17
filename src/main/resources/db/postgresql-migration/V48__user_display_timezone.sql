-- Device-local display remains the default; retain explicitly configured regional zones.
ALTER TABLE user_settings ADD COLUMN use_device_timezone BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE user_settings SET use_device_timezone = FALSE WHERE timezone <> 'UTC';
