package ee.cyber.xroad.serviceimporter;

import java.util.*;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.mediator.BackendTypes;
import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.*;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.XroadId;
import ee.ria.xroad.common.identifier.XroadObjectType;
import ee.ria.xroad.common.request.ManagementRequestSender;

import static ee.cyber.xroad.serviceimporter.Helper.*;

/**
 * Handles exporting of X-Road 6.0 client data and importing of X-Road 5.0 client data.
 */
@Slf4j
public class ServiceImporter {

    private static final int SERVICE_WITH_VERSION_PARTS = 3;

    private static final String STATE_SAVED = "saved";

    private static final String[] XROADV5_META_SERVICES = {
        "getProducerACL", "getServiceACL"
    };

    private final IdentifierMappingProvider identifierMapping;
    private final XConf xConf;
    private final Date now;

    private ServerConfType serverConf;

    /**
     * Constructs a new service importer.
     */
    public ServiceImporter() {
        this.identifierMapping = IdentifierMapping.getInstance();
        this.xConf = new XConf();
        this.now = new Date();

        GlobalConf.initForCurrentThread();
    }

    /**
     * Imports X-Road 5.0 clients, optionally deleting the client with the given short name.
     * @param deleteShortName the client short name
     * @throws Exception in case of any errors
     */
    public void doImport(String deleteShortName) throws Exception {
        log.info("Importing clients...");

        serverConf = getConf(); // will throw exception if server conf not initalized

        xConf.readLock();

        try {
            Set<ClientId> importedClients = new HashSet<>();

            for (XConf.Consumer consumer : xConf.getConsumers()) {
                ClientType client = importClient(consumer);

                if (client != null) {
                    String peerType = xConf.getPeerType(consumer);

                    switch (peerType) {
                        case "https":
                            client.setIsAuthentication("SSLAUTH");
                            break;
                        case "https noauth":
                            client.setIsAuthentication("SSLNOAUTH");
                            break;
                        default:
                            client.setIsAuthentication("NOSSL");
                            break;
                    }

                    importInternalSSLCerts(client, consumer, false);

                    importedClients.add(client.getIdentifier());
                }
            }

            for (XConf.Producer producer : xConf.getProducers()) {
                ClientType client = importClient(producer);
                if (client != null) {
                    importInternalSSLCerts(client, producer,
                        importedClients.contains(client.getIdentifier()));

                    importServices(client, producer);

                    importedClients.add(client.getIdentifier());
                }
            }

            if (deleteShortName == null) {
                return;
            }

            ClientId deleteClientId =
                identifierMapping.getClientId(deleteShortName);

            if (deleteClientId == null) {
                return;
            }

            ClientType deleteClient = new ClientDAOImpl().getClient(
                    ServerConfDatabaseCtx.getSession(), deleteClientId);

            if (deleteClient == null) {
                return;
            }

            String status = deleteClient.getClientStatus();
            SecurityServerId serverId = getSecurityServerId();
            ManagementRequestSender sender = getManagementRequestSender();

            // Delete the given client if it was not imported.
            if (!importedClients.contains(deleteClientId)) {

                if (ClientType.STATUS_REGINPROG.equals(status)
                        || ClientType.STATUS_REGISTERED.equals(status)) {

                    log.info("Sending deletion request for client '{}'",
                        deleteClientId);
                    sender.sendClientDeletionRequest(serverId, deleteClientId);
                }

                log.info("Deleting client '{}'", deleteClientId);
                getConf().getClient().remove(deleteClient);
            }
        } finally {
            xConf.unlock();
        }
    }

    private ClientType importClient(XConf.Org org) throws Exception {
        ClientId mappedId = identifierMapping.getClientId(org.getShortName());

        if (mappedId == null) {
            log.warn("Could not find identifier mapping "
                    + "for client '{}', skipping", org.getShortName());
            return null;
        }

        ClientType mappedClient = new ClientDAOImpl().getClient(
                ServerConfDatabaseCtx.getSession(), mappedId);

        if (mappedClient == null) {
            log.info("Importing client '{}'", org.getShortName());

            mappedClient = new ClientType();
            mappedClient.setConf(getConf());
            mappedClient.setIdentifier(getIdentifier(mappedId));
            mappedClient.setClientStatus(STATE_SAVED);

            getConf().getClient().add(mappedClient);
        } else {
            log.info("Client '{}' already exists in X-Road 6.0",
                    org.getShortName());
        }

        // delete everything to make sure the confs are in sync

        log.info("Deleting client WSDLs and ACLs");

        for (WsdlType wsdl : mappedClient.getWsdl()) {
            wsdl.setClient(null);
        }

        mappedClient.getWsdl().clear();
        mappedClient.getAcl().clear();

        return mappedClient;
    }

