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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.db.TransactionCallback;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.metadata.RestServiceDetailsListType;
import ee.ria.xroad.common.metadata.RestServiceType;
import ee.ria.xroad.common.metadata.XRoadRestServiceDetailsType;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.UriUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SharedSessionContract;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.entity.AccessRightEntity;
import org.niis.xroad.serverconf.entity.CertificateEntity;
import org.niis.xroad.serverconf.entity.ClientEntity;
import org.niis.xroad.serverconf.entity.ClientIdEntity;
import org.niis.xroad.serverconf.entity.EndpointEntity;
import org.niis.xroad.serverconf.entity.XRoadIdEntity;
import org.niis.xroad.serverconf.impl.dao.CertificateDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ClientDAOImpl;
import org.niis.xroad.serverconf.impl.dao.IdentifierDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServerConfDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServiceDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServiceDescriptionDAOImpl;
import org.niis.xroad.serverconf.mapper.CertificateMapper;
import org.niis.xroad.serverconf.mapper.ClientMapper;
import org.niis.xroad.serverconf.mapper.EndpointMapper;
import org.niis.xroad.serverconf.mapper.ServerConfMapper;
import org.niis.xroad.serverconf.mapper.ServiceDescriptionMapper;
import org.niis.xroad.serverconf.mapper.ServiceMapper;
import org.niis.xroad.serverconf.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.model.Certificate;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.Description;
import org.niis.xroad.serverconf.model.Endpoint;
import org.niis.xroad.serverconf.model.ServerConf;
import org.niis.xroad.serverconf.model.Service;
import org.niis.xroad.serverconf.model.ServiceDescription;
import org.niis.xroad.serverconf.model.TimestampingService;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx.doInTransaction;

/**
 * Server conf implementation.
 */
@Slf4j
@RequiredArgsConstructor
public class ServerConfImpl implements ServerConfProvider {

    // default service connection timeout in seconds
    protected static final int DEFAULT_SERVICE_TIMEOUT = 30;

    protected final GlobalConfProvider globalConfProvider;

    private final ServiceDAOImpl serviceDao = new ServiceDAOImpl();
    private final IdentifierDAOImpl identifierDao = new IdentifierDAOImpl();
    private final ClientDAOImpl clientDao = new ClientDAOImpl();
    private final CertificateDAOImpl certificateDao = new CertificateDAOImpl();
    private final ServerConfDAOImpl serverConfDao = new ServerConfDAOImpl();
    private final ServiceDescriptionDAOImpl serviceDescriptionDao = new ServiceDescriptionDAOImpl();

