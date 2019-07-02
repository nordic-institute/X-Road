/**
 * The MIT License
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
package org.niis.xroad.restapi.exceptions;

import java.util.List;
import java.util.Map;

/**
 * RuntimeException that (possibly) carries error code
 */
public class ErrorCodedRuntimeException extends RuntimeException implements ErrorCodedException {

    private String errorCode;
    private Map<String, List<String>> warningMap;

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public Map<String, List<String>> getWarningMap() {
        return warningMap;
    }

    public ErrorCodedRuntimeException() {
    }

    public ErrorCodedRuntimeException(String msg) {
        super(msg);
    }

    public ErrorCodedRuntimeException(ErrorCode errorCode) {
        this.errorCode = errorCode.getValue();
    }

    public ErrorCodedRuntimeException(String msg, ErrorCode errorCode) {
        super(msg);
        this.errorCode = errorCode.getValue();
    }

    public ErrorCodedRuntimeException(String msg, Throwable t) {
        super(msg, t);
    }

    public ErrorCodedRuntimeException(String msg, Map<String, List<String>> warningMap) {
        super(msg);
        this.warningMap = warningMap;
    }

    public ErrorCodedRuntimeException(String msg, Throwable t, Map<String, List<String>> warningMap) {
        super(msg, t);
        this.warningMap = warningMap;
    }

    public ErrorCodedRuntimeException(String msg, Throwable t, ErrorCode errorCode) {
        super(msg, t);
        this.errorCode = errorCode.getValue();
    }

    public ErrorCodedRuntimeException(Throwable t) {
        super(t);
    }

    public ErrorCodedRuntimeException(Throwable t, ErrorCode errorCode) {
        super(t);
        this.errorCode = errorCode.getValue();
    }

}
