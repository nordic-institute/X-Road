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

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/** Builds the SOAP envelope a client sends to the consumer Security Server's proxy. */
public final class SoapRequests {

    public static final String METRICS = "getSecurityServerMetrics";
    public static final String HEALTH_DATA = "getSecurityServerHealthData";
    public static final String OPERATIONAL_DATA = "getSecurityServerOperationalData";

    private static final int MEMBER_PARTS = 3;
    private static final int ID_PARTS = 4;

    private SoapRequests() {
    }

    /**
     * Client and provider ids are {@code INSTANCE:CLASS:MEMBER:SUBSYSTEM}; the optional security server hint is
     * {@code INSTANCE:CLASS:MEMBER:SERVERCODE}. A {@code null} version or hint leaves its header element out.
     */
    public static String envelope(String consumerClientId, String providerClientId, String serviceCode,
                                  String serviceVersion, String securityServerHint, String messageId) {
        return envelope(consumerClientId, providerClientId, serviceCode, serviceVersion, securityServerHint,
                messageId, "<ap:%s xmlns:ap=\"http://acl-parity.test/producer\"/>".formatted(serviceCode));
    }

    /**
     * The header names no service version; the target service version, when given, goes in the body beside the
     * target service code.
     */
    public static String metaserviceEnvelope(String consumerClientId, String providerClientId,
                                             String metaserviceCode, String targetServiceCode,
                                             String targetServiceVersion, String messageId) {
        var version = targetServiceVersion == null
                ? "" : "<xro:serviceVersion>%s</xro:serviceVersion>".formatted(targetServiceVersion);
        var body = targetServiceCode == null
                ? "<xro:%s/>".formatted(metaserviceCode)
                : "<xro:%1$s><xro:serviceCode>%2$s</xro:serviceCode>%3$s</xro:%1$s>"
                        .formatted(metaserviceCode, targetServiceCode, version);
        return envelope(consumerClientId, providerClientId, metaserviceCode, null, null, messageId, body);
    }

    /**
     * Client and owner are named at member level when the id has no subsystem. {@code monitoringCode} is
     * {@link #METRICS}, {@link #HEALTH_DATA} or {@link #OPERATIONAL_DATA}; {@code filterClientId} applies to
     * health data only.
     */
    public static String monitoringEnvelope(String consumerClientId, String ownerMemberId, String monitoringCode,
                                            String securityServerId, String filterClientId, String messageId) {
        var consumer = clientParts(consumerClientId);
        var owner = clientParts(ownerMemberId);
        var server = parts(securityServerId);
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers"
                        xmlns:mon="http://x-road.eu/xsd/monitoring" xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd">
                    <soapenv:Header>
                        <xro:client iden:objectType="%s">%s</xro:client>
                        <xro:service iden:objectType="SERVICE">%s<iden:serviceCode>%s</iden:serviceCode></xro:service>
                        %s
                        <xro:id>%s</xro:id>
                        <xro:protocolVersion>4.0</xro:protocolVersion>
                    </soapenv:Header>
                    <soapenv:Body>
                        %s
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(objectType(consumer), identifierElements(consumer), identifierElements(owner),
                monitoringCode, securityServerElement(server), messageId,
                monitoringBody(monitoringCode, filterClientId));
    }

    private static String monitoringBody(String monitoringCode, String filterClientId) {
        return switch (monitoringCode) {
            case METRICS -> "<mon:getSecurityServerMetrics/>";
            case HEALTH_DATA -> "<om:getSecurityServerHealthData>%s</om:getSecurityServerHealthData>"
                    .formatted(filterClientId == null ? "" : filterCriteria(clientParts(filterClientId)));
            case OPERATIONAL_DATA -> ("<om:getSecurityServerOperationalData><om:searchCriteria>"
                    + "<om:recordsFrom>0</om:recordsFrom><om:recordsTo>%d</om:recordsTo></om:searchCriteria>"
                    + "</om:getSecurityServerOperationalData>").formatted(Instant.now().getEpochSecond());
            default -> throw new IllegalArgumentException("not a monitoring metaservice: " + monitoringCode);
        };
    }

    private static String filterCriteria(String[] client) {
        return "<om:filterCriteria><om:client iden:objectType=\"%s\">%s</om:client></om:filterCriteria>"
                .formatted(objectType(client), identifierElements(client));
    }

    private static String objectType(String[] client) {
        return client.length == MEMBER_PARTS ? "MEMBER" : "SUBSYSTEM";
    }

    private static String identifierElements(String[] client) {
        var elements = new StringBuilder("<iden:xRoadInstance>%s</iden:xRoadInstance>".formatted(client[0]))
                .append("<iden:memberClass>%s</iden:memberClass>".formatted(client[1]))
                .append("<iden:memberCode>%s</iden:memberCode>".formatted(client[2]));
        if (client.length == ID_PARTS) {
            elements.append("<iden:subsystemCode>%s</iden:subsystemCode>".formatted(client[MEMBER_PARTS]));
        }
        return elements.toString();
    }

    private static String[] clientParts(String id) {
        var parts = id.split(":", -1);
        if (parts.length != MEMBER_PARTS && parts.length != ID_PARTS) {
            throw new IllegalArgumentException("expected INSTANCE:CLASS:MEMBER[:SUBSYSTEM] but got " + id);
        }
        return parts;
    }

    private static String envelope(String consumerClientId, String providerClientId, String serviceCode,
                                   String serviceVersion, String securityServerHint, String messageId,
                                   String body) {
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
                        %s
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(Stream.of(List.of(consumer), List.of(provider),
                        List.of(serviceCode, version, server, messageId, body))
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
