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
package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.TestCertUtil.PKCS12;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import java.util.ArrayList;
import java.util.List;

/**
 * Test serverconf implementation.
 */
public class TestSuiteServerConf extends EmptyServerConf {

    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId DEFAULT_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT", "SUBCODE5");
    private static final String SERVICE1 = "SERVICE1";
    private static final String SERVICE2 = "SERVICE2";
    private static final String SERVICE3 = "SERVICE3";
    private static final String SERVICE4 = "SERVICE4";

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return SecurityServerId.Conf.create("EE", "BUSINESS", "consumer",
                "proxytest");
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        String serviceAddress = currentTestCase().getServiceAddress(service);
        if (serviceAddress != null) {
            return serviceAddress;
        }

        return "127.0.0.1:" + ProxyTestSuite.SERVICE_PORT
                + ((service != null) ? "/" + service.getServiceCode() : "");
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return currentTestCase().serviceExists(service);
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path) {
        return currentTestCase().isQueryAllowed(sender, service);
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return currentTestCase().getDisabledNotice(service);
    }

    @Override
    public InternalSSLKey getSSLKey() {
        PKCS12 internal = TestCertUtil.getInternalKey();
        return new InternalSSLKey(internal.key, internal.certChain);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return IsAuthentication.NOSSL;
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider, DescriptionType descriptionType) {
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
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProvider, ClientId client,
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

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
