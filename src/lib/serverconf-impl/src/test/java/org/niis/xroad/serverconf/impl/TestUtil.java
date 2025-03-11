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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.hibernate.Session;
import org.niis.xroad.serverconf.entity.AccessRightTypeEntity;
import org.niis.xroad.serverconf.entity.CertificateTypeEntity;
import org.niis.xroad.serverconf.entity.ClientIdConfEntity;
import org.niis.xroad.serverconf.entity.ClientTypeEntity;
import org.niis.xroad.serverconf.entity.EndpointTypeEntity;
import org.niis.xroad.serverconf.entity.GroupMemberTypeEntity;
import org.niis.xroad.serverconf.entity.LocalGroupTypeEntity;
import org.niis.xroad.serverconf.entity.ServerConfTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceDescriptionTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceIdConfEntity;
import org.niis.xroad.serverconf.entity.ServiceTypeEntity;
import org.niis.xroad.serverconf.entity.TspTypeEntity;
import org.niis.xroad.serverconf.entity.XRoadIdConfEntity;
import org.niis.xroad.serverconf.mapper.XroadIdConfMapper;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.util.Date;

import static ee.ria.xroad.common.util.EncoderUtils.decodeBase64;
import static org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx.doInTransaction;

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
            ServerConfTypeEntity conf = createTestData(session);
            session.persist(conf);
            return null;
        });
    }

    static void cleanDB() throws Exception {
        doInTransaction(session -> {
            var q = session.createNativeMutationQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    static ServerConfTypeEntity createTestData(Session session) {
        ServerConfTypeEntity conf = new ServerConfTypeEntity();
        conf.setServerCode(SERVER_CODE);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            ClientTypeEntity client = new ClientTypeEntity();
            client.setConf(conf);
            conf.getClient().add(client);

            if (i == 0) {
                client.setIdentifier(createClientIdConfEntity());
                conf.setOwner(client);
                continue;
            } else {
                ClientIdConfEntity id;
                if (i == NUM_CLIENTS - 1) {
                    id = createClientIdConfEntity(client(i), SUBSYSTEM);
                } else {
                    id = createClientIdConfEntity(client(i));
                }

                client.setIdentifier(id);
                client.setClientStatus(CLIENT_STATUS + i);
            }

            switch (i) {
                case 1:
                    client.setIsAuthentication("SSLAUTH");
                    CertificateTypeEntity ct = new CertificateTypeEntity();
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
                ServiceDescriptionTypeEntity serviceDescription = new ServiceDescriptionTypeEntity();
                serviceDescription.setClient(client);
                serviceDescription.setUrl(SERVICEDESCRIPTION_URL + j);
                serviceDescription.setType(DescriptionType.WSDL);

                for (int k = 0; k < NUM_SERVICES; k++) {
                    ServiceTypeEntity service = new ServiceTypeEntity();
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
            final EndpointTypeEntity endpoint = new EndpointTypeEntity();
            endpoint.setServiceCode(serviceCode);
            endpoint.setMethod("*");
            endpoint.setPath("**");
            endpoint.setGenerated(false);
            session.persist(endpoint);

            client.getEndpoint().add(endpoint);

            client.getAcl().add(
                    createAccessRight(endpoint, client.getIdentifier()));

            ClientIdConfEntity cl = XroadIdConfMapper.get().toEntity(ClientId.Conf.create("XX", "memberClass", "memberCode" + i));
            client.getAcl().add(createAccessRight(endpoint, cl));

            ServiceIdConfEntity se = XroadIdConfMapper.get().toEntity(ServiceId.Conf.create("XX", "memberClass",
                    "memberCode" + i, "subsystemCode", "serviceCode" + i));
            client.getAcl().add(createAccessRight(endpoint, se));

            LocalGroupId.Conf lg = LocalGroupId.Conf.create("testGroup" + i);
            client.getAcl().add(createAccessRight(endpoint, XroadIdConfMapper.get().toEntity(lg)));

            //rest service
            ServiceDescriptionTypeEntity serviceDescription = new ServiceDescriptionTypeEntity();
            serviceDescription.setClient(client);
            serviceDescription.setUrl(SERVICEDESCRIPTION_URL + "rest");
            serviceDescription.setType(DescriptionType.REST);

            ServiceTypeEntity service = new ServiceTypeEntity();
            service.setServiceDescription(serviceDescription);
            service.setTitle(SERVICE_TITLE + "REST");
            service.setServiceCode("rest");

            EndpointTypeEntity restEndpoint = new EndpointTypeEntity();
            restEndpoint.setServiceCode(service.getServiceCode());
            restEndpoint.setMethod("GET");
            restEndpoint.setPath("/api/**");
            restEndpoint.setGenerated(false);
            session.persist(restEndpoint);
            client.getEndpoint().add(restEndpoint);
            client.getAcl().add(createAccessRight(restEndpoint, client.getIdentifier()));

            EndpointTypeEntity restEndpoint2 = new EndpointTypeEntity();
            restEndpoint2.setServiceCode(service.getServiceCode());
            restEndpoint2.setMethod("POST");
            restEndpoint2.setPath("/api/test/*");
            restEndpoint2.setGenerated(false);
            session.persist(restEndpoint2);
            client.getEndpoint().add(restEndpoint2);
            client.getAcl().add(createAccessRight(restEndpoint2, client.getIdentifier()));

            LocalGroupTypeEntity localGroup = new LocalGroupTypeEntity();
            localGroup.setGroupCode("localGroup" + i);
            localGroup.setDescription("local group description");
            localGroup.setUpdated(new Date());
            GroupMemberTypeEntity localGroupMember = new GroupMemberTypeEntity();
            localGroupMember.setAdded(new Date());
            localGroupMember.setGroupMemberId(cl);
            localGroup.getGroupMember().add(localGroupMember);

            client.getLocalGroup().add(localGroup);
        }

        for (int j = 0; j < NUM_TSPS; j++) {
            TspTypeEntity tsp = new TspTypeEntity();
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

    static ClientIdConfEntity createClientIdConfEntity() {
        return XroadIdConfMapper.get().toEntity(ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, MEMBER_CODE));
    }

    static ClientIdConfEntity createClientIdConfEntity(String memberCode) {
        return createClientIdConfEntity(memberCode, null);
    }

    static ClientIdConfEntity createClientIdConfEntity(String memberCode,
                                                 String subsystemCode) {
        return XroadIdConfMapper.get().toEntity(ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode,
                subsystemCode));
    }

    static String client(int idx) {
        return CLIENT_CODE + "-" + idx;
    }

    static String service(int serviceDescriptionIdx, int serviceIdx) {
        return SERVICE_CODE + "-" + serviceDescriptionIdx + "-" + serviceIdx;
    }

    static AccessRightTypeEntity createAccessRight(EndpointTypeEntity endpoint, XRoadIdConfEntity xRoadId) {
        AccessRightTypeEntity accessRight = new AccessRightTypeEntity();
        accessRight.setEndpoint(endpoint);
        accessRight.setSubjectId(xRoadId);
        accessRight.setRightsGiven(new Date());

        return accessRight;
    }

}
