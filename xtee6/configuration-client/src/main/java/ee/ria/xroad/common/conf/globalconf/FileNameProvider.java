package ee.ria.xroad.common.conf.globalconf;

import java.nio.file.Path;

/**
 * Used for getting the full file name of a downloaded configuration part.
 * The file name is used to save the downloaded data to disk.
 */
public interface FileNameProvider {

    /**
     * @param file the file
     * @return the full path where the file should be save
     * @throws Exception if an error occurs
     */
    Path getFileName(ConfigurationFile file) throws Exception;

}
