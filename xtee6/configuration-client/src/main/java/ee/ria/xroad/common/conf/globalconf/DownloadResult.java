package ee.ria.xroad.common.conf.globalconf;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * Holds the download result of a configuration. Contains any exceptions per
 * download location and a list of files that are contained in the configuration
 * directory that was successfully downloaded.
 */
@Getter
class DownloadResult {

    private final Map<ConfigurationLocation, Exception> exceptions =
            new HashMap<>();

    //private final Set<ConfigurationFile> files = new HashSet<>();
    private Configuration configuration;

    private boolean success = false;

    void addFailure(ConfigurationLocation location, Exception e) {
        exceptions.put(location, e);
    }

    DownloadResult success(Configuration configuraton) {
        success = true;

        this.configuration = configuraton;
        return this;
    }

    DownloadResult failure() {
        success = false;
        return this;
    }
}
