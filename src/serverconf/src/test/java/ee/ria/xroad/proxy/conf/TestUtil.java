/**
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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.Date;

import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;

/**
 * Contains server conf test utility methods.
 */
public final class TestUtil {

    static final String SERVER_CODE = "TestServer";

    static final String XROAD_INSTANCE = "XX";
    static final String MEMBER_CLASS = "FooClass";
    static final String MEMBER_CODE = "BarCode";
    static final String SUBSYSTEM = "SubSystem";

    static final String CLIENT_STATUS = "status";
    static final String CLIENT_CODE = "client";

    static final String SERVICEDESCRIPTION_URL = "servicedescriptionurl";

    static final String SERVICE_URL = "serviceUrl";
    static final String SERVICE_VERSION = "v1";
    static final String SERVICE_CODE = "serviceCode";
    static final String SERVICE_TITLE = "service";
    static final int SERVICE_TIMEOUT = 1234;

    static final int NUM_CLIENTS = 5;
    static final int NUM_SERVICEDESCRIPTIONS = 2;
    static final int NUM_SERVICES = 4;
    static final int NUM_TSPS = 5;

    static final String BASE64_CERT =
            "MIIDiDCCAnCgAwIBAgIIVYNTWA8JcLwwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE"
            + "AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw"
            + "HhcNMTIxMTE5MDkxNDIzWhcNMTQxMTE5MDkxNDIzWjATMREwDwYDVQQDDAhwcm9k"
            + "dWNlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALKNC381RiACCftv"
            + "ApBzk5HD5YHw0u9SOkwcIkn4cZ4eQWrlROnqHTpS9IVSBoOz6pjCx/FwxZTdpw0j"
            + "X+bRYpxnj11I2XKzHfhfa6BvL5VkaDtjGpOdSGMJUtrI6m9jFiYryEmYHWxPlL9V"
            + "pDK0KknevYm2BR23/xDHweBSZ7tkMENU1kXFWLunoBys+W0waR+Z8HH5WNuBLz8X"
            + "z2iz/6KQ5BoWSPJc9P5TXNOBB+5XyjBR2ogoAOtX53OJzu0wMgLpjuJGdfcpy1S9"
            + "ukU27B21i2MfZ6Tjhu9oKrAIgcMWJaHJ/gRX6iX1vXlfhUTkE1ACSfvhZdntKLzN"
            + "TZGEcxsCAwEAAaOBuzCBuDBYBggrBgEFBQcBAQRMMEowSAYIKwYBBQUHMAGGPGh0"
            + "dHA6Ly9pa3MyLXVidW50dS5jeWJlci5lZTo4MDgwL2VqYmNhL3B1YmxpY3dlYi9z"
            + "dGF0dXMvb2NzcDAdBgNVHQ4EFgQUUHtGmEl0Cuh/x/wj+UU5S7Wui48wDAYDVR0T"
            + "AQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAOBgNVHQ8B"
            + "Af8EBAMCBeAwDQYJKoZIhvcNAQEFBQADggEBACJqqey5Ywoegq+Rjo4v89AN78Ou"
            + "tKtRzQZtuCZP9+ZhY6ivCPK4F8Ne6qpWZb63OLORyQosDAvj6m0iCFMsUZS3nC0U"
            + "DR0VyP2WrOihBOFC4CA7H2X4l7pkSyMN73ZC6icXkbj9H0ix5/Bv3Ug64DK9SixG"
            + "RxMwLxouIzk7WvePQ6ywlhGvZRTXxhr0DwvfZnPXxHDPB2q+9pKzC9h2txG1tyD9"
            + "ffohEC/LKdGrHSe6hnTRedQUN3hcMQqCTc5cHsaB8bh5EaHrib3RR0YsOhjAd6IC"
            + "ms33BZnfNWQuGVTXw74Eu/P1JkwR0ReO+XuxxMp3DW2epMfL44OHWTb6JGY=";

    private TestUtil() {
    }

    /**
     * Creates in-memory test database and fills it with test data.
     * @throws Exception if an error occurs
     */
    public static void prepareDB() throws Exception {
        System.setProperty(
                SystemProperties.DATABASE_PROPERTIES,
                "src/test/resources/hibernate.properties");

        prepareDB(true);
    }

    /**
     * Creates in-memory test database and fills it with test data.
     * @param clean if true, database is cleaned
     * @throws Exception if an error occurs
     */
    public static void prepareDB(boolean clean) throws Exception {
        if (clean) {
            cleanDB();
        }

        doInTransaction(session -> {
            ServerConfType conf = createTestData(session);
            session.save(conf);
            return null;
        });
    }

