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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapRequestsTest {

    private static final String CONSUMER = "DEV:COM:4321:TestClient";
    private static final String PROVIDER = "DEV:COM:1234:TestService";

    @Test
    void envelopeNamesClientServiceCodeVersionAndMessageId() {
        var xml = SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v03", "v2", null, "ID-7");

        assertThat(xml)
                .contains("<xro:client iden:objectType=\"SUBSYSTEM\">")
                .contains("<iden:memberCode>4321</iden:memberCode>")
                .contains("<iden:subsystemCode>TestClient</iden:subsystemCode>")
                .contains("<xro:service iden:objectType=\"SERVICE\">")
                .contains("<iden:memberCode>1234</iden:memberCode>")
                .contains("<iden:subsystemCode>TestService</iden:subsystemCode>")
                .contains("<iden:serviceCode>pab-v03</iden:serviceCode>")
                .contains("<iden:serviceVersion>v2</iden:serviceVersion>")
                .contains("<xro:id>ID-7</xro:id>")
                .contains("<xro:protocolVersion>4.0</xro:protocolVersion>");
    }

    @Test
    void envelopeKeepsADottedServiceCodeVerbatim() {
        assertThat(SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v129.x", "v1", null, "ID-1"))
                .contains("<iden:serviceCode>pab-v129.x</iden:serviceCode>");
    }

    @Test
    void bodyElementIsNamedAfterTheServiceCode() throws Exception {
        for (var code : new String[]{"pab-v03", "pab-v129.x"}) {
            var doc = parse(SoapRequests.envelope(CONSUMER, PROVIDER, code, "v1", null, "ID-1"));

            var bodyChild = doc.getElementsByTagNameNS("http://acl-parity.test/producer", "*").item(0);

            assertThat(bodyChild.getLocalName()).isEqualTo(code);
        }
    }

    @Test
    void envelopeWithoutAVersionHasNoVersionElementAndNoNullText() {
        var xml = SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v132b", null, null, "ID-1");

        assertThat(xml)
                .doesNotContain("serviceVersion")
                .doesNotContain("null");
    }

    @Test
    void envelopeWithoutAVersionIsWellFormedXml() throws Exception {
        var doc = parse(SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v132b", null, null, "ID-1"));

        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("Envelope");
    }

    @Test
    void envelopeWithoutAHintHasNoSecurityServerElement() {
        assertThat(SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v03", "v1", null, "ID-1"))
                .doesNotContain("securityServer");
    }

    @Test
    void envelopeWithAHintNamesThatSecurityServer() {
        var xml = SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v131", "v1", "DEV:COM:1234:ss-unknown", "ID-1");

        assertThat(xml)
                .contains("<xro:securityServer iden:objectType=\"SERVER\">")
                .contains("<iden:serverCode>ss-unknown</iden:serverCode>")
                .contains("</xro:securityServer>");
    }

    @Test
    void envelopeIsWellFormedXmlWithAndWithoutAHint() throws Exception {
        for (var hint : new String[]{null, "DEV:COM:1234:ss-unknown"}) {
            var doc = parse(SoapRequests.envelope(CONSUMER, PROVIDER, "pab-v03", "v1", hint, "ID-1"));

            assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("Envelope");
        }
    }

    @Test
    void rejectsAnIdentifierWithoutFourParts() {
        assertThatThrownBy(() -> SoapRequests.envelope("DEV:COM:4321", PROVIDER, "c", "v1", null, "ID-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SoapRequests.envelope(CONSUMER, PROVIDER, "c", "v1", "DEV:COM", "ID-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
