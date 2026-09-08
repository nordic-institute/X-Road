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
package org.niis.xroad.common.core.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

import java.util.function.Supplier;

/**
 * Fluent recorder for attaching typed attributes to an OpenTelemetry span.
 * Silently becomes a no-op when the target span is not recording.
 */
public final class SpanAttributes {

    private SpanAttributes() {
        throw new AssertionError();
    }

    /**
     * Returns a recorder targeting the currently active span.
     */
    public static Recorder onCurrent() {
        return on(Span.current());
    }

    /**
     * Returns a recorder targeting the given span.
     */
    public static Recorder on(Span span) {
        return span.isRecording() ? new ActiveRecorder(span) : NoOpRecorder.INSTANCE;
    }

    /**
     * Fluent API for accumulating and applying span attributes.
     */
    public sealed interface Recorder permits ActiveRecorder, NoOpRecorder {

        /**
         * Sets an attribute on the span; silently skips if value is null.
         */
        <T> Recorder set(AttributeKey<T> key, T value);

        /**
         * Sets an attribute using a supplier; skips if supplier or its result is null.
         * The supplier is not invoked when the span is not recording.
         */
        <T> Recorder set(AttributeKey<T> key, Supplier<T> supplier);

        /**
         * Reserved for future use; currently a no-op.
         */
        @SuppressWarnings("checkstyle:EmptyBlock")
        default void apply() {
        }
    }

    record ActiveRecorder(Span span) implements Recorder {
        @Override
        public <T> Recorder set(AttributeKey<T> key, T value) {
            if (value != null) {
                span.setAttribute(key, value);
            }
            return this;
        }

        @Override
        public <T> Recorder set(AttributeKey<T> key, Supplier<T> supplier) {
            if (supplier != null) {
                var value = supplier.get();
                if (value != null) {
                    span.setAttribute(key, value);
                }
            }
            return this;
        }
    }

    enum NoOpRecorder implements Recorder {
        INSTANCE;

        @Override
        public <T> Recorder set(AttributeKey<T> key, T value) {
            return this;
        }

        @Override
        public <T> Recorder set(AttributeKey<T> key, Supplier<T> supplier) {
            return this;
        }
    }
}
