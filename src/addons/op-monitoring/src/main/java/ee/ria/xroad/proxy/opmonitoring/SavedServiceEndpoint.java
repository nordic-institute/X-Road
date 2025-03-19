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

import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.StringUtil;

import java.util.Comparator;

@Slf4j
@RequiredArgsConstructor
class SavedServiceEndpoint {

    private final ServerConfProvider serverConfProvider;

    String getPathIfExists(OpMonitoringData data) {
        if (data.isProducer() && !StringUtil.isEmpty(data.getRestPath())) {
            try {
                var endpointTypes = serverConfProvider.getServiceEndpoints(data.getServiceId()).stream()
                        .map(v -> new EndpointType(data.getServiceId().getServiceCode(), v.getMethod(), v.getPath(), false))
                        .filter(ep -> ep.matches(data.getRestMethod(), data.getRestPath()))
                        // sort by path and method before finding first
                        // because [* /pets/*] and [GET /pets/first] also matches, but we want [GET /pets/first] is returned
                        .min(Comparator.comparing(EndpointType::getPath).reversed()
                                .thenComparing(EndpointType::getMethod, Comparator.reverseOrder()));
                // the path is logged only if it can be resolved to an endpoint described for the service
                return endpointTypes.map(EndpointType::getPath).orElse(null);
            } catch (Exception e) {
                log.error("Cannot query saved endpoint for: {}", data.getRestPath(), e);
            }
        }
        return null;
    }
}
