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
package org.niis.xroad.common.core.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SpanAttributesTest {

    @RegisterExtension
    static final OpenTelemetryExtension OTEL = OpenTelemetryExtension.create();

    @Test
    void singleSetAppearsOnSpan() {
        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, "asset-1")
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.ASSET_ID)).isEqualTo("asset-1");
    }

    @Test
    void chainedMixedTypeSetsAllAppear() {
        var longKey = AttributeKey.longKey("xroad.test.long");
        var boolKey = AttributeKey.booleanKey("xroad.test.bool");

        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, "asset-2")
                .set(longKey, 42L)
                .set(boolKey, true)
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.ASSET_ID)).isEqualTo("asset-2");
        assertThat(attrs.get(longKey)).isEqualTo(42L);
        assertThat(attrs.get(boolKey)).isTrue();
    }

    @Test
    void nullValueIsSkipped() {
        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, (String) null)
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.ASSET_ID)).isNull();
    }

    @Test
    void supplierReturningValueSetsAttribute() {
        Supplier<String> supplier = () -> "supplier-val";
        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, supplier)
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.ASSET_ID)).isEqualTo("supplier-val");
    }

    @Test
    void supplierReturningNullIsSkipped() {
        Supplier<String> nullSupplier = () -> null;
        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, nullSupplier)
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.ASSET_ID)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonRecordingSpanSkipsAllAndNeverInvokesSupplier() {
        Supplier<String> supplier = Mockito.mock(Supplier.class);
        var nonRecording = Span.getInvalid();

        SpanAttributes.on(nonRecording)
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, "ignored")
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ID, supplier)
                .apply();

        Mockito.verify(supplier, Mockito.never()).get();
        assertThat(OTEL.getSpans()).isEmpty();
    }

    @Test
    void onCurrentWithNoActiveSpanIsNoOp() {
        SpanAttributes.onCurrent()
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, "orphan")
                .apply();
        assertThat(OTEL.getSpans()).isEmpty();
    }

    @Test
    void explicitOnAttachesToGivenSpan() {
        var span = startSpan();
        SpanAttributes.on(span)
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ID, "cp-1")
                .apply();
        span.end();

        var attrs = OTEL.getSpans().get(0).getAttributes();
        assertThat(attrs.get(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ID)).isEqualTo("cp-1");
    }

    private Span startSpan() {
        return OTEL.getOpenTelemetry().getTracer("test").spanBuilder("s").startSpan();
    }
}
