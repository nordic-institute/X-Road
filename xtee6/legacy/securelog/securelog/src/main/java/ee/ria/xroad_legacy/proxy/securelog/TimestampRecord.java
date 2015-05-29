package ee.ria.xroad_legacy.proxy.securelog;

import java.util.ArrayList;
import java.util.List;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.CryptoUtils;

class TimestampRecord extends LogRecord {
    private static final String NUMBERS_SEPARATOR = ",";

    private List<Long> numbers;

    TimestampRecord(List<Long> numbers, String manifestXml,
            String digestMethod, byte[] timestampDER) {
        super(Type.TIMESTAMP, getNumbersStr(numbers),
                CryptoUtils.encodeBase64(timestampDER), digestMethod,
                CryptoUtils.encodeBase64(manifestXml));
        this.numbers = numbers;
    }

    static String getNumbersStr(List<Long> numbers) {
        StringBuilder sb = new StringBuilder();

        for (Long nr : numbers) {
            if (sb.length() > 0) {
                sb.append(NUMBERS_SEPARATOR);
            }
            sb.append(nr);
        }

        return sb.toString();
    }

    /**
     * Parses the given timestamp log string to get the list of log record
     * number for which the timestamp applies.
     */
    static List<Long> parseNumbersList(String line) {
        List<Long> numbers = new ArrayList<>();

        String[] parts = line.split(LOG_SEPARATOR, 7); // Line end is irrelevant
        checkRecordLength(parts, 7);

        String[] nrs = parts[5].split(NUMBERS_SEPARATOR);
        for (String nr : nrs) {
            try {
                numbers.add(Long.parseLong(nr));
            } catch (NumberFormatException e) {
                throw new CodedException(ErrorCodes.X_SLOG_MALFORMED_RECORD, e);
            }
        }

        return numbers;
    }

    /** Returns a new copy of the record numbers of this timestamp record. */
    List<Long> getNumbers() {
        return new ArrayList<>(numbers);
    }
}
