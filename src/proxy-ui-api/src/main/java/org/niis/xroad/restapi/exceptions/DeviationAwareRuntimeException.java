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
 * RuntimeException that (possibly) carries error code.
 * Root of all deviation aware exceptions
 */
public class DeviationAwareRuntimeException extends RuntimeException implements DeviationAware {

    private final Error error;
    private final Collection<Warning> warnings;

    @Override
    public Error getError() {
        return error;
    }

    @Override
    public Collection<Warning> getWarnings() {
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
     * @param error
     */
    public DeviationAwareRuntimeException(String msg, Error error) {
        super(msg);
        this.error = error;
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
     * @param error
     */
    public DeviationAwareRuntimeException(String msg, Throwable t, Error error) {
        this(msg, t, error, null);
    }

    /**
     * @param msg
     * @param t
     * @param error
     * @param warnings
     */
    public DeviationAwareRuntimeException(String msg, Throwable t, Error error,
                                          Collection<Warning> warnings) {
        super(msg, t);
        this.error = error;
        this.warnings = warnings;
    }

    /**
     * @param error
     */
    public DeviationAwareRuntimeException(Error error) {
        this(error, null);
    }

    /**
     * @param error
     * @param warnings
     */
    public DeviationAwareRuntimeException(Error error, Collection<Warning> warnings) {
        this.error = error;
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
     * @param error
     */
    public DeviationAwareRuntimeException(Throwable t, Error error) {
        this(t, error, null);
    }

    /**
     * @param t
     * @param error
     * @param warnings
     */
    public DeviationAwareRuntimeException(Throwable t, Error error, Collection<Warning> warnings) {
        super(t);
        this.error = error;
        this.warnings = warnings;
    }
}
