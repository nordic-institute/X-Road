package ee.cyber.sdsb.common.conf.serverconf;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.WsdlDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.*;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.LocalGroupId;
import ee.cyber.sdsb.common.identifier.SdsbId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey.*;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.cyber.sdsb.common.util.CryptoUtils.loadPkcs12KeyStore;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

public class ServerConfImpl implements ServerConfProvider {

    // default service connection timeout in seconds
    private static final int DEFAULT_SERVICE_TIMEOUT = 30;

    private static SecurityServerId identifier;

    // ------------------------------------------------------------------------

    @Override
    public SecurityServerId getIdentifier() {
        return tx(new TransactionCallback<SecurityServerId>() {
            @Override
            public SecurityServerId call(Session session) throws Exception {
                if (identifier == null) {
                    ServerConfType confType = getConf();

                    ClientType owner = confType.getOwner();
                    if (owner == null) {
                        throw new CodedException(X_MALFORMED_SERVERCONF,
                                "Owner is not set");
                    }

                    identifier = SecurityServerId.create(owner.getIdentifier(),
                            confType.getServerCode());
                }

                return identifier;
            }
        });
    }

    @Override
    public boolean serviceExists(final ServiceId service) {
        return tx(new TransactionCallback<Boolean>() {
            @Override
            public Boolean call(Session session) throws Exception {
                return ServiceDAOImpl.getInstance().serviceExists(session,
                        service);
            }
        });
    }

    @Override
    public String getServiceAddress(final ServiceId service) {
        return tx(new TransactionCallback<String>() {
            @Override
            public String call(Session session) throws Exception {
                ServiceType serviceType = getService(service);
                if (serviceType != null) {
                    return serviceType.getUrl();
                }

                return null;
            }
        });
    }

    @Override
    public int getServiceTimeout(final ServiceId service) {
        return tx(new TransactionCallback<Integer>() {
            @Override
            public Integer call(Session session) throws Exception {
                ServiceType serviceType = getService(service);
                if (serviceType != null) {
                    return serviceType.getTimeout();
                }

                return DEFAULT_SERVICE_TIMEOUT;
            }
        });
    }

    @Override
    public List<ServiceId> getAllServices(final ClientId serviceProvider) {
        return tx(new TransactionCallback<List<ServiceId>>() {
            @Override
            public List<ServiceId> call(Session session) throws Exception {
                return ServiceDAOImpl.getInstance().getServices(session,
                        serviceProvider);
            }
        });
    }

    @Override
    public List<ServiceId> getAllowedServices(final ClientId serviceProvider,
            final ClientId client) {
        return tx(new TransactionCallback<List<ServiceId>>() {
            @Override
            public List<ServiceId> call(Session session) throws Exception {
                List<ServiceId> allServices =
                        ServiceDAOImpl.getInstance().getServices(session,
                                serviceProvider);

                List<ServiceId> allowedServices = new ArrayList<>();
                for (ServiceId service : allServices) {
                    if (internalIsQueryAllowed(client, service)) {
                        allowedServices.add(service);
                    }
                }

                return allowedServices;
            }
        });
    }

    @Override
    public boolean isSslAuthentication(final ServiceId service) {
        return tx(new TransactionCallback<Boolean>() {
            @Override
            public Boolean call(Session session) throws Exception {
                ServiceType serviceType = getService(service);
                if (serviceType != null) {
                    Boolean auth = serviceType.getSslAuthentication();
                    return auth != null ? auth : true;
                }

                throw new CodedException(X_UNKNOWN_SERVICE,
                        "Service '%s' not found", service);
            }
        });
    }

    @Override
    public List<ClientId> getMembers() {
        return tx(new TransactionCallback<List<ClientId>>() {
            @Override
            public List<ClientId> call(Session session) throws Exception {
                List<ClientId> members = new ArrayList<>();
                for (ClientType clientType : getConf().getClient()) {
                    members.add(clientType.getIdentifier());
                }

                return members;
            }
        });
    }

    @Override
    public IsAuthentication getIsAuthentication(final ClientId client) {
        return tx(new TransactionCallback<IsAuthentication>() {
            @Override
            public IsAuthentication call(Session session) throws Exception {
                ClientType clientType = getClient(client);
                if (clientType != null) {
                    String isAuth = clientType.getIsAuthentication();
                    if (isAuth == null) {
                        return IsAuthentication.NOSSL;
                    }

                    return IsAuthentication.valueOf(isAuth);
                }

                return null; // client not found
            }
        });
    }

    @Override
    public List<X509Certificate> getIsCerts(final ClientId client)
            throws Exception {
        return tx(new TransactionCallback<List<X509Certificate>>() {
            @Override
            public List<X509Certificate> call(Session session) throws Exception {
                List<X509Certificate> certs = new ArrayList<>();
                for (CertificateType cert :
                        ClientDAOImpl.getInstance().getIsCerts(session,
                                client)) {
                    certs.add(readCertificate(cert.getData()));
                }

                return certs;
            }
        });
    }

