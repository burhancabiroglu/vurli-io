-- Raw clicks, kept 90 days (K22). No IP address: only the country derived from it (K23).
create table click_events (
    id            bigint generated always as identity primary key,
    link_id       uuid         not null references links (id),
    occurred_at   timestamptz  not null,
    referrer_host varchar(253),
    country       char(2),
    device_type   varchar(16)  not null,
    constraint ck_click_events_device check (device_type in ('DESKTOP', 'MOBILE', 'TABLET', 'OTHER'))
);

create index ix_click_events_link_time on click_events (link_id, occurred_at);
create index ix_click_events_time on click_events (occurred_at);

-- One row per link and completed UTC day, kept forever. Breakdowns map a key to a
-- count; an empty key means unknown (no referrer, no country).
create table link_daily_stats (
    link_id     uuid    not null references links (id),
    day         date    not null,
    clicks      bigint  not null,
    by_referrer jsonb   not null,
    by_country  jsonb   not null,
    by_device   jsonb   not null,
    primary key (link_id, day)
);
