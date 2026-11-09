-- Pre-Liquibase script for PostgreSQL
-- Enables btree_gist extension required for GIST exclusion constraints on user_id and timestamps
CREATE
EXTENSION IF NOT EXISTS btree_gist;
