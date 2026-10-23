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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRequestsTest {

    @Test
    void buildsTheProxyUrlFromProviderServiceAndPath() {
        assertThat(RestRequests.url("http://localhost:32768", "DEV:COM:1234:TestService", "pab-v01", "/a/b"))
                .isEqualTo("http://localhost:32768/r1/DEV/COM/1234/TestService/pab-v01/a/b");
        assertThat(RestRequests.url("http://localhost:32768", "DEV:COM:1234:TestService", "pab-v01", ""))
                .isEqualTo("http://localhost:32768/r1/DEV/COM/1234/TestService/pab-v01");
    }

    @Test
    void keepsEncodedLettersDotSegmentsAndQueryAsWritten() {
        var path = "/api/%2e%2e/t%65st%2D/../x?a=%20";

        assertThat(RestRequests.url("http://h:1", "DEV:COM:1234:TestService", "svc", path))
                .isEqualTo("http://h:1/r1/DEV/COM/1234/TestService/svc/api/%2e%2e/t%65st%2D/../x?a=%20");
    }

    @Test
    void rejectsAPathWithoutLeadingSlash() {
        assertThatThrownBy(() -> RestRequests.url("http://h:1", "DEV:COM:1234:TestService", "pab-v01", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metaserviceUrlHasNoQueryWithoutATarget() {
        assertThat(RestRequests.metaserviceUrl("http://h:1", "DEV:COM:1234:TestService", "listMethods", null))
                .isEqualTo("http://h:1/r1/DEV/COM/1234/TestService/listMethods");
    }

    @Test
    void metaserviceUrlNamesTheTargetServiceCodeInTheQuery() {
        assertThat(RestRequests.metaserviceUrl("http://h:1", "DEV:COM:1234:TestService", "getOpenAPI", "pab-v93"))
                .isEqualTo("http://h:1/r1/DEV/COM/1234/TestService/getOpenAPI?serviceCode=pab-v93");
    }

    @Test
    void listClientsUrlIsTheMetaserviceOfTheGivenProxyOrigin() {
        assertThat(RestRequests.listClientsUrl("http://localhost:32768")).isEqualTo("http://localhost:32768/listClients");
    }

    @Test
    void clientHeaderUsesSlashes() {
        assertThat(RestRequests.clientHeader("DEV:COM:4321:TestClient")).isEqualTo("DEV/COM/4321/TestClient");
    }

    @Test
    void clientHeaderOfAMemberHasNoSubsystemPart() {
        assertThat(RestRequests.clientHeader(AclParticipants.MEMBER_OF_CONSUMER)).isEqualTo("DEV/COM/4321");
    }
}