    @Override
    public SecurityServerId.Conf getIdentifier() {
        return tx(session -> {
            ServerConf confType = getConf(session);
            Client owner = confType.getOwner();
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
            Service serviceType = getService(session, service);
            if (serviceType != null) {
                return serviceType.getUrl();
            }

            return null;
        });
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return tx(session -> {
            Service serviceType = getService(session, service);
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
            serviceDao.getServicesByDescriptionType(session, XRoadIdMapper.get().toEntity(serviceProvider),
                            Description.OPENAPI3, Description.REST)
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
            serviceDao.getServicesByDescriptionType(session, serviceProvider, Description.OPENAPI3,
                            Description.REST)
                    .forEach(serviceId -> {
                        final List<Endpoint> acl = getAclEndpoints(session, client, serviceId);
                        if (!acl.isEmpty()) {
                            final List<ee.ria.xroad.common.metadata.Endpoint> endpoints = getServiceEndpoints(serviceId);
                            XRoadRestServiceDetailsType serviceDetails = createRestServiceDetails(serviceId);
                            for (ee.ria.xroad.common.metadata.Endpoint e : endpoints) {
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
        serviceDetails.setServiceType(getRestServiceType(getDescription(serviceId)));
        return serviceDetails;
    }

    private RestServiceType getRestServiceType(Description description) {
        return switch (description) {
            case REST -> RestServiceType.REST;
            case OPENAPI3 -> RestServiceType.OPENAPI;
            default -> throw new IllegalArgumentException("The given parameter is not a REST service type!");
        };
    }

    @Override
    public List<ServiceId.Conf> getAllServices(ClientId serviceProvider) {
        return tx(session -> XRoadIdMapper.get().toServices(serviceDao.getServices(session, serviceProvider)));
    }

    @Override
    public List<ServiceId.Conf> getServicesByDescription(ClientId serviceProvider, Description description) {
        return tx(session -> XRoadIdMapper.get().toServices(
                serviceDao.getServicesByDescriptionType(session, serviceProvider, description)
        ));
    }

    @Override
    public List<ServiceId.Conf> getAllowedServices(ClientId serviceProvider, ClientId client) {
        return tx(session -> {
            List<ServiceId.Conf> allServices =
                    XRoadIdMapper.get().toServices(serviceDao.getServices(session, serviceProvider));
            return allServices.stream()
                    .filter(s -> !getAclEndpoints(session, client, s).isEmpty())
                    .collect(Collectors.toList());
        });
    }

    @Override
    public List<ServiceId.Conf> getAllowedServicesByDescription(ClientId serviceProvider, ClientId client, Description description) {
        return tx(session -> {
            List<ServiceId.Conf> allServices =
                    XRoadIdMapper.get().toServices(
                            serviceDao.getServicesByDescriptionType(session, serviceProvider, description)
                    );
            return allServices.stream()
                    .filter(s -> !getAclEndpoints(session, client, s).isEmpty())
                    .collect(Collectors.toList());
        });
    }

    @Override
    public boolean isSslAuthentication(ServiceId service) {
        return tx(session -> {
            Service serviceType = getService(session, service);
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
        return tx(session -> getConf(session).getClients().stream()
                .map(Client::getIdentifier)
                .collect(Collectors.toList()));
    }

    @Override
    public String getMemberStatus(ClientId memberId) {
        return tx(session -> {
            Client client = getClient(session, memberId);
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
            Client clientType = getClient(session, client);
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
        return tx(session -> CertificateMapper.get().toTargets(clientDao.getIsCerts(session, client)).stream()
                .map(Certificate::getData)
                .map(CryptoUtils::readCertificate)
                .toList());
    }

    @Override
    public List<X509Certificate> getAllIsCerts() {
        return tx(session -> certificateDao.findAll(session).stream()
                .map(CertificateEntity::getData)
                .map(CryptoUtils::readCertificate)
                .toList());
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return tx(session -> {
            ServiceDescription serviceDescription = getServiceDescription(session, service);
            if (serviceDescription != null && serviceDescription.isDisabled()) {
                if (serviceDescription.getDisabledNotice() == null) {
                    return String.format("Service '%s' is disabled", service);
                }

                return serviceDescription.getDisabledNotice();
            }

            return null;
        });
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return InternalSSLKey.load();
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return isQueryAllowed(sender, service, null, null);
    }

    @Override
    public boolean isQueryAllowed(ClientId client, ServiceId service, String method, String path) {
        return tx(session -> internalIsQueryAllowed(session, client, service, method, path));
    }

    @Override
    public List<String> getTspUrl() {
        return tx(session -> getConf(session).getTimestampingServices().stream()
                .map(TimestampingService::getUrl)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList()));
    }

    @Override
    public Description getDescription(ServiceId service) {
        return tx(session -> {
            Service serviceType = getService(session, service);
            if (serviceType != null && serviceType.getServiceDescription() != null) {
                return serviceType.getServiceDescription().getType();
            }

            return null;
        });
    }

    @Override
    public String getServiceDescriptionURL(ServiceId service) {
        return tx(session -> {
            Service serviceType = getService(session, service);
            if (serviceType != null && serviceType.getServiceDescription() != null) {
                return serviceType.getServiceDescription().getUrl();
            }
            return null;
        });
    }

    @Override
    public List<ee.ria.xroad.common.metadata.Endpoint> getServiceEndpoints(ServiceId serviceId) {
        return tx(session -> clientDao.getClient(session, serviceId.getClientId()).getEndpoints().stream()
                .map(e -> EndpointMapper.get().toTarget(e))
                .filter(e -> e.getServiceCode().equals(serviceId.getServiceCode()))
                .filter(e -> !e.isBaseEndpoint())
                .map(e -> createEndpoint(e.getMethod(), e.getPath()))
                .toList());
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

    private static ee.ria.xroad.common.metadata.Endpoint createEndpoint(String method, String path) {
        ee.ria.xroad.common.metadata.Endpoint endpoint = new ee.ria.xroad.common.metadata.Endpoint();
        endpoint.setMethod(method);
        endpoint.setPath(path);
        return endpoint;
    }

    // ------------------------------------------------------------------------

    protected ServerConf getConf(Session session) {
        return ServerConfMapper.get().toTarget(serverConfDao.getConf(session));
    }

    protected Client getClient(Session session, ClientId c) {
        return ClientMapper.get().toTarget(clientDao.getClient(session, c));
    }

    protected Service getService(Session session, ServiceId s) {
        return ServiceMapper.get().toTarget(serviceDao.getService(session, s));
    }

    protected ServiceDescription getServiceDescription(Session session, ServiceId service) {
        return ServiceDescriptionMapper.get().toTarget(serviceDescriptionDao.getServiceDescription(session, service));
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
    protected List<Endpoint> getAclEndpoints(Session session, ClientId client, ServiceId service) {
        log.debug("getAcl, session = {}", session);

        final ClientEntity serviceOwner = clientDao.getClient(session, service.getClientId());

        if (serviceOwner == null) {
            // should not normally happen, but possible if service and acl caches are in inconsistent state
            // (see CachingServerConfImpl))
            throw new CodedException(X_UNKNOWN_SERVICE, "Service '%s' owner not found", service);
        }

        final ClientIdEntity localClientId = identifierDao.findClientId(session, client);
        // localClientId can be null if the permissions are defined by a global group

        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<AccessRightEntity> query = cb.createQuery(AccessRightEntity.class);
        final Root<ClientEntity> root = query.from(ClientEntity.class);
        final Join<ClientEntity, AccessRightEntity> acl = root.join("accessRights");
        final Join<AccessRightEntity, EndpointEntity> endpoint = acl.join("endpoint");
        final Join<AccessRightEntity, XRoadIdEntity> identifier = acl.join("subjectId");
        acl.fetch("endpoint");

        var orPredicates = new ArrayList<Predicate>();
        if (localClientId != null) {
            orPredicates.add(cb.equal(identifier, localClientId));
        }
        orPredicates.add(cb.equal(identifier.get("objectType"), XRoadObjectType.GLOBALGROUP));
        orPredicates.add(cb.equal(identifier.get("objectType"), XRoadObjectType.LOCALGROUP));

        query.select(acl).where(cb.and(
                        cb.equal(root, serviceOwner),
                        cb.equal(endpoint.get("serviceCode"), service.getServiceCode())),
                cb.or(orPredicates.toArray(new Predicate[0])));
        var accessRights = session.createQuery(query).setReadOnly(true).list();
        return EndpointMapper.get().toTargets(
                accessRights.stream()
                .filter(it -> subjectMatches(serviceOwner, it.getSubjectId(), client))
                .map(AccessRightEntity::getEndpoint)
                .toList()
        );
    }

    private boolean subjectMatches(ClientEntity serviceOwner, XRoadId aclSubject, ClientId client) {
        if (aclSubject instanceof GlobalGroupId globalGroupId) {
            return globalConfProvider.isSubjectInGlobalGroup(client, globalGroupId);
        } else if (aclSubject instanceof LocalGroupId localGroupId) {
            return isMemberInLocalGroup(client, localGroupId, serviceOwner);
        } else {
            return client.equals(aclSubject);
        }
    }

    private boolean isMemberInLocalGroup(ClientId member, LocalGroupId groupId, ClientEntity groupOwner) {
        return groupOwner.getLocalGroups().stream()
                .filter(g -> Objects.equals(groupId.getGroupCode(), g.getGroupCode()))
                .flatMap(g -> g.getGroupMembers().stream())
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
