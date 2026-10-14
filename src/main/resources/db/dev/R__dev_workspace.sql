-- Development only (loaded by the dev profile): the workspace the dev API acts for.
insert into workspaces (id, name, created_at)
values ('00000000-0000-0000-0000-000000000001', 'dev', now())
on conflict (id) do nothing;
