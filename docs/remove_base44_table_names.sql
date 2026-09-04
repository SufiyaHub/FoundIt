-- Rename Base44-prefixed Supabase mirror tables/functions to neutral names.
-- Run this only after confirming the app code will be updated to call the new RPC names.
--
-- New names:
-- base44_users -> app_users
-- base44_login_events -> login_events
-- base44_generated_project_records -> generated_project_records
-- base44_application_data -> application_data
--
-- base44_user_id columns are renamed to user_id.

begin;

do $$
begin
    if to_regclass('public.base44_users') is not null
       and to_regclass('public.app_users') is null then
        alter table public.base44_users rename to app_users;
    end if;

    if to_regclass('public.base44_login_events') is not null
       and to_regclass('public.login_events') is null then
        alter table public.base44_login_events rename to login_events;
    end if;

    if to_regclass('public.base44_generated_project_records') is not null
       and to_regclass('public.generated_project_records') is null then
        alter table public.base44_generated_project_records rename to generated_project_records;
    end if;

    if to_regclass('public.base44_application_data') is not null
       and to_regclass('public.application_data') is null then
        alter table public.base44_application_data rename to application_data;
    end if;
end;
$$;

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'app_users'
          and column_name = 'base44_user_id'
    ) then
        alter table public.app_users rename column base44_user_id to user_id;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'login_events'
          and column_name = 'base44_user_id'
    ) then
        alter table public.login_events rename column base44_user_id to user_id;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'generated_project_records'
          and column_name = 'base44_user_id'
    ) then
        alter table public.generated_project_records rename column base44_user_id to user_id;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'application_data'
          and column_name = 'base44_user_id'
    ) then
        alter table public.application_data rename column base44_user_id to user_id;
    end if;
end;
$$;

drop function if exists public.mirror_base44_user(text, text, text, text, jsonb, text);
drop function if exists public.mirror_base44_generated_project(text, text, text, text, text, text, jsonb, jsonb, jsonb);
drop function if exists public.mirror_base44_application_data(text, text, text, text, jsonb);

create or replace function public.mirror_user(
    p_user_id text,
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
    v_user_id text := nullif(p_user_id, '');
begin
    if v_user_id is null then
        raise exception 'user id is required';
    end if;

    insert into public.app_users (
        user_id,
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
    on conflict (user_id) do update
    set
        email = coalesce(excluded.email, public.app_users.email),
        name = coalesce(excluded.name, public.app_users.name),
        role = coalesce(excluded.role, public.app_users.role),
        raw_user = excluded.raw_user,
        last_seen_at = now(),
        updated_at = now();

    insert into public.login_events (
        user_id,
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

create or replace function public.mirror_generated_project(
    p_user_id text,
    p_email text default null,
    p_title text default null,
    p_prompt text default null,
    p_generation_type text default null,
    p_status text default 'completed',
    p_input_data jsonb default '{}'::jsonb,
    p_output_data jsonb default '{}'::jsonb,
    p_metadata jsonb default '{}'::jsonb
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id text;
begin
    insert into public.generated_project_records (
        user_id,
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
        nullif(p_user_id, ''),
        nullif(p_email, ''),
        nullif(p_title, ''),
        p_prompt,
        p_generation_type,
        coalesce(nullif(p_status, ''), 'completed'),
        coalesce(p_input_data, '{}'::jsonb),
        coalesce(p_output_data, '{}'::jsonb),
        coalesce(p_metadata, '{}'::jsonb)
    )
    returning id::text into v_id;

    return v_id;
end;
$$;

create or replace function public.mirror_application_data(
    p_user_id text,
    p_email text default null,
    p_record_type text default 'general',
    p_title text default null,
    p_data jsonb default '{}'::jsonb
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id text;
begin
    insert into public.application_data (
        user_id,
        email,
        record_type,
        title,
        data
    )
    values (
        nullif(p_user_id, ''),
        nullif(p_email, ''),
        coalesce(nullif(p_record_type, ''), 'general'),
        nullif(p_title, ''),
        coalesce(p_data, '{}'::jsonb)
    )
    returning id::text into v_id;

    return v_id;
end;
$$;

grant execute on function public.mirror_user(text, text, text, text, jsonb, text) to anon, authenticated;
grant execute on function public.mirror_generated_project(text, text, text, text, text, text, jsonb, jsonb, jsonb) to anon, authenticated;
grant execute on function public.mirror_application_data(text, text, text, text, jsonb) to anon, authenticated;

commit;
