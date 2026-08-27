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
package org.niis.xroad.migration.acme;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.migration.utils.DbCredentials;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recovers the original-case form of a PKCS12 keystore alias that Java's {@link java.security.KeyStore}
 * has irreversibly lowercased at write time, by matching it against the case-sensitive client identifiers
 * configured in the serverconf database.
 */
@Slf4j
public final class AcmeAccountKeyAliasResolver {

    private static final List<String> ALIAS_PREFIXES = List.of("auth_", "sign_");

    private static final String SELECT_CLIENT_IDENTIFIERS =
            "SELECT i.xroad_instance, i.member_class, i.member_code, i.subsystem_code "
                    + "FROM client c JOIN identifier i ON c.identifier = i.id";

    private final Map<String, String> lowercaseToOriginalCase;

    private AcmeAccountKeyAliasResolver(Map<String, String> lowercaseToOriginalCase) {
        this.lowercaseToOriginalCase = lowercaseToOriginalCase;
    }

    /**
     * An empty resolver that never recovers original casing. Used when no serverconf database is available.
     */
    public static AcmeAccountKeyAliasResolver identity() {
        return new AcmeAccountKeyAliasResolver(Map.of());
    }

    /**
     * Builds a resolver from every client identifier configured in the serverconf database.
     */
    public static AcmeAccountKeyAliasResolver fromServerconfDatabase(DbCredentials dbCredentials) throws SQLException {
        return fromKnownClientIds(loadClientIdentifiers(dbCredentials));
    }

    /**
     * Builds a resolver directly from a collection of encoded client IDs, bypassing the database.
     *
     * <p>Two distinct client identifiers that differ only by case fold to the same PKCS12 alias and
     * cannot be told apart from an enumerated (lowercased) alias alone. Rather than picking an
     * arbitrary winner between them - which would silently misattribute a decrypted key to the wrong
     * client's Vault entry - any such colliding identifier is excluded so it resolves to
     * {@link Optional#empty()} for every client sharing that lowercase form.
     */
    static AcmeAccountKeyAliasResolver fromKnownClientIds(Collection<String> encodedClientIds) {
        Map<String, String> lowercaseToOriginalCase = new HashMap<>();
        Set<String> ambiguousLowercaseIds = new HashSet<>();
        for (String encodedClientId : encodedClientIds) {
            String lowercaseId = encodedClientId.toLowerCase(Locale.ROOT);
            String existing = lowercaseToOriginalCase.putIfAbsent(lowercaseId, encodedClientId);
            if (existing != null && !existing.equals(encodedClientId)) {
                ambiguousLowercaseIds.add(lowercaseId);
            }
        }
        for (String ambiguousLowercaseId : ambiguousLowercaseIds) {
            log.warn("Multiple client identifiers in serverconf differ only by case and both fold to '{}' - "
                    + "their ACME account key aliases cannot be told apart from an enumerated PKCS12 alias, so "
                    + "none of them will be resolved and their keys will be skipped during migration",
                    ambiguousLowercaseId);
            lowercaseToOriginalCase.remove(ambiguousLowercaseId);
        }
        return new AcmeAccountKeyAliasResolver(lowercaseToOriginalCase);
    }

    /**
     * Recovers the original-case alias for a lowercased alias enumerated from a PKCS12 keystore.
     *
     * @param enumeratedAlias the lowercased alias as returned by {@link java.security.KeyStore#aliases()}
     * @return the original-case alias, or {@link Optional#empty()} if no matching client identifier was found
     */
    public Optional<String> resolveOriginalCaseAlias(String enumeratedAlias) {
        return lookup(enumeratedAlias).or(() -> resolvePrefixedAlias(enumeratedAlias));
    }

    private Optional<String> resolvePrefixedAlias(String enumeratedAlias) {
        return ALIAS_PREFIXES.stream()
                .filter(enumeratedAlias::startsWith)
                .findFirst()
                .flatMap(prefix -> lookup(enumeratedAlias.substring(prefix.length())).map(prefix::concat));
    }

    private Optional<String> lookup(String alias) {
        return Optional.ofNullable(lowercaseToOriginalCase.get(alias.toLowerCase(Locale.ROOT)));
    }

    private static Collection<String> loadClientIdentifiers(DbCredentials dbCredentials) throws SQLException {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(dbCredentials.jdbcUrl());
        dataSource.setUser(dbCredentials.username());
        dataSource.setPassword(new String(dbCredentials.password()));
        if (StringUtils.isNotBlank(dbCredentials.schema())) {
            dataSource.setCurrentSchema(dbCredentials.schema());
        }

        List<String> encodedClientIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(SELECT_CLIENT_IDENTIFIERS)) {
            while (resultSet.next()) {
                String encodedId = ClientId.Conf.create(
                        resultSet.getString("xroad_instance"),
                        resultSet.getString("member_class"),
                        resultSet.getString("member_code"),
                        resultSet.getString("subsystem_code")
                ).asEncodedId();
                encodedClientIds.add(encodedId);
            }
        }

        log.info("Loaded {} client identifier(s) from serverconf database for ACME alias resolution", encodedClientIds.size());
        return encodedClientIds;
    }
}
