begin;
select pg_advisory_xact_lock(724800);
create table if not exists equipment(id bigint primary key,name text not null,category text not null,description text not null,daily_price numeric(12,2) not null check(daily_price>0),stock int not null check(stock>0),illustration text not null);
create table if not exists reservations(rental_id uuid primary key,equipment_id bigint not null references equipment(id),start_date date not null,end_date date not null,quantity int not null check(quantity>0),check(end_date>start_date));
create index if not exists reservations_equipment_period on reservations(equipment_id,start_date,end_date);
insert into equipment values
(1,'Палатка North Ridge 2','Палатки','Двухместная палатка для маршрутов с ночёвкой. Вес 2,4 кг.',690,3,'tent'),
(2,'Спальник Boreal −5','Спальники','Тёплый спальник с комфортной температурой до −5 °C.',350,5,'sleeping'),
(3,'Рюкзак Trail 65','Рюкзаки','Треккинговый рюкзак 65 л с регулируемой подвеской.',290,4,'backpack'),
(4,'Горелка Summit','Кухня','Компактная газовая горелка с ветрозащитой. Баллон отдельно.',190,4,'stove'),
(5,'Палатка Solo Peak','Палатки','Лёгкая одноместная палатка для автономного похода.',490,1,'tent'),
(6,'Коврик Cloud Mat','Комфорт','Самонадувающийся коврик толщиной 5 см.',180,6,'mat') on conflict(id) do nothing;

create table if not exists outbox(event_id uuid primary key,routing_key text not null,payload text not null,created_at timestamptz default current_timestamp,sent_at timestamptz);
create index if not exists outbox_pending on outbox(created_at) where sent_at is null;
create table if not exists processed_events(event_id uuid primary key,processed_at timestamptz default current_timestamp);

commit;
