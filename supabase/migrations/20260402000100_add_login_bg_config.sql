INSERT INTO site_settings (key, value)
SELECT
  'login_bg_config',
  json_build_object(
    'images', json_build_array(value),
    'randomEnabled', false,
    'selectedImage', value
  )::text
FROM site_settings
WHERE key = 'login_bg_url'
ON CONFLICT (key) DO NOTHING;
