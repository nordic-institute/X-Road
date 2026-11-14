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
import org.niis.xroad.proxy.core.serverproxy.AgreementTokenAccessCheck;
import org.niis.xroad.serverconf.model.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

class SiblingSecurityServerTokenTest {

    private static final ClientId CLIENT = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "petstore");
    private static final String METHOD = "GET";
    private static final String PATH = "/pets/1";

    private static final ProviderSecurityServer.Acl ACL = ProviderSecurityServer.Acl.empty()
            .allowing(CLIENT, SERVICE, new Endpoint("petstore", METHOD, "/pets/*", false));

    @Test
    void aTokenIssuedByOneSecurityServerIsAcceptedThereAndRejectedByItsSiblingSecurityServer() {
        var issuingSecurityServer = ProviderSecurityServer.create(ACL, keysWithId("1"));
        var siblingSecurityServer = ProviderSecurityServer.create(ACL, keysWithId("1"));

        var token = issuingSecurityServer.issueToken(CLIENT, SERVICE, METHOD, PATH).mintedToken().orElseThrow();

        assertThat(issuingSecurityServer.checkToken(token, CLIENT, SERVICE, METHOD, PATH))
                .isInstanceOf(AgreementTokenAccessCheck.Decision.Accepted.class);
        assertThat(siblingSecurityServer.checkToken(token, CLIENT, SERVICE, METHOD, PATH))
                .isEqualTo(new AgreementTokenAccessCheck.Decision.Rejected(AgreementTokenRejectionReason.INVALID_SIGNATURE.name()));
    }

    private static TestAgreementTokenKeyProvider keysWithId(String keyId) {
        var keys = new TestAgreementTokenKeyProvider();
        keys.addKey(keyId);
        return keys;
    }
}
