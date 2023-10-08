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
package ee.ria.xroad.common;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.UUID;

/**
 * Exception thrown by proxy business logic. Contains SOAP fault information
 * (code, message, actor and detail).
 */
public class CodedException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 4225113353511950429L;

    @Getter
    protected String faultCode;

    @Getter
    protected String faultActor = "";

    @Getter
    @Setter
    protected String faultDetail = "";

    @Getter
    protected String faultString = "";

    @Getter
    protected String[] arguments;

    @Getter
    protected String translationCode;

    /**
     * Creates new exception using the fault code.
     * @param faultCode the fault code
     */
    public CodedException(String faultCode) {
        this.faultCode = faultCode;
        faultDetail = String.valueOf(UUID.randomUUID());
    }

    /**
     * Creates new exception with fault code and fault message.
     * @param faultCode the fault code
     * @param faultMessage the message
     */
    public CodedException(String faultCode, String faultMessage) {
        super(faultMessage);

        this.faultCode = faultCode;
        faultDetail = String.valueOf(UUID.randomUUID());
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

        setArguments(args);
    }

    /**
     * Creates new exception with fault code and fault message.
     * The message is constructed using String.format(). from parameters
     * format and args.
     * @param faultCode the fault code
     * @param format the string format
     * @param args the arguments
     */
    public CodedException(String faultCode, Throwable cause, String format, Object... args) {
        super(String.format(format, args), cause);

        this.faultCode = faultCode;
        faultDetail = String.valueOf(UUID.randomUUID());
        faultString = super.getMessage();

        setArguments(args);
    }



    /**
     * Creates exception from fault code and cause.
     * @param faultCode the fault code
     * @param cause the cause
     */
    public CodedException(String faultCode, Throwable cause) {
        super(cause);

        this.faultCode = faultCode;
        this.faultDetail = String.valueOf(UUID.randomUUID());
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
                                           String faultActor, String faultDetail, String faultXml) {
        Fault ret = new Fault(faultCode, faultString);

        ret.faultActor = faultActor;
        ret.faultDetail = faultDetail;
        ret.faultXml = faultXml;

        return ret;
    }

    /**
     * Creates new exception with translation code for i18n.
     * @param faultCode the fault code
     * @param trCode the translation code
     * @param faultMessage the message
     * @return CodedException
     */
    public static CodedException tr(String faultCode, String trCode,
            String faultMessage) {
        CodedException ret = new CodedException(faultCode, faultMessage);

        ret.translationCode = trCode;

        return ret;
    }

    /**
     * Creates new exception with translation code for i18n and arguments.
     * @param faultCode the fault code
     * @param trCode the translation code
     * @param faultMessage the message
     * @param args optional arguments
     * @return CodedException
     */
    public static CodedException tr(String faultCode, String trCode,
            String faultMessage, Object... args) {
        CodedException ret = new CodedException(faultCode, faultMessage, args);

        ret.translationCode = trCode;

        return ret;
    }

    /**
     * Creates new exception with translation code for i18n, arguments and the {@link Throwable} that
     * caused this exception.
     * @param faultCode the fault code
     * @param cause the actual causing {@link Throwable}
     * @param trCode the translation code
     * @param faultMessage the message
     * @param args optional arguments
     * @return CodedException
     */
    public static CodedException tr(String faultCode, Throwable cause, String trCode,
                                    String faultMessage, Object... args) {

        CodedException ret = new CodedException(faultCode, cause, faultMessage, args);

        ret.translationCode = trCode;

        return ret;
    }


    @Override
    public String getMessage() {
        return toString();
    }

    /**
     * Returns the string representation of the exception in form of
     * [code]: [detail].
     * @return String
     */
    @Override
    public String toString() {
        return faultCode + ": " + faultString;
    }

    /**
     * Returns the current exception with prefix appended in front of
     * the fault code.
     * @param prefixes optional prefixes
     * @return CodedException
     */
    public CodedException withPrefix(String... prefixes) {
        String prefix = StringUtils.join(prefixes, ".");

        if (!faultCode.startsWith(prefix)) {
            faultCode = prefix + "." + faultCode;
        }

        return this;
    }

    /**
     * Converts provided arguments to string array.
     */
    private void setArguments(Object... args) {
        arguments = new String[args.length];

        for (int i = 0; i < args.length; i++) {
            arguments[i] = args[i] != null ? args[i].toString() : "";
        }
    }


    /**
     * Encapsulates error message read from SOAP fault.
     * This allows processing faults separately in ClientProxy.
     */
    @SuppressWarnings("serial") // does not need to have serial
    public static class Fault extends CodedException implements Serializable {

        @Getter
        private String faultXml;

        /**
         * Creates new fault.
         * @param faultCode the code
         * @param faultString the details
         */
        public Fault(String faultCode, String faultString) {
            super(faultCode, faultString);
        }
    }
}
