package ee.cyber.xroad.mediator;

import java.util.Set;

import ee.cyber.sdsb.common.conf.ConfProvider;
import ee.cyber.sdsb.common.identifier.ClientId;

/**
 * Converts between SDSB identifiers and X-Road 5.0 short names.
 */
public interface IdentifierMappingProvider extends ConfProvider {

    ClientId getClientId(String shortName);

    String getShortName(ClientId clientId);

    Set<ClientId> getClientIds();

    Set<String> getShortNames();
}
