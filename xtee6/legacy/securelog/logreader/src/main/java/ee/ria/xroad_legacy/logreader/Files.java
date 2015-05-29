package ee.ria.xroad_legacy.logreader;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles opening and reading of the log files.
 */
class Files {
    private static final Logger LOG = LoggerFactory.getLogger(Files.class);

    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmss");
    private static final long INFINITY = Long.MAX_VALUE;

    private File directory;
    private SortedMap<Long, LogFile> filesByDate;

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    Files(String directory) {
        this.directory = new File(directory);
        if (!this.directory.isDirectory()) {
            throw new RuntimeException(
                    "\"" + directory + "\" is not a directory");
        }
    }

    LogRecord binSearch(long beginTime, long endTime,
            RecordType recordType, int fieldNo, String fieldValue)
            throws Exception {
        LOG.debug("binSearch({}, {}, {}, {}, {})",
                new Object[] {beginTime,  endTime,  recordType,
                        fieldNo, fieldValue});

        // Locate the beginning of the time period.
        LogFile startFile = getFileForDate(beginTime);
        if (startFile == null) {
            throw new RuntimeException("Cannot find file for given start time");
        }

        // Find the starting position.
        LogPosition startPosition = startFile.seekToTime(beginTime);
        LOG.debug("Found start position: {}", startPosition);
        if (startPosition == null) {
            // The date is not found in this file. We'll move to next.
            startFile = nextFile(startFile);
            startPosition = LogPosition.BEGINNING;
            if (startFile == null) {
                throw new RuntimeException(
                        "Cannot find file for given start time");
            }
        }

        while (startFile != null) {
            // Search forward from the starting position.
            LogRecord ret = startFile.searchForward(startPosition, recordType,
                    endTime, new FieldMatchesPred(fieldNo, fieldValue));

            if (ret == LogRecord.NEXT_FILE) {
                LOG.debug("Moving to next file");
                startFile = nextFile(startFile);
            } else {
                return ret;
            }
        }

        // Files are exhausted, didn't find anything.
        return null;
    }

    LogRecord findByNumber(LogRecord startingRecord, RecordType recordType,
            long recordNumber) throws Exception {
        LOG.debug("findByNumber({}:{}, {}, {})",
                new Object[] {startingRecord.file, startingRecord.pos,
                    recordType, recordNumber});

        // TODO: #2701 if we assume that time stamps may be taken long after
        // signature record, then we should use binary search instead
        // of linear scanning.

        long startingNumber = startingRecord.getRecordNumber();
        LogPosition pos = startingRecord;

        if (startingNumber < recordNumber) {
            LOG.debug("Searching forwards");
            LogFile startFile = startingRecord.file;
            while (startFile != null) {
                LogRecord ret = startFile.searchForward(pos,
                        recordType, Long.MAX_VALUE,
                        new FieldMatchesPred(2, String.valueOf(recordNumber)));
                if (ret == LogRecord.NEXT_FILE) {
                    startFile = nextFile(startFile);
                    pos = LogRecord.BEGINNING;
                } else {
                    return ret;
                }
            }
            // Not found.
            return null;
        } else if (startingNumber > recordNumber) {
            LOG.debug("Searching backwards");
            LogFile startFile = startingRecord.file;
            while (startFile != null) {
                LogRecord ret = startFile.searchBackwards(pos,
                        recordType, 0,
                        new FieldMatchesPred(2, String.valueOf(recordNumber)));
                if (ret == LogRecord.NEXT_FILE) {
                    startFile = prevFile(startFile);
                    pos = startFile == null ? null : startFile.endPosition();
                } else {
                    return ret;
                }
            }
            // Not found.
            return null;
        } else {
            LOG.debug("Already at target record");

            // Whoa, we landed directly on target record.
            return startingRecord;
        }
    }

    LogRecord searchForward(LogRecord startingRecord, RecordType recordType,
            SearchPredicate predicate) throws Exception {
        LOG.debug("searchForward({}:{}, {})",
                new Object[] {startingRecord.file, startingRecord.pos,
                        recordType});

        LogPosition pos = startingRecord;

        LogFile startFile = startingRecord.file;
        while (startFile != null) {
            LogRecord ret = startFile.searchForward(pos,
                    recordType, Long.MAX_VALUE, predicate);
            if (ret == LogRecord.NEXT_FILE) {
                startFile = nextFile(startFile);
                pos = LogRecord.BEGINNING;
            } else {
                return ret;
            }
        }
        // Not found.
        return null;
    }

    /**
    * Refresh the directory information.
    */
    void readDirectory() throws Exception {
        LOG.debug("Reading directory {}", directory.getAbsolutePath());

        filesByDate = new TreeMap<>();

        for (File file: directory.listFiles()) {
            if (file.getName().equals("slog")) {
                // It's the last file in the list.
                LOG.debug("Found: slog");

                filesByDate.put(INFINITY, new LogFile(file));
            } else if (file.getName().startsWith("slog.")) {
                LOG.debug("Found: {}", file.getName());

                // This assumes that there is only one file created every
                // second.
                long fileDate = dateFromFilename(file.getName());
                filesByDate.put(fileDate, new LogFile(file));
            } else {
                LOG.debug("Ignoring: {}", file.getName());
            }
        }
    }

    private long dateFromFilename(String name) throws Exception {
        String[] parts = name.split("\\.");
        String dateString = parts[1];
        Date dateValue = DATE_FORMAT.parse(dateString);
        return dateValue.getTime() / 1000;
    }

    LogFile getFileForDate(long date) {
        SortedMap<Long, LogFile> tail = filesByDate.tailMap(date);
        if (tail.isEmpty()) {
            return null;
        } else {
            return tail.values().iterator().next();
        }
    }

    private LogFile nextFile(LogFile logFile) throws Exception {
        LOG.debug("nextFile({})", logFile);

        Collection<LogFile> files = filesByDate.values();
        Iterator i = files.iterator();

        while (i.hasNext()) {
            Object f = i.next();

            if (f == logFile) {
                if (i.hasNext()) {
                    return (LogFile) i.next();
                } else {
                    // This was the last file.
                    return null;
                }
            }
        }

        // We shouldn't be here -- the logFile must be present in the map.
        throw new IllegalArgumentException(
                "nextFile() was passed unknown file: " + logFile.getName());
    }

    private LogFile prevFile(LogFile logFile) throws Exception {
        LOG.debug("prevFile({})", logFile);

        Collection<LogFile> files = filesByDate.values();
        Iterator i = files.iterator();

        LogFile prev = null;
        while (i.hasNext()) {
            LogFile f = (LogFile) i.next();

            if (f == logFile) {
                // It's OK if prev == null. This just means that there is
                // no previous file.
                return prev;
            }

            prev = f;
        }

        // We shouldn't be here -- the logFile must be present in the map.
        throw new IllegalArgumentException(
                "prevFile() was passed unknown file: " + logFile.getName());
    }

    private static class FieldMatchesPred extends SearchPredicate {
        private int fieldNo;
        private String fieldValue;

        FieldMatchesPred(int fieldNo, String fieldValue) {
            this.fieldNo = fieldNo;
            this.fieldValue = fieldValue;
        }

        @Override
        boolean matches(LogFile file, int recordStart) {
            String f = file.readField(recordStart, fieldNo);
            return StringUtils.equals(fieldValue, f);
        }
    }
}
