begin;
select pg_advisory_xact_lock(724800);
create table if not exists rentals(id uuid primary key,owner_id text not null,equipment_id bigint not null,equipment_name text not null,start_date date not null,end_date date not null,quantity int not null,total numeric(12,2) not null,recipient text not null,address text not null,status text not null,request_id text not null,created_at timestamptz default current_timestamp);
create table if not exists rental_history(sequence bigserial primary key,rental_id uuid not null references rentals(id),status text not null,request_id text not null,occurred_at timestamptz default current_timestamp);
create index if not exists rental_history_stream on rental_history(rental_id,sequence);

create table if not exists outbox(event_id uuid primary key,routing_key text not null,payload text not null,created_at timestamptz default current_timestamp,sent_at timestamptz);
create index if not exists outbox_pending on outbox(created_at) where sent_at is null;
create table if not exists processed_events(event_id uuid primary key,processed_at timestamptz default current_timestamp);

commit;