    private void importInternalSSLCerts(ClientType client, XConf.Org org,
            boolean keepOld) throws Exception {
        log.info("Importing internal SSL certs for '{}'", org.getShortName());

        if (!keepOld) {
            client.getIsCert().clear();
        }

        List<CertificateType> newCerts = new ArrayList<>();

        certs:
        for (byte[] cert : xConf.getInternalSSLCerts(org)) {
            for (CertificateType existingCert : client.getIsCert()) {
                if (Arrays.equals(cert, existingCert.getData())) {
                    continue certs;
                }
            }

            CertificateType certType = new CertificateType();
            certType.setData(cert);
            newCerts.add(certType);
        }

        client.getIsCert().addAll(newCerts);
    }

    private void importServices(ClientType client, XConf.Producer producer)
            throws Exception {
        log.info("Importing services for '{}'", producer.getShortName());

        if (!xConf.adapterExists(producer)) {
            log.info("Adapter not configured, skipping");
            return;
        }

        int serviceTimeout = xConf.getServiceTimeout(producer);

        Map<String, List<String>> authorizedClients =
            xConf.getServiceAuthorizedClients(producer);

        Map<String, List<String>> authorizedGroups =
            xConf.getServiceAuthorizedGroups(producer);

        String adapterURL = xConf.getAdapterURL(producer);
        boolean sslNoAuth = xConf.getPeerType(producer).equals("https noauth");

        WsdlType newWsdl = new WsdlType();
        newWsdl.setUrl(xConf.getWsdlURL(producer));
        newWsdl.setClient(client);
        newWsdl.setBackendURL(adapterURL);
        newWsdl.setBackend(BackendTypes.XROADV5);
        newWsdl.setRefreshedDate(now);

        for (String serviceName : xConf.getServices(producer)) {
            log.info("Importing service '{}'", serviceName);

            String[] splitService = serviceName.split("\\.");

            ServiceType service = new ServiceType();
            service.setWsdl(newWsdl);
            service.setServiceCode(splitService[1]);
            if (splitService.length == SERVICE_WITH_VERSION_PARTS) {
                service.setServiceVersion(splitService[2]);
            }
            service.setUrl(getServiceMediatorURL(client.getIdentifier()));
            service.setTimeout(serviceTimeout);
            service.setSslAuthentication(!sslNoAuth);

            newWsdl.getService().add(service);

            importServiceAcl(getAcl(client, service.getServiceCode()),
                             authorizedClients.get(service.getServiceCode()),
                             authorizedGroups.get(service.getServiceCode()));
        }

        // for meta services, import only acl
        for (String serviceCode : XROADV5_META_SERVICES) {
            importServiceAcl(getAcl(client, serviceCode),
                             authorizedClients.get(serviceCode),
                             authorizedGroups.get(serviceCode));
        }

        client.getWsdl().add(newWsdl);
    }

    private void importServiceAcl(AclType acl, List<String> authorizedClients,
            List<String> authorizedGroups) throws Exception {

        log.info("Importing ACL for service '{}'", acl.getServiceCode());

        if (authorizedClients != null) {
            // add those authorized clients for which we have
            // identifier mapping
            for (String subjectShortName : authorizedClients) {
                ClientId subjectId =
                    identifierMapping.getClientId(subjectShortName);

                if (subjectId != null) {
                    AuthorizedSubjectType authorizedSubject =
                        new AuthorizedSubjectType();
                    authorizedSubject.setSubjectId(getIdentifier(subjectId));
                    authorizedSubject.setRightsGiven(now);

                    acl.getAuthorizedSubject().add(authorizedSubject);
                } else {
                    log.warn("Could not find identifier mapping for "
                            + "authorized subject '{}', skipping",
                            subjectShortName);
                }
            }
        }

        if (authorizedGroups != null) {
            String instanceIdentifier = GlobalConf.getInstanceIdentifier();

            for (String subjectShortName : authorizedGroups) {
                GlobalGroupId groupId = GlobalGroupId.create(
                    instanceIdentifier, subjectShortName);

                AuthorizedSubjectType authorizedSubject =
                    new AuthorizedSubjectType();
                authorizedSubject.setSubjectId(getIdentifier(groupId));
                authorizedSubject.setRightsGiven(now);

                acl.getAuthorizedSubject().add(authorizedSubject);
            }
        }
    }

    private String getServiceMediatorURL(ClientId clientId) throws Exception {
        return "http://"
            + MediatorSystemProperties.getServiceMediatorConnectorHost()
            + ":" + MediatorSystemProperties.getServiceMediatorHttpPort()
            + "/?" + getClientIdAsQueryString(clientId);
    }

