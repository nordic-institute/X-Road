/*
 * The MIT License
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

package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.AgreementTokenRejectionReason;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.proxy.core.serverproxy.AgreementTokenAccessCheck.Decision;
import org.niis.xroad.serverconf.model.BaseEndpoint;
import org.niis.xroad.serverconf.model.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

class SiblingSecurityServerAccessTest {

    private static final ClientId CLIENT = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "petstore");

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";
    private static final String PET_PATH = "/pets/1";
    private static final String PETS_PATH = "/pets";

    private static final Endpoint GET_PETS_ENDPOINT = new Endpoint("petstore", GET, "/pets/*", false);
    private static final Endpoint POST_PETS_ENDPOINT = new Endpoint("petstore", POST, PETS_PATH, false);
    private static final Endpoint SERVICE_ENDPOINT = new Endpoint("petstore", BaseEndpoint.ANY_METHOD, BaseEndpoint.ANY_PATH, false);

    private static final ProviderSecurityServer.Acl GET_PETS_ACL =
            ProviderSecurityServer.Acl.empty().allowing(CLIENT, SERVICE, GET_PETS_ENDPOINT);
    private static final ProviderSecurityServer.Acl GET_AND_POST_PETS_ACL =
            ProviderSecurityServer.Acl.empty().allowing(CLIENT, SERVICE, GET_PETS_ENDPOINT, POST_PETS_ENDPOINT);
    private static final ProviderSecurityServer.Acl SERVICE_LEVEL_ACL =
            ProviderSecurityServer.Acl.empty().allowing(CLIENT, SERVICE, SERVICE_ENDPOINT);

    private static final Decision ACCEPTED = new Decision.Accepted();
    private static final Decision SCOPE_MISMATCH = new Decision.Denied(AgreementTokenRejectionReason.SCOPE_MISMATCH.name());

    @Test
    void siblingSecurityServersWithTheSameRestEndpointRuleGrantTheSameScopeAndGiveTheSameVerdicts() {
        var securityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"));
        var siblingSecurityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"));

        var token = securityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();
        var siblingToken = siblingSecurityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();

        var expectedScope = new AgreementTokenScope(GET, "/pets/*");
        assertThat(securityServer.tokenScope(token, CLIENT, SERVICE)).containsExactly(expectedScope);
        assertThat(siblingSecurityServer.tokenScope(siblingToken, CLIENT, SERVICE)).containsExactly(expectedScope);
        assertThat(securityServer.checkToken(token, CLIENT, SERVICE, GET, PET_PATH)).isEqualTo(ACCEPTED);
        assertThat(siblingSecurityServer.checkToken(siblingToken, CLIENT, SERVICE, GET, PET_PATH)).isEqualTo(ACCEPTED);
        assertThat(securityServer.checkToken(token, CLIENT, SERVICE, DELETE, PET_PATH)).isEqualTo(SCOPE_MISMATCH);
        assertThat(siblingSecurityServer.checkToken(siblingToken, CLIENT, SERVICE, DELETE, PET_PATH)).isEqualTo(SCOPE_MISMATCH);
    }

    @Test
    void siblingSecurityServersWithTheSameServiceLevelRuleGrantTheSameScopeAndGiveTheSameVerdicts() {
        var securityServer = ProviderSecurityServer.create(SERVICE_LEVEL_ACL, keysWithId("1"));
        var siblingSecurityServer = ProviderSecurityServer.create(SERVICE_LEVEL_ACL, keysWithId("1"));

        var token = securityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();
        var siblingToken = siblingSecurityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();

        var expectedScope = new AgreementTokenScope(BaseEndpoint.ANY_METHOD, BaseEndpoint.ANY_PATH);
        assertThat(securityServer.tokenScope(token, CLIENT, SERVICE)).containsExactly(expectedScope);
        assertThat(siblingSecurityServer.tokenScope(siblingToken, CLIENT, SERVICE)).containsExactly(expectedScope);
        assertThat(securityServer.checkToken(token, CLIENT, SERVICE, DELETE, PET_PATH)).isEqualTo(ACCEPTED);
        assertThat(siblingSecurityServer.checkToken(siblingToken, CLIENT, SERVICE, DELETE, PET_PATH)).isEqualTo(ACCEPTED);
    }

    @Test
    void aSiblingSecurityServerWithANarrowerAclIssuesANarrowerTokenAndDeniesWhatItsAclDoesNotGrant() {
        var securityServer = ProviderSecurityServer.create(GET_AND_POST_PETS_ACL, keysWithId("1"));
        var narrowerSiblingSecurityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"));

        var token = securityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();
        var siblingToken = narrowerSiblingSecurityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH).mintedToken().orElseThrow();

        var scope = securityServer.tokenScope(token, CLIENT, SERVICE);
        var siblingScope = narrowerSiblingSecurityServer.tokenScope(siblingToken, CLIENT, SERVICE);
        assertThat(scope).containsExactlyInAnyOrder(
                new AgreementTokenScope(GET, "/pets/*"), new AgreementTokenScope(POST, PETS_PATH));
        assertThat(siblingScope).containsExactly(new AgreementTokenScope(GET, "/pets/*"));
        assertThat(scope).containsAll(siblingScope);

        assertThat(securityServer.checkToken(token, CLIENT, SERVICE, POST, PETS_PATH)).isEqualTo(ACCEPTED);
        assertThat(narrowerSiblingSecurityServer.checkToken(siblingToken, CLIENT, SERVICE, POST, PETS_PATH))
                .isEqualTo(SCOPE_MISMATCH);
        assertThat(narrowerSiblingSecurityServer.checkToken(siblingToken, CLIENT, SERVICE, GET, PET_PATH)).isEqualTo(ACCEPTED);
    }

    @Test
    void aSiblingSecurityServerWithAnEmptyAclIssuesNoToken() {
        var securityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"));
        var emptyAclSiblingSecurityServer = ProviderSecurityServer.create(ProviderSecurityServer.Acl.empty(), keysWithId("1"));

        assertThat(securityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH)).isInstanceOf(AgreementTokenIssuer.Outcome.Minted.class);
        assertThat(emptyAclSiblingSecurityServer.issueToken(CLIENT, SERVICE, GET, PET_PATH))
                .isInstanceOf(AgreementTokenIssuer.Outcome.Refused.class);
    }

    /**
     * {@link Decision.Absent} hands the call to the server proxy's ACL check; ServerRestMessageProcessorTest pins
     * that hand-over.
     */
    @Test
    void withoutATokenASiblingSecurityServerThatAllowsLegacyCallsLeavesTheDecisionToItsAcl() {
        var siblingSecurityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"));

        assertThat(siblingSecurityServer.checkToken(null, CLIENT, SERVICE, GET, PET_PATH)).isEqualTo(new Decision.Absent());
    }

    @Test
    void withoutATokenASiblingSecurityServerThatRefusesLegacyCallsRejectsTheCall() {
        var siblingSecurityServer = ProviderSecurityServer.create(GET_PETS_ACL, keysWithId("1"),
                ProviderSecurityServer.Mode.DSP_REFUSAL);

        assertThat(siblingSecurityServer.checkToken(null, CLIENT, SERVICE, GET, PET_PATH))
                .isEqualTo(new Decision.Rejected("AGREEMENT_TOKEN_REQUIRED"));
    }

    private static TestAgreementTokenKeyProvider keysWithId(String keyId) {
        var keys = new TestAgreementTokenKeyProvider();
        keys.addKey(keyId);
        return keys;
    }
}
