/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.asyncdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import ee.ria.xroad.asyncdb.messagequeue.RequestInfo;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * API for asynchonous requests log writer
 */
@Slf4j
public class AsyncLogWriter {
    public static final String ASYNC_LOG_FILENAME = "async";
    public static final char FIELD_SEPARATOR = ';';

    private ClientId provider;

    /**
     * Creates AsyncLogWriter with provider.
     *
     * @param provider - provider the queue is for.
     */
    public AsyncLogWriter(ClientId provider) {
        this.provider = provider;
    }

    /**
     * Appends request to asynchonous requests log.
     *
     * @param requestInfo - request to append
     * @param lastSendResult - result (success or failure) of last sending
     * @param firstRequestSendCount - how many times first request in the queue
     *                              has been tried to send.
     * @throws Exception - when writing into log fails
     */
    public void appendToLog(RequestInfo requestInfo, String lastSendResult,
                            int firstRequestSendCount) throws Exception {
        String info = String.format(
                "Appending to asynclog -lastSendResult: '%s', first request "
                        + "send count: '%d' and request info: '%s'",
                lastSendResult, firstRequestSendCount, requestInfo);
        log.info(info);

        StringBuilder sb = new StringBuilder();

        String currentTimeInTimestampSeonds = dateToSecondsString(new Date());
        log.debug("Current date in timestamp seconds: '{}'",
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
        log.debug("Log file line: '{}'", logFileLine);

        Callable<Object> task = () -> {
            String logFilePath = getLogFilePath();
            log.info("Async-log for provider '{}' written to '{}'",
                    provider, logFilePath);
            FileUtils.write(new File(logFilePath), logFileLine,
                    StandardCharsets.UTF_8, true);
            return null;
        };

        AsyncDBUtil.performLocked(task, getLogFilePath(), this);
    }

    private static String dateToSecondsString(Date date) {
        return Long.toString(TimeUnit.MILLISECONDS.toSeconds(date.getTime()));
    }

    private static String getLogFilePath() {
        String logRootDir = SystemProperties.getLogPath();
        return AsyncDBUtil.makePath(logRootDir, ASYNC_LOG_FILENAME);
    }
}
