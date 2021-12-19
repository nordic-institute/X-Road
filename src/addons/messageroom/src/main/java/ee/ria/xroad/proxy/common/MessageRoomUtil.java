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
package ee.ria.xroad.proxy.common;

import ee.ria.xroad.common.conf.serverconf.model.MessageRoomSubscriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.UriUtils.uriSegmentPercentDecode;

/**
 * Message rooms utility class.
 */
public final class MessageRoomUtil {

    private MessageRoomUtil() { }

    public static boolean isValidPublisher(ClientId clientId) {
        return MessageRoomProperties.getEnabledPublisherSubsystems().stream()
                .filter(s -> s.equals(clientId))
                .findFirst()
                .isPresent();
    }

    public static Optional<MessageRoomSubscriptionType> findSubscription(
            List<MessageRoomSubscriptionType> subscriptions, ServiceId xRoadServiceId) {
        return subscriptions.stream()
                .filter(s -> s.getSubscriberServiceId().equals(xRoadServiceId.toShortString()))
                .findFirst();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public static ServiceId decodeServiceId(String value) {
        final String[] parts = value.split("/", 6);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid Service Id");
        }
        return ServiceId.create(
                uriSegmentPercentDecode(parts[0]),
                uriSegmentPercentDecode(parts[1]),
                uriSegmentPercentDecode(parts[2]),
                uriSegmentPercentDecode(parts[3]),
                uriSegmentPercentDecode(parts[4])
        );
    }
}
