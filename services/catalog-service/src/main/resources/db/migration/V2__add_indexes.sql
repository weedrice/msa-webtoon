-- Add indexes for common query patterns
-- Title index for text search performance
create index if not exists idx_catalog_title on catalog(title);

-- Tags GIN index for array operations
create index if not exists idx_catalog_tags on catalog using gin(tags);

-- Updated_at index for sorting and filtering by update time
create index if not exists idx_catalog_updated_at on catalog(updated_at);

-- Add version column for optimistic locking
alter table catalog add column if not exists version bigint default 0 not null;

-- Create index on version for conflict detection
create index if not exists idx_catalog_version on catalog(version);
