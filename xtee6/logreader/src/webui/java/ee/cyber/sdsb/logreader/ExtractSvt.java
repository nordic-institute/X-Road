package ee.cyber.sdsb.logreader;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.asic.AsicContainerEntries;

import static ee.cyber.sdsb.common.securelog.MessageRecord.hashQueryId;

public class ExtractSvt extends HttpServlet {

    private static final long serialVersionUID = 6231622234819766192L;

    private static final Logger LOG =
            LoggerFactory.getLogger(ExtractSvt.class);

    private static final DateTimeFormatter DATE_TIME_PARSER =
            ISODateTimeFormat.dateTimeParser();

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        try (ServletOutputStream output = response.getOutputStream()) {
            String queryId = getParameter(request, "queryId");
            Date startDate = parseDate(request.getParameter("startDate"));
            Date endDate = parseDate(request.getParameter("endDate"));

            LOG.debug("doGet queryId: {}, startDate: {}, endDate: {}",
                    new Object[] {queryId, startDate, endDate});

            AsicContainer asic =
                    extractAsicContainer(queryId, startDate, endDate);
            sendAsicContainer(response, output, queryId, asic);
        } catch (Exception e) {
            LOG.error("Error extracting ASiC container: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
        }
    }

    private static AsicContainer extractAsicContainer(String queryId,
            Date startDate, Date endDate) throws Exception {
        String path = getLogReaderPath();
        LogReader logReader = new LogReader(path);
        return logReader.read(queryId, startDate, endDate);
    }

    private static void sendAsicContainer(HttpServletResponse response,
            ServletOutputStream output, String queryId, AsicContainer asic)
                    throws Exception {
        String idHash = hashQueryId(queryId);
        String fileName = idHash + AsicContainerEntries.FILENAME_SUFFIX;

        response.setContentType(AsicContainerEntries.MIMETYPE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Content-Transfer-Encoding", "binary");

        output.write(asic.getBytes());
    }

    private static String getLogReaderPath() {
        return SystemProperties.getLogReaderPath();
    }

    private static Date parseDate(String dateString) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        return DATE_TIME_PARSER.parseDateTime(dateString).toDate();
    }

    private static String getParameter(HttpServletRequest request,
            String parameterName) throws Exception {
        String parameterValue = request.getParameter(parameterName);
        if (StringUtils.isEmpty(parameterValue)) {
            throw new Exception("Missing parameter '" + parameterName + "'");
        }

        return parameterValue;
    }

}
