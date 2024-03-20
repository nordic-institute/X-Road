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

package org.niis.xroad.edc.extension.edr.callback;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.spi.callback.CallbackEventRemoteMessage;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.event.Event;
import org.eclipse.edc.spi.message.RemoteMessageDispatcher;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class LocalCallbackMessageDispatcherImpl implements RemoteMessageDispatcher {

    public static final String CALLBACK_EVENT_LOCAL = "callback-event-local";

    private final LocalCallbackRegistryImpl registry;

    @Override
    public String protocol() {
        return CALLBACK_EVENT_LOCAL;
    }

    @Override
    public <T, M extends RemoteMessage> CompletableFuture<StatusResult<T>> dispatch(Class<T> responseType, M message) {
        if (message instanceof CallbackEventRemoteMessage<?>) {
            var result = registry.handleMessage((CallbackEventRemoteMessage<? extends Event>) message);
            if (result.succeeded()) {
                return CompletableFuture.completedFuture(StatusResult.success(null));
            } else {
                return CompletableFuture.completedFuture(StatusResult.failure(ResponseStatus.FATAL_ERROR, result.getFailureDetail()));
            }
        }
        return CompletableFuture.failedFuture(
                new EdcException("Message type %s not supported".formatted(message.getClass().getSimpleName())));
    }

}
