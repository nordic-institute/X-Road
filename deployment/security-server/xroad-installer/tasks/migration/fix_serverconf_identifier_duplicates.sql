-- The caller passes the serverconf schema: psql -v schema=<schema>. It is
-- "serverconf" unless the database setup prefixed it.
SET search_path TO :"schema", public;

BEGIN;

LOCK TABLE identifier IN EXCLUSIVE MODE;
LOCK TABLE accessright IN EXCLUSIVE MODE;
LOCK TABLE client IN EXCLUSIVE MODE;
LOCK TABLE groupmember IN EXCLUSIVE MODE;

DO $$
DECLARE
bad_count bigint;
BEGIN
SELECT COUNT(*)
INTO bad_count
FROM identifier
WHERE "type" IS NULL
   OR "type" NOT IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP', 'LOCALGROUP');

IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows with invalid type values (allowed values: MEMBER, SUBSYSTEM, GLOBALGROUP, LOCALGROUP)',
            bad_count;
END IF;

SELECT COUNT(*)
INTO bad_count
FROM identifier
WHERE servicecode IS NOT NULL
   OR serviceversion IS NOT NULL;

IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows where servercode, servicecode or serviceversion is NOT NULL (these columns should be NULL)',
            bad_count;
END IF;
END $$;

DROP TABLE IF EXISTS tmp_identifier_groups;
CREATE TEMP TABLE tmp_identifier_groups AS
SELECT
    MIN(id) AS main_id,
    "type",
    xroadinstance,
    memberclass,
    membercode,
    subsystemcode,
    groupcode,
    servercode
FROM identifier
WHERE "type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP')
GROUP BY
    "type",
    xroadinstance,
    memberclass,
    membercode,
    subsystemcode,
    groupcode,
    servercode
HAVING COUNT(*) > 1;

DROP TABLE IF EXISTS tmp_identifier_dedup_map;
CREATE TEMP TABLE tmp_identifier_dedup_map AS
SELECT
    i.id AS dup_id,
    g.main_id
FROM identifier i
         JOIN tmp_identifier_groups g
              ON i."type" = g."type"
                  AND i.xroadinstance = g.xroadinstance
                  AND i.memberclass IS NOT DISTINCT FROM g.memberclass
    AND i.membercode IS NOT DISTINCT FROM g.membercode
    AND i.subsystemcode IS NOT DISTINCT FROM g.subsystemcode
    AND i.groupcode IS NOT DISTINCT FROM g.groupcode
    AND i.servercode IS NOT DISTINCT FROM g.servercode
WHERE i.id <> g.main_id
  AND i."type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP');

UPDATE accessright ar
SET subjectid = m.main_id
    FROM tmp_identifier_dedup_map m
WHERE ar.subjectid = m.dup_id;

UPDATE client c
SET identifier = m.main_id
    FROM tmp_identifier_dedup_map m
WHERE c.identifier = m.dup_id;

UPDATE groupmember gm
SET groupmemberid = m.main_id
    FROM tmp_identifier_dedup_map m
WHERE gm.groupmemberid = m.dup_id;

DELETE FROM identifier i
    USING tmp_identifier_dedup_map m
WHERE i.id = m.dup_id;

DO $$
DECLARE
remaining_count integer;
BEGIN
SELECT COUNT(*)
INTO remaining_count
FROM (
         SELECT 1
         FROM identifier
         WHERE "type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP')
         GROUP BY
             "type",
             xroadinstance,
             memberclass,
             membercode,
             subsystemcode,
             groupcode,
             servercode
         HAVING COUNT(*) > 1
     ) d;

IF remaining_count <> 0 THEN
        RAISE EXCEPTION
            'Duplicate rows still remain in serverconf.identifier for MEMBER, SUBSYSTEM and GLOBALGROUP: %. Fix the problematic rows manually and re-run the upgrade wizard.',
            remaining_count;
END IF;
END $$;

COMMIT;