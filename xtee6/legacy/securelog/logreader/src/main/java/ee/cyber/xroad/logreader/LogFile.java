package ee.cyber.xroad.logreader;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogFile {
    private static final Logger LOG = LoggerFactory.getLogger(LogFile.class);

    private File file;

    private FileInputStream inputStream;
    private FileChannel channel;
    MappedByteBuffer buffer;

    LogFile(File file) {
        this.file = file;
    }

    String getName() {
        return file.getName();
    }

    LogPosition seekToTime(long beginTime) throws Exception {
        LOG.debug("seekToTime({})", beginTime);
        ensureOpenFile();

        int lower = 0;
        int upper = buffer.capacity();

        int recordStart = -1;
        while (lower <= upper) {
            int pos = (lower + upper) / 2;

            LOG.debug("Searching, {} - {}, pos = {}",
                    new Object[] {lower, upper, pos});

            long recordTime = -1;
            while (recordTime == -1) {
                recordStart = ffwdToRecordStart(pos);
                LOG.debug("Record start: {}", recordStart);
                if (recordStart == -1) {
                    // We are at the end of file.
                    return null;
                }

                recordTime = getTime(recordStart);
                LOG.debug("recordTime = {}, looking for {}",
                        recordTime, beginTime);
                if (recordTime == -1) {
                    // Let's look at the next record
                    pos = recordStart + 1;
                }
            }

            if (recordTime > beginTime) {
                if (recordStart > upper) {
                    // We are in position where upper-lower is in the
                    // same range as the length of the log line.
                    if (lower >= upper) {
                        // OK, we have exhausted the search space. If the
                        // log contained this time, it would be here.
                        return new LogPosition(this, lower);
                    } else {
                        // Try one more time.
                        upper = lower;
                    }
                } else {
                    upper = pos;
                }
            } else if (recordTime < beginTime) {
                lower = recordStart;
            } else { // recordTime == beginTime
                // We found the exact match.
                return new LogPosition(this, recordStart);
            }
        }

        // We didn't find the exact match. Return the current state.
        return new LogPosition(this, recordStart);
    }

    private int ffwdToRecordStart(int startPos) {
        int capacity = buffer.capacity();

        for (int pos = startPos; pos < capacity; ++pos) {
//            LOG.debug("Reading: \"{}\"", (char) buffer.get(pos));
            if (buffer.get(pos) == '\n') {
                // Skip over consecutive newlines.
                while (pos < capacity && buffer.get(pos) == '\n') {
                    ++pos;
                }

                // Check for EOF.
                return pos == capacity ? -1 : pos;
            }
        }
        return -1;
    }

    private int rewindToRecordStart(int startPos) {
        int pos;
        // Skip over newlines if we were at the record start.

        for (pos = startPos - 1; pos >= 0 && buffer.get(pos) == '\n'; --pos) {
        }

        // Read chars until next newline is reached.
        for (; pos >= 0; --pos) {
//            LOG.debug("Reading: \"{}\"", (char) buffer.get(pos));
            if (buffer.get(pos) == '\n') {
                // OK, we are at newline. The record starts
                // at pos + 1.
                return pos + 1;
            }
        }
        return -1;
    }

    private long getTime(int startPos) {
//        LOG.debug("getTime({})", startPos);

        String fieldVal = readField(startPos, 5);
        if (fieldVal == null) {
            return -1;
        } else {
            try {
                return Long.parseLong(fieldVal);
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
    }

    String readField(int startPos, int fieldNo) {
        int pos = startPos;

        // Skip fieldNo -1 fields.
        for (int i = 1; i < fieldNo; ++i) {
            pos = skipSeparator(pos);
            pos = skipField(pos);
        }

        pos = skipSeparator(pos);
        int next = skipField(pos);
        byte[] data = new byte[next - pos];
        for (int i = 0; i < next - pos; ++i) {
            data[i] = buffer.get(pos + i);
        }

        return new String(data, StandardCharsets.US_ASCII);
    }

    private int skipSeparator(int pos) {
//        LOG.debug("skipSeparator({})", pos);

        int newPos = pos;
        int capacity = buffer.capacity();

        while (newPos < capacity
                && (buffer.get(newPos) == ' '
                || buffer.get(newPos) == '\n')) {
            ++newPos;
        }
        return newPos;
    }

    private int skipField(int pos) {
//        LOG.debug("skipField({})", pos);

        int newPos = pos;
        int capacity = buffer.capacity();

        while (newPos < capacity
                && buffer.get(newPos) != ' '
                && buffer.get(newPos) != '\n') {
            ++newPos;
        }
        return newPos;
    }

    // TODO: #2701 replace endTime with end predicate
    LogRecord searchForward(LogPosition startPosition,
            RecordType recordType, long endTime, SearchPredicate predicate)
            throws Exception {
        ensureOpenFile();

        int capacity = buffer.capacity();
        for (int pos = startPosition.pos; pos > -1 && pos < capacity;
                pos = ffwdToRecordStart(pos)) {
            if (recordType != getRecordType(pos)) {
                // TODO: #2701 maybe check time occassionally?
                continue;
            }

            if (getTime(pos) > endTime) {
                return null;
            }

            if (predicate.matches(this, pos)) {
                return new LogRecord(this, pos);
            }
        }

        // Reached end of file, but not the end of time period.
        // Signal the need for next file.
        return LogRecord.NEXT_FILE;
    }

    LogRecord searchBackwards(LogPosition startPosition,
            RecordType recordType, long startTime, SearchPredicate predicate)
            throws Exception {
        ensureOpenFile();

        for (int pos = startPosition.pos; pos >= 0;
                pos = rewindToRecordStart(pos)) {
            if (recordType != getRecordType(pos)) {
                // TODO: #2701 maybe check time occassionally?
                continue;
            }

            if (getTime(pos) < startTime) {
                return null;
            }

            if (predicate.matches(this, pos)) {
                return new LogRecord(this, pos);
            }
        }

        // Reached end of file, but not the end of time period.
        // Signal the need for next file.
        return LogRecord.NEXT_FILE;
    }

    private RecordType getRecordType(int recordStart) {
        return RecordType.fromChar((char) buffer.get(recordStart));
    }

    private void ensureOpenFile() throws Exception {
        if (inputStream != null) {
            return;
        }

        inputStream = new FileInputStream(file);
        channel = inputStream.getChannel();
        buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());

        LOG.debug("Opened file {}: {} bytes", getName(), channel.size());
        LOG.debug("Char buffer, limit = {}, capacity = {}", buffer.limit(),
                buffer.capacity());
    }

    void close() throws Exception {
        if (inputStream != null) {
            channel.close();
            inputStream.close();
        }
    }

    LogPosition endPosition() {
        return new LogPosition(this, buffer.capacity() - 1);
    }

    LogRecord startRecord() {
        return new LogRecord(this, 0);
    }

    @Override
    public String toString() {
        return file.getName();
    }
}
