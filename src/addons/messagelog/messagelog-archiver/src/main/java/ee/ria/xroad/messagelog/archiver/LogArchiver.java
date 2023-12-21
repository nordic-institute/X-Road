/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.messagelog.archiver;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.archive.ArchiveDigest;
import ee.ria.xroad.common.messagelog.archive.DigestEntry;
import ee.ria.xroad.common.messagelog.archive.LogArchiveBase;
import ee.ria.xroad.common.messagelog.archive.LogArchiveWriter;
import ee.ria.xroad.messagelog.database.MessageRecordEncryption;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveTransactionBatchSize;
import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveTransferCommand;
import static ee.ria.xroad.messagelog.database.MessageLogDatabaseCtx.doInTransaction;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Reads all non-archived time-stamped records from the database, writes them
 * to archive file and marks the records as archived.
 */
@Slf4j
public class LogArchiver implements Job {

    private static final String PROPERTY_NAME_ARCHIVED = "archived";

    public static final int FETCH_SIZE = 10;

    private final Path archivePath = Paths.get(MessageLogProperties.getArchivePath());

    @Override
    public void execute(JobExecutionContext context) {
        try {
            Long maxRecordId = doInTransaction(this::getMaxRecordId);
            if (maxRecordId != null) {
                while (handleArchive(maxRecordId)) {
                    // body intentionally empty
                }
            }
            onArchivingDone();
        } catch (Exception ex) {
            log.error("Failed to archive log records", ex);
        }
    }

    private void markArchived(Session session, List<Long> recordIds) {
        session.createQuery("UPDATE AbstractLogRecord r SET r.archived = true WHERE r.id in (?1)")
                .setParameter(1, recordIds)
                .executeUpdate();
    }

