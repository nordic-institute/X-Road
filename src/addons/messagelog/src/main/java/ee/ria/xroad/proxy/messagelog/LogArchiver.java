/**
 * The MIT License
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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.messagelog.archive.DigestEntry;
import ee.ria.xroad.common.messagelog.archive.LogArchiveBase;
import ee.ria.xroad.common.messagelog.archive.LogArchiveWriter;

import akka.actor.UntypedActor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.hibernate.Session;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveTransferCommand;
import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;
import static org.apache.commons.lang3.StringUtils.isBlank;


/**
 * Reads all non-archived time-stamped records from the database, writes them
 * to archive file and marks the records as archived.
 */
@Slf4j
@RequiredArgsConstructor
public class LogArchiver extends UntypedActor {

    private static final int MAX_RECORDS_IN_ARCHIVE = 10;
    private static final int MAX_RECORDS_IN_BATCH = 360;
    private static final String PROPERTY_NAME_ARCHIVED = "archived";

    public static final String START_ARCHIVING = "doArchive";

    private final Path archivePath;
    private final Path workingPath;
    private boolean safeTransactionBatch;

    @Override
    public void onReceive(Object message) {
        log.trace("onReceive({})", message);

        if (START_ARCHIVING.equals(message)) {
            try {
                Long maxTimestampId = doInTransaction(session -> getMaxTimestampId(session));
                if (maxTimestampId != null) {
                    while (handleArchive(maxTimestampId)) {
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to archive log records", ex);
            }
        } else {
            unhandled(message);
        }
    }

    private boolean handleArchive(long maxTimestampId) throws Exception {
        return doInTransaction(session -> {
            List<LogRecord> records = getRecordsToBeArchived(session, maxTimestampId);
            if (records == null || records.isEmpty()) {
                log.info("No records to be archived at this time");
                return false;
            }

            log.info("Archiving log records...");

            long start = System.currentTimeMillis();
            int recordsArchived = 0;

            try (LogArchiveWriter archiveWriter = createLogArchiveWriter(session)) {
                while (!records.isEmpty()) {
                    if (archive(archiveWriter, records)) {
                        runTransferCommand(getArchiveTransferCommand());
                    }
                    recordsArchived += records.size();

                    //flush changes (records marked as archived) and free memory
                    //used up by cached records retrieved previously in the session
                    session.flush();
                    session.clear();

                    if (safeTransactionBatch
                            && recordsArchived >= MessageLogProperties.getArchiveTransactionBatchSize()) {
                        log.info("Archived {} log records in {} ms", recordsArchived,
                                System.currentTimeMillis() - start);
                        return true;
                    }

                    records = getRecordsToBeArchived(session, maxTimestampId);
                }
            } catch (Exception e) {
                throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
            } finally {
                runTransferCommand(getArchiveTransferCommand());
            }

            log.info("Archived {} log records in {} ms", recordsArchived,
                    System.currentTimeMillis() - start);

            return false;
        });
    }

    private boolean archive(LogArchiveWriter archiveWriter, List<LogRecord> records) throws Exception {

        boolean producedArchiveFile = false;
        for (LogRecord record : records) {
            producedArchiveFile |= archiveWriter.write(record);
        }

        return producedArchiveFile;
    }

    private LogArchiveWriter createLogArchiveWriter(Session session) {
        return new LogArchiveWriter(
                getArchivePath(),
                getWorkingPath(),
                this.new HibernateLogArchiveBase(session)
        );
    }

    private Path getArchivePath() {
        if (!Files.isDirectory(archivePath)) {
            throw new RuntimeException("Log output path (" + archivePath + ") must be directory");
        }

        if (!Files.isWritable(archivePath)) {
            throw new RuntimeException("Log output path (" + archivePath + ") must be writable");
        }

        return archivePath;
    }

    private Path getWorkingPath() {
        if (!Files.isDirectory(workingPath)) {
            throw new RuntimeException("Log working path (" + workingPath + ") must be directory");
        }

        if (!Files.isWritable(workingPath)) {
            throw new RuntimeException("Log working path (" + workingPath + ") must be writable");
        }

        return workingPath;
    }

    protected List<LogRecord> getRecordsToBeArchived(Session session, long maxTimestampId) {
        /* Implementation note. Log cleaning assumes that the records are archived starting from the oldest
          (smallest id). If this is changed, log cleaning must be changed accordingly. */

        List<LogRecord> recordsToArchive = new ArrayList<>();
        safeTransactionBatch = false;
        int allowedInArchiveCount = MAX_RECORDS_IN_ARCHIVE;
        for (TimestampRecord ts : getNonArchivedTimestampRecords(session, MAX_RECORDS_IN_BATCH, maxTimestampId)) {
            List<MessageRecord> messages = getNonArchivedMessageRecords(session, ts.getId(), allowedInArchiveCount);
            if (allTimestampMessagesArchived(session, ts.getId())) {
                log.trace("Timestamp record #{} will be archived", ts.getId());
                recordsToArchive.add(ts);
                safeTransactionBatch = true;
            } else {
                log.trace("Timestamp record #{} still related to non-archived message records", ts.getId());
            }

            recordsToArchive.addAll(messages);
            allowedInArchiveCount -= messages.size();
            if (safeTransactionBatch || allowedInArchiveCount <= 0) {
                break;
            }
        }
        return recordsToArchive;
    }

    protected List<TimestampRecord> getNonArchivedTimestampRecords(Session session, int maxRecordsToGet,
            long maxTimestampId) {

        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<TimestampRecord> query = cb.createQuery(TimestampRecord.class);
        final Root<TimestampRecord> t = query.from(TimestampRecord.class);

        query.select(t).where(cb.and(
                cb.isFalse(t.get(PROPERTY_NAME_ARCHIVED))),
                cb.le(t.get("id"), maxTimestampId)).orderBy(cb.asc(t.get("id")));

        return session.createQuery(query).setMaxResults(maxRecordsToGet).getResultList();
    }

    protected Long getMaxTimestampId(Session session) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Long> query = cb.createQuery(Long.class);
        final Root<TimestampRecord> t = query.from(TimestampRecord.class);

        query.select(cb.max(t.get("id"))).where(cb.isFalse(t.get(PROPERTY_NAME_ARCHIVED)));
        return session.createQuery(query).uniqueResult();
    }

    protected List<MessageRecord> getNonArchivedMessageRecords(Session session, Long timestampRecordNumber,
            int maxRecordsToGet) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRecord> query = cb.createQuery(MessageRecord.class);
        final Root<MessageRecord> m = query.from(MessageRecord.class);

        query.select(m).where(cb.and(
                cb.isFalse(m.get(PROPERTY_NAME_ARCHIVED)),
                cb.equal(m.get("timestampRecord").get("id"), timestampRecordNumber)
        ));

        return session.createQuery(query).setMaxResults(maxRecordsToGet).getResultList();
    }

