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
package ee.ria.xroad.common.conf.serverconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.dao.CertificateDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDescriptionDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.db.TransactionCallback;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.metadata.Endpoint;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;
import ee.ria.xroad.common.metadata.RestServiceType;
import ee.ria.xroad.common.metadata.XRoadRestServiceDetailsType;
import ee.ria.xroad.common.util.UriUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SharedSessionContract;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Server conf implementation.
 */
@Slf4j
public class ServerConfImpl implements ServerConfProvider {

    // default service connection timeout in seconds
    protected static final int DEFAULT_SERVICE_TIMEOUT = 30;

    private final ServiceDAOImpl serviceDao = new ServiceDAOImpl();
    private final IdentifierDAOImpl identifierDao = new IdentifierDAOImpl();
    private final ClientDAOImpl clientDao = new ClientDAOImpl();
    private final CertificateDAOImpl certificateDao = new CertificateDAOImpl();
    private final ServerConfDAOImpl serverConfDao = new ServerConfDAOImpl();
    private final ServiceDescriptionDAOImpl serviceDescriptionDao = new ServiceDescriptionDAOImpl();

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return tx(session -> {
            ServerConfType confType = getConf(session);
            ClientType owner = confType.getOwner();
            if (owner == null) {
                throw new CodedException(X_MALFORMED_SERVERCONF, "Owner is not set");
            }
            return SecurityServerId.Conf.create(owner.getIdentifier(), confType.getServerCode());
        });
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return tx(session -> serviceDao.serviceExists(session, service));
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getUrl();
            }

            return null;
        });
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getTimeout();
            }

            return DEFAULT_SERVICE_TIMEOUT;
        });
    }

    @Override
    public RestServiceDetailsListType getRestServices(ClientId serviceProvider) {
        return tx(session -> {
            RestServiceDetailsListType restServiceDetailsList = new RestServiceDetailsListType();
            serviceDao.getServicesByDescriptionType(session, serviceProvider, DescriptionType.OPENAPI3,
                            DescriptionType.REST)
                    .forEach(serviceId -> {
                        XRoadRestServiceDetailsType serviceDetails = createRestServiceDetails(serviceId);
                        serviceDetails.getEndpointList().addAll(getServiceEndpoints(serviceId));
                        restServiceDetailsList.getService().add(serviceDetails);
                    });
            return restServiceDetailsList;
        });
    }

    @Override
    public RestServiceDetailsListType getAllowedRestServices(ClientId serviceProvider, ClientId client) {
        return tx(session -> {
            RestServiceDetailsListType restServiceDetailsList = new RestServiceDetailsListType();
            serviceDao.getServicesByDescriptionType(session, serviceProvider, DescriptionType.OPENAPI3,
                            DescriptionType.REST)
                    .forEach(serviceId -> {
                        final List<EndpointType> acl = getAclEndpoints(session, client, serviceId);
                        if (!acl.isEmpty()) {
                            final List<Endpoint> endpoints = getServiceEndpoints(serviceId);
                            XRoadRestServiceDetailsType serviceDetails = createRestServiceDetails(serviceId);
                            for (Endpoint e : endpoints) {
                                if (acl.stream().anyMatch(it -> it.matches(e.getMethod(), e.getPath()))) {
                                    serviceDetails.getEndpointList().add(e);
                                }
                            }
                            restServiceDetailsList.getService().add(serviceDetails);
                        }
                    });
            return restServiceDetailsList;
        });
    }

    private XRoadRestServiceDetailsType createRestServiceDetails(ServiceId serviceId) {
        XRoadRestServiceDetailsType serviceDetails = new XRoadRestServiceDetailsType();
        serviceDetails.setXRoadInstance(serviceId.getXRoadInstance());
        serviceDetails.setMemberClass(serviceId.getMemberClass());
        serviceDetails.setMemberCode(serviceId.getMemberCode());
        serviceDetails.setSubsystemCode(serviceId.getSubsystemCode());
        serviceDetails.setServiceCode(serviceId.getServiceCode());
        serviceDetails.setObjectType(XRoadObjectType.SERVICE);
        serviceDetails.setServiceType(getRestServiceType(getDescriptionType(serviceId)));
        return serviceDetails;
    }

    private RestServiceType getRestServiceType(DescriptionType descriptionType) {
        return switch (descriptionType) {
            case REST -> RestServiceType.REST;
            case OPENAPI3 -> RestServiceType.OPENAPI;
            default -> throw new IllegalArgumentException("The given parameter is not a REST service type!");
        };
    }

    @Override
    public List<ServiceId.Conf> getAllServices(ClientId serviceProvider) {
        return tx(session -> serviceDao.getServices(session, serviceProvider));
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescriptionType(ClientId serviceProvider,
                                                             DescriptionType descriptionType) {
        return tx(session -> serviceDao.getServicesByDescriptionType(session, serviceProvider, descriptionType));
    }

    @Override
    public List<ServiceId.Conf> getAllowedServices(ClientId serviceProvider, ClientId client) {
        return tx(session -> {
            List<ServiceId.Conf> allServices =
                    serviceDao.getServices(session, serviceProvider);
            return allServices.stream()
                    .filter(s -> !getAclEndpoints(session, client, s).isEmpty())
                    .collect(Collectors.toList());
        });
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescriptionType(ClientId serviceProvider, ClientId client,
            DescriptionType descriptionType) {
        return tx(session -> {
            List<ServiceId.Conf> allServices =
                    serviceDao.getServicesByDescriptionType(session, serviceProvider, descriptionType);
            return allServices.stream()
                    .filter(s -> !getAclEndpoints(session, client, s).isEmpty())
                    .collect(Collectors.toList());
        });
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null) {
                return ObjectUtils.defaultIfNull(
                        serviceType.getSslAuthentication(), true);
            }

            throw new CodedException(X_UNKNOWN_SERVICE,
                    "Service '%s' not found", service);
        });
    }

    @Override
    public List<ClientId.Conf> getMembers() {
        return tx(session -> getConf(session).getClient().stream()
                .map(ClientType::getIdentifier)
                .collect(Collectors.toList()));
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return tx(session -> {
            ClientType client = getClient(session, memberId);
            if (client != null) {
                return client.getClientStatus();
            } else {
                return null;
            }
        });
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return tx(session -> {
            ClientType clientType = getClient(session, client);
            if (clientType != null) {
                String isAuth = clientType.getIsAuthentication();
                if (isAuth == null) {
                    return IsAuthentication.NOSSL;
                }

                return IsAuthentication.valueOf(isAuth);
            }

            return null; // client not found
        });
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return tx(session -> clientDao.getIsCerts(session, client).stream()
                .map(c -> readCertificate(c.getData()))
                .collect(Collectors.toList()));
    }

    @Override
    public List<X509Certificate> getAllIsCerts() {
        return tx(session -> certificateDao.findAll(session).stream()
                .map(c -> readCertificate(c.getData()))
                .collect(Collectors.toList()));
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return tx(session -> {
            ServiceDescriptionType serviceDescriptionType = getServiceDescription(session, service);
            if (serviceDescriptionType != null && serviceDescriptionType.isDisabled()) {
                if (serviceDescriptionType.getDisabledNotice() == null) {
                    return String.format("Service '%s' is disabled", service);
                }

                return serviceDescriptionType.getDisabledNotice();
            }

            return null;
        });
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return InternalSSLKey.load();
    }

    @Override
    public boolean isQueryAllowed(ClientId client, ServiceId service, String method, String path) {
        return tx(session -> internalIsQueryAllowed(session, client, service, method, path));
    }

    @Override
    public List<String> getTspUrl() {
        return tx(session -> getConf(session).getTsp().stream()
                .map(TspType::getUrl)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList()));
    }

    @Override
    public DescriptionType getDescriptionType(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null && serviceType.getServiceDescription() != null) {
                return serviceType.getServiceDescription().getType();
            }

            return null;
        });
    }

    @Override
    public String getServiceDescriptionURL(ServiceId service) {
        return tx(session -> {
            ServiceType serviceType = getService(session, service);
            if (serviceType != null && serviceType.getServiceDescription() != null) {
                return serviceType.getServiceDescription().getUrl();
            }
            return null;
        });
    }

    @Override
    public List<Endpoint> getServiceEndpoints(ServiceId service) {
        return tx(session -> getClient(session, service.getClientId()).getEndpoint().stream()
                .filter(e -> e.getServiceCode().equals(service.getServiceCode()))
                .filter(e -> !e.isBaseEndpoint())
                .map(e -> createEndpoint(e.getMethod(), e.getPath()))
                .collect(Collectors.toList()));
    }

    @Override
    public boolean isAvailable() {
        try {
            return doInTransaction(SharedSessionContract::isConnected);
        } catch (Exception e) {
            log.warn("Unable to check Serverconf availability", e);
            return false;
        }
    }

    private static Endpoint createEndpoint(String method, String path) {
        Endpoint endpoint = new Endpoint();
        endpoint.setMethod(method);
        endpoint.setPath(path);
        return endpoint;
    }

    // ------------------------------------------------------------------------

    protected ServerConfType getConf(Session session) {
        return serverConfDao.getConf(session);
    }

    protected ClientType getClient(Session session, ClientId c) {
        return clientDao.getClient(session, c);
    }

    protected ServiceType getService(Session session, ServiceId s) {
        return serviceDao.getService(session, s);
    }

    protected ServiceDescriptionType getServiceDescription(Session session, ServiceId service) {
        return serviceDescriptionDao.getServiceDescription(session, service);
    }

    private boolean internalIsQueryAllowed(Session session, ClientId client, ServiceId service, String method,
            String path) {

        if (client == null) {
            return false;
        }

        return checkAccessRights(session, client, service, method, path);
    }

    @SuppressWarnings("squid:S3776")
    private boolean checkAccessRights(Session session, ClientId client, ServiceId service, String method, String path) {
        final String normalizedPath;
        if (path == null) {
            normalizedPath = null;
        } else {
            normalizedPath = UriUtils.uriPathPercentDecode(URI.create(path).normalize().getRawPath(), true);
        }
        return getAclEndpoints(session, client, service).stream()
                .anyMatch(ep -> ep.matches(method, normalizedPath));
    }

    /**
     * Returns the endpoints the client has access to.
     * <p>
     * Includes only endpoints the client has a direct acl entry for, does not check for implicitly allowed endpoints.
     */
    protected List<EndpointType> getAclEndpoints(Session session, ClientId client, ServiceId service) {
        log.debug("getAcl, session = {}", session);

        final ClientType serviceOwner = getClient(session, service.getClientId());

        if (serviceOwner == null) {
            // should not normally happen, but possible if service and acl caches are in inconsistent state
            // (see CachingServerConfImpl))
            throw new CodedException(X_UNKNOWN_SERVICE, "Service '%s' owner not found", service);
        }

        final ClientId localClientId = identifierDao.findClientId(session, client);
        // localClientId can be null if the permissions are defined by a global group

        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<AccessRightType> query = cb.createQuery(AccessRightType.class);
        final Root<ClientType> root = query.from(ClientType.class);
        final Join<ClientType, AccessRightType> acl = root.join("acl");
        final Join<AccessRightType, EndpointType> endpoint = acl.join("endpoint");
        final Join<AccessRightType, XRoadId> identifier = acl.join("subjectId");
        acl.fetch("endpoint");

        query.select(acl).where(cb.and(
                        cb.equal(root, serviceOwner),
                        cb.equal(endpoint.get("serviceCode"), service.getServiceCode())),
                cb.or(cb.equal(identifier, localClientId),
                        cb.equal(identifier.get("type"), XRoadObjectType.GLOBALGROUP),
                        cb.equal(identifier.get("type"), XRoadObjectType.LOCALGROUP)));

        return session.createQuery(query).setReadOnly(true).list().stream()
                .filter(it -> subjectMatches(serviceOwner, it.getSubjectId(), client))
                .map(AccessRightType::getEndpoint)
                .collect(Collectors.toList());
    }

    private boolean subjectMatches(ClientType serviceOwner, XRoadId aclSubject, ClientId client) {
        if (aclSubject instanceof GlobalGroupId) {
            return GlobalConf.isSubjectInGlobalGroup(client, (GlobalGroupId) aclSubject);
        } else if (aclSubject instanceof LocalGroupId) {
            return isMemberInLocalGroup(client, (LocalGroupId) aclSubject, serviceOwner);
        } else {
            return client.equals(aclSubject);
        }
    }

    private boolean isMemberInLocalGroup(ClientId member, LocalGroupId groupId, ClientType groupOwner) {
        return groupOwner.getLocalGroup().stream()
                .filter(g -> Objects.equals(groupId.getGroupCode(), g.getGroupCode()))
                .flatMap(g -> g.getGroupMember().stream())
                .anyMatch(m -> m.getGroupMemberId().equals(member));
    }

    /**
     * Runs the callback in transaction, creating a new transaction if necessary and otherwise joining the current one.
     * In the case of join, the transaction is not committed.
     */
    protected <T> T tx(TransactionCallback<T> t) {
        try {
            return doInTransaction(t);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

}
