package ee.cyber.sdsb.common.conf.globalconf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    private final Set<String> files = new HashSet<>();

    private boolean success = false;

    void addFailure(ConfigurationLocation location, Exception e) {
        exceptions.put(location, e);
    }

    DownloadResult success(Set<String> downloadedFiles) {
        success = true;
        this.files.addAll(downloadedFiles);
        return this;
    }

    DownloadResult failure() {
        success = false;
        return this;
    }
}
