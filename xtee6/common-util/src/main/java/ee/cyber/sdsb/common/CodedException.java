package ee.cyber.sdsb.common;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Exception thrown by proxy business logic. Contains SOAP fault information
 * (code, message, actor and detail).
 */
public class CodedException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 4225113353511950429L;

    protected String faultCode;
    protected String faultActor = "";
    protected String faultDetail = "";
    protected String faultString = "";

    /**
     * Creates new exception using the fault code.
     * @param faultCode the fault code
     */
    public CodedException(String faultCode) {
        this.faultCode = faultCode;
        faultDetail = ExceptionUtils.getStackTrace(this);
    }

    /**
     * Creates new exception with fault code and fault message.
     * @param faultCode the fault code
     * @param faultMessage the message
     */
    public CodedException(String faultCode, String faultMessage) {
        super(faultMessage);

        this.faultCode = faultCode;
        faultDetail = ExceptionUtils.getStackTrace(this);
        faultString = faultMessage;
    }

    /**
     * Creates new exception with fault code and fault message.
     * The message is constructed using String.format(). from parameters
     * format and args.
     * @param faultCode the fault code
     * @param format the string format
     * @param args the arguments
     */
    public CodedException(String faultCode, String format, Object... args) {
        this(faultCode, String.format(format, args));
    }

    /**
     * Creates exception from fault code and cause.
     * @param faultCode the fault code
     * @param cause the cause
     */
    public CodedException(String faultCode, Throwable cause) {
        super(cause);

        this.faultCode = faultCode;
        this.faultDetail = ExceptionUtils.getStackTrace(cause);
        this.faultString = cause.getMessage();
    }

    /**
     * Creates new exception with full SOAP fault details.
     * @param faultCode the fault code
     * @param faultString the fault string (e.g. the message)
     * @param faultActor the fault actor
     * @param faultDetail the details
     * @return new proxy exception
     */
    public static CodedException fromFault(String faultCode, String faultString,
            String faultActor, String faultDetail) {
        CodedException ret = new Fault(faultCode, faultString);

        ret.faultActor = faultActor;
        ret.faultDetail = faultDetail;

        return ret;
    }

    /** Returns SOAP fault code. */
    public String getFaultCode() {
        return faultCode;
    }

    /** Returns SOAP fault string. */
    public String getFaultString() {
        return faultString;
    }

    /** Returns SOAP fault actor. */
    public String getFaultActor() {
        return faultActor;
    }

    /** Returns SOAP fault detail. */
    public String getFaultDetail() {
        return faultDetail;
    }

    /** Simply calls toString(). */
    @Override
    public String getMessage() {
        return toString();
    }

    /**
     * Returns the string representation of the exception in form of
     * [code]: [detail].
     */
    @Override
    public String toString() {
        return faultCode + ": " + faultString;
    }

    /** Returns the current exception with prefix appended in front of
     * the fault code.
     */
    public CodedException withPrefix(String ...prefix) {
        faultCode = StringUtils.join(prefix, ".") + "." + faultCode;

        return this;
    }

    /**
     * Encapsulates error message read from SOAP fault.
     * This allows processing faults separately in clientproxy.
     */
    @SuppressWarnings("serial") // does not need to have serial
    public static class Fault extends CodedException implements Serializable {
        public Fault(String faultCode, String faultString) {
            super(faultCode, faultString);
        }
    }
}
