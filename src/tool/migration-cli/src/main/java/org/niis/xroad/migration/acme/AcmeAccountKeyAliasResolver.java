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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recovers the candidate original-case form(s) of a PKCS12 keystore alias that Java's
 * {@link java.security.KeyStore} has irreversibly lowercased at write time, by matching it against the
 * case-sensitive client identifiers configured in the serverconf database. Case-insensitive matching means
 * more than one client identifier can plausibly explain the same enumerated alias; this resolver surfaces
 * every such candidate rather than guessing, leaving disambiguation to the caller.
 */
@Slf4j
public final class AcmeAccountKeyAliasResolver {

    private static final List<String> ALIAS_PREFIXES = List.of("auth_", "sign_");

    private static final String SELECT_CLIENT_IDENTIFIERS =
            "SELECT i.xroad_instance, i.member_class, i.member_code, i.subsystem_code "
                    + "FROM client c JOIN identifier i ON c.identifier = i.id";

    private final Map<String, Set<String>> candidatesByLowercaseId;

    private AcmeAccountKeyAliasResolver(Map<String, Set<String>> candidatesByLowercaseId) {
        this.candidatesByLowercaseId = candidatesByLowercaseId;
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
     * <p>Two distinct client identifiers that differ only by case fold to the same PKCS12 alias, so an
     * enumerated (lowercased) alias alone cannot tell them apart - both are kept as candidates and left
     * for {@link #resolveOriginalCaseAliasCandidates(String)}'s caller to disambiguate, e.g. by testing
     * each one as a PKCS12 entry password: password matching is exact-byte, so at most one candidate can
     * ever actually decrypt a given entry.
     */
    static AcmeAccountKeyAliasResolver fromKnownClientIds(Collection<String> encodedClientIds) {
        return new AcmeAccountKeyAliasResolver(groupByLowercaseId(encodedClientIds));
    }

    private static Map<String, Set<String>> groupByLowercaseId(Collection<String> encodedClientIds) {
        Map<String, Set<String>> candidatesByLowercaseId = new HashMap<>();
        for (String encodedClientId : encodedClientIds) {
            candidatesByLowercaseId
                    .computeIfAbsent(encodedClientId.toLowerCase(Locale.ROOT), id -> new LinkedHashSet<>())
                    .add(encodedClientId);
        }
        return candidatesByLowercaseId;
    }

    /**
     * Returns every original-case client identifier that could plausibly be the enumerated (lowercased)
     * PKCS12 alias, given both its bare and its auth_/sign_-prefixed encodings.
     *
     * <p>Usually zero or one candidate. More than one means the enumerated alias is genuinely ambiguous
     * between multiple case-colliding clients; the caller must disambiguate rather than assume any one
     * candidate is correct (e.g. only one can be the real PKCS12 entry password).
     *
     * @param enumeratedAlias the lowercased alias as returned by {@link java.security.KeyStore#aliases()}
     * @return every matching original-case candidate, possibly empty
     */
    public List<String> resolveOriginalCaseAliasCandidates(String enumeratedAlias) {
        Set<String> candidates = new LinkedHashSet<>(lookup(enumeratedAlias));
        candidates.addAll(prefixedCandidates(enumeratedAlias));
        return List.copyOf(candidates);
    }

    private Set<String> prefixedCandidates(String enumeratedAlias) {
        return ALIAS_PREFIXES.stream()
                .filter(enumeratedAlias::startsWith)
                .findFirst()
                .map(prefix -> withPrefix(prefix, lookup(enumeratedAlias.substring(prefix.length()))))
                .orElse(Set.of());
    }

    private static Set<String> withPrefix(String prefix, Set<String> memberIds) {
        Set<String> prefixed = new LinkedHashSet<>();
        for (String memberId : memberIds) {
            prefixed.add(prefix + memberId);
        }
        return prefixed;
    }

    private Set<String> lookup(String alias) {
        return candidatesByLowercaseId.getOrDefault(alias.toLowerCase(Locale.ROOT), Set.of());
    }

    private static Collection<String> loadClientIdentifiers(DbCredentials dbCredentials) throws SQLException {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(dbCredentials.jdbcUrl());
        dataSource.setUser(dbCredentials.username());
        dataSource.setPassword(new String(dbCredentials.password()));
        if (StringUtils.isNotBlank(dbCredentials.schema())) {
            dataSource.setCurrentSchema(dbCredentials.schema());
        }

        Set<String> encodedClientIds = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(SELECT_CLIENT_IDENTIFIERS)) {
            while (resultSet.next()) {
                ClientId.Conf clientId = ClientId.Conf.create(
                        resultSet.getString("xroad_instance"),
                        resultSet.getString("member_class"),
                        resultSet.getString("member_code"),
                        resultSet.getString("subsystem_code")
                );
                encodedClientIds.addAll(candidateEncodedIdsFor(clientId));
            }
        }

        log.info("Loaded {} distinct member identifier(s) from serverconf database for ACME alias resolution",
                encodedClientIds.size());
        return encodedClientIds;
    }

    static Set<String> candidateEncodedIdsFor(ClientId clientId) {
        if (clientId.getSubsystemCode() == null) {
            return Set.of(clientId.asEncodedId());
        }
        return Set.of(clientId.asEncodedId(), clientId.getMemberId().asEncodedId());
    }
}
