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
package org.niis.xroad.test.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Setter;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.SharedParameters;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * GlobalConf wrapper to allow old-style TestGlobalConf usage withing spring based tests.
 */
@Setter
public class TestGlobalConfWrapper implements GlobalConfProvider {
    private GlobalConfProvider globalConfProvider;

    public TestGlobalConfWrapper(GlobalConfProvider globalConfProvider) {
        this.globalConfProvider = globalConfProvider;
    }

    @Override
    public void reload() {
        globalConfProvider.reload();
    }

    @Override
    public boolean isValid() {
        return globalConfProvider.isValid();
    }

    @Override
    public void verifyValidity() {
        globalConfProvider.verifyValidity();
    }

    @Override
    public String getInstanceIdentifier() {
        return globalConfProvider.getInstanceIdentifier();
    }

    @Override
    public Set<String> getInstanceIdentifiers() {
        return globalConfProvider.getInstanceIdentifiers();
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        return globalConfProvider.getMembers(instanceIdentifiers);
    }

    @Override
    public String getMemberName(ClientId clientId) {
        return globalConfProvider.getMemberName(clientId);
    }

    @Override
    public String getSubsystemName(ClientId clientId) {
        return globalConfProvider.getSubsystemName(clientId);
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers) {
        return globalConfProvider.getGlobalGroups(instanceIdentifiers);
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        return globalConfProvider.getGlobalGroupDescription(globalGroupId);
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifiers) {
        return globalConfProvider.getMemberClasses(instanceIdentifiers);
    }

    @Override
    public Collection<String> getProviderAddress(ClientId serviceProvider) {
        return globalConfProvider.getProviderAddress(serviceProvider);
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        return globalConfProvider.getSecurityServerAddress(serverId);
    }

    @Override
    public ClientId.Conf getSubjectName(SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) throws Exception {
        return globalConfProvider.getSubjectName(parameters, cert);
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate member) throws Exception {
        return globalConfProvider.getOcspResponderAddresses(member);
    }

    @Override
    public List<String> getOcspResponderAddressesForCaCertificate(X509Certificate caCert) throws Exception {
        return globalConfProvider.getOcspResponderAddressesForCaCertificate(caCert);
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        return globalConfProvider.getOcspResponderCertificates();
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier, X509Certificate memberCert) throws Exception {
        return globalConfProvider.getCaCert(instanceIdentifier, memberCert);
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return globalConfProvider.getAllCaCerts();
    }

    @Override
    public List<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return globalConfProvider.getAllCaCerts(instanceIdentifier);
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier, X509Certificate subject) throws Exception {
        return globalConfProvider.getCertChain(instanceIdentifier, subject);
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate ca, X509Certificate ocspCert) {
        return globalConfProvider.isOcspResponderCert(ca, ocspCert);
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return globalConfProvider.getAuthTrustChain();
    }

    @Override
    public SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception {
        return globalConfProvider.getServerId(cert);
    }

    @Override
    public ClientId getServerOwner(SecurityServerId serverId) {
        return globalConfProvider.getServerOwner(serverId);
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert, ClientId memberId) throws Exception {
        return globalConfProvider.authCertMatchesMember(cert, memberId);
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(AuthCertificateProfileInfo.Parameters parameters,
                                                                    X509Certificate cert) throws Exception {
        return globalConfProvider.getAuthCertificateProfileInfo(parameters, cert);
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(SignCertificateProfileInfo.Parameters parameters,
                                                                    X509Certificate cert) throws Exception {
        return globalConfProvider.getSignCertificateProfileInfo(parameters, cert);
    }

    @Override
    public List<String> getApprovedTspUrls(String instanceIdentifier) {
        return globalConfProvider.getApprovedTspUrls(instanceIdentifier);
    }

    @Override
    public List<SharedParameters.ApprovedTSA> getApprovedTsps(String instanceIdentifier) {
        return globalConfProvider.getApprovedTsps(instanceIdentifier);
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier, String approvedTspUrl) {
        return globalConfProvider.getApprovedTspName(instanceIdentifier, approvedTspUrl);
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws Exception {
        return globalConfProvider.getTspCertificates();
    }

    @Override
    public Set<String> getKnownAddresses() {
        return globalConfProvider.getKnownAddresses();
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subject, GlobalGroupId group) {
        return globalConfProvider.isSubjectInGlobalGroup(subject, group);
    }

    @Override
    public boolean isSecurityServerClient(ClientId client, SecurityServerId securityServer) {
        return globalConfProvider.isSecurityServerClient(client, securityServer);
    }

    @Override
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        return globalConfProvider.existsSecurityServer(securityServerId);
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return globalConfProvider.getVerificationCaCerts();
    }

    @Override
    public String getManagementRequestServiceAddress() {
        return globalConfProvider.getManagementRequestServiceAddress();
    }

    @Override
    public ClientId getManagementRequestService() {
        return globalConfProvider.getManagementRequestService();
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() throws Exception {
        return globalConfProvider.getCentralServerSslCertificate();
    }

    @Override
    public int getOcspFreshnessSeconds() {
        return globalConfProvider.getOcspFreshnessSeconds();
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return globalConfProvider.getTimestampingIntervalSeconds();
    }

    @Override
    public List<SecurityServerId.Conf> getSecurityServers(String... instanceIdentifiers) {
        return globalConfProvider.getSecurityServers(instanceIdentifiers);
    }

    @Override
    public ApprovedCAInfo getApprovedCA(String instanceIdentifier, X509Certificate cert) throws CodedException {
        return globalConfProvider.getApprovedCA(instanceIdentifier, cert);
    }

    @Override
    public GlobalConfExtensions getGlobalConfExtensions() {
        return globalConfProvider.getGlobalConfExtensions();
    }

    @Override
    public OptionalInt getVersion() {
        return globalConfProvider.getVersion();
    }

    @Override
    public Optional<SharedParameters.MaintenanceMode> getMaintenanceMode(SecurityServerId serverId) {
        return globalConfProvider.getMaintenanceMode(serverId);
    }
}
