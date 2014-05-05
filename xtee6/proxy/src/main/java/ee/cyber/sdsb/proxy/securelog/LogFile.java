package ee.cyber.sdsb.proxy.securelog;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.proxy.securelog.LogRecord.Type;

/**
 * Represents log file. Not thread safe!
 */
public class LogFile implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LogFile.class);

    // TODO teha üldine koht charseti või seda kasutavate abimeetodite jaoks
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final DateFormat LOG_FILE_DATE_FORMAT = new SimpleDateFormat(
            "yyyyMMddHHmmss.SSS");
    private static final Random LOG_FILE_RANDOM = new Random();

    private Path path;
    private FileChannel channel;
    private LogState state;

    /** Parameter to method "read" to handle file content line by line. */
    static interface Handler {
        /** Called for each line in the log file in the correct order. */
        void handle(String line);
    }

    LogFile() throws Exception {
        this(SystemProperties.getSecureLogFile());
    }

    LogFile(String logFileName) throws Exception {
        path = FileSystems.getDefault().getPath(logFileName);
        channel = openChannel(path, false);
        initState();
    }

    private void initState() throws Exception {
        StateReader reader = new StateReader();

        read(reader);
        state = reader.getState();
        LOG.debug("initState() - initial state: {}", state);
        if (reader.isLogEmpty) {
            write(state.toLogStr());
        }
    }

    void read(Handler handler) throws IOException {
        BufferedReader br = new BufferedReader(Channels.newReader(channel,
                CHARSET.name()));

        channel.position(0);
        for (String line; (line = br.readLine()) != null;) {
            handler.handle(line);
        }
        channel.position(channel.size());
    }

    /**
     * Writes the record to the log file and updates the state by the record.
     */
    void write(LogRecord logRecord, String hashAlg) throws Exception {
        if (logRecord instanceof SignatureRecord) {
            Long nr = state.getSignatureRecordNr(
                    (SignatureRecord) logRecord);
            if (nr != null) {
                LOG.debug("Signature already logged, record nr {}", nr);
                logRecord.setNr(nr);
                return;
            }
        }

        write(logRecord.toLogStr(state.getPrevRecord(), hashAlg));
        state.update(logRecord);
    }

    /**
     * Writes the specified lines to log, adding newline to the end of each
     * line.
     */
    private void write(String... lines) throws IOException {
        for (String s : lines) {
            channel.write(CHARSET.encode(s + System.lineSeparator()));
        }
    }

    /**
     * Decides whether it is necessary to rotate.
     */
    boolean mustRotate() throws IOException {
        return channel.size() > getLogFileMaxSizeBytes();
    }

    /**
     * Rotates the log file: renames the current file, closes it, opens new file
     * and writes the state into the new file.
     */
    void rotate() throws IOException {
        LOG.debug("rotate() called");
        while (true) {
            Path newPath = FileSystems.getDefault().getPath(
                    getRotatedLogFileName());
            try {
                Files.move(path, newPath);
                channel.close();
                channel = openChannel(path, true);
                break;
            } catch (FileAlreadyExistsException e) {
                LOG.debug("rotate() - file exists: " + newPath);
            }
        }
        write(state.toLogStr());
        LOG.debug("rotate() finished");
    }

    LogState getState() {
        return state;
    }

    /**
     * Closes the underlying file.
     */
    @Override
    public void close() throws IOException {
        LOG.debug("close() called");
        channel.close();
    }

    private static String getRotatedLogFileName() {
        return SystemProperties.getSecureLogFile() + "." +
                LOG_FILE_DATE_FORMAT.format(new Date())
                + "." + Math.abs(LOG_FILE_RANDOM.nextInt());
    }

    private static long getLogFileMaxSizeBytes() {
        // TODO Make configurable
        return 33554432; // 32MB;
    }

    private static FileChannel openChannel(Path path, boolean createNew)
            throws IOException {
        FileChannel channel = FileChannel.open(path,
                createNew ? StandardOpenOption.CREATE_NEW
                        : StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
        if (!createNew) {
            channel.position(channel.size());
        }

        return channel;
    }

    /** Reads the state from a log file. */
    private static class StateReader implements Handler {
        String lastLogRow = null;
        Map<Long, TodoRecord> todoMap = new LinkedHashMap<>();
        boolean isLogEmpty = true;

        @Override
        public void handle(String line) {
            if (Type.TODO.isTypeOf(line)) {
                parseSignatureRecord(line);
            } else {
                if (Type.TIMESTAMP.isTypeOf(line)) {
                    parseTimestampRecord(line);
                } else if (Type.SIGNATURE.isTypeOf(line)) {
                    parseSignatureRecord(line);
                }
                lastLogRow = line;
            }
            isLogEmpty = false;
        }

        LogState getState() throws Exception {
            return isLogEmpty ? LogState.getFirstState() : new LogState(
                    LogRecord.parsePrevRecord(lastLogRow), new ArrayList<>(
                            todoMap.values()));
        }

        void parseSignatureRecord(String line) {
            TodoRecord tr = SignatureRecord.parseTodoRecord(line);
            todoMap.put(tr.getNr(), tr);
        }

        void parseTimestampRecord(String line) {
            // Remove elements for which timestamp was taken
            for (Long nr : TimestampRecord.parseNumbersList(line)) {
                todoMap.remove(nr);
            }
        }
    }
}
