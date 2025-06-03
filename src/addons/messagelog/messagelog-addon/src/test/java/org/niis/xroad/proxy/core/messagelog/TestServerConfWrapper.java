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
package org.niis.xroad.proxy.core.messagelog;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;

import lombok.Setter;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.security.cert.X509Certificate;
import java.util.List;

@Setter
public class TestServerConfWrapper implements ServerConfProvider {
    private ServerConfProvider serverConfProvider;

    public TestServerConfWrapper(ServerConfProvider serverConfProvider) {
        this.serverConfProvider = serverConfProvider;
    }

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return serverConfProvider.getIdentifier();
    }

    @Override
    public boolean serviceExists(ServiceId serviceId) {
        return serverConfProvider.serviceExists(serviceId);
    }

    @Override
    public String getDisabledNotice(ServiceId serviceId) {
        return serverConfProvider.getDisabledNotice(serviceId);
    }

    @Override
    public String getServiceAddress(ServiceId serviceId) {
        return serverConfProvider.getServiceAddress(serviceId);
    }

    @Override
    public int getServiceTimeout(ServiceId serviceId) {
        return serverConfProvider.getServiceTimeout(serviceId);
    }

    @Override
    public RestServiceDetailsListType getRestServices(ClientId serviceProviderId) {
        return serverConfProvider.getRestServices(serviceProviderId);
    }

    @Override
    public RestServiceDetailsListType getAllowedRestServices(ClientId serviceProviderId, ClientId clientId) {
        return serverConfProvider.getAllowedRestServices(serviceProviderId, clientId);
    }

    @Override
    public List<ServiceId.Conf> getAllServices(ClientId serviceProviderId) {
        return serverConfProvider.getAllServices(serviceProviderId);
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProviderId, DescriptionType descriptionType) {
        return serverConfProvider.getServicesByDescriptionType(serviceProviderId, descriptionType);
    }

    @Override
    public List<ServiceId.Conf> getAllowedServices(ClientId serviceProviderId, ClientId clientId) {
        return serverConfProvider.getAllowedServices(serviceProviderId, clientId);
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProviderId, ClientId clientId,
                                                                    DescriptionType descriptionType) {
        return serverConfProvider.getAllowedServicesByDescriptionType(serviceProviderId, clientId, descriptionType);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId clientId) {
        return serverConfProvider.getIsAuthentication(clientId);
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId clientId) throws Exception {
        return serverConfProvider.getIsCerts(clientId);
    }

    @Override
    public List<X509Certificate> getAllIsCerts() {
        return serverConfProvider.getAllIsCerts();
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return serverConfProvider.getSSLKey();
    }

    @Override
    public boolean isSslAuthentication(ServiceId serviceId) {
        return serverConfProvider.isSslAuthentication(serviceId);
    }

    @Override
    public List<ClientId.Conf> getMembers() throws Exception {
        return serverConfProvider.getMembers();
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return serverConfProvider.getMemberStatus(memberId);
    }

    @Override
    public boolean isQueryAllowed(ClientId senderId, ServiceId serviceId) {
        return serverConfProvider.isQueryAllowed(senderId, serviceId);
    }

    @Override
    public boolean isQueryAllowed(ClientId senderId, ServiceId serviceId, String method, String path) {
        return serverConfProvider.isQueryAllowed(senderId, serviceId, method, path);
    }

    @Override
    public List<String> getTspUrl() {
        return serverConfProvider.getTspUrl();
    }

    @Override
    public DescriptionType getDescriptionType(ServiceId serviceId) {
        return serverConfProvider.getDescriptionType(serviceId);
    }

    @Override
    public String getServiceDescriptionURL(ServiceId serviceId) {
        return serverConfProvider.getServiceDescriptionURL(serviceId);
    }

    @Override
    public List<Endpoint> getServiceEndpoints(ServiceId serviceId) {
        return serverConfProvider.getServiceEndpoints(serviceId);
    }

    @Override
    public MaintenanceMode getMaintenanceMode() {
        return serverConfProvider.getMaintenanceMode();
    }
}
