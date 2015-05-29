package ee.cyber.xroad.clientsimporter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.xroad.mediator.identifiermapping.MappingsType;
import ee.cyber.xroad.mediator.identifiermapping.MappingsType.Mapping;
import ee.cyber.xroad.mediator.identifiermapping.ObjectFactory;
import ee.ria.xroad.common.identifier.ClientId;


/**
 * Converts X-Road 5.0 short names to X-Road 6.0 identifiers.
 */
public class IdentifierMapping {
    private static final Logger LOG =
            LoggerFactory.getLogger(IdentifierMapping.class);

    private MappingsType confType;
    private final Map<String, ClientId> shortNameToClientId = new HashMap<>();

    /**
     * Loads identifier mapping configuration from the mapping XML string.
     * @param mappingXml the mapping XML string
     * @throws Exception in case of any errors
     */
    public IdentifierMapping(String mappingXml) throws Exception {
        JAXBContext jaxbCtx = JAXBContext.newInstance(ObjectFactory.class);
        InputStream in = new ByteArrayInputStream(
                mappingXml.getBytes(StandardCharsets.UTF_8));
        Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
        JAXBElement<MappingsType> root =
                (JAXBElement<MappingsType>) unmarshaller.unmarshal(in);
        confType = root.getValue();

        cacheMapping();
    }

    private void cacheMapping() {
        LOG.trace("cacheMapping()");

        for (Mapping mapping : confType.getMapping()) {
            LOG.trace("\t{} -> {}", mapping.getOldId(), mapping.getNewId());

            shortNameToClientId.put(mapping.getOldId(), mapping.getNewId());
        }
    }

    /**
     * @param shortName the X-Road 5.0 short name
     * @return the X-Road 6.0 identifier that corresponds to the given short name
     */
    public ClientId getClientId(String shortName) {
        LOG.trace("getClientId({})", shortName);

        return shortNameToClientId.get(shortName);
    }
}
