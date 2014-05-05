package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.conf.ServerConfCommonImpl;
import ee.cyber.sdsb.common.conf.serverconf.*;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.LocalGroupId;
import ee.cyber.sdsb.common.identifier.SdsbId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.util.OcspClient;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

public class ServerConfImpl extends ServerConfCommonImpl
        implements ServerConfProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServerConfImpl.class);

    // Caches the OCSP responses in memory and on disk.
    private OcspResponseManager ocspResponseManager = new OcspResponseManager();

    public ServerConfImpl(String confFileName) {
        super(confFileName, ServerConfSchemaValidator.class);
    }

    @Override
    public List<X509Certificate> getCertsForOcsp() throws Exception {
        List<X509Certificate> certs = new ArrayList<>();

        // add all member certs
        certs.addAll(getMemberCerts());

        // add SSL cert
        AuthKey authKey = KeyConf.getAuthKey();
        if (authKey != null && authKey.getCert() != null) {
            certs.add(authKey.getCert());
        }

        return certs;
    }

    @Override
    public List<X509Certificate> getMemberCerts() throws Exception {
        List<X509Certificate> memberCerts = new ArrayList<>();
        for (ClientType clientType : confType.getClient()) {
            try {
                memberCerts.addAll(
                        KeyConf.getMemberCerts(clientType.getIdentifier()));
            } catch (Exception e) {
                LOG.error("Failed to get certs for member '"
                        + clientType.getIdentifier() + "'", e);
            }
        }

        return memberCerts;
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
        String certHash = calculateCertHexHash(cert);
        // TODO: this lock can become a performance bottleneck
        // if one OCSP query takes too long and blocks all the other queries.
        synchronized (this) {
            OCSPResp resp = ocspResponseManager.getResponse(certHash);
            if (resp == null) {
                // We did not get a response from local cache -- we need to
                // retrieve the response from the responder and store it.
                try {
                    resp = OcspClient.queryCertStatus(cert);
                } catch (Exception e) {
                    LOG.error("Failed to fetch certificate '" +
                            cert.getSerialNumber() + "' status", e);
                }

                if (resp != null) {
                    setOcspResponse(certHash, resp);
                }
            }
            return resp;
        }
    }

    @Override
    public boolean isCachedOcspResponse(String certHash) throws Exception {
        return ocspResponseManager.getResponse(certHash) != null;
    }

    @Override
    public void setOcspResponse(String certHash, OCSPResp response)
            throws Exception {
        ocspResponseManager.setResponse(certHash, response);
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return serviceIdToServiceType.containsKey(service);
    }

    @Override
    public boolean isQueryAllowed(ClientId client, ServiceId service) {
        // Sanity check
        if (!serviceExists(service) || client == null) {
            return false;
        }

        ServiceType serviceType = serviceIdToServiceType.get(service);

        for (AuthorizedSubjectType authorizedSubject
                : serviceType.getAuthorizedSubject()) {

            SdsbId subjectId = authorizedSubject.getSubjectId();

            if (subjectId instanceof GlobalGroupId) {
                if (GlobalConf.isSubjectInGlobalGroup(client,
                        (GlobalGroupId) subjectId)) {
                    return true;
                }
            } else if (subjectId instanceof LocalGroupId) {
                if (isMemberInLocalGroup(client,
                        (LocalGroupId) subjectId, service)) {
                    return true;
                }
            } else if (subjectId instanceof ClientId) {
                if (client.equals(subjectId)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        WsdlType wsdl = serviceIdToWsdlType.get(service);
        if (wsdl.isDisabled()) {
            return wsdl.getDisabledNotice() == null ? "Out of order" :
                wsdl.getDisabledNotice();
        } else {
            return null;
        }
    }

    @Override
    public Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId serviceId) {
        ServiceType service = serviceIdToServiceType.get(serviceId);
        return service.getRequiredSecurityCategory();
    }

    @Override
    public String getGlobalConfDistributorUrl() {
        // TODO: Handle multiple distributors
        List<GlobalConfDistributorType> globalConfDistributors =
                confType.getGlobalConfDistributor();
        if (!globalConfDistributors.isEmpty()) {
            return globalConfDistributors.get(0).getUrl();
        }

        return null;
    }

    @Override
    public X509Certificate getGlobalConfVerificationCert() throws Exception {
        // TODO: Handle multiple distributors
        List<GlobalConfDistributorType> globalConfDistributors =
                confType.getGlobalConfDistributor();
        if (!globalConfDistributors.isEmpty()) {
            byte[] base64CertificateData =
                    globalConfDistributors.get(0).getVerificationCert();
            return readCertificate(base64CertificateData);
        }

        return null;
    }

    @Override
    public List<String> getTspUrl() {
        List<String> ret = new ArrayList<>();

        for (TspType tsp: confType.getTsp()) {
            if (tsp.getUrl() != null && !tsp.getUrl().isEmpty()) {
                ret.add(tsp.getUrl());
            }
        }

        return ret;
    }

    private boolean isMemberInLocalGroup(ClientId member,
            LocalGroupId groupId, ServiceId serviceId) {
        LocalGroupType group = findLocalGroup(groupId.getGroupCode(),
                serviceId.getClientId());
        if (group == null) {
            return false;
        }
        for (GroupMemberType groupMember : group.getGroupMember()) {
            if (groupMember.getGroupMemberId().equals(member)) {
                return true;
            }
        }
        return false;
    }

    private LocalGroupType findLocalGroup(String groupCode,
            ClientId groupOwnerId) {
        ClientType owner = clientIdToClientType.get(groupOwnerId);
        // No need to check for null because we already know the service
        // (and therefore the owner) exists.
        for (LocalGroupType localGroup : owner.getLocalGroup()) {
            if (StringUtils.equals(groupCode, localGroup.getGroupCode())) {
                return localGroup;
            }
        }
        return null;
    }
}
