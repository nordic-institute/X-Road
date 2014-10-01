package ee.cyber.sdsb.asyncdb;

import ee.cyber.sdsb.asyncdb.messagequeue.RequestInfo;

public interface AsyncLogWriter {
    String ASYNC_LOG_FILENAME = "async";
    char FIELD_SEPARATOR = ';';

    public void appendToLog(RequestInfo requestInfo, String lastSendResult,
            int firstRequestSendCount) throws Exception;
}
