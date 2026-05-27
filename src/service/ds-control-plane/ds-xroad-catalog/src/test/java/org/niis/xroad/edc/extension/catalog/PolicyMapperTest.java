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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.PolicyType;
import org.junit.jupiter.api.Test;
import org.niis.xroad.serverconf.model.Endpoint;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyMapperTest {

    private final PolicyMapper mapper = new PolicyMapper();
    private static final String PARTICIPANT_CTX = "xroad-provider";

    private static final ClientId.Conf CLIENT_SUBJECT = ClientId.Conf.create("DEV", "GOV", "1234", "ConsumerSS");
    private static final GlobalGroupId GLOBAL_GROUP_SUBJECT = GlobalGroupId.Conf.create("DEV", "security-server-owners");
    private static final LocalGroupId LOCAL_GROUP_SUBJECT = LocalGroupId.Conf.create("localAdmins");

    private static final ServiceId.Conf SERVICE_ID = ServiceId.Conf.create("DEV", "GOV", "5678", "ProviderSS", "helloService", "v1");
    private static final String ASSET_ID = SERVICE_ID.asEncodedId();
    private static final String POLICY_ID = ASSET_ID + ":" + CLIENT_SUBJECT.asEncodedId();

    @Test
    void clientIdSubjectProducesCorrectConstraintVocabulary() {
        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        var clientConstraint = extractAtomicConstraint(rootAnd.getConstraints().getFirst());

        assertThat(leftExpressionValue(clientConstraint)).isEqualTo(PolicyMapper.XROAD_CLIENT_ID_CONSTRAINT);
        assertThat(clientConstraint.getOperator()).isEqualTo(Operator.EQ);
        assertThat(rightExpressionValue(clientConstraint)).isEqualTo(CLIENT_SUBJECT.asEncodedId());
    }

    @Test
    void globalGroupIdSubjectProducesCorrectConstraintVocabulary() {
        var policyId = ASSET_ID + ":" + GLOBAL_GROUP_SUBJECT.asEncodedId();
        var result = mapper.toPolicyDefinition(policyId, GLOBAL_GROUP_SUBJECT, List.of(), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        var clientConstraint = extractAtomicConstraint(rootAnd.getConstraints().getFirst());

        assertThat(leftExpressionValue(clientConstraint)).isEqualTo(PolicyMapper.XROAD_GLOBALGROUP_CONSTRAINT);
        assertThat(rightExpressionValue(clientConstraint)).isEqualTo(GLOBAL_GROUP_SUBJECT.asEncodedId());
    }

    @Test
    void localGroupIdSubjectProducesCorrectConstraintVocabulary() {
        var policyId = ASSET_ID + ":" + LOCAL_GROUP_SUBJECT.asEncodedId();
        var result = mapper.toPolicyDefinition(policyId, LOCAL_GROUP_SUBJECT, List.of(), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        var clientConstraint = extractAtomicConstraint(rootAnd.getConstraints().getFirst());

        assertThat(leftExpressionValue(clientConstraint)).isEqualTo(PolicyMapper.XROAD_LOCALGROUP_CONSTRAINT);
        assertThat(rightExpressionValue(clientConstraint)).isEqualTo(LOCAL_GROUP_SUBJECT.asEncodedId());
    }

    @Test
    void multiEndpointServiceProducesOrConstraintWithCorrectCardinality() {
        var ep1 = new Endpoint("helloService", "GET", "/api/hello", false);
        var ep2 = new Endpoint("helloService", "POST", "/api/hello", false);

        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(ep1, ep2), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        assertThat(rootAnd.getConstraints()).hasSize(2);

        var orConstraint = (OrConstraint) rootAnd.getConstraints().get(1);
        assertThat(orConstraint.getConstraints()).hasSize(2);

        // Each OR child is a datapath AtomicConstraint (single path, no additionalCondition -> not wrapped in AND)
        var path1 = extractAtomicConstraint(orConstraint.getConstraints().getFirst());
        assertThat(leftExpressionValue(path1)).isEqualTo(PolicyMapper.XROAD_DATAPATH_CONSTRAINT);
        assertThat(rightExpressionValue(path1)).isEqualTo("GET /api/hello");

        var path2 = extractAtomicConstraint(orConstraint.getConstraints().get(1));
        assertThat(rightExpressionValue(path2)).isEqualTo("POST /api/hello");
    }

    @Test
    void singleEndpointProducesOrConstraintWithSingleChild() {
        var ep = new Endpoint("helloService", "GET", "/api/hello", false);

        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(ep), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        assertThat(rootAnd.getConstraints()).hasSize(2);

        // Single endpoint still wrapped in OrConstraint per implementation
        var orConstraint = (OrConstraint) rootAnd.getConstraints().get(1);
        assertThat(orConstraint.getConstraints()).hasSize(1);

        var pathConstraint = extractAtomicConstraint(orConstraint.getConstraints().getFirst());
        assertThat(rightExpressionValue(pathConstraint)).isEqualTo("GET /api/hello");
    }

    @Test
    void baseEndpointOnlyProducesClientConstraintWithoutOrConstraint() {
        var baseEp = new Endpoint("helloService", "*", "**", true);

        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(baseEp), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        // Only client constraint, no OrConstraint for paths
        assertThat(rootAnd.getConstraints()).hasSize(1);
        var clientConstraint = extractAtomicConstraint(rootAnd.getConstraints().getFirst());
        assertThat(leftExpressionValue(clientConstraint)).isEqualTo(PolicyMapper.XROAD_CLIENT_ID_CONSTRAINT);
    }

    @Test
    void emptyEndpointListProducesClientConstraintOnly() {
        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(), PARTICIPANT_CTX);

        var rootAnd = extractRootAndConstraint(result.getPolicy().getPermissions().getFirst().getConstraints());
        assertThat(rootAnd.getConstraints()).hasSize(1);
        var clientConstraint = extractAtomicConstraint(rootAnd.getConstraints().getFirst());
        assertThat(leftExpressionValue(clientConstraint)).isEqualTo(PolicyMapper.XROAD_CLIENT_ID_CONSTRAINT);
    }

    @Test
    void validAdditionalConditionJsonProducesExtraConstraints() {
        var json = """
                [{"leftExpression":"xroad:customKey","operator":"EQ","rightExpression":"someValue"}]""";

        var constraints = mapper.parseAdditionalConditions(json, "GET", "/api/hello", ASSET_ID);

        assertThat(constraints).hasSize(1);
        var atomic = extractAtomicConstraint(constraints.getFirst());
        assertThat(leftExpressionValue(atomic)).isEqualTo("xroad:customKey");
        assertThat(atomic.getOperator()).isEqualTo(Operator.EQ);
        assertThat(rightExpressionValue(atomic)).isEqualTo("someValue");
    }

    @Test
    void malformedAdditionalConditionJsonLogsWarnAndReturnsEmpty() {
        var logAppender = attachListAppender(PolicyMapper.class);

        var constraints = mapper.parseAdditionalConditions("not valid json", "GET", "/api/hello", ASSET_ID);

        assertThat(constraints).isEmpty();
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("Skipping malformed additionalCondition"));
    }

    @Test
    void nullAdditionalConditionReturnsEmptyList() {
        var constraints = mapper.parseAdditionalConditions(null, "GET", "/api/hello", ASSET_ID);
        assertThat(constraints).isEmpty();
    }

    @Test
    void emptyAdditionalConditionReturnsEmptyList() {
        var constraints = mapper.parseAdditionalConditions("  ", "GET", "/api/hello", ASSET_ID);
        assertThat(constraints).isEmpty();
    }

    @Test
    void policyDefinitionHasCorrectMetadata() {
        var result = mapper.toPolicyDefinition(POLICY_ID, CLIENT_SUBJECT, List.of(), PARTICIPANT_CTX);

        assertThat(result.getId()).isEqualTo(POLICY_ID);
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
        assertThat(result.getPolicy().getType()).isEqualTo(PolicyType.SET);
        assertThat(result.getPolicy().getPermissions()).hasSize(1);
        assertThat(result.getPolicy().getPermissions().getFirst().getAction().getType())
                .isEqualTo("http://www.w3.org/ns/odrl/2/use");
    }

    // -- Helpers --

    private static AndConstraint extractRootAndConstraint(List<Constraint> constraints) {
        assertThat(constraints).hasSize(1);
        assertThat(constraints.getFirst()).isInstanceOf(AndConstraint.class);
        return (AndConstraint) constraints.getFirst();
    }

    private static AtomicConstraint extractAtomicConstraint(Constraint constraint) {
        assertThat(constraint).isInstanceOf(AtomicConstraint.class);
        return (AtomicConstraint) constraint;
    }

    private static String leftExpressionValue(AtomicConstraint constraint) {
        return ((LiteralExpression) constraint.getLeftExpression()).getValue().toString();
    }

    private static String rightExpressionValue(AtomicConstraint constraint) {
        return ((LiteralExpression) constraint.getRightExpression()).getValue().toString();
    }

    private static ListAppender<ILoggingEvent> attachListAppender(Class<?> clazz) {
        var logger = (Logger) LoggerFactory.getLogger(clazz);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
