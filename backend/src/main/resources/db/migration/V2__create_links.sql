create table links (
    id                uuid          primary key,
    workspace_id      uuid          not null references workspaces (id),
    -- Codes are never reused: deleted links keep their row (deleted_at),
    -- so the unique constraint also reserves codes of deleted links.
    code              varchar(50)   not null,
    target_url        varchar(2048) not null,
    status            varchar(16)   not null,
    expires_at        timestamptz,
    deleted_at        timestamptz,
    safety_checked_at timestamptz,
    version           bigint        not null,
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null,
    constraint uk_links_code unique (code),
    constraint ck_links_status check (status in ('ACTIVE', 'DISABLED', 'BLOCKED'))
);

-- Panel lists a workspace's links, newest first.
create index ix_links_workspace_created on links (workspace_id, created_at desc);
