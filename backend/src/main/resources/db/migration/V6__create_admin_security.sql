-- Second factor of administrators (K47). The TOTP secret is AES-GCM encrypted with a key
-- from configuration; confirmed_at stays null until the first code from the app is
-- accepted. last_used_step refuses a code that was already used.
create table admin_credentials (
    user_id        uuid        primary key references users (id) on delete cascade,
    totp_secret    bytea       not null,
    confirmed_at   timestamptz,
    last_used_step bigint      not null default -1,
    created_at     timestamptz not null
);

-- One-time recovery codes (K49); only their SHA-256 is stored.
create table admin_backup_codes (
    user_id    uuid        not null references users (id) on delete cascade,
    code_hash  bytea       not null,
    used_at    timestamptz,
    created_at timestamptz not null,
    constraint uk_admin_backup_codes_hash unique (code_hash)
);

create index ix_admin_backup_codes_user on admin_backup_codes (user_id);

-- Who did what, for administrator and system actions. Rows are only ever inserted.
create table audit_log (
    id          bigint      generated always as identity primary key,
    actor_id    uuid        references users (id),
    action      varchar(64) not null,
    target_type varchar(32),
    target_id   varchar(64),
    details     jsonb       not null default '{}'::jsonb,
    created_at  timestamptz not null
);

create index ix_audit_log_created on audit_log (created_at desc);
