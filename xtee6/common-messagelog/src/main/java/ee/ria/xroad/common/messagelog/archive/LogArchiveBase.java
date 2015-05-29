package ee.ria.xroad.common.messagelog.archive;

import java.util.List;

import ee.ria.xroad.common.messagelog.LogRecord;

/**
 * Interface for accessing message log database for archive-related purposes.
 */
public interface LogArchiveBase {
    /**
     * Makes archive-related changes to the base - marks entries as archived and
     * creates entry for last archived file.
     *
     * @param toArchive message records to be marked as archived.
     * @param lastArchive metadata of last archived entry.
     * @throws Exception if archiving fails.
     */
    void archive(final List<LogRecord> toArchive, DigestEntry lastArchive)
            throws Exception;

    /**
     * Returns metadata of last archived entry.
     *
     * @return digest and file name of last archive.
     * @throws Exception if loading last archive fails.
     */
    DigestEntry loadLastArchive() throws Exception;
}
