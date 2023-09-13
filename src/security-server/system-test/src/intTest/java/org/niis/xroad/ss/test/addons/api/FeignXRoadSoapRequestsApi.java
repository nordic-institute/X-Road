package org.niis.xroad.ss.test.addons.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "securityServerMetricsRequestsApi")
public interface FeignXRoadSoapRequestsApi {

    @PostMapping
    ResponseEntity<String> getSecurityServerMetrics(byte[] body);

}
