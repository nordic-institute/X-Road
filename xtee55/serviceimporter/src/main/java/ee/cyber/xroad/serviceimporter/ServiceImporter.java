package ee.cyber.xroad.serviceimporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.util.MediatorUtils;
import ee.cyber.xroad.serviceimporter.XConf.Producer;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.conf.serverconf.model.WsdlType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import static ee.cyber.xroad.serviceimporter.Helper.getConf;
import static ee.cyber.xroad.serviceimporter.Helper.getIdentifier;

/**
 * Handles importing of X-Road 5.0 client data.
 */
@Slf4j
public class ServiceImporter {

    private static final String STATE_SAVED = "saved";

    private final IdentifierMappingProvider identifierMapping;
    private final XConf xConf;
    private final Date now;

    /**
     * Constructs a new service importer.
     */
    public ServiceImporter() {
        this.identifierMapping = IdentifierMapping.getInstance();
        this.xConf = new XConf();
        this.now = new Date();

        GlobalConf.initForCurrentThread();
    }

    List<String> getProducersNamesToImport() throws IOException {
        return xConf.getProducers().stream()
                .map(c -> c.getShortName())
                .collect(Collectors.toList());
    }

    List<String> getServiceCodesToImport(String producerName) throws IOException {
        Producer producer = findProducer(producerName);

        if (producer == null) {
            log.warn("Producer '{}' does not exist in X-Road 5.0",
                    producerName);

            return null;
        }

        List<String> serviceCodes = new ArrayList<>();

        for (String v5FullServiceName : xConf.getServices(producer)) {
            String serviceCode = MediatorUtils.extractServiceCode(v5FullServiceName);

            if (serviceCode == null) {
                log.warn("Invalid service name '{}', skipping",
                        v5FullServiceName);

                continue;
            }

            serviceCodes.add(serviceCode);
        }

        return serviceCodes;
    }

    /**
     * Imports X-Road 5.0 client service ACL for the given producer and service.
     * @param producerName short name of the producer that owns the services
     * @param serviceName name of the service for which to import the ACL
     * @throws Exception in case of any errors
     */
    public void importAcl(String producerName, String serviceName)
            throws Exception {
        log.info("Importing service '{}' ACL for '{}' ...", serviceName,
                producerName);

        getConf(); // will throw exception if server conf not initalized

        xConf.readLock();

        try {
            ClientId mappedId = identifierMapping.getClientId(producerName);

            if (mappedId == null) {
                log.warn("Could not find identifier mapping "
                        + "for client '{}', skipping", producerName);
                return;
            }

            ClientType existingClient = new ClientDAOImpl().getClient(
                    ServerConfDatabaseCtx.getSession(), mappedId);

            if (existingClient != null) {
                Producer producer = findProducer(producerName);

                if (producer == null) {
                    log.warn("Producer '{}' does not exist in X-Road 5.0",
                            producerName);
                    return;
                }

                Map<String, List<String>> authorizedClients =
                    xConf.getServiceAuthorizedClients(producer);

                Map<String, List<String>> authorizedGroups =
                    xConf.getServiceAuthorizedGroups(producer);

                ServiceType service = findService(serviceName, existingClient);

                if (service == null) {
                    log.warn("Service '{}' does not exist for '{}'"
                            + " in X-Road 6.0", serviceName, producerName);
                    return;
                }

                clearServiceAcl(existingClient, serviceName);

                importServiceAcl(existingClient, serviceName,
                        authorizedClients.get(serviceName),
                        authorizedGroups.get(serviceName));
            } else {
                log.warn("Client '{}' does not exists in X-Road 6.0",
                        producerName);
            }
        } finally {
            xConf.unlock();
        }
    }

    private void clearServiceAcl(ClientType existingClient, String serviceName) {
        existingClient.getAcl().removeIf(
                acl -> acl.getServiceCode().equals(serviceName));
    }

    private ServiceType findService(
            String serviceName, ClientType existingClient) {
        for (WsdlType wsdl : existingClient.getWsdl()) {
            for (ServiceType s : wsdl.getService()) {
                if (s.getServiceCode().equals(serviceName)) {
                    return s;
                }
            }
        }

        return null;
    }

    private Producer findProducer(String producerName) throws IOException {
        for (Producer p : xConf.getProducers()) {
            if (p.getShortName().equals(producerName)) {
                return p;
            }
        }

        return null;
    }

    /**
     * Imports X-Road 5.0 client configuration, excluding ACLs.
     * @throws Exception in case of any errors
     */
    public void importServices() throws Exception {
        log.info("Importing clients...");

        getConf(); // will throw exception if server conf not initialized

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

        log.info("Deleting WSDLs and ACLs of client '{}'", org.getShortName());

        for (WsdlType wsdl : mappedClient.getWsdl()) {
            wsdl.setClient(null);
        }

        mappedClient.getWsdl().clear();
        mappedClient.getAcl().clear();

        return mappedClient;
    }

    private void importInternalSSLCerts(ClientType client, XConf.Org org,
            boolean keepOld) throws Exception {
        log.info("Importing internal TLS certs for client '{}'",
                org.getShortName());

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
        log.info("Importing services of producer '{}'", producer.getShortName());

        if (!xConf.adapterExists(producer)) {
            log.info("Adapter not configured, skipping");

            return;
        }

        int serviceTimeout = xConf.getServiceTimeout(producer);
        String adapterURL = xConf.getAdapterURL(producer);
        boolean sslNoAuth = xConf.getPeerType(producer).equals("https noauth");

        WsdlType newWsdl = new WsdlType();
        newWsdl.setUrl("http://IMPORTED-V5-SERVICES"); //dummy value
        newWsdl.setClient(client);
        newWsdl.setRefreshedDate(now);

        for (String serviceName : xConf.getServices(producer)) {
            log.info("Importing service '{}'", serviceName);

            String serviceCode = MediatorUtils.extractServiceCode(serviceName);

            if (serviceCode == null) {
                log.warn("Invalid service name '{}', skipping", serviceName);

                continue;
            }

            String serviceVersion = MediatorUtils.extractServiceVersion(serviceName);

            ServiceType service = new ServiceType();
            service.setWsdl(newWsdl);
            service.setServiceCode(serviceCode);

            if (serviceVersion != null) {
                service.setServiceVersion(serviceVersion);
            }

            service.setUrl(adapterURL);
            service.setTimeout(serviceTimeout);
            service.setSslAuthentication(!sslNoAuth);

            newWsdl.getService().add(service);
        }

        newWsdl.setDisabled(true);
        client.getWsdl().add(newWsdl);
    }

    private void importServiceAcl(ClientType client, String serviceCode,
            List<String> authorizedClients,
            List<String> authorizedGroups) throws Exception {

        log.info("Importing ACL for service '{}'", serviceCode);

        if (authorizedClients != null) {
            // add those authorized clients for which we have
            // identifier mapping
            for (String subjectShortName : authorizedClients) {
                ClientId subjectId =
                    identifierMapping.getClientId(subjectShortName);

                if (subjectId != null) {
                    AccessRightType accessRight = new AccessRightType();
                    accessRight.setServiceCode(serviceCode);
                    accessRight.setSubjectId(getIdentifier(subjectId));
                    accessRight.setRightsGiven(now);

                    client.getAcl().add(accessRight);
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

                AccessRightType accessRight = new AccessRightType();
                accessRight.setServiceCode(serviceCode);
                accessRight.setSubjectId(getIdentifier(groupId));
                accessRight.setRightsGiven(now);

                client.getAcl().add(accessRight);
            }
        }
    }
}
