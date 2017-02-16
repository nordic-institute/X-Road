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
