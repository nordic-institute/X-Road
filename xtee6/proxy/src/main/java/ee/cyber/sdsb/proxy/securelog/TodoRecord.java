package ee.cyber.sdsb.proxy.securelog;

/** Interface for log records for which timestamp should be taken. */
interface TodoRecord {
    long getNr();
    String getTsManifestId();
    String getTsManifestDigestMethod();
    String getTsManifestDigest();

    /** Indicates whether this instance is currently in process. */
    boolean isInProcess();
    /** Set the corresponding flag. Do not call directly, only via LogState. */
    void setInProcess(boolean inProcess);
    /** Returns the time in milliseconds since processing was started. */
    long processingDurationMillis();

    String toTodoStr();
}