    private String getClientIdAsQueryString(ClientId clientId)
            throws Exception {
        StringBuilder str = new StringBuilder();

        str.append("xRoadInstance=");
        str.append(urlEncode(clientId.getXRoadInstance()));
        str.append("&memberClass=");
        str.append(urlEncode(clientId.getMemberClass()));
        str.append("&memberCode=");
        str.append(urlEncode(clientId.getMemberCode()));

        if (clientId.getSubsystemCode() != null) {
            str.append("&subsystemCode=");
            str.append(urlEncode(clientId.getSubsystemCode()));
        }

        return str.toString();
    }

    private AclType getAcl(ClientType client, String serviceCode) {
        for (AclType acl : client.getAcl()) {
            if (acl.getServiceCode().equals(serviceCode)) {
                return acl;
            }
        }

        AclType acl = new AclType();
        acl.setServiceCode(serviceCode);
        client.getAcl().add(acl);

        return acl;
    }

    private SecurityServerId getSecurityServerId() {
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        String serverCode = serverConf.getServerCode();

        return SecurityServerId.create(
            ownerId.getXRoadInstance(), ownerId.getMemberClass(),
            ownerId.getMemberCode(), serverCode);
    }

    private ManagementRequestSender getManagementRequestSender() {
        ClientId receiver = GlobalConf.getManagementRequestService();
        ClientId sender = serverConf.getOwner().getIdentifier();

        return new ManagementRequestSender(
            "serviceImporter", receiver, sender);
    }

    /**
     * Exports X-Road 6.0 clients, optionally deleting the client with the given ID.
     * @param deleteClientId the client ID
     * @throws Exception in case of any errors
     */
    public void doExport(ClientId deleteClientId) throws Exception {
        log.info("Exporting clients...");

        getConf(); // will throw exception if server conf not initalized

        xConf.writeLock();

        try {
            for (ClientType client : getConf().getClient()) {
                String shortName =
                    identifierMapping.getShortName(client.getIdentifier());

                if (shortName == null) {
                    log.warn("Could not find identifier mapping for "
                            + "client '{}', skipping", client.getIdentifier());
                    continue;
                }

                String fullName =
                        GlobalConf.getMemberName(client.getIdentifier());

                XConf.Consumer consumer = new XConf.Consumer(shortName);
                XConf.Producer producer = new XConf.Producer(shortName);

                if (xConf.createOrg(consumer, fullName)) {
                    log.info("Exporting client '{}' as consumer",
                             client.getIdentifier());
                }

                if (xConf.createOrg(producer, fullName)) {
                    log.info("Exporting client '{}' as producer",
                             client.getIdentifier());
                }

                exportInternalSSLConf(consumer, client);
                exportInternalSSLConf(producer, client);

                exportServices(producer, client);
            }

            if (deleteClientId == null) {
                return;
            }

            String deleteShortName =
                identifierMapping.getShortName(deleteClientId);

            if (deleteShortName == null) {
                return;
            }

            XConf.Consumer deleteConsumer = new XConf.Consumer(deleteShortName);
            XConf.Producer deleteProducer = new XConf.Producer(deleteShortName);

            if (xConf.orgExists(deleteConsumer)) {
                log.info("Deleting consumer '{}'", deleteShortName);
                xConf.deleteOrg(deleteConsumer);
            }

            if (xConf.orgExists(deleteProducer)) {
                log.info("Deleting producer '{}'", deleteShortName);
                xConf.deleteOrg(deleteProducer);
            }
        } finally {
            xConf.unlock();
        }
    }

    private void exportServices(XConf.Producer producer, ClientType client)
            throws Exception {
        log.info("Exporting services for '{}'", client.getIdentifier());

        if (client.getWsdl().isEmpty()) {
            log.info("No services found, deleting adapter conf and ACL");
            xConf.deleteAdapterConf(producer);
            xConf.deleteAcl(producer);
            return;
        }

        xConf.setPeerIP(producer, "127.0.0.1");
        xConf.setPeerPort(producer,
            MediatorSystemProperties.getServiceMediatorHttpPort());
        xConf.setAdapterURI(producer,
            "/?" + getClientIdAsQueryString(client.getIdentifier()));
        xConf.setSchemaURI(producer,
            "/wsdl?" + getClientIdAsQueryString(client.getIdentifier()));

        XConfAcl xconfAcl = new XConfAcl();

        // export localgroups as lists of clients
        for (LocalGroupType localGroup : client.getLocalGroup()) {
            xconfAcl.localGroups.put(localGroup.getGroupCode(),
                localGroup.getGroupMember());
        }

        int timeout = 0;
        for (WsdlType wsdl : client.getWsdl()) {
            log.debug("Exporting services from WSDL '{}'", wsdl.getUrl());

            for (ServiceType service : wsdl.getService()) {
                exportService(producer, service, xconfAcl);

                timeout = Math.max(timeout, service.getTimeout());
            }
        }

        for (AclType aclType : client.getAcl()) {
            exportAcl(producer, aclType, xconfAcl);
        }

        xConf.setServiceTimeout(producer, timeout);

        xConf.saveQueries(producer, xconfAcl.queries);
        xConf.saveConsumers(producer, xconfAcl.consumers);
        xConf.saveGroups(producer, xconfAcl.groups);
        xConf.saveACL(producer, xconfAcl.cacl);
        xConf.saveGACL(producer, xconfAcl.gacl);
    }

