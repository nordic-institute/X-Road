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
package org.niis.xroad.restapi.exceptions;

import lombok.Getter;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * RuntimeException that (possibly) carries error code.
 * Root of all deviation aware runtimeexceptions
 */
@Getter
public class DeviationAwareRuntimeException extends RuntimeException implements DeviationAware {

    private final ErrorDeviation errorDeviation;
    private final Collection<WarningDeviation> warningDeviations;

    public DeviationAwareRuntimeException(String msg,
                                          Throwable t,
                                          ErrorDeviation errorDeviation,
                                          Collection<WarningDeviation> warningDeviations) {
        super(msg, t);
        this.errorDeviation = errorDeviation;
        this.warningDeviations = warningDeviations;
    }

    public DeviationAwareRuntimeException(Throwable t,
                                          ErrorDeviation errorDeviation,
                                          Collection<WarningDeviation> warningDeviations) {
        this(errorDeviation.toString(), t, errorDeviation, warningDeviations);
    }

    public DeviationAwareRuntimeException(String msg,
                                          Throwable t,
                                          ErrorDeviation errorDeviation) {
        this(msg, t, errorDeviation, List.of());
    }

    public DeviationAwareRuntimeException(Throwable t,
                                          ErrorDeviation errorDeviation) {
        this(errorDeviation.toString(), t, errorDeviation, List.of());
    }

    public DeviationAwareRuntimeException(String msg,
                                          ErrorDeviation errorDeviation,
                                          Collection<WarningDeviation> warningDeviations) {
        super(msg);
        this.errorDeviation = errorDeviation;
        this.warningDeviations = warningDeviations;
    }

    public DeviationAwareRuntimeException(ErrorDeviation errorDeviation,
                                          Collection<WarningDeviation> warningDeviations) {
        this(errorDeviation.toString(), errorDeviation, warningDeviations);
    }

    public DeviationAwareRuntimeException(String msg,
                                          ErrorDeviation errorDeviation) {
        this(msg, errorDeviation, List.of());
    }

    public DeviationAwareRuntimeException(ErrorDeviation errorDeviation) {
        this(errorDeviation.toString(), errorDeviation, List.of());
    }


    public <DE extends Exception & DeviationAware> DeviationAwareRuntimeException(@NonNull final DE exception) {
        this(exception.getMessage(), exception);
    }

    public <DE extends Exception & DeviationAware> DeviationAwareRuntimeException(String message,
                                                                                  @NonNull final DE exception) {
        this(message, exception, exception.getErrorDeviation(), exception.getWarningDeviations());
    }

}
