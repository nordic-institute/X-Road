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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataRequest;
import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.NetworkInterface;
import java.util.List;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;

@Slf4j
public class OpMonitoringDataProcessor {
    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();

    private static final String NO_ADDRESS_FOUND = "No suitable IP address is bound to the network interface ";
    private static final String NO_INTERFACE_FOUND = "No non-loopback network interface found";

    private String ipAddress;

    String prepareMonitoringMessage(List<OpMonitoringData> dataToProcess) throws JsonProcessingException {
        StoreOpMonitoringDataRequest request = new StoreOpMonitoringDataRequest();

        for (OpMonitoringData data : dataToProcess) {
            request.addRecord(data.getData());
        }

        return OBJECT_WRITER.writeValueAsString(request);
    }

    String getIpAddress() {
        try {
            if (ipAddress == null) {
                NetworkInterface ni = list(getNetworkInterfaces()).stream()
                        .filter(OpMonitoringDataProcessor::isNonLoopback)
                        .findFirst()
                        .orElseThrow(() -> new Exception(NO_INTERFACE_FOUND));

                Exception addressNotFound = new Exception(NO_ADDRESS_FOUND + ni.getDisplayName());

                ipAddress = list(ni.getInetAddresses()).stream()
                        .filter(addr -> !addr.isLinkLocalAddress())
                        .findFirst()
                        .orElseThrow(() -> addressNotFound)
                        .getHostAddress();

                if (ipAddress == null) {
                    throw addressNotFound;
                }
            }

            return ipAddress;
        } catch (Exception e) {
            log.error("Cannot get IP address of a non-loopback network interface", e);

            return "0.0.0.0";
        }
    }

    @SneakyThrows
    private static boolean isNonLoopback(NetworkInterface ni) {
        return !ni.isLoopback() && ni.isUp();
    }
}
