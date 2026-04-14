create table if not exists public.user_follow_relations (
    id bigserial primary key,
    follower_id bigint not null references public.user_accounts(id) on delete cascade,
    followee_id bigint not null references public.user_accounts(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_follow_relation_pair unique (follower_id, followee_id),
    constraint chk_follow_not_self check (follower_id <> followee_id)
);

create index if not exists idx_user_follow_relations_follower on public.user_follow_relations(follower_id);
create index if not exists idx_user_follow_relations_followee on public.user_follow_relations(followee_id);

create table if not exists public.user_like_relations (
    id bigserial primary key,
    actor_id bigint not null references public.user_accounts(id) on delete cascade,
    target_user_id bigint not null references public.user_accounts(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint uk_user_like_pair unique (actor_id, target_user_id),
    constraint chk_like_not_self check (actor_id <> target_user_id)
);

create index if not exists idx_user_like_relations_actor on public.user_like_relations(actor_id);
create index if not exists idx_user_like_relations_target on public.user_like_relations(target_user_id);
