package ee.cyber.sdsb.common.conf.globalconf;

import java.util.List;

/**
 * Describes a configuration source where configuration can be downloaded.
 */
public interface ConfigurationSource {

    /**
     * @return the instance identifier of the source
     */
    String getInstanceIdentifier();

    /**
     * @return configuration locations which are used to download the configuration.
     */
    List<ConfigurationLocation> getLocations();

}
