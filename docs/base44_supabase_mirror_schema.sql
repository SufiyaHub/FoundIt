-- Base44-compatible Supabase mirror schema.
-- Use this when Base44 remains the primary auth provider and Supabase is only
-- a supplementary database/mirror layer.
--
-- Why separate tables?
-- The original public.users table references auth.users(id), which only works
-- when users sign in through Supabase Auth. Base44 users do not exist in
-- auth.users, so client-side mirror inserts are blocked by RLS/foreign keys.

create extension if not exists pgcrypto;

create table if not exists public.base44_users (
    id uuid primary key default gen_random_uuid(),
    base44_user_id text not null unique,
    email text,
    name text,
    role text,
    raw_user jsonb not null default '{}'::jsonb,
    first_seen_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.base44_login_events (
    id bigint generated always as identity primary key,
    base44_user_id text,
    email text,
    name text,
    user_agent text,
    raw_user jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table if not exists public.base44_generated_project_records (
    id uuid primary key default gen_random_uuid(),
    base44_user_id text,
    email text,
    title text,
    prompt text,
    generation_type text,
    status text not null default 'completed',
    input_data jsonb not null default '{}'::jsonb,
    output_data jsonb not null default '{}'::jsonb,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.base44_application_data (
    id uuid primary key default gen_random_uuid(),
    base44_user_id text,
    email text,
    record_type text not null,
    title text,
    data jsonb not null default '{}'::jsonb,
    is_archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists set_base44_users_updated_at on public.base44_users;
create trigger set_base44_users_updated_at
before update on public.base44_users
for each row execute function public.set_updated_at();

drop trigger if exists set_base44_generated_project_records_updated_at on public.base44_generated_project_records;
create trigger set_base44_generated_project_records_updated_at
before update on public.base44_generated_project_records
for each row execute function public.set_updated_at();

drop trigger if exists set_base44_application_data_updated_at on public.base44_application_data;
create trigger set_base44_application_data_updated_at
before update on public.base44_application_data
for each row execute function public.set_updated_at();

alter table public.base44_users enable row level security;
alter table public.base44_login_events enable row level security;
alter table public.base44_generated_project_records enable row level security;
alter table public.base44_application_data enable row level security;

-- Base44 cannot provide a Supabase Auth JWT, so anon insert/update is allowed
-- for mirror writes from the published website. Reads remain blocked for anon.
-- For higher security later, move these writes to a Supabase Edge Function with
-- the service role key and remove anon update/insert policies.

drop policy if exists "Base44 mirror can insert users" on public.base44_users;
create policy "Base44 mirror can insert users"
on public.base44_users
for insert
to anon
with check (true);

drop policy if exists "Base44 mirror can update users" on public.base44_users;
create policy "Base44 mirror can update users"
on public.base44_users
for update
to anon
using (true)
with check (true);

drop policy if exists "Base44 mirror can insert login events" on public.base44_login_events;
create policy "Base44 mirror can insert login events"
on public.base44_login_events
for insert
to anon
with check (true);

drop policy if exists "Base44 mirror can insert generated records" on public.base44_generated_project_records;
create policy "Base44 mirror can insert generated records"
on public.base44_generated_project_records
for insert
to anon
with check (true);

drop policy if exists "Base44 mirror can insert application data" on public.base44_application_data;
create policy "Base44 mirror can insert application data"
on public.base44_application_data
for insert
to anon
with check (true);

create index if not exists base44_users_user_id_idx
on public.base44_users (base44_user_id);

create index if not exists base44_login_events_user_created_idx
on public.base44_login_events (base44_user_id, created_at desc);

create index if not exists base44_generated_records_user_created_idx
on public.base44_generated_project_records (base44_user_id, created_at desc);

create index if not exists base44_application_data_user_type_created_idx
on public.base44_application_data (base44_user_id, record_type, created_at desc);

create or replace function public.mirror_base44_user(
    p_base44_user_id text,
    p_email text default null,
    p_name text default null,
    p_role text default null,
    p_raw_user jsonb default '{}'::jsonb,
    p_user_agent text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id text := nullif(p_base44_user_id, '');
begin
    if v_user_id is null then
        raise exception 'base44 user id is required';
    end if;

    insert into public.base44_users (
        base44_user_id,
        email,
        name,
        role,
        raw_user,
        last_seen_at
    )
    values (
        v_user_id,
        nullif(p_email, ''),
        nullif(p_name, ''),
        nullif(p_role, ''),
        coalesce(p_raw_user, '{}'::jsonb),
        now()
    )
    on conflict (base44_user_id) do update
    set
        email = coalesce(excluded.email, public.base44_users.email),
        name = coalesce(excluded.name, public.base44_users.name),
        role = coalesce(excluded.role, public.base44_users.role),
        raw_user = excluded.raw_user,
        last_seen_at = now(),
        updated_at = now();

    insert into public.base44_login_events (
        base44_user_id,
        email,
        name,
        user_agent,
        raw_user
    )
    values (
        v_user_id,
        nullif(p_email, ''),
        nullif(p_name, ''),
        nullif(left(coalesce(p_user_agent, ''), 512), ''),
        coalesce(p_raw_user, '{}'::jsonb)
    );
end;
$$;

create or replace function public.mirror_base44_generated_project(
    p_base44_user_id text,
    p_email text default null,
    p_title text default null,
    p_prompt text default null,
    p_generation_type text default null,
    p_status text default 'completed',
    p_input_data jsonb default '{}'::jsonb,
    p_output_data jsonb default '{}'::jsonb,
    p_metadata jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
begin
    insert into public.base44_generated_project_records (
        base44_user_id,
        email,
        title,
        prompt,
        generation_type,
        status,
        input_data,
        output_data,
        metadata
    )
    values (
        nullif(p_base44_user_id, ''),
        nullif(p_email, ''),
        nullif(p_title, ''),
        p_prompt,
        p_generation_type,
        coalesce(nullif(p_status, ''), 'completed'),
        coalesce(p_input_data, '{}'::jsonb),
        coalesce(p_output_data, '{}'::jsonb),
        coalesce(p_metadata, '{}'::jsonb)
    )
    returning id into v_id;

    return v_id;
end;
$$;

create or replace function public.mirror_base44_application_data(
    p_base44_user_id text,
    p_email text default null,
    p_record_type text default 'general',
    p_title text default null,
    p_data jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
begin
    insert into public.base44_application_data (
        base44_user_id,
        email,
        record_type,
        title,
        data
    )
    values (
        nullif(p_base44_user_id, ''),
        nullif(p_email, ''),
        coalesce(nullif(p_record_type, ''), 'general'),
        nullif(p_title, ''),
        coalesce(p_data, '{}'::jsonb)
    )
    returning id into v_id;

    return v_id;
end;
$$;

revoke all on function public.mirror_base44_user(text, text, text, text, jsonb, text) from public;
revoke all on function public.mirror_base44_generated_project(text, text, text, text, text, text, jsonb, jsonb, jsonb) from public;
revoke all on function public.mirror_base44_application_data(text, text, text, text, jsonb) from public;

grant execute on function public.mirror_base44_user(text, text, text, text, jsonb, text) to anon;
grant execute on function public.mirror_base44_generated_project(text, text, text, text, text, text, jsonb, jsonb, jsonb) to anon;
grant execute on function public.mirror_base44_application_data(text, text, text, text, jsonb) to anon;

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'base44_generated_project_records'
    ) then
        alter publication supabase_realtime add table public.base44_generated_project_records;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'base44_application_data'
    ) then
        alter publication supabase_realtime add table public.base44_application_data;
    end if;
end;
$$;
