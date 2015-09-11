package ee.ria.xroad.common;

import lombok.Getter;

/**
 * Exception thrown by proxy business logic. This exception forces to respond
 * with specified HTTP status code and plain text error message instead of
 * SOAP fault message.
 */
public class CodedExceptionWithHttpStatus extends CodedException {

    @Getter
    private int status;

    /**
     * Creates new exception with HTTP status code, fault code and fault message.
     * @param status the HTTP status code
     * @param faultCode the fault code
     * @param faultMessage the message
     */
    public CodedExceptionWithHttpStatus(
            int status, String faultCode, String faultMessage) {
        super(faultCode, faultMessage);

        this.status = status;
    }

    /**
     * Creates new exception with HTTP status code and coded exception.
     * @param status the HTTP status code
     * @param e the coded exception
     */
    public CodedExceptionWithHttpStatus(int status, CodedException e) {
        super(e.getFaultCode(), e.getFaultString());

        this.faultDetail = e.getFaultDetail();
        this.status = status;
    }
}