    private boolean handleArchive(long maxRecordId) throws Exception {
        return doInTransaction(session -> {
            final int limit = getArchiveTransactionBatchSize();
            final String archiveTransferCommand = getArchiveTransferCommand();
            final long start = System.currentTimeMillis();
            final MessageRecordEncryption messageRecordEncryption = MessageRecordEncryption.getInstance();

            int recordsArchived = 0;
            log.info("Archiving log records...");

            try (LogArchiveWriter archiveWriter = createLogArchiveWriter(session)) {
                List<Long> recordIds = new ArrayList<>(100);
                try (Stream<MessageRecord> records = getNonArchivedMessageRecords(session, maxRecordId, limit)) {
                    for (Iterator<MessageRecord> it = records.iterator(); it.hasNext(); ) {
                        MessageRecord messageRecord = it.next();
                        recordIds.add(messageRecord.getId());
                        messageRecordEncryption.prepareDecryption(messageRecord);
                        if (archiveWriter.write(messageRecord)) {
                            runTransferCommand(archiveTransferCommand);
                        }
                        //evict record from persistence context to avoid running out of memory
                        session.detach(messageRecord);
                        recordsArchived++;

                        if (recordsArchived % 100 == 0) {
                            markArchived(session, recordIds);
                            recordIds.clear();
                        }
                    }
                }
                if (recordsArchived > 0) {
                    if (recordIds.size() > 0) {
                        markArchived(session, recordIds);
                        recordIds.clear();
                    }
                    markTimestampRecordsArchived(session);
                }
                session.flush();
            } catch (Exception e) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
            } finally {
                if (recordsArchived > 0) {
                    runTransferCommand(archiveTransferCommand);
                    log.info("Archived {} log records in {} ms", recordsArchived, System.currentTimeMillis() - start);
                }
            }
            //try to continue if the batch was full (there might be more)
            return recordsArchived == limit;
        });
    }

    private LogArchiveWriter createLogArchiveWriter(Session session) throws IOException {
        return new LogArchiveWriter(
                getArchivePath(),
                new HibernateLogArchiveBase(session)
        );
    }

    private Path getArchivePath() throws IOException {
        if (!Files.isDirectory(archivePath)) {
            throw new IOException("Log output path (" + archivePath + ") must be directory");
        }

        if (!Files.isWritable(archivePath)) {
            throw new IOException("Log output path (" + archivePath + ") must be writable");
        }

        return archivePath;
    }

    protected int markTimestampRecordsArchived(Session session) {
        return session
                .createQuery(""
                        + "UPDATE TimestampRecord t SET t.archived = true "
                        + "WHERE t.archived = false AND NOT EXISTS ("
                        + "SELECT 0 FROM MessageRecord m "
                        + "WHERE m.archived = false and t.id = m.timestampRecord.id)"
                ).executeUpdate();
    }

    protected Long getMaxRecordId(Session session) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Long> query = cb.createQuery(Long.class);
        final Root<MessageRecord> t = query.from(MessageRecord.class);

        query.select(cb.max(t.get("id")))
                .where(cb.and(
                        cb.isNotNull(t.get("timestampRecord")),
                        cb.isFalse(t.get(PROPERTY_NAME_ARCHIVED))));
        return session.createQuery(query).uniqueResult();
    }

    protected Stream<MessageRecord> getNonArchivedMessageRecords(Session session, Long maxId, int limit) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRecord> query = cb.createQuery(MessageRecord.class);
        final Root<MessageRecord> m = query.from(MessageRecord.class);

        query.select(m)
                .where(cb.and(
                        cb.isNotNull(m.get("timestampRecord")),
                        cb.isFalse(m.get(PROPERTY_NAME_ARCHIVED)),
                        cb.lessThanOrEqualTo(m.get("id"), maxId)))
                .orderBy(
                        // order is important for archive grouping
                        cb.asc(m.get("memberClass")),
                        cb.asc(m.get("memberCode")),
                        cb.asc(m.get("subsystemCode")),
                        cb.asc(m.get("id")));

        return session
                .createQuery(query)
                .setMaxResults(limit)
                .setReadOnly(true)
                // log records can be large, avoid fetching too much
                // by default, PostgreSQL fetches the whole result set (limit)
                .setFetchSize(FETCH_SIZE)
                .getResultStream();
    }

    protected void onArchivingDone() {
        //hook for testing
    }

    private static void runTransferCommand(String transferCommand) {
        if (isBlank(transferCommand)) {
            return;
        }

        log.info("Transferring archives with shell command: \t{}", transferCommand);
        Process process = null;
        try {
            String[] command = new String[]{"/bin/bash", "-c", transferCommand};
            String standardError = null;

            process = new ProcessBuilder(command).redirectOutput(Paths.get("/dev/null").toFile()).start();

            try (InputStream error = process.getErrorStream()) {
                standardError = IOUtils.toString(error, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard error", e);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMsg = String.format("Running archive transfer command '%s' exited with status '%d'",
                        transferCommand, exitCode);
                log.error(
                        "{}\n -- STANDARD ERROR START\n{}\n"
                                + " -- STANDARD ERROR END",
                        errorMsg,
                        standardError);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while executing transfer command '{}'", transferCommand, e);
        } catch (Exception e) {
            log.error("Failed to execute archive transfer command '{}'", transferCommand, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static class HibernateLogArchiveBase implements LogArchiveBase {

        HibernateLogArchiveBase(Session session) {
            this.session = session;
        }

        private final Session session;

        @Override
        public void markArchiveCreated(String groupName, DigestEntry lastArchive) {
            ArchiveDigest digest = findArchiveDigest(groupName).orElse(new ArchiveDigest(groupName));
            digest.setDigestEntry(lastArchive);
            session.saveOrUpdate(digest);
        }

        @Override
        public void markRecordArchived(LogRecord logRecord) {
            logRecord.setArchived(true);
        }

        @Override
        public DigestEntry loadLastArchive(String groupName) {
            return findArchiveDigest(groupName)
                    .map(ArchiveDigest::getDigestEntry)
                    .orElse(DigestEntry.empty());
        }

        protected Optional<ArchiveDigest> findArchiveDigest(String groupName) {
            final CriteriaBuilder cb = session.getCriteriaBuilder();
            final CriteriaQuery<ArchiveDigest> query = cb.createQuery(ArchiveDigest.class);
            final Root<ArchiveDigest> archiveDigest = query.from(ArchiveDigest.class);
            final Expression<String> name = archiveDigest.get("groupName");

            query.select(archiveDigest);
            if (groupName == null) {
                query.where(cb.isNull(name));
            } else {
                query.where(cb.equal(name, groupName));
            }

            return session.createQuery(query)
                    .setMaxResults(1)
                    .list()
                    .stream()
                    .findFirst();
        }
    }
}
