package ee.cyber.xroad.proxy.securelog;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.xml.security.c14n.Canonicalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;

import static ee.cyber.sdsb.common.ErrorCodes.X_LOGGING_FAILED_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateException;

public class LogManager implements StartStop {

    private static final Logger LOG = LoggerFactory.getLogger(LogManager.class);

    /** Number of minutes the LogWriter should wait for the next task,
     * until it timestamps all the current log records. */
    private static final int TASK_TIMEOUT_MINUTES = 1;

    /** The log request queue. */
    private static final BlockingQueue<Task> queue =
            new ArrayBlockingQueue<>(100);

    private static LogManager instance = new LogManager();

    private Thread logWriterThread;

    /** Flag for managed threads whether to run or finish. */
    private volatile boolean isRunning = true;

    /** Holds the first error that occurred during working. */
    private volatile CodedException executionException;

    static class TimestampFailed implements Task {
        List<TodoRecord> todoList;

        TimestampFailed(List<TodoRecord> todoList) {
            this.todoList = todoList;
        }
    }

    /**
     * Runnable, that processes the blocking queue - writes the log records to
     * log file and marks them done.
     */
    class LogWriter implements Runnable {

        @Override
        public void run() {
            try (LogFile logFile = new LogFile()) {
                LOG.debug("started");
                while (isRunning) {
                    try {
                        handleNextTask(logFile);
                        clearError();
                    } catch (InterruptedException e) {
                        // Do nothing.
                    } catch (Exception e) {
                        setError(e);
                    }
                }
            } catch (Exception ex) {
                setError(ex);
            }

            LOG.debug("stopped");
        }

        private void handleNextTask(LogFile logFile) throws Exception {
            Task task = queue.poll(TASK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (task == null) { // poll timed out
                LOG.trace("task timed out, timestamping any records left");
                startTimestamper(logFile, getTimestampRecordsCount());
                return;
            }

            LOG.debug("got task {}", task.getClass().getName());
            if (task instanceof LogRecord) {
                handleLogRecordTask(logFile, (LogRecord) task);
            } else if (task instanceof TimestampFailed) {
                handleTimestampFailedTask(logFile, (TimestampFailed) task);
            }
        }

        private void handleTimestampFailedTask(LogFile logFile,
                TimestampFailed timestampFailed) {
            logFile.getState().timestampFailed(timestampFailed.todoList);
        }

        private void handleLogRecordTask(LogFile logFile, LogRecord logRecord)
                throws Exception {
            LOG.trace("got log record, type {}", logRecord.getType());
            try {
                logFile.write(logRecord, getHashAlg());
            } catch (Exception e) {
                logRecord.setError(e); // save the error for later
            } finally {
                logRecord.setDone();
            }

            final int tsrCount = getTimestampRecordsCount();
            if (logFile.getState().getActiveTodoCount() > tsrCount) {
                startTimestamper(logFile, tsrCount);
            }

            if (logFile.mustRotate()) {
                logFile.rotate();
            }
        }

        private void startTimestamper(LogFile logFile, int tsrCount) {
            if (!GlobalConf.isValid()) {
                return;
            }

            List<String> tspUrls = ServerConf.getTspUrl();
            if (tspUrls.isEmpty()) {
                LOG.warn("Cannot time-stamp, no TSPs configured");
                return;
            }

            List<TodoRecord> todo =
                    logFile.getState().takeTodoIntoProcess(tsrCount);
            if (!todo.isEmpty()) {
                new Thread(new Timestamper(todo, tspUrls)).start();
            }
        }
    }

    public static LogManager getInstance() {
        return instance;
    }

    /**
     * Returns the hash algorithm that is used to hash the linking info of log
     * records.
     */
    static String getHashAlg() {
        // TODO #2605 Make configurable
        return CryptoUtils.SHA256_ID;
    }

    /**
     * Returns the hash algorithm that is used to hash the timestamp root
     * manifest and is added to the ReferenceInfo element as DigestMethod.
     */
    static String getTsManifestHashAlg() {
        // TODO #2605 Make configurable
        return CryptoUtils.SHA256_ID;
    }

    /**
     * The canonicalization method that is used to canonicalize the
     * ReferenceInfo element that is later digested and sent to the TSA.
     */
    static String getC14nMethodUri() {
        // TODO #2605 Make configurable
        return Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS;
    }

    /**
     * Returns the hash algorithm that is used to get the digest of the input to
     * the TSA.
     */
    static String getTsaHashAlg() {
        // TODO #2605 Make configurable
        return CryptoUtils.SHA256_ID;
    }

    /**
     * If the number of Todo records is greater than this number, the same
     * amount of Todo records is taken for timestamping.
     */
    static int getTimestampRecordsCount() {
        // TODO #2605 Make configurable
        // With hashchains, this number is of no importance because then
        // the size of time stamp does not depend on number of inputs.
        return 10;
    }

    static void queue(Task task) throws InterruptedException {
        instance.checkError();

        LOG.trace("queue() called - adding new task to the queue");
        queue.put(task);
    }

    static void log(LogRecord logRecord) throws InterruptedException {
        instance.checkError();

        LOG.trace("log() called - adding new record to the queue");
        queue.put(logRecord);
    }

    private LogManager() {
        LOG.debug("LogManager() called");
    }

    @Override
    public void start() {
        LOG.debug("start() called");

        logWriterThread = new Thread(new LogWriter(), "LogWriter");
        logWriterThread.start();
    }

    @Override
    public void join() throws InterruptedException {
        LOG.debug("join() called");

        logWriterThread.join();
    }

    @Override
    public void stop() throws Exception {
        LOG.debug("stop() called");

        isRunning = false;
        logWriterThread.interrupt();
    }

    private synchronized void checkError() {
        if (executionException != null) {
            CodedException thrown =
                    CodedException.fromFault(executionException.getFaultCode(),
                            executionException.getFaultString(),
                            executionException.getFaultActor(),
                            executionException.getFaultDetail());
            throw thrown.withPrefix(X_LOGGING_FAILED_X);
        }
    }

    private void setError(Exception e) {
        if (executionException == null) {
            LOG.error("Error in LogWriter", e);

            executionException = translateException(e);
        }
    }

    private void clearError() {
        executionException = null;
    }
}
