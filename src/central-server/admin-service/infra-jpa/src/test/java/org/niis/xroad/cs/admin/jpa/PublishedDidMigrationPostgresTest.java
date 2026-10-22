/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.cs.admin.jpa;

import liquibase.Scope;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.command.CommandScope;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the real {@code centerui-changelog.xml} in two steps on a database of its own: everything before the
 * published-DIDs changeset, then a seed of servers, members and clients, then the rest. The assertions run on what
 * the changeset made of the rows that already existed.
 */
class PublishedDidMigrationPostgresTest {

    private static final String CHANGELOG = "liquibase/centerui-changelog.xml";
    private static final String FEATURE_CHANGESET = "202610141200-published-dids";
    private static final String DATABASE = "published_did_migration";

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static List<Integer> serverIds;
    private static List<Integer> memberIds;

    @BeforeAll
    static void migratePopulatedDatabase() throws Exception {
        SharedPostgres.start();
        var url = createDatabase();
        var container = SharedPostgres.CONTAINER;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");

        update(url, changeSetsBefore(FEATURE_CHANGESET));
        assertThat(appliedChangeSets()).doesNotContain(FEATURE_CHANGESET).isNotEmpty();
        assertThat(columnNames("security_servers")).doesNotContain("system_did", "dsp_base_url");
        assertThat(tableExists("member_dids")).isFalse();
        seedServersMembersAndClients();

        update(url, null);
        assertThat(appliedChangeSets()).contains(FEATURE_CHANGESET);
    }

