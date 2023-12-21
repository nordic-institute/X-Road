/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.JobManager;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_LOGGING_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_TIMESTAMPING_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

/**
 * Contains methods for saving entries to the message log.
 */
@Slf4j
public final class MessageLog {
    private static final String LOG_MANAGER_IMPL_CLASS = SystemProperties.PREFIX + "proxy.messageLogManagerImpl";
    private static AbstractLogManager logManager;

    private MessageLog() {
    }

    /**
     * Initializes the message log using the provided actor system. Use control aware mailbox.
     *
     * @param jobManager the job manager
     * @return false if NullLogManager was initialized, true otherwise
     */
    public static boolean init(JobManager jobManager) {
        Class<? extends AbstractLogManager> clazz = getLogManagerImpl();

        log.trace("Using implementation class: {}", clazz);

        try {
            logManager = clazz.getDeclaredConstructor(JobManager.class).newInstance(jobManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize LogManager", e);
        }

        return NullLogManager.class != clazz;
    }

    public static void shutdown() {
        logManager.shutdown();
    }


    /**
     * Save the message and signature to message log. Attachments are not logged.
     *
     * @param message    the message
     * @param signature  the signature
     * @param clientSide whether this message is logged by the client proxy
     * @param xRequestId (optional) additional request if to distinguish request/response pairs
     */
    public static void log(SoapMessageImpl message, SignatureData signature, boolean clientSide,
                           String xRequestId) {
        try {
            assertInitialized();
            logManager.log(new SoapLogMessage(message, signature, clientSide, xRequestId));
        } catch (Exception e) {
            throw translateWithPrefix(X_LOGGING_FAILED_X, e);
        }
    }

    /**
     * Save the message and signature to message log. The message body is saved from an input stream.
     */
    public static void log(RestRequest message, SignatureData signature, CacheInputStream body, boolean clientside,
                           String xRequestId) {
        try {
            assertInitialized();
            logManager.log(new RestLogMessage(message.getQueryId(), message.getClientId(), message.getServiceId(),
                    message, signature, body, clientside, xRequestId));
        } catch (Exception e) {
            throw translateWithPrefix(X_LOGGING_FAILED_X, e);
        }
    }

    /**
     * Save the message and signature to message log. The message body is saved from an input stream.
     */
    public static void log(RestRequest request, RestResponse message,
                           SignatureData signature, CacheInputStream body, boolean clientside, String xRequestId) {
        try {
            assertInitialized();
            logManager.log(new RestLogMessage(request.getQueryId(), request.getClientId(), request.getServiceId(),
                    message, signature, body, clientside, xRequestId));
        } catch (Exception e) {
            throw translateWithPrefix(X_LOGGING_FAILED_X, e);
        }
    }

    public static void log(SoapMessageImpl message, SignatureData signature, boolean clientSide) {
        log(message, signature, clientSide, null);
    }

    public static void log(RestRequest message, SignatureData signature, CacheInputStream body, boolean clientside) {
        log(message, signature, body, clientside, null);
    }

    public static void log(RestRequest request, RestResponse message,
                           SignatureData signature, CacheInputStream body, boolean clientside) {
        log(request, message, signature, body, clientside, null);
    }

    public static Map<String, DiagnosticsStatus> getDiagnosticStatus() {
        assertInitialized();
        return logManager.getDiagnosticStatus();
    }


    /**
     * Returns a time-stamp record for a given message record.
     *
     * @param record the message record
     * @return the time-stamp record or null, if time-stamping failed.
     */
    public static TimestampRecord timestamp(MessageRecord record) {
        try {
            log.trace("timestamp()");
            assertInitialized();
            return logManager.timestamp(record.getId());
        } catch (Exception e) {
            throw translateWithPrefix(X_TIMESTAMPING_FAILED_X, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractLogManager> getLogManagerImpl() {
        String logManagerImplClassName = System.getProperty(LOG_MANAGER_IMPL_CLASS, NullLogManager.class.getName());

        try {
            Class<?> clazz = Class.forName(logManagerImplClassName);

            return (Class<? extends AbstractLogManager>) clazz;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load log manager impl: " + logManagerImplClassName, e);
        }
    }

    private static void assertInitialized() {
        if (logManager == null) {
            throw new IllegalStateException("not initialized");
        }
    }

}
