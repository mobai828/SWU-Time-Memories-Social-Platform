alter table if exists public.user_accounts
    add column if not exists signature varchar(48);
