package ee.cyber.sdsb.proxy.securelog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.util.CryptoUtils;

class LogRecord implements PrevRecord, Task {
    private static final Logger LOG = LoggerFactory.getLogger(LogRecord.class);

    enum Type {
        SOAP("M"), ENC_SOAP("E"), SIGNATURE("S"), TIMESTAMP("T"),
                FIRST_ROW("#"), TODO("?");
        String value;
        static Map<String, Type> values;

        private Type(String value) {
            this.value = value;
        }

        static Type byValue(String value) {
            if (values == null) {
                values = new HashMap<>();
                for (Type t : values()) {
                    values.put(t.value, t);
                }
            }
            return values.get(value);
        }

        /** Returns true iff <i>this</i> is the type of the specified line. */
        boolean isTypeOf(String line) {
            return line != null && line.startsWith(value);
        }
    }

    protected static final String LOG_SEPARATOR = " ";
    protected static final String MISSING_VALUE = "-";

    private static final int CUSTOM_FIELD_COUNT = 5;

    private static final String LINKING_INFO_SEPARATOR = " ";

    private static final String[] MISSING_VALUES =
            { MISSING_VALUE, MISSING_VALUE, MISSING_VALUE, MISSING_VALUE };

    private static final String MISSING_VALUES_STR = concatLog(MISSING_VALUES);

    /** The record type. */
    private Type type;

    /** The sequence number of the message. */
    private long nr;

    /** All fields as strings. */
    protected String[] fields = new String[5 + CUSTOM_FIELD_COUNT];

    /** Indicates whether the log record is logged. */
    private final Semaphore semaphore = new Semaphore(0);

    /** Holds any error that might occurr during working. */
    private volatile CodedException executionException;

    /**
     * Creates new log record.
     *
     * @param type the type of the record.
     * @param fields the log fields with indexes 5...8 dependent of the log
     *        record type (see the specification).
     */
    LogRecord(Type type, String... fields) {
        if (fields.length > CUSTOM_FIELD_COUNT) {
            throw new IllegalArgumentException("Must have max "
                    + CUSTOM_FIELD_COUNT + " fields");
        }
        this.type = type;
        this.fields[0] = type.value;
        System.arraycopy(fields, 0, this.fields, 5, fields.length);
    }

    /**
     * Creates log record from the provided data that is formatted for logging.
     */
    LogRecord(Type type, long nr, String hashAlg, String linkingInfo,
            String recordTime, String... fields) {
        this(type, fields);
        this.nr = nr;
        this.fields[1] = String.valueOf(nr);
        this.fields[2] = hashAlg;
        this.fields[3] = linkingInfo;
        this.fields[4] = recordTime;
    }

    /**
     * Returns the first log record.
     */
    public static PrevRecord getFirstRecord(String hashAlg) throws Exception {
        return new LogRecord(Type.FIRST_ROW, 0, hashAlg,
                CryptoUtils.hexDigest(hashAlg, ""),
                MISSING_VALUE, MISSING_VALUES);
    }

    /**
     * Parses the given log string to retrieve the first 4 fields as a
     * PrevRecord instance. Fields 6..10 are set to MISSING_VALUE.
     */
    public static PrevRecord parsePrevRecord(String s) {
        String[] parts = s.split(LOG_SEPARATOR, 5); // Line end is irrelevant
        checkRecordLength(parts, 4);
        return new LogRecord(Type.byValue(parts[0]), Long.parseLong(parts[1]),
                parts[2], parts[3], MISSING_VALUE, MISSING_VALUES);
    }

    static String concatLog(String... parts) {
        return LogUtil.concat(LOG_SEPARATOR, parts);
    }

    public Type getType() {
        return type;
    }

    void setNr(long nr) {
        this.nr = nr;
        fields[1] = String.valueOf(nr);
    }

    @Override
    public long getNr() {
        return nr;
    }

    public String getNrStr() {
        return fields[1];
    }

    @Override
    public String getHashAlg() {
        return fields[2];
    }

    @Override
    public String getLinkingInfo() {
        return fields[3];
    }

    @Override
    public String toFirstRowStr() {
        return concatLog(Type.FIRST_ROW.value, getNrStr(), getHashAlg(),
                getLinkingInfo(), MISSING_VALUE, MISSING_VALUES_STR);
    }

    public String toLogStr(PrevRecord previous, String hashAlg)
            throws Exception {
        calculateFields(previous, hashAlg);
        return concatLog(fields);
    }

    void calculateFields(PrevRecord previous, String hashAlg)
            throws Exception {
        String linkingInfoInStr = LogUtil.concat(LINKING_INFO_SEPARATOR,
                previous.getHashAlg(), previous.getLinkingInfo(),
                getType().value, getNrStr(), fields[5], fields[6], fields[7]);
        nr = previous.getNr() + 1;
        fields[1] = String.valueOf(nr);
        fields[2] = hashAlg;
        fields[3] = CryptoUtils.hexDigest(hashAlg, linkingInfoInStr);
        fields[4] = String.valueOf(LogUtil.getUnixTimestamp());
    }

    void setDone() {
        LOG.debug("setDone(): {}", this);
        semaphore.release();
    }

    void waitUntilDone() throws Exception {
        LOG.debug("waitUntilDone(): {}", this);
        semaphore.acquire();

        checkError();
    }

    void setError(Exception e) {
        LOG.error("Error in LogRecord", e);

        executionException = ErrorCodes.translateException(e);
    }

    void checkError() {
        if (executionException != null) {
            throw executionException.withPrefix(ErrorCodes.X_LOGGING_FAILED_X);
        }
    }

    static void checkRecordLength(String[] parts, int expectedLength) {
        if (parts.length < expectedLength) {
            throw new CodedException(ErrorCodes.X_SLOG_MALFORMED_RECORD,
                    "Unexpected record length: %s", parts.length);
        }
    }
}
