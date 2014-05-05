package ee.cyber.sdsb.asyncdb;


public interface AsyncLogWriter {
    String ASYNC_LOG_FILENAME = "async";
    char FIELD_SEPARATOR = '\t';

    public void appendToLog(RequestInfo requestInfo, String lastSendResult,
            int firstRequestSendCount) throws Exception;
}