    protected boolean allTimestampMessagesArchived(Session session, Long timestampRecordNumber) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Long> query = cb.createQuery(Long.class);
        final Root<MessageRecord> m = query.from(MessageRecord.class);

        query.select(cb.count(m))
                .where(cb.and(
                        cb.equal(m.get(PROPERTY_NAME_ARCHIVED), false),
                        cb.equal(m.get("timestampRecord").get("id"), timestampRecordNumber)));

        return session.createQuery(query).getSingleResult() == 0;
    }

    protected void markArchiveCreated(final DigestEntry lastArchive,
            final Session session) throws Exception {
        if (lastArchive != null) {
            log.debug("Digest entry will be saved here...");
            session.createQuery("delete from " + DigestEntry.class.getName()).executeUpdate();
            session.save(lastArchive);
        }
    }

    private static void runTransferCommand(String transferCommand) {
        if (isBlank(transferCommand)) {
            return;
        }

        log.info("Transferring archives with shell command: \t{}", transferCommand);

        try {
            String[] command = new String[] {"/bin/bash", "-c", transferCommand};

            Process process = new ProcessBuilder(command).start();

            StandardErrorCollector standardErrorCollector =
                    new StandardErrorCollector(process);

            new StandardOutputReader(process).start();

            standardErrorCollector.start();
            standardErrorCollector.join();

            process.waitFor();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = String.format(
                        "Running archive transfer command '%s' "
                                + "exited with status '%d'",
                        transferCommand,
                        exitCode);

                log.error(
                        "{}\n -- STANDARD ERROR START\n{}\n"
                                + " -- STANDARD ERROR END",
                        errorMsg,
                        standardErrorCollector.getStandardError());
            }
        } catch (Exception e) {
            log.error("Failed to execute archive transfer command '{}'", transferCommand, e);
        }
    }

    @Value
    private class HibernateLogArchiveBase implements LogArchiveBase {

        private Session session;

        @Override
        public void markArchiveCreated(DigestEntry lastArchive)
                throws Exception {
            LogArchiver.this.markArchiveCreated(lastArchive, session);
        }

        @Override
        public void markRecordArchived(LogRecord logRecord) {
            log.trace("Setting {} #{} archived",
                    logRecord.getClass().getName(), logRecord.getId());

            logRecord.setArchived(true);
            session.saveOrUpdate(logRecord);
        }

        @Override
        public DigestEntry loadLastArchive() {
            List<DigestEntry> lastArchiveEntries =
                    session
                            .createQuery(
                                    "select new " + DigestEntry.class.getName()
                                            + "(d.digest, d.fileName) from DigestEntry d", DigestEntry.class
                            )
                            .setMaxResults(1)
                            .list();

            return lastArchiveEntries.isEmpty()
                    ? DigestEntry.empty() : lastArchiveEntries.get(0);
        }
    }

    @RequiredArgsConstructor
    private static class StandardOutputReader extends Thread {
        private final Process process;

        @Override
        public void run() {
            try (InputStream input = process.getInputStream()) {
                IOUtils.copy(input, new NullOutputStream());
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard output", e);
            }
        }
    }

    @RequiredArgsConstructor
    private static class StandardErrorCollector extends Thread {
        private final Process process;

        @Getter
        private String standardError;

        @Override
        public void run() {
            try (InputStream error = process.getErrorStream()) {
                standardError = IOUtils.toString(error, StandardCharsets.UTF_8);
            } catch (IOException e) {
                // We can ignore it.
                log.error("Could not read standard error", e);
            }
        }
    }
}
