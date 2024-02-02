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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.TestCertUtil.getCertChainCert;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the global configuration functionality.
 */
public class GlobalConfTest {
    private static final String GOOD_CONF_DIR = "../common-util/src/test/resources/globalconf_good_v2";
    private static final Path GOOD_CONF_FILES = Paths.get(GOOD_CONF_DIR, "files");

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Sets up the test configuration.
     *
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        GlobalConf.reset();
        System.setProperty(SystemProperties.CONFIGURATION_PATH, GOOD_CONF_DIR);

        createConfigurationFiles();

        GlobalConf.reload();
    }

    private static void createConfigurationFiles() throws IOException {
        List<String> confFiles = new ArrayList<>();
        File files = GOOD_CONF_FILES.toFile();

        confFiles.add(getConfFileName("bar", "shared-params.xml"));
        confFiles.add(getConfFileName("EE", "private-params.xml"));
        confFiles.add(getConfFileName("EE", "shared-params.xml"));
        confFiles.add(getConfFileName("foo", "private-params.xml"));
        confFiles.add(getConfFileName("foo", "shared-params.xml"));

        FileUtils.writeLines(files, StandardCharsets.UTF_8.name(), confFiles);
    }

    private static String getConfFileName(String instanceIdentifier, String fileName) {
        return Paths.get(GOOD_CONF_DIR, instanceIdentifier, fileName).toAbsolutePath().normalize().toString();
    }

    @AfterClass
    public static void cleanUpAfterClass() {
        deleteConfigurationFiles(GOOD_CONF_FILES);
    }

    private static void deleteConfigurationFiles(Path confFiles) {
        try {
            Files.delete(confFiles);
        } catch (IOException e) {
            // Ignore.
        }
    }

    @Test
    public void isValidConfiguration() {
        assertTrue(GlobalConf.isValid());
    }

    /**
     * Tests getting all instance identifiers from the configuration.
     */
    @Test
    public void getInstanceIdentifiers() {
        assertTrue(Arrays.asList("EE", "bar", "foo").containsAll(GlobalConf.getInstanceIdentifiers()));
    }

    /**
     * Tests checking if subject is in global group.
     */
    @Test
    public void isSubjectInGlobalGroup() {
        assertTrue(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member1", "subsys"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertTrue(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertFalse(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2", "subsys"),
                GlobalGroupId.Conf.create("EE", "Test group"))
        );
        assertFalse(GlobalConf.isSubjectInGlobalGroup(
                ClientId.Conf.create("EE", "BUSINESS", "member2"),
                GlobalGroupId.Conf.create("non-existent-instance", "non-existent-group"))
        );
    }

    /**
     * Tests getting the global group description.
     */
    @Test
    public void getGlobalGroupDescription() {
        assertEquals("Description", GlobalConf.getGlobalGroupDescription(GlobalGroupId.Conf.create("EE", "Test group")));
        assertNull("Description", GlobalConf.getGlobalGroupDescription(GlobalGroupId.Conf.create("EE", "foo")));
    }

    /**
     * Tests getting the members.
     */
    @Test
    public void getMembers() {
        List<ClientId> expected = Arrays.asList(newClientId("producer"), newClientId("consumer"),
                newClientId("foo"), newClientId("foo", "foosubsystem"));

        assertEquals(expected, GlobalConf.getMembers("EE").stream()
                .map(i -> i.getId())
                .collect(Collectors.toList()));
    }

    /**
     * Tests getting the provider addresses.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getProviderAddress() {
        ClientId consumer = newClientId("consumer");
        ClientId producer = newClientId("producer");

        Set<String> expected = new HashSet<>();
        expected.add("https://www.foo.com/bar");
        expected.add("127.0.0.1");

        assertEquals(expected, GlobalConf.getProviderAddress(consumer));
        assertEquals(singleton("127.0.0.1"), GlobalConf.getProviderAddress(producer));
    }

    /**
     * Tests getting the CA certificate for an organization.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getCaCertForOrg() throws Exception {
        X509Certificate org = TestCertUtil.getProducer().certChain[0];
        assertNotNull(org);

        X509Certificate x509 = GlobalConf.getCaCert("EE", org);

        assertNotNull(x509);
    }

    /**
     * Tests getting the certificate chain for an organization.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getCertChain() throws Exception {
        X509Certificate org = getCertChainCert("user_3.p12");

        assertNotNull(org);

        CertChain certChain = GlobalConf.getCertChain("EE", org);
        List<X509Certificate> chain = certChain.getAllCerts();

        assertEquals(5, chain.size());
        assertEquals(getCertChainCert("root_ca.p12"), chain.get(4));
        assertEquals(getCertChainCert("ca_1.p12"), chain.get(3));
        assertEquals(getCertChainCert("ca_2.p12"), chain.get(2));
        assertEquals(getCertChainCert("ca_3.p12"), chain.get(1));
        assertEquals(getCertChainCert("user_3.p12"), chain.get(0));
    }

    /**
     * Tests getting all OCSP responder certificates.
     */
    @Test
    public void getAllOcspResponderCertificates() {
        List<X509Certificate> ocspResponderCerts = GlobalConf.getOcspResponderCertificates();

        for (X509Certificate cert : ocspResponderCerts) {
            assertNotNull("Got null certificate", cert);
        }

        assertEquals(12, ocspResponderCerts.size());
    }

    /**
     * Tests getting the OCSP responder addresses of an organization.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getOcspResponderAddresses() throws Exception {
        // Does not matter which org exactly as long as CA is adminca1
        X509Certificate orgCert = TestCertUtil.getConsumer().certChain[0];
        List<String> actualAddresses =
                GlobalConf.getOcspResponderAddresses(orgCert);
        List<String> expectedAddresses = Arrays.asList(
                "http://127.0.0.1:8082/ocsp",
                "http://www.example.net/ocsp");

        for (String address : expectedAddresses) {
            assertTrue(actualAddresses.contains(address));
        }
    }

    /**
     * Tests that the authentication certificate matches the member it belongs to.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void authCertMatchesMember() throws Exception {
        X509Certificate producerCert = TestCertUtil.getProducer().certChain[0];
        X509Certificate consumerCert = TestCertUtil.getConsumer().certChain[0];
        ClientId producer = newClientId("producer");
        ClientId consumer = newClientId("consumer");

        assertTrue(GlobalConf.authCertMatchesMember(producerCert, producer));
        assertFalse(GlobalConf.authCertMatchesMember(consumerCert, producer));
        assertFalse(GlobalConf.authCertMatchesMember(producerCert, consumer));
        assertTrue(GlobalConf.authCertMatchesMember(consumerCert, consumer));
    }

    /**
     * Tests getting the service identifier for an organization.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getServerId() throws Exception {
        SecurityServerId server =
                SecurityServerId.Conf.create("EE", "BUSINESS", "foo",
                        "fooServerCode");
        X509Certificate cert = TestCertUtil.getProducer().certChain[0];
        assertEquals(server, GlobalConf.getServerId(cert));
    }

    /**
     * Tests getting the owner of a security server.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getServerOwner() throws Exception {
        SecurityServerId serverId = SecurityServerId.Conf.create("EE", "BUSINESS", "producer", "producerServerCode");

        ClientId owner = ClientId.Conf.create("EE", "BUSINESS", "producer");
        ClientId ownerFromGlobalConf = GlobalConf.getServerOwner(serverId);

        assertEquals(owner, ownerFromGlobalConf);

        serverId = SecurityServerId.Conf.create("EE", "BUSINESS", "producer", "unknown");
        ownerFromGlobalConf = GlobalConf.getServerOwner(serverId);

        assertNull(ownerFromGlobalConf);
    }

    /**
     * Tests whether a certificate is an OCSP responder certificate.
     */
    @Test
    public void isOcspResponderCert() {
        X509Certificate caCert = TestCertUtil.getCaCert();

        assertFalse(GlobalConf.isOcspResponderCert(caCert, caCert));

        PKCS12 ocspSigner = TestCertUtil.getOcspSigner();
        X509Certificate ocspCert = ocspSigner.certChain[0];
        assertTrue(GlobalConf.isOcspResponderCert(caCert, ocspCert));
    }

    /**
     * Tests getting the subject name from a certificate.
     * @throws Exception if an error occurs
     */
    @Test
    public void getSubjectName() throws Exception {
        X509Certificate cert = TestCertUtil.getProducer().certChain[0];

        ClientId expected = ClientId.Conf.create("EE", "BUSINESS", "producer");
        ClientId actual = GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(ClientId.Conf.create("EE", "foo", "bar"), "baz"), cert);

        assertEquals(expected, actual);
    }

    /**
     * Tests getting the verification certificates.
     */
    @Test
    public void getVerificationCaCerts() {
        List<X509Certificate> certs = GlobalConf.getVerificationCaCerts();

        assertEquals(4, certs.size());
    }

    /**
     * Tests getting the known addresses.
     */
    @Test
    public void getKnownAddresses() {
        Set<String> expectedAddresses = new HashSet<>(
                Arrays.asList(
                    "127.0.0.1",
                    "https://www.foo.com/bar",
                    "https://foo.bar.baz"));
        Set<String> actualAddresses = GlobalConf.getKnownAddresses();

        assertEquals(expectedAddresses, actualAddresses);
    }

    /**
     * Tests getting the TSP certificates.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getTspCerts() throws Exception {
        List<X509Certificate> tspCertificates = GlobalConf.getTspCertificates();

        assertEquals(3, tspCertificates.size());
    }

    /**
     * Tests getting the global settings.
     */
    @Test
    public void getGlobalSettings() {
        String serviceAddr = GlobalConf.getManagementRequestServiceAddress();

        assertEquals("http://mgmt.com:1234", serviceAddr);
        assertEquals(newClientId("servicemember2"), GlobalConf.getManagementRequestService());
        assertEquals(42, GlobalConf.getOcspFreshnessSeconds());
    }

    /**
     * Tests that a client identifier is a security server client.
     */
    @Test
    public void isSecurityServerClient() {
        ClientId client1 = ClientId.Conf.create("EE", "BUSINESS", "consumer");
        ClientId client2 = ClientId.Conf.create("EE", "BUSINESS", "producer");
        ClientId client3 = ClientId.Conf.create("EE", "BUSINESS", "foo", "foosubsystem");
        ClientId client4 = ClientId.Conf.create("EE", "xx", "foo", "foosubsystem");

        SecurityServerId server1 = SecurityServerId.Conf.create("EE", "BUSINESS", "producer", "producerServerCode");
        SecurityServerId server2 = SecurityServerId.Conf.create("EE", "BUSINESS", "producer", "foo");
        SecurityServerId server3 = SecurityServerId.Conf.create("EE", "BUSINESS", "foo", "FooBarServerCode");

        assertTrue(GlobalConf.isSecurityServerClient(client1, server1));
        assertTrue(GlobalConf.isSecurityServerClient(client2, server1));
        assertTrue(GlobalConf.isSecurityServerClient(client3, server1));
        assertFalse(GlobalConf.isSecurityServerClient(client4, server1));

        assertFalse(GlobalConf.isSecurityServerClient(client1, server2));
        assertFalse(GlobalConf.isSecurityServerClient(client2, server2));
        assertFalse(GlobalConf.isSecurityServerClient(client3, server2));

        assertFalse(GlobalConf.isSecurityServerClient(client3, server3));
    }

    /**
     * Tests getting the security server identifiers.
     */
    @Test
    public void getSecurityServers() {
        String instanceIdentifier = "EE";

        SecurityServerId server1 = SecurityServerId.Conf.create("EE", "BUSINESS", "producer", "producerServerCode");
        SecurityServerId server2 = SecurityServerId.Conf.create("EE", "BUSINESS", "consumer", "consumerServerCode");
        SecurityServerId server3 = SecurityServerId.Conf.create("EE", "BUSINESS", "foo", "fooServerCode");
        SecurityServerId server4 = SecurityServerId.Conf.create("EE", "BUSINESS", "foo", "FooBarServerCode");

        List<SecurityServerId> expectedList = new ArrayList<SecurityServerId>();

        expectedList.add(server1);
        expectedList.add(server2);
        expectedList.add(server3);
        expectedList.add(server4);

        assertEquals(expectedList, GlobalConf.getSecurityServers(instanceIdentifier));
    }

    /**
     * Tests getting approved CAs.
     */
    @Test
    public void getApprovedCAs() {
        Collection<ApprovedCAInfo> cas = GlobalConf.getApprovedCAs("EE");

        assertEquals(5, cas.size());
    }

    private static ClientId newClientId(String name) {
        return ClientId.Conf.create("EE", "BUSINESS", name);
    }

    private static ClientId newClientId(String name, String subsystem) {
        return ClientId.Conf.create("EE", "BUSINESS", name, subsystem);
    }
}
