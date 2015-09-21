package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.messagelog.LogRecord;

/**
 * Interface for accessing message log database for archive-related purposes.
 */
public interface LogArchiveBase {
    /**
     * Saves digest entry corresponding to created log archive.
     *
     * @param lastArchive metadata of last archived entry.
     * @throws Exception if archiving fails.
     */
    void markArchiveCreated(DigestEntry lastArchive) throws Exception;

    /**
     * Marks log record (either message or timestamp) as archived.
     *
     * @param logRecord the log record to be marked as archived.
     * @throws Exception if marking records as archived fails.
     */
    void markRecordArchived(final LogRecord logRecord) throws Exception;

    /**
     * Returns metadata of last archived entry.
     *
     * @return digest and file name of last archive.
     * @throws Exception if loading last archive fails.
     */
    DigestEntry loadLastArchive() throws Exception;
}
