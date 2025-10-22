create table if not exists catalog (
  id varchar primary key,
  title varchar not null,
  "desc" text,
  tags text[],
  updated_at timestamptz default now(),
  version bigint default 0 not null
);

create index if not exists idx_catalog_title on catalog(title);
create index if not exists idx_catalog_tags on catalog using gin(tags);
create index if not exists idx_catalog_updated_at on catalog(updated_at);
create index if not exists idx_catalog_version on catalog(version);
