/*
 * The MIT License
 *
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

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.EncoderUtils;

import org.hibernate.Session;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.LocalGroupIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.ServiceIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.XRoadIdEntity;
import org.niis.xroad.common.identifiers.jpa.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.ServerConfCommonProperties;
import org.niis.xroad.serverconf.ServerConfDbProperties;
import org.niis.xroad.serverconf.impl.entity.AccessRightEntity;
import org.niis.xroad.serverconf.impl.entity.CertificateEntity;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.GroupMemberEntity;
import org.niis.xroad.serverconf.impl.entity.LocalGroupEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.entity.TimestampingServiceEntity;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.util.Date;
import java.util.Map;

import static org.niis.xroad.common.properties.ConfigUtils.defaultConfiguration;
import static org.niis.xroad.common.properties.ConfigUtils.initConfiguration;

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


    static Map<String, String> serverConfHibernateProperties = Map.of(
            "xroad.db.serverconf.hibernate.dialect", "org.hibernate.dialect.HSQLDialect",
            "xroad.db.serverconf.hibernate.connection.driver_class", "org.hsqldb.jdbcDriver",
            "xroad.db.serverconf.hibernate.connection.url", "jdbc:hsqldb:mem:serverconf",
            "xroad.db.serverconf.hibernate.connection.username", "serverconf",
            "xroad.db.serverconf.hibernate.connection.password", "serverconf",
            "xroad.db.serverconf.hibernate.hbm2ddl.auto", "create-drop"
    );
    static ServerConfDbProperties serverConfDbProperties = initConfiguration(ServerConfDbProperties.class, serverConfHibernateProperties);
    static ServerConfCommonProperties serverConfProperties = defaultConfiguration(ServerConfCommonProperties.class);

    private TestUtil() {
    }

    /**
     * Creates in-memory test database and fills it with test data.
     *
     * @throws Exception if an error occurs
     */
    public static void prepareDB(DatabaseCtx ctx) throws Exception {
        prepareDB(ctx, true);
    }

    /**
     * Creates in-memory test database and fills it with test data.
     *
     * @param clean if true, database is cleaned
     * @throws Exception if an error occurs
     */
    public static void prepareDB(DatabaseCtx ctx, boolean clean) throws Exception {
        if (clean) {
            cleanDB(ctx);
        }

        ctx.doInTransaction(session -> {
            ServerConfEntity conf = createTestData(session);
            session.persist(conf);
            return null;
        });
    }

    static void cleanDB(DatabaseCtx ctx) throws Exception {
        ctx.doInTransaction(session -> {
            var q = session.createNativeMutationQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }

    static ServerConfEntity createTestData(Session session) {
        ServerConfEntity conf = new ServerConfEntity();
        conf.setServerCode(SERVER_CODE);

        for (int i = 0; i < NUM_CLIENTS; i++) {
            ClientEntity client = new ClientEntity();
            client.setConf(conf);
            if (i == 0) {
                client.setIdentifier(createClientIdConfEntity());
                conf.setOwner(client);
            } else {
                ClientIdEntity id;
                if (i == NUM_CLIENTS - 1) {
                    id = createClientIdConfEntity(client(i), SUBSYSTEM);
                } else {
                    id = createClientIdConfEntity(client(i));
                }

                client.setIdentifier(id);
                client.setClientStatus(CLIENT_STATUS + i);
            }
            session.persist(client.getIdentifier());
            session.persist(client);
            if (i == 0) {
                continue;
            }
            switch (i) {
                case 1:
                    client.setIsAuthentication("SSLAUTH");
                    CertificateEntity ct = new CertificateEntity();
                    ct.setData(EncoderUtils.decodeBase64(BASE64_CERT));
                    client.getCertificates().add(ct);
                    break;
                case 2:
                    client.setIsAuthentication("SSLNOAUTH");
                    break;
                default:
                    client.setIsAuthentication("NOSSL");
                    break;
            }

            for (int j = 0; j < NUM_SERVICEDESCRIPTIONS; j++) {
                ServiceDescriptionEntity serviceDescription = new ServiceDescriptionEntity();
                serviceDescription.setClient(client);
                serviceDescription.setUrl(SERVICEDESCRIPTION_URL + j);
                serviceDescription.setType(DescriptionType.WSDL);

                for (int k = 0; k < NUM_SERVICES; k++) {
                    ServiceEntity service = new ServiceEntity();
                    service.setServiceDescription(serviceDescription);
                    service.setTitle(SERVICE_TITLE + k);
                    service.setServiceCode(service(j, k));

                    if (k != NUM_SERVICES - 2) {
                        service.setServiceVersion(SERVICE_VERSION);
                    }

                    service.setUrl(SERVICE_URL + k);
                    service.setTimeout(SERVICE_TIMEOUT);

                    service.setSslAuthentication(k % 2 == 0);

                    serviceDescription.getServices().add(service);
                }

                if (j == NUM_SERVICEDESCRIPTIONS - 1) {
                    serviceDescription.setDisabled(true);
                    serviceDescription.setDisabledNotice("disabledNotice");
                }

                client.getServiceDescriptions().add(serviceDescription);
            }

            String serviceCode = service(1, 1);
            final EndpointEntity endpoint = new EndpointEntity();
            endpoint.setServiceCode(serviceCode);
            endpoint.setMethod("*");
            endpoint.setPath("**");
            endpoint.setGenerated(false);
            session.persist(endpoint);

            client.getEndpoints().add(endpoint);

            client.getAccessRights().add(
                    createAccessRight(endpoint, client.getIdentifier()));

            ClientIdEntity cl = MemberIdEntity.create("XX", "memberClass", "memberCode" + i);
            session.persist(cl);
            client.getAccessRights().add(createAccessRight(endpoint, cl));

            ServiceIdEntity se = ServiceIdEntity.create("XX", "memberClass",
                    "memberCode" + i, "subsystemCode", "serviceCode" + i);
            session.persist(se);
            client.getAccessRights().add(createAccessRight(endpoint, se));

            LocalGroupIdEntity lg = LocalGroupIdEntity.create("testGroup" + i);
            session.persist(lg);
            client.getAccessRights().add(createAccessRight(endpoint, lg));

            //rest service
            ServiceDescriptionEntity serviceDescription = new ServiceDescriptionEntity();
            serviceDescription.setClient(client);
            serviceDescription.setUrl(SERVICEDESCRIPTION_URL + "rest");
            serviceDescription.setType(DescriptionType.REST);

            ServiceEntity service = new ServiceEntity();
            service.setServiceDescription(serviceDescription);
            service.setTitle(SERVICE_TITLE + "REST");
            service.setServiceCode("rest");

            EndpointEntity restEndpoint = new EndpointEntity();
            restEndpoint.setServiceCode(service.getServiceCode());
            restEndpoint.setMethod("GET");
            restEndpoint.setPath("/api/**");
            restEndpoint.setGenerated(false);
            session.persist(restEndpoint);
            client.getEndpoints().add(restEndpoint);
            client.getAccessRights().add(createAccessRight(restEndpoint, client.getIdentifier()));

            EndpointEntity restEndpoint2 = new EndpointEntity();
            restEndpoint2.setServiceCode(service.getServiceCode());
            restEndpoint2.setMethod("POST");
            restEndpoint2.setPath("/api/test/*");
            restEndpoint2.setGenerated(false);
            session.persist(restEndpoint2);
            client.getEndpoints().add(restEndpoint2);
            client.getAccessRights().add(createAccessRight(restEndpoint2, client.getIdentifier()));

            LocalGroupEntity localGroup = new LocalGroupEntity();
            localGroup.setGroupCode("localGroup" + i);
            localGroup.setDescription("local group description");
            localGroup.setUpdated(new Date());
            GroupMemberEntity localGroupMember = new GroupMemberEntity();
            localGroupMember.setAdded(new Date());
            localGroupMember.setGroupMemberId(cl);
            localGroup.getGroupMembers().add(localGroupMember);

            client.getLocalGroups().add(localGroup);
        }

        for (int j = 0; j < NUM_TSPS; j++) {
            TimestampingServiceEntity tsp = new TimestampingServiceEntity();
            tsp.setName("tspName" + j);
            tsp.setUrl("tspUrl" + j);
            conf.getTimestampingServices().add(tsp);
        }

        return conf;
    }

    static ServiceId.Conf createTestServiceId(String memberCode, String serviceCode) {
        return ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode, null,
                serviceCode);
    }

    static ServiceId.Conf createTestServiceId(String memberCode, String serviceCode, String serviceVerison) {
        return ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode, null,
                serviceCode, serviceVerison);
    }

    static ServiceIdEntity createTestServiceIdEntity(String memberCode, String serviceCode, String serviceVerison) {
        return ServiceIdEntity.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode, null,
                serviceCode, serviceVerison);
    }

    static ServiceId.Conf createTestServiceId(ClientId member, String serviceCode, String serviceVersion) {
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

    static ClientIdEntity createClientIdConfEntity() {
        return XRoadIdMapper.get().toEntity(ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, MEMBER_CODE));
    }

    static ClientIdEntity createClientIdConfEntity(String memberCode) {
        return createClientIdConfEntity(memberCode, null);
    }

    static ClientIdEntity createClientIdConfEntity(String memberCode,
                                                   String subsystemCode) {
        return XRoadIdMapper.get().toEntity(ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode,
                subsystemCode));
    }

    static String client(int idx) {
        return CLIENT_CODE + "-" + idx;
    }

    static String service(int serviceDescriptionIdx, int serviceIdx) {
        return SERVICE_CODE + "-" + serviceDescriptionIdx + "-" + serviceIdx;
    }

    static AccessRightEntity createAccessRight(EndpointEntity endpoint, XRoadIdEntity xRoadId) {
        AccessRightEntity accessRight = new AccessRightEntity();
        accessRight.setEndpoint(endpoint);
        accessRight.setSubjectId(xRoadId);
        accessRight.setRightsGiven(new Date());

        return accessRight;
    }

}
