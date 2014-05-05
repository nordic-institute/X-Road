package ee.cyber.sdsb.logreader;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.asic.AsicContainer;

public class ExtractSvt extends HttpServlet {

    private static final long serialVersionUID = 6231622234819766192L;

    private static final Logger LOG =
            LoggerFactory.getLogger(ExtractSvt.class);

    private static final DateTimeFormatter DATE_TIME_PARSER =
            ISODateTimeFormat.dateTimeParser();

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        ServletOutputStream output = response.getOutputStream();
        try {
            String queryId = request.getParameter("queryId");
            Date startDate = parseDate(request.getParameter("startDate"));
            Date endDate = parseDate(request.getParameter("endDate"));

            LOG.debug("doGet queryId: {}, startDate: {}, endDate: {}",
                    new Object[] {queryId, startDate, endDate});

            if (queryId != null && startDate != null && endDate != null) {
                AsicContainer asic =
                        extractAsicContainer(queryId, startDate, endDate);
                sendAsicContainer(response, output, queryId, asic);
            } else {
                // TODO: Better errors?
                throw new Exception("Missing arguments");
            }
        } catch (Exception e) {
            LOG.error("Error extracting ASiC container: {}", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
        } finally {
            output.close();
        }
    }

    private static AsicContainer extractAsicContainer(String queryId,
            Date startDate, Date endDate) throws Exception {
        String path = getLogReaderPath();
        LogReader logReader = new LogReader(path);
        return logReader.extractSignature(queryId, startDate, endDate);
    }

    private static void sendAsicContainer(HttpServletResponse response,
            ServletOutputStream output, String queryId, AsicContainer asic)
                    throws Exception {
        String idHash = LogReader.hashQueryId(queryId);
        String fileName = idHash + AsicContainer.FILENAME_SUFFIX;

        response.setContentType(AsicContainer.MIMETYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Content-Transfer-Encoding", "binary");

        output.write(asic.getBytes());
    }

    private static String getLogReaderPath() {
        // TODO: could we use DefaultFilepaths.SECURE_LOG_DIR here as
        // default path?
        String path = System.getProperty(SystemProperties.LOG_READER_PATH);
        if (path == null || path.isEmpty()) {
            return ".";
        }

        return path;
    }

    private static Date parseDate(String dateString) {
        if (dateString == null) {
            return null;
        }

        return DATE_TIME_PARSER.parseDateTime(dateString).toDate();
    }

}
