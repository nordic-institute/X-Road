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
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.test.serverconf.EmptyServerConf;

import java.util.ArrayList;
import java.util.List;

/**
 * Test serverconf implementation.
 */
@RequiredArgsConstructor
public class TestSuiteServerConf extends EmptyServerConf {

    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId DEFAULT_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT", "SUBCODE5");
    private static final String SERVICE1 = "SERVICE1";
    private static final String SERVICE2 = "SERVICE2";
    private static final String SERVICE3 = "SERVICE3";
    private static final String SERVICE4 = "SERVICE4";

    private final ProxyTestSuiteHelper proxyTestSuiteHelper;

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return SecurityServerId.Conf.create("EE", "BUSINESS", "consumer",
                "proxytest");
    }

    @Override
    public String getServiceAddress(ServiceId serviceId) {
        String serviceAddress = currentTestCase().getServiceAddress(serviceId);
        if (serviceAddress != null) {
            return serviceAddress;
        }

        return "127.0.0.1:" + proxyTestSuiteHelper.servicePort
                + ((serviceId != null) ? "/" + serviceId.getServiceCode() : "");
    }

    @Override
    public boolean serviceExists(ServiceId serviceId) {
        return currentTestCase().serviceExists(serviceId);
    }

    @Override
    public boolean isQueryAllowed(ClientId senderId, ServiceId serviceId, String method, String path) {
        return currentTestCase().isQueryAllowed(senderId, serviceId);
    }

    @Override
    public String getDisabledNotice(ServiceId serviceId) {
        return currentTestCase().getDisabledNotice(serviceId);
    }

    @Override
    public InternalSSLKey getSSLKey() {
        PKCS12 internal = TestCertUtil.getInternalKey();
        return new InternalSSLKey(internal.key, internal.certChain);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId clientId) {
        return IsAuthentication.NOSSL;
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProviderId, DescriptionType descriptionType) {
        List<ServiceId.Conf> list = new ArrayList<>();
        if (descriptionType == DescriptionType.REST) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE1));
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE2));
        }
        if (descriptionType == DescriptionType.OPENAPI3) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE3));
        }
        if (descriptionType == DescriptionType.WSDL) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE4));
        }
        return list;
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProviderId, ClientId clientId,
                                                                    DescriptionType descriptionType) {
        List<ServiceId.Conf> list = new ArrayList<>();
        if (descriptionType == DescriptionType.REST) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE2));
        }
        if (descriptionType == DescriptionType.OPENAPI3) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE3));
        }
        if (descriptionType == DescriptionType.WSDL) {
            list.add(ServiceId.Conf.create(DEFAULT_CLIENT, SERVICE4));
        }
        return list;
    }

    private MessageTestCase currentTestCase() {
        return proxyTestSuiteHelper.currentTestCase;
    }
}
