package ee.ria.xroad.common.messagelog.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import ee.ria.xroad.common.asic.AsicContainerNameGenerator;
import ee.ria.xroad.common.messagelog.MessageRecord;

import static ee.ria.xroad.common.messagelog.MessageLogProperties.getArchiveMaxFilesize;
import static ee.ria.xroad.common.messagelog.archive.LogArchiveWriter.MAX_RANDOM_GEN_ATTEMPTS;

/**
 * Encapsulates logic of creating log archive from ASiC containers.
 */
@Slf4j
@RequiredArgsConstructor
class LogArchiveCache {

    private enum State {
        NEW, ADDING, ROTATING
    }

    private final Supplier<String> randomGenerator;
    private final LinkingInfoBuilder linkingInfoBuilder;

    private State state = State.NEW;
    private List<MessageRecord> cachedRecords = new ArrayList<>();
    private byte[] currentArchive;

    private Set<Date> creationTimes = new TreeSet<>();

    void add(MessageRecord messageRecord) throws IOException {
        validateMessageRecord(messageRecord);

        handleRotation();

        cacheRecord(messageRecord);

        updateState();
    }

    byte[] getArchiveBytes() {
        return currentArchive;
    }

    boolean isRotating() {
        return state == State.ROTATING;
    }

    Date getStartTime() {
        return (Date) creationTimes.toArray()[0];
    }

    Date getEndTime() {
        return (Date) creationTimes.toArray()[creationTimes.size() - 1];
    }

    private void validateMessageRecord(MessageRecord record)
            throws IOException {
        if (record == null) {
            throw new IllegalArgumentException(
                    "Message record to be archived must not be null");
        }
    }

    private void handleRotation() {
        if (state != State.ROTATING) {
            return;
        }

        cachedRecords = new ArrayList<>();
        creationTimes = new TreeSet<>();
    }

    private void cacheRecord(MessageRecord messageRecord) throws IOException {
        creationTimes.add(new Date(messageRecord.getTime()));
        cachedRecords.add(messageRecord);
        currentArchive = getAsicContainersArchive(cachedRecords);
    }

    private void updateState() {
        if (currentArchive.length > getArchiveMaxFilesize()) {
            state = State.ROTATING;
        } else {
            state = State.ADDING;
        }
    }

    private byte[] getAsicContainersArchive(
            List<MessageRecord> asicContainersToArchive)
            throws IOException {
        log.trace("Getting AsiC containers archive for {} records", asicContainersToArchive.size());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            AsicContainerNameGenerator nameGen =
                    new AsicContainerNameGenerator(randomGenerator,
                            MAX_RANDOM_GEN_ATTEMPTS);

            for (MessageRecord each : asicContainersToArchive) {
                byte[] containerBytes = each.toAsicContainer().getBytes();

                String type = each.isResponse() ? "response" : "request";
                String queryId = each.getQueryId();
                String archiveFilename =
                        nameGen.getArchiveFilename(queryId, type);
                linkingInfoBuilder.addNextFile(archiveFilename, containerBytes);

                ZipEntry entry = new ZipEntry(archiveFilename);

                zos.putNextEntry(entry);
                zos.write(containerBytes);
                zos.closeEntry();
            }

            ZipEntry linkingInfoEntry = new ZipEntry("linkinginfo");

            zos.putNextEntry(linkingInfoEntry);
            zos.write(linkingInfoBuilder.build());
            zos.closeEntry();

            linkingInfoBuilder.afterArchiveCreated();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            IOUtils.closeQuietly(bos);
        }

        return bos.toByteArray();
    }
}
