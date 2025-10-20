create table if not exists catalog (
  id varchar primary key,
  title varchar not null,
  "desc" text,
  tags text[],
  updated_at timestamptz default now()
);

