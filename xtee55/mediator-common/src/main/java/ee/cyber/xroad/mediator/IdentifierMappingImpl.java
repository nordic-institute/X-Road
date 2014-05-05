package ee.cyber.xroad.mediator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.xroad.mediator.identifiermapping.MappingsType;
import ee.cyber.xroad.mediator.identifiermapping.MappingsType.Mapping;
import ee.cyber.xroad.mediator.identifiermapping.ObjectFactory;

public class IdentifierMappingImpl extends AbstractXmlConf<MappingsType>
        implements IdentifierMappingProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(IdentifierMappingImpl.class);

    private final Map<String, ClientId> shortNameToClientId = new HashMap<>();
    private final Map<ClientId, String> clientIdToShortName = new HashMap<>();

    public IdentifierMappingImpl() {
        super(ObjectFactory.class,
                MediatorSystemProperties.getIdentifierMappingFile());
        cacheMappings();
    }

    private void cacheMappings() {
        StringBuilder sb = null;

        if (LOG.isTraceEnabled()) {
            sb = new StringBuilder();
            sb.append("cacheMappings()\n");
        }

        for (Mapping mapping : confType.getMapping()) {
            if (LOG.isTraceEnabled()) {
                sb.append(String.format("\t%s -> %s\n", mapping.getOldId(),
                        mapping.getNewId()));
            }

            shortNameToClientId.put(mapping.getOldId(), mapping.getNewId());
            clientIdToShortName.put(mapping.getNewId(), mapping.getOldId());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace(sb.toString());
        }
    }

    @Override
    public ClientId getClientId(String shortName) {
        LOG.trace("getClientId({})", shortName);

        return shortNameToClientId.get(shortName);
    }

    @Override
    public String getShortName(ClientId clientId) {
        LOG.trace("getShortName({})", clientId);

        return clientIdToShortName.get(clientId);
    }

    @Override
    public Set<ClientId> getClientIds() {
        LOG.trace("getClientIds()");

        return clientIdToShortName.keySet();
    }

    @Override
    public Set<String> getShortNames() {
        LOG.trace("getShortNames()");

        return shortNameToClientId.keySet();
    }
}
