package ee.cyber.sdsb.asyncdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;

public class AsyncLogWriterImpl implements AsyncLogWriter {
    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncLogWriterImpl.class);

    private ClientId provider;

    public AsyncLogWriterImpl(ClientId provider) {
        this.provider = provider;
    }

    @Override
    public void appendToLog(RequestInfo requestInfo, String lastSendResult,
            int firstRequestSendCount) throws Exception {
        String info = String.format(
                "Appending to asynclog -lastSendResult: '%s', first request " +
                        "send count: '%d' and request info: '%s'",
                lastSendResult, firstRequestSendCount, requestInfo);
        LOG.info(info);

        // TODO: client and provider names can contain spaces.

        StringBuilder sb = new StringBuilder();

        String currentTimeInTimestampSeonds = dateToSecondsString(new Date());
        LOG.debug("Current date in timestamp seconds: '{}'",
                currentTimeInTimestampSeonds);

        sb.append(currentTimeInTimestampSeonds).append(FIELD_SEPARATOR);
        sb.append(dateToSecondsString(requestInfo.getReceivedTime()))
                .append(FIELD_SEPARATOR);

        String removedTime = requestInfo.getRemovedTime() != null
                ? dateToSecondsString(requestInfo.getRemovedTime()) : "0";
        sb.append(removedTime).append(FIELD_SEPARATOR);
        sb.append(lastSendResult).append(FIELD_SEPARATOR);
        sb.append(firstRequestSendCount).append(FIELD_SEPARATOR);
        sb.append(provider.toShortString()).append(FIELD_SEPARATOR);
        sb.append(requestInfo.getSender()).append(FIELD_SEPARATOR);
        sb.append(requestInfo.getUser()).append(FIELD_SEPARATOR);
        sb.append(requestInfo.getService()).append(FIELD_SEPARATOR);

        String requestIdEscaped =
                StringEscapeUtils.escapeJava(requestInfo.getId());
        sb.append(requestIdEscaped);
        sb.append('\n');

        final String logFileLine = sb.toString();
        LOG.debug("Log file line: '{}'", logFileLine);

        Callable<Object> task = new Callable<Object>() {

            @Override
            public Object call() throws Exception {
                String logFilePath = getLogFilePath();
                LOG.info("Async-log for provider '{}' written to '{}'",
                        provider, logFilePath);
                FileUtils.write(new File(logFilePath), logFileLine,
                        StandardCharsets.UTF_8, true);
                return null;
            }

        };
        AsyncDBUtil.performLocked(task, getLogFilePath(), this);
    }

    private static String dateToSecondsString(Date date) {
        return Long.toString(date.getTime() / 1000);
    }

    private static String getLogFilePath() {
        String logRootDir = SystemProperties.getLogPath();
        return AsyncDBUtil.makePath(logRootDir, ASYNC_LOG_FILENAME);
    }
}
