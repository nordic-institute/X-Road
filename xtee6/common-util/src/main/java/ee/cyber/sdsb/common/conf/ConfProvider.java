package ee.cyber.sdsb.common.conf;

import java.io.OutputStream;

public interface ConfProvider {

    /**
     * Returns whether the configuration has changed. For XML based
     * configuration implementations, this might mean checking if the
     * underlying XML file has changed (for example, by comparing the
     * XML checksums).
     */
    boolean hasChanged();

    /**
     * Loads the conf from given file.
     */
    void load(String fileName) throws Exception;

    /**
     * Saves the configuration to the file it was loaded from.
     */
    void save() throws Exception;

    /**
     * Saves the configuration to the output stream.
     */
    void save(OutputStream out) throws Exception;
}
