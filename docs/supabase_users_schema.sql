-- FoundIt Supabase auth/profile schema.
-- Run this in the Supabase SQL editor for the production project.

create extension if not exists pgcrypto;

create table if not exists public.users (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null unique,
    name text,
    login_count integer not null default 0,
    last_login_at timestamptz,
    last_login_user_agent text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.user_login_events (
    id bigint generated always as identity primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    email text not null,
    user_agent text,
    created_at timestamptz not null default now()
);

create table if not exists public.generated_project_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    prompt text,
    generation_type text,
    status text not null default 'completed',
    input_data jsonb not null default '{}'::jsonb,
    output_data jsonb not null default '{}'::jsonb,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.application_data (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    record_type text not null,
    title text,
    data jsonb not null default '{}'::jsonb,
    is_archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.users enable row level security;
alter table public.user_login_events enable row level security;
alter table public.generated_project_records enable row level security;
alter table public.application_data enable row level security;

drop policy if exists "Users can read their own profile" on public.users;
create policy "Users can read their own profile"
on public.users
for select
to authenticated
using (id = auth.uid());

drop policy if exists "Users can update their own profile" on public.users;
create policy "Users can update their own profile"
on public.users
for update
to authenticated
using (id = auth.uid())
with check (id = auth.uid());

drop policy if exists "Users can read their own login events" on public.user_login_events;
create policy "Users can read their own login events"
on public.user_login_events
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "Users can manage their own generated records" on public.generated_project_records;
create policy "Users can manage their own generated records"
on public.generated_project_records
for all
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists "Users can manage their own application data" on public.application_data;
create policy "Users can manage their own application data"
on public.application_data
for all
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_public_users_updated_at on public.users;
create trigger set_public_users_updated_at
before update on public.users
for each row execute function public.set_updated_at();

drop trigger if exists set_generated_project_records_updated_at on public.generated_project_records;
create trigger set_generated_project_records_updated_at
before update on public.generated_project_records
for each row execute function public.set_updated_at();

drop trigger if exists set_application_data_updated_at on public.application_data;
create trigger set_application_data_updated_at
before update on public.application_data
for each row execute function public.set_updated_at();

create or replace function public.handle_auth_user_upsert()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    insert into public.users (id, email, name)
    values (
        new.id,
        new.email,
        coalesce(new.raw_user_meta_data ->> 'name', new.raw_user_meta_data ->> 'full_name')
    )
    on conflict (id) do update
    set
        email = excluded.email,
        name = coalesce(excluded.name, public.users.name),
        updated_at = now();

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_auth_user_upsert();

drop trigger if exists on_auth_user_updated on auth.users;
create trigger on_auth_user_updated
after update of email, raw_user_meta_data on auth.users
for each row execute function public.handle_auth_user_upsert();

create or replace function public.record_login(p_user_agent text default null)
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    v_user_id uuid := auth.uid();
    v_email text;
    v_user_agent text := nullif(left(coalesce(p_user_agent, ''), 512), '');
begin
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;

    select email into v_email
    from auth.users
    where id = v_user_id;

    insert into public.user_login_events (user_id, email, user_agent)
    values (v_user_id, v_email, v_user_agent);

    update public.users
    set
        email = v_email,
        last_login_at = now(),
        last_login_user_agent = v_user_agent,
        login_count = login_count + 1,
        updated_at = now()
    where id = v_user_id;
end;
$$;

revoke all on function public.record_login(text) from public;
grant execute on function public.record_login(text) to authenticated;

create index if not exists generated_project_records_user_created_idx
on public.generated_project_records (user_id, created_at desc);

create index if not exists application_data_user_type_created_idx
on public.application_data (user_id, record_type, created_at desc);

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'generated_project_records'
    ) then
        alter publication supabase_realtime add table public.generated_project_records;
    end if;

    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'application_data'
    ) then
        alter publication supabase_realtime add table public.application_data;
    end if;
end;
$$;
