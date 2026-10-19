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

import java.util.List;
import java.util.stream.Stream;

/** Builds the SOAP envelope a client sends to the consumer Security Server's proxy. */
public final class SoapRequests {

    private static final int ID_PARTS = 4;

    private SoapRequests() {
    }

    /**
     * @param consumerClientId   the calling client in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param providerClientId   the service owner in {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM} form
     * @param serviceCode        the called service code
     * @param serviceVersion     the called service version, or {@code null} to name none
     * @param securityServerHint the {@code INSTANCE:CLASS:MEMBER:SERVERCODE} security server to name in the header,
     *                           or {@code null} for none
     * @param messageId          the unique message id
     */
    public static String envelope(String consumerClientId, String providerClientId, String serviceCode,
                                  String serviceVersion, String securityServerHint, String messageId) {
        var consumer = parts(consumerClientId);
        var provider = parts(providerClientId);
        var version = serviceVersion == null
                ? "" : "<iden:serviceVersion>%s</iden:serviceVersion>".formatted(serviceVersion);
        var server = securityServerHint == null ? "" : securityServerElement(parts(securityServerHint));
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
                    <soapenv:Header>
                        <xro:client iden:objectType="SUBSYSTEM">
                            <iden:xRoadInstance>%s</iden:xRoadInstance>
                            <iden:memberClass>%s</iden:memberClass>
                            <iden:memberCode>%s</iden:memberCode>
                            <iden:subsystemCode>%s</iden:subsystemCode>
                        </xro:client>
                        <xro:service iden:objectType="SERVICE">
                            <iden:xRoadInstance>%s</iden:xRoadInstance>
                            <iden:memberClass>%s</iden:memberClass>
                            <iden:memberCode>%s</iden:memberCode>
                            <iden:subsystemCode>%s</iden:subsystemCode>
                            <iden:serviceCode>%s</iden:serviceCode>
                            %s
                        </xro:service>
                        %s
                        <xro:id>%s</xro:id>
                        <xro:userId>acl-parity</xro:userId>
                        <xro:protocolVersion>4.0</xro:protocolVersion>
                    </soapenv:Header>
                    <soapenv:Body>
                        <ap:%s xmlns:ap="http://acl-parity.test/producer"/>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(Stream.of(List.of(consumer), List.of(provider),
                        List.of(serviceCode, version, server, messageId, serviceCode))
                .flatMap(List::stream).toArray());
    }

    private static String securityServerElement(String[] server) {
        return ("<xro:securityServer iden:objectType=\"SERVER\"><iden:xRoadInstance>%s</iden:xRoadInstance>"
                + "<iden:memberClass>%s</iden:memberClass><iden:memberCode>%s</iden:memberCode>"
                + "<iden:serverCode>%s</iden:serverCode></xro:securityServer>")
                .formatted((Object[]) server);
    }

    private static String[] parts(String id) {
        var parts = id.split(":", -1);
        if (parts.length != ID_PARTS) {
            throw new IllegalArgumentException("expected INSTANCE:CLASS:MEMBER:CODE but got " + id);
        }
        return parts;
    }
}
