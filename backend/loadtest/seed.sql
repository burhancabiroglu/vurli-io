-- 10,000 links (lt00000 ... lt09999) and one "viral" link, in one workspace. Codes are
-- written directly; the application would refuse nothing here, they are valid codes.
insert into workspaces (id, name, created_at)
values ('00000000-0000-0000-0000-00000000a11d', 'loadtest', now())
on conflict do nothing;

insert into links (id, workspace_id, code, target_url, status, version, created_at, updated_at)
select gen_random_uuid(), '00000000-0000-0000-0000-00000000a11d', 'lt' || lpad(n::text, 5, '0'),
       'https://example.com/page/' || n, 'ACTIVE', 0, now(), now()
from generate_series(0, 9999) as n
on conflict do nothing;

insert into links (id, workspace_id, code, target_url, status, version, created_at, updated_at)
values (gen_random_uuid(), '00000000-0000-0000-0000-00000000a11d', 'viral', 'https://example.com/viral', 'ACTIVE', 0,
        now(), now())
on conflict do nothing;
