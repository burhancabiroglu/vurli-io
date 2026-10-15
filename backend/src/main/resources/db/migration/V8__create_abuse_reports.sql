-- Reports from the public form on vurli.io/abuse. The code is kept as typed (normalized);
-- link_id is set when a link has that code. The reporter's email is optional and only
-- shown to administrators.
create table abuse_reports (
    id             uuid          primary key,
    code           varchar(50)   not null,
    link_id        uuid          references links (id),
    reason         varchar(16)   not null,
    details        varchar(2000) not null default '',
    reporter_email varchar(254),
    status         varchar(16)   not null default 'OPEN',
    created_at     timestamptz   not null,
    resolved_at    timestamptz,
    resolved_by    uuid          references users (id),
    constraint ck_abuse_reports_reason check (reason in ('PHISHING', 'MALWARE', 'SPAM', 'OTHER')),
    constraint ck_abuse_reports_status check (status in ('OPEN', 'RESOLVED'))
);

create index ix_abuse_reports_open on abuse_reports (created_at desc) where status = 'OPEN';
