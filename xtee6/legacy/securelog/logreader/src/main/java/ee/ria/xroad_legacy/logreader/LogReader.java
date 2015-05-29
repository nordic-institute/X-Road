package ee.ria.xroad_legacy.logreader;

import java.util.Date;

import ee.ria.xroad_legacy.common.asic.AsicContainer;
import ee.ria.xroad_legacy.common.signature.Signature;
import ee.ria.xroad_legacy.common.signature.SignatureData;
import ee.ria.xroad_legacy.common.util.CryptoUtils;

import static ee.ria.xroad_legacy.common.util.CryptoUtils.MD5_ID;
import static ee.ria.xroad_legacy.common.util.CryptoUtils.hexDigest;
import static ee.ria.xroad_legacy.logreader.RecordType.*;

/**
 * Main worker class for log reader.
 */
class LogReader {

    private static final int FIELD_MESSAGE = 7;
    private static final int FIELD_SIGNATURE = 6;
    private static final int FIELD_TIMESTAMP = 7;
    private static final int FIELD_TIMESTAMP_MANIFEST = 9;
    private static final int FIELD_HASH_CHAIN_RESULT = 9;
    private static final int FIELD_HASH_CHAIN = 10;

    private final Files logFiles;

    public LogReader() {
        this(new Files("log"));
    }

    public LogReader(String path) {
        this(new Files(path));
    }

    public LogReader(Files f) {
        logFiles = f;
    }

    /**
     * Creates ASIC container for query with ID queryId. The logs are
     * searched for dates starting from <code>begin</code> and ending
     * at <code>end</code>.
     *
     * @param queryId the query ID
     * @param begin the begin date
     * @param end the end date
     */
    AsicContainer extractSignature(String queryId, Date begin, Date end)
            throws Exception {
        return extractSignature(queryId, getTimeInSeconds(begin),
                getTimeInSeconds(end));
    }

    /**
     * Creates ASIC container for query with ID queryId. The logs are
     * searched for dates starting from <code>begin</code> and ending
     * at <code>end</code>.
     *
     * @param queryId the query ID
     * @param begin the begin time in seconds
     * @param end the end time in seconds
     */
    AsicContainer extractSignature(String queryId, long begin, long end)
            throws Exception {
        // Refresh directory
        logFiles.readDirectory();

        LogRecord soapRecord = findSoapRecord(queryId, begin, end);
        if (soapRecord == null) {
            throw new Exception("Cannot find SOAP record with ID " + queryId);
        }

        LogRecord signatureRecord = findSignatureRecord(soapRecord);
        if (signatureRecord == null) {
            throw new Exception("Cannot find signature record for SOAP with ID "
                            + queryId);
        }

        LogRecord timestampRecord = findTimestampRecord(signatureRecord);
        if (timestampRecord == null) {
            throw new Exception("Cannot find timestamp record for SOAP with ID "
                            + queryId);
        }

        String messageBase64 = soapRecord.getField(FIELD_MESSAGE);
        String signatureBase64 = signatureRecord.getField(FIELD_SIGNATURE);
        String timestampDERBase64 = timestampRecord.getField(FIELD_TIMESTAMP);
        String timestampManifestBase64 =
                timestampRecord.getField(FIELD_TIMESTAMP_MANIFEST);

        String hashChainResultBase64 =
                soapRecord.getField(FIELD_HASH_CHAIN_RESULT);
        String hashChainBase64 = soapRecord.getField(FIELD_HASH_CHAIN);

        return createAsic(messageBase64, signatureBase64, hashChainResultBase64,
                hashChainBase64, timestampDERBase64, timestampManifestBase64);
    }

    static AsicContainer createAsic(String messageBase64,
            String signatureBase64, String timestampDERBase64,
            String timestampManifestBase64) throws Exception {
        return createAsic(messageBase64, signatureBase64, null, null,
                timestampDERBase64, timestampManifestBase64);
    }

    static AsicContainer createAsic(String messageBase64,
            String signatureBase64, String hashChainResultBase64,
            String hashChainBase64, String timestampDERBase64,
            String timestampManifestBase64) throws Exception {
        String messageXml = decodeBase64(messageBase64);
        String signatureXml = decodeBase64(signatureBase64);
        String hashChainResult = decodeBase64(hashChainResultBase64);
        String hashChain = decodeBase64(hashChainBase64);
        String timestampManifestXml = decodeBase64(timestampManifestBase64);

        Signature signature = new Signature(signatureXml);
        signature.addTimestampManifest(timestampManifestXml);
        signature.addXadesTimestamp(timestampDERBase64);

        return new AsicContainer(messageXml,
                new SignatureData(signature.toXml(), hashChainResult,
                        hashChain));
    }

    private LogRecord findSoapRecord(String queryId, long beginTime,
            long endTime) throws Exception {
        String queryIdHash = hashQueryId(queryId);

        return logFiles.binSearch(beginTime, endTime, SOAP, 6, queryIdHash);
    }

    private LogRecord findSignatureRecord(LogRecord soapRecord)
            throws Exception {
        return logFiles.findByNumber(soapRecord, SIGNATURE,
                Long.parseLong(soapRecord.getField(8)));
    }

    private LogRecord findTimestampRecord(LogRecord signatureRecord)
            throws Exception {
        return logFiles.searchForward(signatureRecord, TIMESTAMP,
                new CommaSepFieldContains(6,
                        String.valueOf(signatureRecord.getRecordNumber())));
    }

    static String decodeBase64(String base64Encoded) {
        if (base64Encoded != null && !base64Encoded.isEmpty()
                && !base64Encoded.equals("-")) {
            return new String(CryptoUtils.decodeBase64(base64Encoded));
        }

        return null;
    }

    static String hashQueryId(String queryId) throws Exception {
        return hexDigest(MD5_ID, queryId);
    }

    private static long getTimeInSeconds(Date date) {
        return date.getTime() / 1000;
    }

    private static class CommaSepFieldContains extends SearchPredicate {
        private int fieldNo;
        private String searchVal;

        CommaSepFieldContains(int fieldNo, String searchVal) {
            this.fieldNo = fieldNo;
            this.searchVal = searchVal;
        }

        @Override
        boolean matches(LogFile file, int recordStart) {
            String s = file.readField(recordStart, fieldNo);
            String[] parts = s.split(",");
            for (String p: parts) {
                if (searchVal.equals(p)) {
                    return true;
                }
            }
            return false;
        }
    }
}
