/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.test;

import jakarta.annotation.Nonnull;
import org.niis.xroad.common.core.BuiltinServiceCodes;
import org.niis.xroad.proxy.core.dsp.DspAcquisition;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;

/**
 * Consumer-side DSP processor for tests that stub {@link #execute} per case. Create it with
 * {@code mock(StubbableDspRequestProcessor.class, CALLS_REAL_METHODS)}, so that {@link #bypassesDsp} applies the
 * metaservice rule of the production processor until a test stubs it. A {@code null} request, as passed by an
 * argument matcher while a test stubs {@link #bypassesDsp}, does not bypass DSP. A test stubs {@link #execute} with
 * {@code doReturn(...).when(processor).execute(...)}: the unstubbed method fails, and so does a
 * {@code when(processor.execute(...))} call.
 */
public abstract class StubbableDspRequestProcessor implements DspRequestProcessor {

    @Nonnull
    @Override
    public DspAcquisition execute(DspRequest request) {
        throw new IllegalStateException("DspRequestProcessor.execute is not stubbed");
    }

    @Override
    public boolean bypassesDsp(DspRequest request) {
        var serviceId = request == null ? null : request.serviceId();
        return serviceId != null && BuiltinServiceCodes.METASERVICES.contains(serviceId.getServiceCode());
    }
}
