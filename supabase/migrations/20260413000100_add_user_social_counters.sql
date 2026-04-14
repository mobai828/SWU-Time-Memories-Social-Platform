alter table if exists public.user_accounts
    add column if not exists follower_count integer not null default 0,
    add column if not exists following_count integer not null default 0,
    add column if not exists likes_received_count integer not null default 0;
