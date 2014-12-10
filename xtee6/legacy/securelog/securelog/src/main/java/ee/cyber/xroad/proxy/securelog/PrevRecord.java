package ee.cyber.xroad.proxy.securelog;

/**
 * Represents previous log record and is used for calculating the linking info.
 * The interface is used both for full log records (i.e. records that are logged
 * during current system execution) and partial log records (the ones read from
 * previous log file).
 */
interface PrevRecord {
    long getNr();
    String getHashAlg();
    String getLinkingInfo();

    String toFirstRowStr();
}
