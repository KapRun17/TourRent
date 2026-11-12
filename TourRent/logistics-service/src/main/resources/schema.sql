begin;
select pg_advisory_xact_lock(724800);
create table if not exists deliveries(id bigserial primary key,rental_id uuid not null unique,owner_id text not null,recipient text not null,address text not null,status text not null,request_id text not null,created_at timestamptz default current_timestamp,updated_at timestamptz default current_timestamp);

create table if not exists outbox(event_id uuid primary key,routing_key text not null,payload text not null,created_at timestamptz default current_timestamp,sent_at timestamptz);
create index if not exists outbox_pending on outbox(created_at) where sent_at is null;
create table if not exists processed_events(event_id uuid primary key,processed_at timestamptz default current_timestamp);

commit;
