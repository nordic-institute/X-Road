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

import java.util.Collection;

/**
 * RuntimeException that (possibly) carries error code
 */
public class DeviationAwareRuntimeException extends RuntimeException implements DeviationAwareException {

    private final Deviation error;
    private final Collection<Deviation> warnings;

    @Override
    public Deviation getError() {
        return error;
    }

    @Override
    public Collection<Deviation> getWarnings() {
        return warnings;
    }

    /**
     * no args
     */
    public DeviationAwareRuntimeException() {
        super();
        this.error = null;
        this.warnings = null;
    }

    /**
     * @param msg
     */
    public DeviationAwareRuntimeException(String msg) {
        super(msg);
        this.error = null;
        this.warnings = null;
    }

    /**
     * @param msg
     * @param errorCode
     */
    public DeviationAwareRuntimeException(String msg, ErrorCode errorCode) {
        super(msg);
        this.error = new Deviation(errorCode.getValue());
        this.warnings = null;
    }

    /**
     * @param msg
     * @param t
     */
    public DeviationAwareRuntimeException(String msg, Throwable t) {
        this(msg, t, null, null);
    }

    /**
     * @param msg
     * @param t
     * @param errorCode
     */
    public DeviationAwareRuntimeException(String msg, Throwable t, ErrorCode errorCode) {
        this(msg, t, errorCode, null);
    }

    /**
     * @param msg
     * @param t
     * @param errorCode
     * @param warnings
     */
    public DeviationAwareRuntimeException(String msg, Throwable t, ErrorCode errorCode,
                                          Collection<Deviation> warnings) {
        super(msg, t);
        this.error = new Deviation(errorCode.getValue());
        this.warnings = warnings;
    }

    /**
     * @param errorCode
     */
    public DeviationAwareRuntimeException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * @param errorCode
     * @param warnings
     */
    public DeviationAwareRuntimeException(ErrorCode errorCode, Collection<Deviation> warnings) {
        this.error = new Deviation(errorCode.getValue());
        this.warnings = warnings;
    }

    /**
     * @param t
     */
    public DeviationAwareRuntimeException(Throwable t) {
        this(t, null, null);
    }

    /**
     * @param t
     * @param errorCode
     */
    public DeviationAwareRuntimeException(Throwable t, ErrorCode errorCode) {
        this(t, errorCode, null);
    }

    /**
     * @param t
     * @param errorCode
     * @param warnings
     */
    public DeviationAwareRuntimeException(Throwable t, ErrorCode errorCode, Collection<Deviation> warnings) {
        super(t);
        this.error = (errorCode == null ? null : new Deviation(errorCode.getValue()));
        this.warnings = warnings;
    }
}
