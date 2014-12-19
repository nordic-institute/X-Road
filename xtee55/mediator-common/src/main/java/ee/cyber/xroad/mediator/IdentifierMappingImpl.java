package ee.cyber.xroad.mediator;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.conf.AbstractXmlConf;
import ee.cyber.sdsb.common.conf.globalconf.ConfigurationDirectory;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.xroad.mediator.identifiermapping.MappingsType;
import ee.cyber.xroad.mediator.identifiermapping.MappingsType.Mapping;
import ee.cyber.xroad.mediator.identifiermapping.ObjectFactory;

@Slf4j
public class IdentifierMappingImpl extends AbstractXmlConf<MappingsType>
        implements IdentifierMappingProvider {

    private final Map<String, ClientId> shortNameToClientId = new HashMap<>();
    private final Map<ClientId, String> clientIdToShortName = new HashMap<>();

    public IdentifierMappingImpl() {
        super(ObjectFactory.class,
                MediatorSystemProperties.getIdentifierMappingFile());
        cacheMappings();
    }

    private void cacheMappings() {
        StringBuilder sb = null;

        if (log.isTraceEnabled()) {
            sb = new StringBuilder();
            sb.append("cacheMappings()\n");
        }

        for (Mapping mapping : confType.getMapping()) {
            if (log.isTraceEnabled()) {
                sb.append(String.format("\t%s -> %s\n", mapping.getOldId(),
                        mapping.getNewId()));
            }

            shortNameToClientId.put(mapping.getOldId(), mapping.getNewId());
            clientIdToShortName.put(mapping.getNewId(), mapping.getOldId());
        }

        if (log.isTraceEnabled()) {
            log.trace(sb.toString());
        }
    }

    @Override
    public void load(String fileName) throws Exception {
        ConfigurationDirectory.verifyUpToDate(Paths.get(fileName));
        super.load(fileName);
    }

    @Override
    public ClientId getClientId(String shortName) {
        log.trace("getClientId({})", shortName);

        return shortNameToClientId.get(shortName);
    }

    @Override
    public String getShortName(ClientId clientId) {
        log.trace("getShortName({})", clientId);

        return clientIdToShortName.get(clientId);
    }

    @Override
    public Set<ClientId> getClientIds() {
        log.trace("getClientIds()");

        return clientIdToShortName.keySet();
    }

    @Override
    public Set<String> getShortNames() {
        log.trace("getShortNames()");

        return shortNameToClientId.keySet();
    }
}
