package ee.ria.xroad_legacy.logreader;

import java.io.FileOutputStream;
import java.util.Date;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

import ee.ria.xroad_legacy.common.SystemProperties;
import ee.ria.xroad_legacy.common.asic.AsicContainer;
import ee.ria.xroad_legacy.common.asic.AsicContainerEntries;

public class LogReaderMain {

    private static final DateTimeFormatter DATE_TIME_PARSER =
            ISODateTimeFormat.dateTimeParser();

    public static void main(String[] args) {
        disableLogging(); // we do not want any logger messages

        if (args.length < 3) {
            showUsage();
        } else {
            try {
                extractAsicContainer(args);
            } catch (Exception e) {
                System.err.println("Error extracting ASiC container: "
                        + e.getMessage());
                System.exit(1);
            }
        }
    }

    private static void extractAsicContainer(String[] args) throws Exception {
        String id = args[0];
        Date startDate = parseDate(args[1]);
        Date endDate = parseDate(args[2]);
        String path = getLogReaderPath(args);

        System.out.println("Extracing ASiC container...");
        System.out.println("\tQuery id:\t" + id);
        System.out.println("\tStart date:\t" + startDate);
        System.out.println("\tEnd date:\t" + endDate);
        System.out.println("\tLog path:\t" + path);

        LogReader logReader = new LogReader(path);
        AsicContainer asic = logReader.extractSignature(id, startDate, endDate);
        saveAsicContainerToDisk(id, asic);
    }

    private static String getLogReaderPath(String[] args) {
        // We expect the path to the log files either as a command line argument
        // or set via system parameter.
        if (args.length > 3) {
            return args[3];
        }

        return SystemProperties.getLogReaderPath();
    }

    private static void saveAsicContainerToDisk(String queryId,
            AsicContainer asic) throws Exception {
        String idHash = LogReader.hashQueryId(queryId);
        String fileName = idHash + AsicContainerEntries.FILENAME_SUFFIX;

        FileOutputStream out = new FileOutputStream(fileName);
        out.write(asic.getBytes());
        out.close();

        System.out.println("Extracted ASiC container to file " + fileName);
    }

    private static Date parseDate(String dateString) {
        return DATE_TIME_PARSER.parseDateTime(dateString).toDate();
    }

    private static void showUsage() {
        System.out.print("Usage: LogReaderMain ");
        System.out.println("<query id> <start date> <end date> (<log folder>)");
    }

    private static void disableLogging() {
        LoggerContext context =
                (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
    }
}
