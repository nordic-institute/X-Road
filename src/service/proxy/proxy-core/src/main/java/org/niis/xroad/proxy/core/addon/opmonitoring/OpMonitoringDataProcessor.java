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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.StoreOpMonitoringDataRequest;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.net.NetworkInterface.networkInterfaces;

@Slf4j
public class OpMonitoringDataProcessor {
    private static final String NO_ADDRESS_FOUND = "No suitable IP address is bound to network interfaces";

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();
    private static final Duration IP_RESOLUTION_CACHE_DURATION = Duration.ofMinutes(10);

    private String ipAddress;
    private Instant ipAddressLastResolutionAt;

    String prepareMonitoringMessage(List<OpMonitoringData> dataToProcess) throws JsonProcessingException {
        StoreOpMonitoringDataRequest request = new StoreOpMonitoringDataRequest();

        for (OpMonitoringData data : dataToProcess) {
            request.addRecord(data.getData());
        }

        return OBJECT_WRITER.writeValueAsString(request);
    }

    String getIpAddress() {
        try {
            if (ipAddress != null && ipAddressLastResolutionAt != null
                    && !ipAddressLastResolutionAt.isBefore(Instant.now().minus(IP_RESOLUTION_CACHE_DURATION))) {
                return ipAddress;
            }

            ipAddress = networkInterfaces()
                    .filter(OpMonitoringDataProcessor::isNonLoopback)
                    .filter(OpMonitoringDataProcessor::hasAnyUsableAddress)
                    .min(OpMonitoringDataProcessor::compareNetworkInterfaces)
                    .stream()
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(OpMonitoringDataProcessor::isUsableAddress)
                    .min(OpMonitoringDataProcessor::compareInetAddresses)
                    .map(InetAddress::getHostAddress)
                    .orElseThrow(OpMonitoringDataProcessor::addressNotFoundException);

            ipAddressLastResolutionAt = Instant.now();

            return ipAddress;
        } catch (Exception e) {
            log.error("Cannot get IP address of a non-loopback network interface", e);
            // keep the failed resolution cached for resolution cache duration as well.
            ipAddressLastResolutionAt = Instant.now();
            ipAddress = "0.0.0.0";
            return ipAddress;
        }
    }

    private static boolean isNonLoopback(NetworkInterface ni) {
        try {
            return !ni.isLoopback() && ni.isUp();
        } catch (SocketException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private static boolean hasAnyUsableAddress(NetworkInterface ni) {
        return ni.inetAddresses().anyMatch(OpMonitoringDataProcessor::isUsableAddress);
    }

    private static int compareNetworkInterfaces(NetworkInterface ni1, NetworkInterface ni2) {
        return Integer.compare(ni1.getIndex(), ni2.getIndex());
    }

    private static boolean isUsableAddress(InetAddress addr) {
        return !addr.isLoopbackAddress()
                && !addr.isLinkLocalAddress()
                && addr.getHostAddress() != null;
    }

    private static int compareInetAddresses(InetAddress a1, InetAddress a2) {
        // prefer IPv4 addresses
        return Boolean.compare(a1 instanceof Inet6Address, a2 instanceof Inet6Address);
    }

    private static XrdRuntimeException addressNotFoundException() {
        return XrdRuntimeException.systemInternalError(NO_ADDRESS_FOUND);
    }
}
