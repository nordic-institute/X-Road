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

package org.niis.xroad.edc.extension.store.contractnegotiation;

import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two assumptions the forked save path was written against: the stock EDC contract-negotiation SQL
 * templates' column sets, and the pinned EDC version. Neither {@link XRoadContractNegotiationStore#save} nor
 * {@code XRoadPostgresContractNegotiationStatements} has an upstream extension seam, so nothing else notices when
 * an EDC bump changes either one; this test is the guard.
 *
 * <p>All assertions run against the live output of the actual stock EDC classes on the test classpath, not against
 * a copy of EDC's source, so a real drift always reaches this test as an EDC bump changes what those classes
 * produce.
 *
 * <p>Update procedure after any failure here: re-diff {@code XRoadContractNegotiationStore#save} and
 * {@code XRoadPostgresContractNegotiationStatements} against the current stock
 * {@code org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements}
 * / {@code BaseSqlDialectStatements}. Once a deliberate review confirms the fork still holds (or has been updated
 * to match), copy the new values reported in this test's failure output into the {@code EXPECTED_*} constants
 * below.
 */
class XRoadContractNegotiationStoreCanaryTest {

    private static final String RE_DIFF_HINT = "Re-diff XRoadContractNegotiationStore#save and "
            + "XRoadPostgresContractNegotiationStatements against the current stock EDC contract-negotiation SQL "
            + "store, then, after a deliberate review, update the recorded EXPECTED_* constants in "
            + "XRoadContractNegotiationStoreCanaryTest to match.";

    private static final String EXPECTED_EDC_VERSION = "0.18.0";

    private static final List<String> EXPECTED_AGREEMENT_UPSERT_COLUMNS = List.of(
            "agr_id", "provider_agent_id", "consumer_agent_id", "signing_date", "asset_id",
            "policy", "agr_participant_context_id", "agr_agreement_id", "claims");

    private static final List<String> EXPECTED_NEGOTIATION_UPSERT_COLUMNS = List.of(
            "id", "correlation_id", "counterparty_id", "counterparty_address", "type", "protocol",
            "state", "state_count", "state_timestamp", "error_detail", "agreement_id",
            "contract_offers", "callback_addresses", "trace_context", "created_at", "updated_at",
            "pending", "protocol_messages", "participant_context_id");

    private static final Pattern INSERT_COLUMNS_PATTERN = Pattern.compile("INSERT INTO \\S+ \\(([^)]*)\\)");

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final PostgresDialectStatements stockStatements = new PostgresDialectStatements(leaseStatements, Clock.systemUTC());

    @Test
    void stockAgreementUpsertColumnsMatchRecordedExpectation() {
        var template = stockStatements.getUpsertAgreementTemplate();

        assertThat(insertColumns(template))
                .as(RE_DIFF_HINT)
                .isEqualTo(EXPECTED_AGREEMENT_UPSERT_COLUMNS);
    }

    @Test
    void stockAgreementUpsertArbiterIsStillThePrimaryKeyColumn() {
        var template = stockStatements.getUpsertAgreementTemplate();

        assertThat(template)
                .as("XRoadPostgresContractNegotiationStatements rewrites this exact conflict-arbiter substring to "
                        + "converge same-context self-negotiations onto one row. " + RE_DIFF_HINT)
                .contains("ON CONFLICT (agr_id)");
    }

    @Test
    void stockAgreementUpsertNeverRewritesTheArbiterColumnOnConflict() {
        var template = stockStatements.getUpsertAgreementTemplate();

        assertThat(updateSetClause(template))
                .as("agr_id must stay out of the DO UPDATE SET list: the converge fork depends on the upsert never "
                        + "touching its conflict-arbiter column, or moving the arbiter to the composite pair would "
                        + "let the surviving internal id be silently overwritten. " + RE_DIFF_HINT)
                .doesNotContain("agr_id = EXCLUDED.agr_id");
    }

    @Test
    void stockNegotiationUpsertColumnsMatchRecordedExpectation() {
        var template = stockStatements.getUpsertNegotiationTemplate();

        assertThat(insertColumns(template))
                .as(RE_DIFF_HINT)
                .isEqualTo(EXPECTED_NEGOTIATION_UPSERT_COLUMNS);
    }

    @Test
    void stockNegotiationUpsertStillBindsTheAgreementForeignKeyColumn() {
        var template = stockStatements.getUpsertNegotiationTemplate();

        assertThat(insertColumns(template))
                .as("XRoadContractNegotiationStore#save rebinds this column, by position, to the surviving "
                        + "composite-arbiter agreement id; if the column is renamed, removed or reordered the "
                        + "fork's bind breaks silently. " + RE_DIFF_HINT)
                .contains("agreement_id");
    }

    @Test
    void pinnedEdcVersionMatchesRecordedExpectation() {
        assertThat(catalogEdcVersion())
                .as("The 'edc' pin in gradle/libs.versions.toml moved; every EDC bump must be re-diffed before the "
                        + "recorded pin below is updated. " + RE_DIFF_HINT)
                .isEqualTo(EXPECTED_EDC_VERSION);
    }

    private static List<String> insertColumns(String upsertTemplate) {
        var matcher = INSERT_COLUMNS_PATTERN.matcher(upsertTemplate);
        assertThat(matcher.find())
                .as("upsert template no longer matches the expected 'INSERT INTO <table> (<columns>)' shape: %s", upsertTemplate)
                .isTrue();
        return Arrays.stream(matcher.group(1).split(",\\s*")).map(String::trim).toList();
    }

    private static String updateSetClause(String upsertTemplate) {
        var index = upsertTemplate.indexOf("DO UPDATE SET ");
        assertThat(index)
                .as("upsert template no longer contains a 'DO UPDATE SET' clause: %s", upsertTemplate)
                .isGreaterThanOrEqualTo(0);
        return upsertTemplate.substring(index);
    }

    /**
     * Reads the version catalog's live {@code edc} pin, generated into this test's classpath by the module's
     * {@code generateEdcVersionFixture} Gradle task, rather than duplicating the pin as a second hardcoded source
     * that could drift from {@code gradle/libs.versions.toml} on its own.
     */
    private static String catalogEdcVersion() {
        try (InputStream in = XRoadContractNegotiationStoreCanaryTest.class.getResourceAsStream("/edc-version.txt")) {
            assertThat(in)
                    .as("edc-version.txt was not found on the test classpath; run this module's `test` task through "
                            + "Gradle so the generateEdcVersionFixture task produces it from the version catalog.")
                    .isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read edc-version.txt from the test classpath", e);
        }
    }
}
