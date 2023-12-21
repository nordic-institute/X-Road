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

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implementation of GlobalConfProvider that does nothing but
 * return nulls. You can extend this class and override only the
 * more interesting methods.
 */
public class EmptyGlobalConf implements GlobalConfProvider {

    private static final int DEFAULT_TIMESTAMPING_INTERVAL = 60;
    private static final int DEFAULT_OCSP_FRESHNESS = 3600;

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate org)
            throws Exception {
        return Collections.emptyList();
    }

    @Override
    public List<String> getOcspResponderAddressesForCaCertificate(X509Certificate caCert)
            throws Exception {
        return Collections.emptyList();
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        return Collections.emptyList();
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate orgCert) throws Exception {
        return null;
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return Collections.emptyList();
    }

    @Override
    public List<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate ca,
            X509Certificate ocspCert) {
        return true;
    }

    @Override
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return null;
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return null;
    }

    @Override
    public Set<String> getKnownAddresses() {
        return Collections.emptySet();
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        return false;
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return null;
    }

    @Override
    public Collection<String> getProviderAddress(ClientId serviceProvider) {
        return null;
    }

    @Override
    public String getSecurityServerAddress(SecurityServerId serverId) {
        return null;
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        return false;
    }

    @Override
    public Collection<ApprovedCAInfo> getApprovedCAs(String instanceIdentifier) {
        return null;
    }

    @Override
    public String getManagementRequestServiceAddress() {
        return null;
    }

    @Override
    public ClientId getManagementRequestService() {
        return null;
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() {
        return null;
    }

    @Override
    public boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer) {
        return false;
    }

    @Override
    public boolean existsSecurityServer(SecurityServerId securityServerId) {
        return false;
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        return null;
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public int getOcspFreshnessSeconds() {
        return DEFAULT_OCSP_FRESHNESS;
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return DEFAULT_TIMESTAMPING_INTERVAL;
    }

    @Override
    public String getInstanceIdentifier() {
        return null;
    }

    @Override
    public List<String> getInstanceIdentifiers() {
        return Collections.emptyList();
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        return null;
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifier) {
        return Collections.emptySet();
    }

    @Override
    public String getMemberName(ClientId member) {
        return null;
    }

    @Override
    public List<String> getApprovedTspUrls(String instanceIdentifier) {
        return null;
    }

    @Override
    public List<SharedParameters.ApprovedTSA> getApprovedTsps(String instanceIdentifier) {
        return null;
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        return null;
    }

    @Override
    public SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public ClientId getServerOwner(SecurityServerId serverId) {
        return null;
    }

    @Override
    public void reload() {
      // nothing to reload here
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<SecurityServerId.Conf> getSecurityServers(
            String... instanceIdentifiers) {
        return Collections.emptyList();
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            AuthCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parametrers,
            X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public ApprovedCAInfo getApprovedCA(String instanceIdentifier, X509Certificate cert) {
        return null;
    }

}
