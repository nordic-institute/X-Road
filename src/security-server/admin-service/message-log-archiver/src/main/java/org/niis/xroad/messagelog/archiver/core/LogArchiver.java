/*
 * The MIT License
 *
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
package org.niis.xroad.messagelog.archiver.core;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.messagelog.database.entity.ArchiveDigestEntity;
import ee.ria.xroad.messagelog.database.entity.MessageRecordEntity;
import ee.ria.xroad.messagelog.database.mapper.MessageRecordMapper;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.Session;
import org.niis.xroad.common.messagelog.MessageRecordEncryption;
import org.niis.xroad.common.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.common.pgp.BouncyCastlePgpEncryptionService;
import org.niis.xroad.common.pgp.PgpKeyManager;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.messagelog.archiver.core.config.LogArchiverExecutionProperties;
import org.niis.xroad.messagelog.archiver.mapper.ArchiveDigestMapper;

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

/**
 * Reads all non-archived time-stamped records from the database, writes them
 * to archive file and marks the records as archived.
 */
@Slf4j
@RequiredArgsConstructor
public class LogArchiver {

    private static final String PROPERTY_NAME_ARCHIVED = "archived";

    public static final int FETCH_SIZE = 10;

    private final PgpKeyManager keyManager;
    private final BouncyCastlePgpEncryptionService encryptionService;
    private final GlobalConfProvider globalConfProvider;
    private final DatabaseCtx databaseCtx;
    private final VaultClient vaultClient;

    public void execute(LogArchiverExecutionProperties executionProperties) {
        try {
            Long maxRecordId = databaseCtx.doInTransaction(this::getMaxRecordId);
            if (maxRecordId != null) {
                while (handleArchive(executionProperties, maxRecordId)) {
                    // body intentionally empty
                }
            }
            onArchivingDone();
        } catch (Exception ex) {
            log.error("Failed to archive log records", ex);
        }
    }

    private void markArchived(Session session, List<Long> recordIds) {
        session.createMutationQuery("UPDATE AbstractLogRecordEntity r SET r.archived = true WHERE r.id in (?1)")
                .setParameter(1, recordIds)
                .executeUpdate();
    }

    private boolean handleArchive(LogArchiverExecutionProperties executionProperties, long maxRecordId) {
        return databaseCtx.doInTransaction(session -> {
            final int limit = executionProperties.archiveTransactionBatchSize();
            final long start = System.currentTimeMillis();
            final MessageRecordEncryption messageRecordEncryption = new MessageRecordEncryption(
                    executionProperties.databaseEncryption(), vaultClient);

            int recordsArchived = 0;
            log.info("Archiving log records...");

            try (LogArchiveWriter archiveWriter = createLogArchiveWriter(executionProperties, session)) {
                List<Long> recordIds = new ArrayList<>(100);
                try (Stream<MessageRecordEntity> records = getNonArchivedMessageRecords(session, maxRecordId, limit)) {
                    for (Iterator<MessageRecordEntity> it = records.iterator(); it.hasNext(); ) {
                        MessageRecordEntity entity = it.next();
                        MessageRecord messageRecord = MessageRecordMapper.get().toDTO(entity);
                        recordIds.add(messageRecord.getId());
                        messageRecordEncryption.prepareDecryption(messageRecord);
                        if (archiveWriter.write(messageRecord)) {
                            executionProperties.archiveTransferCommandOpt().ifPresent(this::runTransferCommand);
                        }
                        //evict record from persistence context to avoid running out of memory
                        session.detach(entity);
                        recordsArchived++;

                        if (recordsArchived % 100 == 0) {
                            markArchived(session, recordIds);
                            recordIds.clear();
                        }
                    }
                }
                if (recordsArchived > 0) {
                    if (!recordIds.isEmpty()) {
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
                    executionProperties.archiveTransferCommandOpt().ifPresent(this::runTransferCommand);
                    log.info("Archived {} log records in {} ms", recordsArchived, System.currentTimeMillis() - start);
                }
            }
            //try to continue if the batch was full (there might be more)
            return recordsArchived == limit;
        });
    }

    private LogArchiveWriter createLogArchiveWriter(LogArchiverExecutionProperties executionProperties, Session session)
            throws IOException {
        var encryptionConfigProvider = EncryptionConfigProvider.create(keyManager,
                encryptionService, executionProperties.archiveEncryption());

        return new LogArchiveWriter(globalConfProvider,
                getArchivePath(executionProperties),
                new HibernateLogArchiveBase(session),
                encryptionConfigProvider,
                executionProperties
        );
    }

    private Path getArchivePath(LogArchiverExecutionProperties executionProperties) throws IOException {
        var archivePath = Paths.get(executionProperties.archivePath());
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
                .createMutationQuery("""
                        UPDATE TimestampRecordEntity t SET t.archived = true \
                        WHERE t.archived = false AND NOT EXISTS (\
                        SELECT 0 FROM MessageRecordEntity m \
                        WHERE m.archived = false and t.id = m.timestampRecord.id)"""
                ).executeUpdate();
    }

    protected Long getMaxRecordId(Session session) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Long> query = cb.createQuery(Long.class);
        final Root<MessageRecordEntity> t = query.from(MessageRecordEntity.class);

        query.select(cb.max(t.get("id")))
                .where(cb.and(
                        cb.isNotNull(t.get("timestampRecord")),
                        cb.isFalse(t.get(PROPERTY_NAME_ARCHIVED))));
        return session.createQuery(query).uniqueResult();
    }

    protected Stream<MessageRecordEntity> getNonArchivedMessageRecords(Session session, Long maxId, int limit) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRecordEntity> query = cb.createQuery(MessageRecordEntity.class);
        final Root<MessageRecordEntity> m = query.from(MessageRecordEntity.class);

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

    private void runTransferCommand(String transferCommand) {
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
            var digest = findArchiveDigest(groupName).orElse(new ArchiveDigestEntity(groupName));
            digest.setDigestEntry(ArchiveDigestMapper.get().toEntity(lastArchive));
            session.merge(digest);
        }

        @Override
        public void markRecordArchived(LogRecord logRecord) {
            logRecord.setArchived(true);
        }

        @Override
        public DigestEntry loadLastArchive(String groupName) {
            return findArchiveDigest(groupName)
                    .map(ArchiveDigestEntity::getDigestEntry)
                    .map(ArchiveDigestMapper.get()::toDTO)
                    .orElseGet(DigestEntry::empty);
        }

        protected Optional<ArchiveDigestEntity> findArchiveDigest(String groupName) {
            final CriteriaBuilder cb = session.getCriteriaBuilder();
            final CriteriaQuery<ArchiveDigestEntity> query = cb.createQuery(ArchiveDigestEntity.class);
            final Root<ArchiveDigestEntity> archiveDigest = query.from(ArchiveDigestEntity.class);
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
