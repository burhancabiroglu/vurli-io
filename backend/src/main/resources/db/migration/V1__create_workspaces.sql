-- Workspace is the isolation boundary: every link belongs to exactly one.
-- Owner and membership columns arrive with user accounts (M2).
create table workspaces (
    id         uuid         primary key,
    name       varchar(100) not null,
    created_at timestamptz  not null
);
