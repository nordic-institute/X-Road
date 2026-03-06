-- Containerized Upgrade: FILENAME Normalization
-- Run this ONCE before the first executor jar migration on databases
-- previously migrated by the liquibase:4.33.0 Docker image.
--
-- The old containerized path stored FILENAME values with 'changelog/' prefix:
--   changelog/serverconf-changelog.xml
--   changelog/serverconf/000-baseline.xml
--
-- The new logicalFilePath values match the native path (no prefix):
--   serverconf-changelog.xml
--   serverconf/000-baseline.xml
--
-- This UPDATE normalizes the containerized FILENAME to match logicalFilePath.

UPDATE databasechangelog
SET filename = REPLACE(filename, 'changelog/', '')
WHERE filename LIKE 'changelog/%';

-- Verification: show all distinct filenames after normalization
SELECT DISTINCT filename FROM databasechangelog ORDER BY filename;
