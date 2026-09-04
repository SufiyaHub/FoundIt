-- FoundIt notification preferences and item-status mirror schema.
-- Run this in the Supabase SQL editor after the base user/application schema.

create extension if not exists pgcrypto;

create table if not exists public.app_notification_preferences (
    user_id text primary key,
    email text,
    push_enabled boolean not null default true,
    notify_finder_confirmation boolean not null default true,
    notify_item_returned boolean not null default true,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.item_status_notifications (
    id uuid primary key default gen_random_uuid(),
    user_id text not null,
    email text,
    item_id text,
    item_title text,
    notification_type text not null check (
        notification_type in ('finder_confirmation', 'item_returned')
    ),
    source_status text,
    should_notify boolean not null default true,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.app_notification_preferences enable row level security;
alter table public.item_status_notifications enable row level security;

drop policy if exists "Notification preferences are written through RPC" on public.app_notification_preferences;
create policy "Notification preferences are written through RPC"
on public.app_notification_preferences
for all
to anon, authenticated
using (false)
with check (false);

drop policy if exists "Item status notifications are written through RPC" on public.item_status_notifications;
create policy "Item status notifications are written through RPC"
on public.item_status_notifications
for all
to anon, authenticated
using (false)
with check (false);

create index if not exists item_status_notifications_user_created_idx
on public.item_status_notifications (user_id, created_at desc);

create index if not exists item_status_notifications_type_created_idx
on public.item_status_notifications (notification_type, created_at desc);

create or replace function public.set_notification_preferences_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_app_notification_preferences_updated_at
on public.app_notification_preferences;
create trigger set_app_notification_preferences_updated_at
before update on public.app_notification_preferences
for each row execute function public.set_notification_preferences_updated_at();

create or replace function public.upsert_notification_preferences(
    p_user_id text,
    p_email text default null,
    p_push_enabled boolean default true,
    p_notify_finder_confirmation boolean default true,
    p_notify_item_returned boolean default true,
    p_metadata jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id text := nullif(p_user_id, '');
begin
    if v_user_id is null then
        raise exception 'user id is required';
    end if;

    insert into public.app_notification_preferences (
        user_id,
        email,
        push_enabled,
        notify_finder_confirmation,
        notify_item_returned,
        metadata
    )
    values (
        v_user_id,
        nullif(p_email, ''),
        coalesce(p_push_enabled, true),
        coalesce(p_notify_finder_confirmation, true),
        coalesce(p_notify_item_returned, true),
        coalesce(p_metadata, '{}'::jsonb)
    )
    on conflict (user_id) do update
    set
        email = coalesce(excluded.email, public.app_notification_preferences.email),
        push_enabled = excluded.push_enabled,
        notify_finder_confirmation = excluded.notify_finder_confirmation,
        notify_item_returned = excluded.notify_item_returned,
        metadata = excluded.metadata,
        updated_at = now();
end;
$$;

create or replace function public.mirror_item_status_notification(
    p_user_id text,
    p_email text default null,
    p_item_id text default null,
    p_item_title text default null,
    p_notification_type text default 'finder_confirmation',
    p_source_status text default null,
    p_payload jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
    v_user_id text := nullif(p_user_id, '');
    v_type text := lower(replace(coalesce(nullif(p_notification_type, ''), 'finder_confirmation'), '-', '_'));
    v_should_notify boolean := true;
begin
    if v_user_id is null then
        raise exception 'user id is required';
    end if;

    if v_type not in ('finder_confirmation', 'item_returned') then
        raise exception 'unsupported notification type: %', v_type;
    end if;

    select
        case
            when not push_enabled then false
            when v_type = 'finder_confirmation' then notify_finder_confirmation
            when v_type = 'item_returned' then notify_item_returned
            else true
        end
    into v_should_notify
    from public.app_notification_preferences
    where user_id = v_user_id;

    insert into public.item_status_notifications (
        user_id,
        email,
        item_id,
        item_title,
        notification_type,
        source_status,
        should_notify,
        payload
    )
    values (
        v_user_id,
        nullif(p_email, ''),
        nullif(p_item_id, ''),
        nullif(p_item_title, ''),
        v_type,
        nullif(p_source_status, ''),
        coalesce(v_should_notify, true),
        coalesce(p_payload, '{}'::jsonb)
    )
    returning id into v_id;

    return v_id;
end;
$$;

revoke all on function public.upsert_notification_preferences(text, text, boolean, boolean, boolean, jsonb) from public;
revoke all on function public.mirror_item_status_notification(text, text, text, text, text, text, jsonb) from public;

grant execute on function public.upsert_notification_preferences(text, text, boolean, boolean, boolean, jsonb)
to anon, authenticated;

grant execute on function public.mirror_item_status_notification(text, text, text, text, text, text, jsonb)
to anon, authenticated;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'item_status_notifications'
    ) then
        alter publication supabase_realtime add table public.item_status_notifications;
    end if;
end;
$$;
