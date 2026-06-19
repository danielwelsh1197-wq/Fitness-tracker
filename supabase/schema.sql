-- Fitness dashboard schema. Run once in the Supabase SQL editor.
-- The app reads with the anon key (read-only via RLS); the scraper writes with
-- the service-role key (which bypasses RLS).

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- Activities come from two sources sharing one table: Garmin (new runs, via the
-- scraper) and a one-time Strava export backfill (older runs). They're told apart
-- by the `source` column. Re-running this file recreates the table so column
-- changes take effect; the lifting tables below use `if not exists`, so your
-- lifting data is preserved. The drop cascades to the activity views, which are
-- recreated further down.
drop table if exists activities cascade;
create table activities (
    activity_id      bigint primary key,       -- Garmin or Strava activity id
    sport            text not null,            -- 'run' | 'swim' | 'other'
    activity_type    text,                     -- raw type (Garmin typeKey / Strava sport_type)
    name             text,
    start_time       timestamptz,
    distance_m       double precision,
    duration_s       double precision,
    avg_speed_mps    double precision,
    avg_hr           integer,
    calories         double precision,
    elevation_gain_m double precision,
    source           text not null default 'strava',
    raw              jsonb,
    created_at       timestamptz not null default now()
);

create index if not exists activities_start_time_idx on activities (start_time desc);
create index if not exists activities_sport_idx       on activities (sport);

-- One session == one Keep note. id is the Keep note id so re-parsing overwrites.
create table if not exists lift_sessions (
    id         text primary key,
    date       date,
    title      text,
    raw_text   text,
    created_at timestamptz not null default now()
);

create index if not exists lift_sessions_date_idx on lift_sessions (date desc);

create table if not exists lift_sets (
    id         bigint generated always as identity primary key,
    session_id text not null references lift_sessions (id) on delete cascade,
    exercise   text not null,
    sets       integer not null,
    reps       integer not null,
    weight_kg  numeric not null,
    raw_line   text
);

create index if not exists lift_sets_session_idx  on lift_sets (session_id);
create index if not exists lift_sets_exercise_idx on lift_sets (exercise);

create table if not exists sync_log (
    id          bigint generated always as identity primary key,
    ran_at      timestamptz not null default now(),
    source      text not null,                -- 'strava' | 'sheet' | 'keep'
    status      text not null,                -- 'ok' | 'error'
    items_added integer not null default 0,
    error       text
);

-- ---------------------------------------------------------------------------
-- Year-to-date views (security_invoker => the caller's RLS policies apply)
-- ---------------------------------------------------------------------------

create or replace view v_ytd_activity_stats with (security_invoker = true) as
select
    sport,
    count(*)                                                              as sessions,
    coalesce(sum(distance_m), 0)                                         as total_distance_m,
    coalesce(sum(duration_s), 0)                                         as total_duration_s,
    coalesce(max(distance_m), 0)                                         as longest_distance_m,
    case when sum(distance_m) > 0
         then sum(duration_s) / (sum(distance_m) / 1000.0) end           as avg_sec_per_km,
    case when sum(distance_m) > 0
         then sum(duration_s) / (sum(distance_m) / 100.0)  end           as avg_sec_per_100m
from activities
where start_time >= date_trunc('year', now())
group by sport;

create or replace view v_monthly_activity with (security_invoker = true) as
select
    sport,
    date_trunc('month', start_time)::date as month,
    coalesce(sum(distance_m), 0)          as distance_m,
    coalesce(sum(duration_s), 0)          as duration_s,
    count(*)                              as sessions
from activities
where start_time >= date_trunc('year', now())
group by sport, date_trunc('month', start_time);

create or replace view v_ytd_lift_stats with (security_invoker = true) as
select
    count(distinct s.id)                                  as sessions,
    coalesce(sum(ls.sets * ls.reps * ls.weight_kg), 0)    as total_volume_kg
from lift_sessions s
join lift_sets ls on ls.session_id = s.id
where s.date >= date_trunc('year', now())::date;

create or replace view v_exercise_prs with (security_invoker = true) as
select
    ls.exercise,
    max(ls.weight_kg) as max_weight_kg,
    count(*)          as set_entries
from lift_sets ls
join lift_sessions s on s.id = ls.session_id
where s.date >= date_trunc('year', now())::date
group by ls.exercise;

-- ---------------------------------------------------------------------------
-- Row-level security: anon may only read.
-- ---------------------------------------------------------------------------

alter table activities    enable row level security;
alter table lift_sessions enable row level security;
alter table lift_sets     enable row level security;
alter table sync_log      enable row level security;

drop policy if exists "public read activities"    on activities;
drop policy if exists "public read lift_sessions"  on lift_sessions;
drop policy if exists "public read lift_sets"      on lift_sets;
drop policy if exists "public read sync_log"       on sync_log;

create policy "public read activities"   on activities    for select using (true);
create policy "public read lift_sessions" on lift_sessions for select using (true);
create policy "public read lift_sets"     on lift_sets     for select using (true);
create policy "public read sync_log"      on sync_log      for select using (true);

-- Table & view read grants for the PostgREST roles.
grant select on activities, lift_sessions, lift_sets, sync_log to anon, authenticated;
grant select on v_ytd_activity_stats, v_monthly_activity, v_ytd_lift_stats, v_exercise_prs
    to anon, authenticated;
