package ee.ria.xroad_legacy.logreader;

/**
 * Represents one log recod in securelog.
 */
class LogRecord extends LogPosition {
    /**
     * Special value to signal that the search should continue
     * in the next file.
     */
    static final LogRecord NEXT_FILE = new LogRecord(null, 0);

    LogRecord(LogFile file, int recordStart) {
        super(file, recordStart);
    }

    /**
     * Returns sequence number of the field.
     */
    long getRecordNumber() {
        // TODO: #2701 conversion errors?
        return Long.valueOf(file.readField(pos, 2));
    }

    /**
     * Returns field with given index. Field numbers start from 1.
     */
    String getField(int fieldNumber) {
        return file.readField(pos, fieldNumber);
    }
}
