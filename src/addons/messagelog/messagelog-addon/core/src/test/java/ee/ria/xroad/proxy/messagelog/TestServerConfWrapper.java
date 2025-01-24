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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.AccessRightPath;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;

import lombok.Setter;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public boolean serviceExists(ServiceId service) {
        return serverConfProvider.serviceExists(service);
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return serverConfProvider.getDisabledNotice(service);
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return serverConfProvider.getServiceAddress(service);
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return serverConfProvider.getServiceTimeout(service);
    }

    @Override
    public RestServiceDetailsListType getRestServices(ClientId serviceProvider) {
        return serverConfProvider.getRestServices(serviceProvider);
    }

    @Override
    public Map<XRoadId, Set<AccessRightPath>> getEndpointClients(ClientId serviceProvider, String serviceCode) {
        return serverConfProvider.getEndpointClients(serviceProvider, serviceCode);
    }

    @Override
    public RestServiceDetailsListType getAllowedRestServices(ClientId serviceProvider, ClientId client) {
        return serverConfProvider.getAllowedRestServices(serviceProvider, client);
    }

    @Override
    public List<ServiceId.Conf> getAllServices(ClientId serviceProvider) {
        return serverConfProvider.getAllServices(serviceProvider);
    }

    @Override
    public Map<XRoadId, Set<AccessRightPath>> getAllowedClients(ClientId serviceProvider, String serviceCode) {
        return serverConfProvider.getAllowedClients(serviceProvider, serviceCode);
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider, DescriptionType descriptionType) {
        return serverConfProvider.getServicesByDescriptionType(serviceProvider, descriptionType);
    }

    @Override
    public List<ServiceId.Conf> getAllowedServices(ClientId serviceProvider, ClientId client) {
        return serverConfProvider.getAllowedServices(serviceProvider, client);
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProvider, ClientId client,
                                                                    DescriptionType descriptionType) {
        return serverConfProvider.getAllowedServicesByDescriptionType(serviceProvider, client, descriptionType);
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return serverConfProvider.getIsAuthentication(client);
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return serverConfProvider.getIsCerts(client);
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
    public boolean isSslAuthentication(ServiceId service) {
        return serverConfProvider.isSslAuthentication(service);
    }

    @Override
    public boolean isSubjectAssociatedWithLocalGroup(ClientId clientId, LocalGroupId localGroupId) {
        return serverConfProvider.isSubjectAssociatedWithLocalGroup(clientId, localGroupId);
    }

    @Override
    public boolean isSubjectInLocalGroup(ClientId clientId, LocalGroupId localGroupId) {
        return false;
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
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return serverConfProvider.isQueryAllowed(sender, service);
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service, String method, String path) {
        return serverConfProvider.isQueryAllowed(sender, service, method, path);
    }

    @Override
    public List<String> getTspUrl() {
        return serverConfProvider.getTspUrl();
    }

    @Override
    public DescriptionType getDescriptionType(ServiceId service) {
        return serverConfProvider.getDescriptionType(service);
    }

    @Override
    public String getServiceDescriptionURL(ServiceId service) {
        return serverConfProvider.getServiceDescriptionURL(service);
    }

    @Override
    public List<Endpoint> getServiceEndpoints(ServiceId service) {
        return serverConfProvider.getServiceEndpoints(service);
    }
}