    @Override
    public String getDisabledNotice(final ServiceId service) {
        return tx(new TransactionCallback<String>() {
            @Override
            public String call(Session session) throws Exception {
                WsdlType wsdlType = getWsdl(service);
                if (wsdlType != null && wsdlType.isDisabled()) {
                    if (wsdlType.getDisabledNotice() == null) {
                        return String.format("Service '%s' is disabled",
                                service);
                    }

                    return wsdlType.getDisabledNotice();
                }

                return null;
            }
        });
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        File file = new File(getConfFileDir() + KEY_FILE_NAME);
        if (file.exists()) {
            KeyStore ks = loadPkcs12KeyStore(file, KEY_PASSWORD);

            PrivateKey key = (PrivateKey) ks.getKey(KEY_ALIAS, KEY_PASSWORD);
            if (key == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not get key from '%s'", file);
            }

            X509Certificate cert =
                    (X509Certificate) ks.getCertificate(KEY_ALIAS);
            if (cert == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not get certificate from '%s'", file);
            }

            return new InternalSSLKey(key, cert);
        }

        return null;
    }

    @Override
    public boolean isQueryAllowed(final ClientId client,
            final ServiceId service) {
        return tx(new TransactionCallback<Boolean>() {
            @Override
            public Boolean call(Session session) throws Exception {
                return internalIsQueryAllowed(client, service);
            }
        });
    }

    @Override
    public Collection<SecurityCategoryId> getRequiredCategories(
            final ServiceId service) {
        return tx(new TransactionCallback<Collection<SecurityCategoryId>>() {
            @Override
            public Collection<SecurityCategoryId> call(Session session)
                    throws Exception {
                ServiceType serviceType = getService(service);
                if (serviceType != null) {
                    return serviceType.getRequiredSecurityCategory();
                }

                return Collections.emptyList();
            }
        });
    }

    @Override
    public List<GlobalConfDistributorType> getFileDistributors() {
        return tx(new TransactionCallback<List<GlobalConfDistributorType>>() {
            @Override
            public List<GlobalConfDistributorType> call(Session session)
                    throws Exception {
                List<GlobalConfDistributorType> distributors =
                        getConf().getGlobalConfDistributor();
                Hibernate.initialize(distributors);
                return distributors;
            }
        });
    }

    @Override
    public List<String> getTspUrl() {
        return tx(new TransactionCallback<List<String>>() {
            @Override
            public List<String> call(Session session) throws Exception {
                List<String> ret = new ArrayList<>();

                for (TspType tsp: getConf().getTsp()) {
                    if (tsp.getUrl() != null && !tsp.getUrl().isEmpty()) {
                        ret.add(tsp.getUrl());
                    }
                }

                return ret;
            }
        });
    }

    // ------------------------------------------------------------------------

    protected ServerConfType getConf() {
        try {
            return ServerConfDAOImpl.getInstance().getConf();
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    protected ClientType getClient(ClientId c) {
        try {
            return ClientDAOImpl.getInstance().getClient(getSession(), c);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    protected ServiceType getService(ServiceId s) {
        try {
            return ServiceDAOImpl.getInstance().getService(getSession(), s);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    protected WsdlType getWsdl(ServiceId service) {
        try {
            return WsdlDAOImpl.getInstance().getWsdl(getSession(), service);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e);
        }
    }

    private static String getConfFileDir() {
        String path = SystemProperties.getConfPath();
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }

        return path;
    }

    private static Session getSession() {
        return ServerConfDatabaseCtx.get().getSession();
    }

    private AclType getAcl(ClientType clientType, ServiceId service) {
        for (AclType aclType : clientType.getAcl()) {
            if (StringUtils.equals(service.getServiceCode(),
                    aclType.getServiceCode())) {
                return aclType;
            }
        }

        return null;
    }

    private boolean internalIsQueryAllowed(ClientId client, ServiceId service) {
        if (getService(service) == null || client == null) {
            return false;
        }

        ClientType clientType = getClient(service.getClientId());
        if (clientType == null) {
            return false;
        }

        AclType aclType = getAcl(clientType, service);
        if (aclType == null) {
            return false;
        }

        for (AuthorizedSubjectType authorizedSubject
                : aclType.getAuthorizedSubject()) {
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

    private boolean isMemberInLocalGroup(ClientId member, LocalGroupId groupId,
            ServiceId serviceId) {
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
        ClientType owner = getClient(groupOwnerId);

        // No need to check for null because we already know the service
        // (and therefore the owner) exists.
        for (LocalGroupType localGroup : owner.getLocalGroup()) {
            if (StringUtils.equals(groupCode, localGroup.getGroupCode())) {
                return localGroup;
            }
        }

        return null;
    }

    protected static <T> T tx(TransactionCallback<T> t) {
        try {
            return doInTransaction(t);
        } catch (Exception e) {
            throw translateException(e);
        }
    }
}
