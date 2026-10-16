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
package org.niis.xroad.globalconf.impl;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.impl.extension.GlobalConfExtensionFactoryImpl;
import org.niis.xroad.globalconf.model.ApprovedConnectorTlsCAInfo;
import org.niis.xroad.globalconf.model.ServerSystemValues;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class GlobalConfVer7Test {
    private static final String GOOD_CONF_DIR = "../globalconf-core/src/test/resources/globalconf_good_v7";

    private static final ClientId PRODUCER = ClientId.Conf.create("EE", "BUSINESS", "producer");
    private static final ClientId CONSUMER = ClientId.Conf.create("EE", "BUSINESS", "consumer");
    private static final ClientId FOO = ClientId.Conf.create("EE", "BUSINESS", "foo");
    private static final ClientId FOO_SUBSYSTEM = ClientId.Conf.create("EE", "BUSINESS", "foo", "foosubsystem");
    private static final SecurityServerId PRODUCER_SERVER = SecurityServerId.Conf.create(PRODUCER, "producerServerCode");
    private static final SecurityServerId PRODUCER_SERVER_2 = SecurityServerId.Conf.create(PRODUCER, "producerServerCode2");
    private static final SecurityServerId CONSUMER_SERVER = SecurityServerId.Conf.create(CONSUMER, "consumerServerCode");
    private static final SecurityServerId FOO_SERVER = SecurityServerId.Conf.create(FOO, "fooServerCode");
    private static final SecurityServerId FOO_BAR_SERVER = SecurityServerId.Conf.create(FOO, "FooBarServerCode");

    @ClassRule
    public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    private static GlobalConfProvider globalConfProvider;

    @BeforeClass
    public static void setUpBeforeClass() {
        globalConfProvider = load(GOOD_CONF_DIR);
    }

    private static GlobalConfProvider load(String confDir) {
        var source = new FileSystemGlobalConfSource(confDir);
        return new GlobalConfImpl(source, new GlobalConfExtensions(source, new GlobalConfExtensionFactoryImpl()));
    }

    @Test
    public void getApprovedConnectorTlsCAs() {
        Collection<ApprovedConnectorTlsCAInfo> eeConnectorTlsCas = globalConfProvider.getApprovedConnectorTlsCAs("EE");
        ApprovedConnectorTlsCAInfo connectorTlsCa = eeConnectorTlsCas.stream()
                .filter(ca -> ca.getName().equals("Test DS TLS CA")).findFirst().get();

        assertEquals("Test DS TLS CA", connectorTlsCa.getName());
        assertEquals(TestCertUtil.getCaCert(), connectorTlsCa.getTopCaCert());
        assertEquals(1, connectorTlsCa.getIntermediateCaCerts().size());
        assertEquals(TestCertUtil.getTspCert(), connectorTlsCa.getIntermediateCaCerts().getFirst());
        assertEquals("http://testca.com/acme", connectorTlsCa.getAcmeServerDirectoryUrl());
        assertEquals("192.99.88.7", connectorTlsCa.getAcmeServerIpAddress());
        assertEquals("ds-tls-profile", connectorTlsCa.getConnectorTlsCertificateProfileId());
    }

    @Test
    public void getCredentialIssuerDids() {
        Collection<String> eeIssuerDids = globalConfProvider.getCredentialIssuerDids("EE");

        assertEquals(2, eeIssuerDids.size());
        assertEquals(Set.of("did:web:cs1.ee%3A443:issuer", "did:web:cs2.ee%3A443:issuer"), Set.copyOf(eeIssuerDids));
    }

    @Test
    public void getServerSystemValuesOfServersSharingOneAddress() {
        assertEquals(Optional.of(new ServerSystemValues("did:web:127.0.0.1%3A7183:v1:system",
                "https://127.0.0.1:8183/api/dsp")), globalConfProvider.getServerSystemValues(PRODUCER_SERVER));
        assertEquals(Optional.of(new ServerSystemValues("did:web:127.0.0.1%3A7283:v1:system",
                "https://127.0.0.1:8283/api/dsp")), globalConfProvider.getServerSystemValues(PRODUCER_SERVER_2));
        assertEquals(Optional.of(new ServerSystemValues("did:web:foo.bar.baz%3A7183:v1:system",
                "https://foo.bar.baz:8183/api/dsp")), globalConfProvider.getServerSystemValues(FOO_BAR_SERVER));
    }

    @Test
    public void getServerSystemValuesOfServerWithoutValuesIsEmpty() {
        assertEquals(Optional.empty(), globalConfProvider.getServerSystemValues(CONSUMER_SERVER));
        assertEquals(Optional.empty(), globalConfProvider.getServerSystemValues(FOO_SERVER));
    }

    @Test
    public void getMemberDidPerServer() {
        assertEquals(Optional.of("did:web:127.0.0.1%3A7183:v1:EE:BUSINESS:producer"),
                globalConfProvider.getMemberDid(PRODUCER, PRODUCER_SERVER));
        assertEquals(Optional.of("did:web:127.0.0.1%3A7283:v1:EE:BUSINESS:producer"),
                globalConfProvider.getMemberDid(PRODUCER, PRODUCER_SERVER_2));
        assertEquals(Optional.of("did:web:www.foo.com%3A7183:v1:EE:BUSINESS:consumer"),
                globalConfProvider.getMemberDid(CONSUMER, CONSUMER_SERVER));
    }

    @Test
    public void getMemberDidOfSubsystemResolvesToMember() {
        assertEquals(globalConfProvider.getMemberDid(FOO, FOO_BAR_SERVER),
                globalConfProvider.getMemberDid(FOO_SUBSYSTEM, FOO_BAR_SERVER));
        assertEquals(Optional.of("did:web:foo.bar.baz%3A7183:v2:EE:BUSINESS:foo"),
                globalConfProvider.getMemberDid(FOO_SUBSYSTEM, FOO_BAR_SERVER));
    }

    @Test
    public void getMemberDidReturnsUnknownSchemeVersionUnchanged() {
        assertEquals(Optional.of("did:web:foo.bar.baz%3A7183:v2:EE:BUSINESS:foo"),
                globalConfProvider.getMemberDid(FOO, FOO_BAR_SERVER));
    }

    @Test
    public void getMemberDidWithoutPublishedDidIsEmpty() {
        assertEquals(Optional.empty(), globalConfProvider.getMemberDid(PRODUCER, CONSUMER_SERVER));
        assertEquals(Optional.empty(), globalConfProvider.getMemberDid(FOO, FOO_SERVER));
    }

    @Test
    public void lookupsOfUnknownServerAreEmpty() {
        var unknownServer = SecurityServerId.Conf.create(PRODUCER, "noSuchServerCode");

        assertEquals(Optional.empty(), globalConfProvider.getServerSystemValues(unknownServer));
        assertEquals(Optional.empty(), globalConfProvider.getMemberDid(PRODUCER, unknownServer));
    }

    @Test
    public void lookupsOfUnknownInstanceAreEmpty() {
        var unknownInstance = ClientId.Conf.create("XX", "BUSINESS", "producer");
        var serverOfUnknownInstance = SecurityServerId.Conf.create(unknownInstance, "producerServerCode");

        assertEquals(Optional.empty(), globalConfProvider.getServerSystemValues(serverOfUnknownInstance));
        assertEquals(Optional.empty(), globalConfProvider.getMemberDid(unknownInstance, serverOfUnknownInstance));
    }

    @Test
    public void lookupsWithoutPublishedValuesAreEmpty() throws IOException {
        File confDir = TEMP_FOLDER.newFolder("without-published-values");
        FileUtils.copyDirectory(new File(GOOD_CONF_DIR), confDir);
        File sharedParams = new File(confDir, "EE/shared-params.xml");
        String withoutPublishedValues = FileUtils.readFileToString(sharedParams, UTF_8)
                .replaceAll("\\s*<did securityServer=\"[^\"]*\">[^<]*</did>", "")
                .replaceAll("\\s*<dspBaseUrl>[^<]*</dspBaseUrl>", "")
                .replaceAll("\\s*<systemDid>[^<]*</systemDid>", "")
                .replaceAll("<securityServer id=\"[^\"]*\">", "<securityServer>");
        FileUtils.writeStringToFile(sharedParams, withoutPublishedValues, UTF_8);

        GlobalConfProvider stripped = load(confDir.getPath());

        assertEquals(Optional.empty(), stripped.getServerSystemValues(PRODUCER_SERVER));
        assertEquals(Optional.empty(), stripped.getMemberDid(PRODUCER, PRODUCER_SERVER));
        assertEquals(Optional.empty(), stripped.getMemberDid(FOO_SUBSYSTEM, FOO_BAR_SERVER));
    }
}
