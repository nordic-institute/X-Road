package ee.ria.xroad.common.conf;

import java.io.OutputStream;

/**
 * File based configuration.
 */
public interface ConfProvider {

    /**
     * @return whether the configuration has changed. For XML based
     * configuration implementations, this might mean checking if the
     * underlying XML file has changed (for example, by comparing the
     * XML checksums).
     */
    boolean hasChanged();

    /**
     * Loads the conf from given file.
     * @param fileName the file name
     * @throws Exception if loading fails
     */
    void load(String fileName) throws Exception;

    /**
     * Saves the configuration to the file it was loaded from.
     * @throws Exception if saving fails
     */
    void save() throws Exception;

    /**
     * Saves the configuration to the output stream.
     * @param out the output stream
     * @throws Exception if saving fails
     */
    void save(OutputStream out) throws Exception;
}