    private void exportService(XConf.Producer producer, ServiceType service,
            XConfAcl xconfAcl) throws Exception {
        log.debug("Exporting service '{}'", service.getServiceCode());

        String query = producer.getShortName() + "." + service.getServiceCode();
        String queryWithVersion = service.getServiceVersion() != null
            ? query + "." + service.getServiceVersion() : query;

        xconfAcl.queries.add(queryWithVersion);
    }

    private void exportAcl(XConf.Producer producer, AclType aclType,
            XConfAcl xconfAcl) {
        log.debug("Exporting acl for '{}'", aclType.getServiceCode());

        String query = producer.getShortName() + "." + aclType.getServiceCode();

        for (AuthorizedSubjectType subject : aclType.getAuthorizedSubject()) {
            XroadId subjectId = subject.getSubjectId();

            if (subjectId.getObjectType() == XroadObjectType.GLOBALGROUP) {
                String group = ((GlobalGroupId) subjectId).getGroupCode();

                xconfAcl.groups.add(group);

                if (!xconfAcl.gacl.containsKey(query)) {
                    xconfAcl.gacl.put(query, new HashSet<String>());
                }

                xconfAcl.gacl.get(query).add(group);

            } else if (subjectId.getObjectType() == XroadObjectType.LOCALGROUP) {
                List<GroupMemberType> localGroupMembers =
                        xconfAcl.localGroups.get(
                                ((LocalGroupId) subjectId).getGroupCode());

                for (GroupMemberType groupMember : localGroupMembers) {
                    ClientId clientId = groupMember.getGroupMemberId();

                    String consumerShortName =
                            identifierMapping.getShortName(clientId);

                    if (consumerShortName != null) {
                        xconfAcl.consumers.add(consumerShortName);

                        if (!xconfAcl.cacl.containsKey(query)) {
                            xconfAcl.cacl.put(query, new HashSet<String>());
                        }

                        xconfAcl.cacl.get(query).add(consumerShortName);

                    } else {
                        log.warn("Could not find identifier mapping for "
                                + " authorizedSubject '{}', skipping", clientId);
                    }
                }
            } else {
                String consumerShortName =
                        identifierMapping.getShortName((ClientId) subjectId);

                if (consumerShortName != null) {
                    xconfAcl.consumers.add(consumerShortName);

                    if (!xconfAcl.cacl.containsKey(query)) {
                        xconfAcl.cacl.put(query, new HashSet<String>());
                    }

                    xconfAcl.cacl.get(query).add(consumerShortName);
                } else {
                    log.warn("Could not find identifier mapping for "
                            + " authorizedSubject '{}', skipping", subjectId);
                }
            }
        }
    }

    private void exportInternalSSLConf(XConf.Org org, ClientType client)
            throws Exception {
        log.info("Exporting internal SSL conf for '{}'", org.getShortName());

        // both, consumer and producer, will from now on disable ssl for
        // internal connections to allow access for mediators
        xConf.setPeerType(org, "http");

        // these are actually irrelevant, as ssl is disabled
        List<byte[]> isCerts = new ArrayList<>();
        for (CertificateType ct : client.getIsCert()) {
            isCerts.add(ct.getData());
        }

        xConf.setInternalSSLCerts(org, isCerts);
    }

    static class XConfAcl {
        Map<String, List<GroupMemberType>> localGroups = new HashMap<>();

        // all known queries of this producer
        Set<String> queries = new HashSet<>();
        // all known consumers of this producer's services
        Set<String> consumers = new HashSet<>();
        // all known groups of consumers of this producer's services
        Set<String> groups = new HashSet<>();

        // queries mapped to allowed consumers
        Map<String, Set<String>> cacl = new HashMap<>();
        // queries mapped to allowed groups
        Map<String, Set<String>> gacl = new HashMap<>();
    }
}
