-- Emails are stored lowercased by the application, so a plain unique constraint
-- is enough to stop two accounts for the same address.
create table users (
    id                uuid         primary key,
    email             varchar(254) not null,
    password_hash     varchar(255) not null,
    email_verified_at timestamptz,
    locale            varchar(5)   not null,
    role              varchar(16)  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    constraint uk_users_email unique (email),
    constraint ck_users_role check (role in ('USER', 'ADMIN'))
);

-- Every new workspace gets an owner. Nullable only for rows created before accounts.
alter table workspaces add column owner_id uuid references users (id);

-- Only the SHA-256 of a token is stored: a leaked table cannot be used to verify
-- addresses or reset passwords.
create table email_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    purpose    varchar(16) not null,
    token_hash bytea       not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null,
    constraint uk_email_tokens_hash unique (token_hash),
    constraint ck_email_tokens_purpose check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);

create index ix_email_tokens_user on email_tokens (user_id, purpose, created_at desc);
