package ee.ria.xroad_legacy.proxy.securelog;

import java.util.Random;

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.Reference;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad_legacy.common.signature.Signature;

import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getAlgorithmId;

class SignatureRecord extends LogRecord implements TodoRecord {
    private static final Random RANDOM = new Random();

    private boolean inProcess;
    private long inProcessTime;

    SignatureRecord(String signature, String tsManifestId,
            String tsManifestDigestMethod, String tsManifestDigest) {
        super(Type.SIGNATURE, encodeBase64(signature), tsManifestId,
                tsManifestDigestMethod, tsManifestDigest);
    }

    /**
     * Creates partially initialized instance that can be used as Todo record.
     */
    private SignatureRecord(long nr, String tsManifestId,
            String tsManifestDigestMethod, String tsManifestDigest) {
        super(Type.SIGNATURE, nr, null /* hashAlg */, null /* linkingInfo */,
                null /* time_t */, null /* signature */, tsManifestId,
                tsManifestDigestMethod, tsManifestDigest);
    }

    static String createNewRND() {
        return Long.toString(RANDOM.nextLong());
    }

    static SignatureRecord create(Signature signature) throws Exception {
        Manifest tsManifest = signature.createTimestampManifest(createNewRND());
        Reference ref = signature.getManifestRef(tsManifest.getId());
        String digestMethod = getAlgorithmId(
                ref.getMessageDigestAlgorithm().getAlgorithmURI());
        String digest = encodeBase64(ref.getDigestValue());

        return new SignatureRecord(signature.toXml(), tsManifest.getId(),
                digestMethod, digest);
    }

    /**
     * Parses the given todo or ordinary log string into a Todo record.
     */
    public static TodoRecord parseTodoRecord(String s) {
        String[] parts = s.split(LOG_SEPARATOR, 9);
        try {
            if (Type.TODO.isTypeOf(s)) {
                checkRecordLength(parts, 5);
                return new SignatureRecord(Long.parseLong(parts[1]), parts[2],
                        parts[3], parts[4]);
            }

            checkRecordLength(parts, 9);
            return new SignatureRecord(Long.parseLong(parts[1]), parts[6],
                    parts[7], parts[8]);
        } catch (NumberFormatException ex) {
            throw new CodedException(ErrorCodes.X_SLOG_MALFORMED_RECORD, ex);
        }
    }

    @Override
    public String getTsManifestId() {
        return fields[6];
    }

    @Override
    public String getTsManifestDigestMethod() {
        return fields[7];
    }

    @Override
    public String getTsManifestDigest() {
        return fields[8];
    }

    @Override
    public boolean isInProcess() {
        return inProcess;
    }

    @Override
    public void setInProcess(boolean inProcess) {
        this.inProcess = inProcess;
        inProcessTime = inProcess ? System.currentTimeMillis() : 0;
    }

    @Override
    public long processingDurationMillis() {
        return System.currentTimeMillis() - inProcessTime;
    }

    @Override
    public String toTodoStr() {
        return concatLog(Type.TODO.value, getNrStr(), getTsManifestId(),
                getTsManifestDigestMethod(), getTsManifestDigest());
    }
}
