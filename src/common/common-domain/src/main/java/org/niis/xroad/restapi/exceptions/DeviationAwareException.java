/**
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
package org.niis.xroad.restapi.exceptions;

import java.util.Collection;

/**
 * Checked exception that (possibly) carries error code.
 * Root of all checked deviation aware exceptions
 */
public class DeviationAwareException extends Exception implements DeviationAware {

    private final ErrorDeviation errorDeviation;
    private final Collection<WarningDeviation> warningDeviations;

    @Override
    public ErrorDeviation getErrorDeviation() {
        return errorDeviation;
    }

    @Override
    public Collection<WarningDeviation> getWarningDeviations() {
        return warningDeviations;
    }

    /**
     * @param msg
     * @param errorDeviation
     */
    public DeviationAwareException(String msg, ErrorDeviation errorDeviation) {
        super(msg);
        this.errorDeviation = errorDeviation;
        this.warningDeviations = null;
    }

    /**
     * @param msg
     * @param t
     * @param errorDeviation
     */
    public DeviationAwareException(String msg, Throwable t, ErrorDeviation errorDeviation) {
        this(msg, t, errorDeviation, null);
    }

    /**
     * @param msg
     * @param t
     * @param errorDeviation
     * @param warningDeviations
     */
    public DeviationAwareException(String msg, Throwable t, ErrorDeviation errorDeviation,
                                          Collection<WarningDeviation> warningDeviations) {
        super(msg, t);
        this.errorDeviation = errorDeviation;
        this.warningDeviations = warningDeviations;
    }

    /**
     * @param errorDeviation
     */
    public DeviationAwareException(ErrorDeviation errorDeviation) {
        this(errorDeviation, null);
    }

    /**
     * @param errorDeviation
     * @param warningDeviations
     */
    public DeviationAwareException(ErrorDeviation errorDeviation, Collection<WarningDeviation> warningDeviations) {
        this.errorDeviation = errorDeviation;
        this.warningDeviations = warningDeviations;
    }

    /**
     * @param t
     * @param errorDeviation
     */
    public DeviationAwareException(Throwable t, ErrorDeviation errorDeviation) {
        this(t, errorDeviation, null);
    }

    /**
     * @param t
     * @param errorDeviation
     * @param warningDeviations
     */
    public DeviationAwareException(Throwable t, ErrorDeviation errorDeviation,
            Collection<WarningDeviation> warningDeviations) {
        super(t);
        this.errorDeviation = errorDeviation;
        this.warningDeviations = warningDeviations;
    }
}