    @AfterAll
    static void closeConnection() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void seededServersKeepTheirRowsWithEmptySystemValues() {
        assertThat(serverIds).hasSize(2);
        var rows = jdbc.queryForList("select id, system_did, dsp_base_url from security_servers where id in (?, ?)",
                serverIds.getFirst(), serverIds.getLast());

        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("system_did")).isNull();
            assertThat(row.get("dsp_base_url")).isNull();
        });
    }

    @Test
    void memberDidTableExistsAndIsEmpty() {
        assertThat(tableExists("member_dids")).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from member_dids", Integer.class)).isZero();
        assertThat(memberIds).isNotEmpty();
    }

    @Test
    void newColumnsHaveTheDeclaredTypes() {
        var columns = jdbc.queryForList("select column_name, data_type, character_maximum_length from information_schema.columns "
                + "where table_name = 'security_servers' and column_name in ('system_did', 'dsp_base_url')");

        assertThat(columns).extracting(row -> row.get("column_name") + ":" + row.get("data_type") + ":"
                        + row.get("character_maximum_length"))
                .containsExactlyInAnyOrder("system_did:character varying:4000", "dsp_base_url:text:null");
    }

    @Test
    void featureConstraintsAndCascadingForeignKeysExist() {
        var constraints = jdbc.queryForList("select conname, confdeltype::text as delete_action from pg_constraint "
                + "where conname in ('security_servers_system_values_check', 'security_servers_system_did_excl', "
                + "'member_dids_did_excl', 'member_dids_security_server_id_fk', 'member_dids_member_id_fk', "
                + "'member_dids_security_server_id_member_id_key')");

        assertThat(constraints).extracting(row -> row.get("conname"))
                .containsExactlyInAnyOrder("security_servers_system_values_check", "security_servers_system_did_excl",
                        "member_dids_did_excl", "member_dids_security_server_id_fk", "member_dids_member_id_fk",
                        "member_dids_security_server_id_member_id_key");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
                + "where conname = 'member_dids_security_server_id_member_id_key'", String.class))
                .isEqualTo("UNIQUE (security_server_id, member_id)");
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'member_dids' "
                + "and indexname = 'index_member_dids_on_member_id'", String.class))
                .containsExactly("index_member_dids_on_member_id");
        assertThat(constraints)
                .filteredOn(row -> String.valueOf(row.get("conname")).endsWith("_fk"))
                .extracting(row -> row.get("delete_action"))
                .containsExactly("c", "c");
    }

    @Test
    void historyTriggerRecordsChangesToTheNewColumns() {
        var serverId = insertServer(memberIds.getFirst(), "ss-history");

        jdbc.update("update security_servers set system_did = 'did:web:history.test%3A7183:v1:system', "
                + "dsp_base_url = 'https://history.test:8183/api/dsp' where id = ?", serverId);

        var changedFields = jdbc.queryForList("select field_name from history where table_name = 'security_servers' "
                + "and record_id = ? and operation = 'UPDATE'", String.class, serverId);
        assertThat(changedFields).contains("system_did", "dsp_base_url");
    }

    @Test
    void historyTriggerRecordsInsertsIntoMemberDids() {
        var serverId = insertServer(memberIds.getFirst(), "ss-member-did-history");

        var rowId = jdbc.queryForObject("insert into member_dids (security_server_id, member_id, did, created_at, updated_at) "
                + "values (?, ?, 'did:web:member-history.test%3A7183:v1:member', now(), now()) returning id",
                Integer.class, serverId, memberIds.getFirst());

        var changedFields = jdbc.queryForList("select field_name from history where table_name = 'member_dids' "
                + "and record_id = ? and operation = 'INSERT'", String.class, rowId);
        assertThat(changedFields).contains("did");
    }

    private static String createDatabase() throws Exception {
        var container = SharedPostgres.CONTAINER;
        try (var connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + DATABASE;
    }

    private static int changeSetsBefore(String changeSetId) throws Exception {
        var accessor = new ClassLoaderResourceAccessor();
        var changeLog = ChangeLogParserFactory.getInstance().getParser(CHANGELOG, accessor)
                .parse(CHANGELOG, new ChangeLogParameters(), accessor);
        var ids = changeLog.getChangeSets().stream().map(ChangeSet::getId).toList();
        assertThat(ids).contains(changeSetId);
        return ids.indexOf(changeSetId);
    }

    private static void update(String url, Integer count) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope(count == null ? "update" : "updateCount");
            update.addArgumentValue("changelogFile", CHANGELOG);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("contexts", "!admin");
            if (count != null) {
                update.addArgumentValue("count", count);
            }
            update.execute();
        });
    }

    private static List<String> appliedChangeSets() {
        return jdbc.queryForList("select id from databasechangelog", String.class);
    }

    private static List<String> columnNames(String table) {
        return jdbc.queryForList("select column_name from information_schema.columns where table_name = ?", String.class, table);
    }

    private static boolean tableExists(String table) {
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_name = ?", Integer.class, table) > 0;
    }

    private static void seedServersMembersAndClients() {
        jdbc.update("insert into member_classes (code, description, created_at, updated_at) values ('COM', 'test class', now(), now())");
        var classId = jdbc.queryForObject("select id from member_classes where code = 'COM'", Integer.class);
        var owner = insertMember("owner", classId);
        var client = insertMember("client", classId);
        var subsystem = insertSubsystem(client, "sub", classId);
        var first = insertServer(owner, "ss-one");
        var second = insertServer(owner, "ss-two");
        for (var server : List.of(first, second)) {
            for (var clientId : List.of(owner, client, subsystem)) {
                jdbc.update("insert into server_clients (security_server_id, security_server_client_id, enabled) "
                        + "values (?, ?, true)", server, clientId);
            }
        }
        serverIds = List.of(first, second);
        memberIds = List.of(owner, client);
    }

    private static int insertMember(String code, int classId) {
        var identifier = insertIdentifier("MEMBER", code, null, null);
        return jdbc.queryForObject("insert into security_server_clients (member_code, name, member_class_id, server_client_id, type, "
                + "created_at, updated_at) values (?, ?, ?, ?, 'XRoadMember', now(), now()) returning id",
                Integer.class, code, "name-" + code, classId, identifier);
    }

    private static int insertSubsystem(int memberId, String subsystemCode, int classId) {
        var memberCode = jdbc.queryForObject("select member_code from security_server_clients where id = ?", String.class, memberId);
        var identifier = insertIdentifier("SUBSYSTEM", memberCode, subsystemCode, null);
        return jdbc.queryForObject("insert into security_server_clients (subsystem_code, name, xroad_member_id, server_client_id, "
                + "type, created_at, updated_at) values (?, ?, ?, ?, 'Subsystem', now(), now()) returning id",
                Integer.class, subsystemCode, "name-" + subsystemCode, memberId, identifier);
    }

    private static int insertServer(int ownerId, String serverCode) {
        var ownerCode = jdbc.queryForObject("select member_code from security_server_clients where id = ?", String.class, ownerId);
        insertIdentifier("SERVER", ownerCode, null, serverCode);
        return jdbc.queryForObject("insert into security_servers (server_code, owner_id, address, in_maintenance_mode, created_at, "
                + "updated_at) values (?, ?, ?, false, now(), now()) returning id",
                Integer.class, serverCode, ownerId, serverCode + ".test");
    }

    private static int insertIdentifier(String objectType, String memberCode, String subsystemCode, String serverCode) {
        return jdbc.queryForObject("insert into identifier (object_type, xroad_instance, member_class, member_code, subsystem_code, "
                + "server_code, created_at, updated_at) values (?, 'DEV', 'COM', ?, ?, ?, now(), now()) returning id",
                Integer.class, objectType, memberCode, subsystemCode, serverCode);
    }
}
