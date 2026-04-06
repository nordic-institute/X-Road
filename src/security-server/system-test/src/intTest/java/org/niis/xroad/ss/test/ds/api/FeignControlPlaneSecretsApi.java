package org.niis.xroad.ss.test.ds.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@FeignClient(name = "controlPlaneSecretsApi")
public interface FeignControlPlaneSecretsApi {

    @PostMapping(value = "",
            produces = {"application/json"}, consumes = {"application/json"})
    ResponseEntity<Map<String, Object>> createSecret(@RequestHeader(AUTHORIZATION) String authorization,
                                                                 @RequestBody String body);

}
