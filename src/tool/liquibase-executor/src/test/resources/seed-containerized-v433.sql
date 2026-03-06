-- Seed databasechangelog with rows simulating a v4.33 containerized installation.
-- FILENAME values use the containerized path format with "changelog/" prefix.
INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id)
VALUES ('001-create-table', 'test', 'changelog/test-upgrade/001-create-table.xml', CURRENT_TIMESTAMP, 1, 'EXECUTED', '8:abc123def456', 'createTable tableName=test_data', '', NULL, '4.33.0', NULL, NULL, '0000000001');
