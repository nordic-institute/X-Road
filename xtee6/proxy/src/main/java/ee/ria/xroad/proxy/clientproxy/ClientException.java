package ee.ria.xroad.proxy.clientproxy;

import org.apache.commons.lang3.exception.ExceptionUtils;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

/**
 * This is exception for errors caused by the client, for example,
 * client auth failure, invalid XML, etc.
 */
class ClientException extends CodedException {

    ClientException(CodedException ex) {
        super(ex.getFaultCode(), ex.getFaultString());

        faultActor = ex.getFaultActor();
        faultDetail = ex.getFaultDetail();

        // All the client messages have prefix Client...
        withPrefix(ErrorCodes.CLIENT_X);
    }

    ClientException(String faultCode, Throwable cause) {
        super(faultCode, cause.getMessage());

        faultDetail = ExceptionUtils.getStackTrace(cause);

        // All the client messages have prefix Client...
        withPrefix(ErrorCodes.CLIENT_X);
    }

    ClientException(String faultCode, String format, Object... args) {
        super(faultCode, format, args);

        // All the client messages have prefix Client...
        withPrefix(ErrorCodes.CLIENT_X);
    }
}
