-- An administrator can suspend an account (K50): it can no longer sign in and its
-- sessions are ended. Null means active.
alter table users add column suspended_at timestamptz;
