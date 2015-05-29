package ee.cyber.xroad.mediator;

import java.util.Set;

import ee.ria.xroad.common.conf.ConfProvider;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Converts between X-Road 6.0 identifiers and X-Road 5.0 short names.
 */
public interface IdentifierMappingProvider extends ConfProvider {

    /**
     * @param shortName the X-Road 5.0 short name
     * @return the X-Road 6.0 identifier that corresponds to the given short name
     */
    ClientId getClientId(String shortName);

    /**
     * @param clientId the X-Road 6.0 identifier
     * @return the X-Road short name that corresponds to the given identifier
     */
    String getShortName(ClientId clientId);

    /**
     * @return all X-Road 6.0 identifiers in the mapping
     */
    Set<ClientId> getClientIds();

    /**
     * @return all X-Road 5.0 short names in the mapping
     */
    Set<String> getShortNames();
}