    static void cleanDB() throws Exception {
        doInTransaction(session -> {
            Query q = session.createSQLQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    static ServerConfType createTestData(Session session) {
        ServerConfType conf = new ServerConfType();
        conf.setServerCode(SERVER_CODE);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            ClientType client = new ClientType();
            client.setConf(conf);
            conf.getClient().add(client);

            if (i == 0) {
                client.setIdentifier(createTestClientId());
                conf.setOwner(client);
                continue;
            } else {
                ClientId.Conf id;
                if (i == NUM_CLIENTS - 1) {
                    id = createTestClientId(client(i), SUBSYSTEM);
                } else {
                    id = createTestClientId(client(i));
                }

                client.setIdentifier(id);
                client.setClientStatus(CLIENT_STATUS + i);
            }

            switch (i) {
                case 1:
                    client.setIsAuthentication("SSLAUTH");
                    CertificateType ct = new CertificateType();
                    ct.setData(decodeBase64(BASE64_CERT));
                    client.getIsCert().add(ct);
                    break;
                case 2:
                    client.setIsAuthentication("SSLNOAUTH");
                    break;
                default:
                    client.setIsAuthentication("NOSSL");
                    break;
            }

            for (int j = 0; j < NUM_SERVICEDESCRIPTIONS; j++) {
                ServiceDescriptionType serviceDescription = new ServiceDescriptionType();
                serviceDescription.setClient(client);
                serviceDescription.setUrl(SERVICEDESCRIPTION_URL + j);
                serviceDescription.setType(DescriptionType.WSDL);

                for (int k = 0; k < NUM_SERVICES; k++) {
                    ServiceType service = new ServiceType();
                    service.setServiceDescription(serviceDescription);
                    service.setTitle(SERVICE_TITLE + k);
                    service.setServiceCode(service(j, k));

                    if (k != NUM_SERVICES - 2) {
                        service.setServiceVersion(SERVICE_VERSION);
                    }

                    service.setUrl(SERVICE_URL + k);
                    service.setTimeout(SERVICE_TIMEOUT);

                    service.setSslAuthentication(k % 2 == 0);

                    serviceDescription.getService().add(service);
                }

                if (j == NUM_SERVICEDESCRIPTIONS - 1) {
                    serviceDescription.setDisabled(true);
                    serviceDescription.setDisabledNotice("disabledNotice");
                }

                client.getServiceDescription().add(serviceDescription);
            }

            String serviceCode = service(1, 1);
            final EndpointType endpoint = new EndpointType(serviceCode, "*", "**", false);
            session.persist(endpoint);

            client.getEndpoint().add(endpoint);

            client.getAcl().add(
                    createAccessRight(endpoint, client.getIdentifier()));

            ClientId.Conf cl = ClientId.Conf.create("XX", "memberClass", "memberCode" + i);
            client.getAcl().add(createAccessRight(endpoint, cl));

            ServiceId.Conf se = ServiceId.Conf.create("XX", "memberClass",
                    "memberCode" + i, null, "serviceCode" + i);
            client.getAcl().add(createAccessRight(endpoint, se));

            LocalGroupId.Conf lg = LocalGroupId.Conf.create("testGroup" + i);
            client.getAcl().add(createAccessRight(endpoint, lg));

            //rest service
            ServiceDescriptionType serviceDescription = new ServiceDescriptionType();
            serviceDescription.setClient(client);
            serviceDescription.setUrl(SERVICEDESCRIPTION_URL + "rest");
            serviceDescription.setType(DescriptionType.REST);

            ServiceType service = new ServiceType();
            service.setServiceDescription(serviceDescription);
            service.setTitle(SERVICE_TITLE + "REST");
            service.setServiceCode("rest");

            EndpointType restEndpoint = new EndpointType(service.getServiceCode(), "GET", "/api/**", false);
            session.persist(restEndpoint);
            client.getAcl().add(createAccessRight(restEndpoint, client.getIdentifier()));

            EndpointType restEndpoint2 = new EndpointType(service.getServiceCode(), "POST", "/api/test/*", false);
            session.persist(restEndpoint2);
            client.getAcl().add(createAccessRight(restEndpoint2, client.getIdentifier()));

            LocalGroupType localGroup = new LocalGroupType();
            localGroup.setGroupCode("localGroup" + i);
            localGroup.setDescription("local group description");
            localGroup.setUpdated(new Date());
            GroupMemberType localGroupMember = new GroupMemberType();
            localGroupMember.setAdded(new Date());
            localGroupMember.setGroupMemberId(cl);
            localGroup.getGroupMember().add(localGroupMember);

            client.getLocalGroup().add(localGroup);
        }

        for (int j = 0; j < NUM_TSPS; j++) {
            TspType tsp = new TspType();
            tsp.setName("tspName" + j);
            tsp.setUrl("tspUrl" + j);
            conf.getTsp().add(tsp);
        }

        return conf;
    }

    static ServiceId.Conf createTestServiceId(String memberCode,
            String serviceCode) {
        return ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode, null,
                serviceCode);
    }

    static ServiceId.Conf createTestServiceId(String memberCode, String serviceCode,
            String serviceVerison) {
        return ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode, null,
                serviceCode, serviceVerison);
    }

    static ServiceId.Conf createTestServiceId(ClientId member, String serviceCode,
            String serviceVersion) {
        return ServiceId.Conf.create(member, serviceCode, serviceVersion);
    }

    static ClientId.Conf createTestClientId() {
        return ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, MEMBER_CODE);
    }

    static ClientId.Conf createTestClientId(String memberCode) {
        return createTestClientId(memberCode, null);
    }

    static ClientId.Conf createTestClientId(String memberCode,
            String subsystemCode) {
        return ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode,
                subsystemCode);
    }

    static String client(int idx) {
        return CLIENT_CODE + "-" + idx;
    }

    static String service(int serviceDescriptionIdx, int serviceIdx) {
        return SERVICE_CODE + "-" + serviceDescriptionIdx + "-" + serviceIdx;
    }

    static AccessRightType createAccessRight(EndpointType endpoint, XRoadId xRoadId) {
        AccessRightType accessRight = new AccessRightType();
        accessRight.setEndpoint(endpoint);
        accessRight.setSubjectId(xRoadId);
        accessRight.setRightsGiven(new Date());

        return accessRight;
    }

}
