-- 创建站点设置表
CREATE TABLE site_settings (
    key VARCHAR(50) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 插入默认背景图
INSERT INTO site_settings (key, value) VALUES 
('login_bg_url', 'https://images.unsplash.com/photo-1495344517868-8ebaf0a2044a?q=80&w=2560&auto=format&fit=crop');

-- 允许所有人读取
GRANT SELECT ON site_settings TO anon;
GRANT SELECT ON site_settings TO authenticated;

-- 允许管理员更新
ALTER TABLE site_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Settings are viewable by all" ON site_settings FOR SELECT USING (true);
CREATE POLICY "Admins can update settings" ON site_settings FOR UPDATE USING (
  EXISTS (
    SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
  )
);
CREATE POLICY "Admins can insert settings" ON site_settings FOR INSERT WITH CHECK (
  EXISTS (
    SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
  )
);
