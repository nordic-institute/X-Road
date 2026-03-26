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
package org.niis.xroad.confproxy.test.container;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.niis.xroad.confproxy.test.ConfProxyIntTestContainerSetup;
import org.niis.xroad.test.framework.core.feign.FeignFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Configuration
public class ConfProxyIntTestConfiguration {

    @Bean
    public FeignConfProxyApi confProxyApi(ConfProxyIntTestContainerSetup containerSetup,
                                         FeignFactory feignFactory) {
        var container = containerSetup.getContainerMapping(
                ConfProxyIntTestContainerSetup.CONFIGURATION_PROXY,
                ConfProxyIntTestContainerSetup.Port.HTTP);
        return feignFactory.createClient(FeignConfProxyApi.class,
                "http://%s:%d/api".formatted(container.host(), container.port()), false);
    }

    public interface FeignConfProxyApi {
        @GetMapping("/v1/instances")
        InstancesResponse getInstances(@RequestHeader("Authorization") String authorization);

        @GetMapping("/v1/instances/{name}")
        InstanceResponse getInstance(@PathVariable("name") String name,
                                     @RequestHeader("Authorization") String authorization);

        @PostMapping("/v1/instances/{name}/signing-key")
        InstanceResponse addSigningKey(@PathVariable("name") String name,
                                       @RequestBody AddSigningKeyRequest request,
                                       @RequestHeader("Authorization") String authorization);

        @PatchMapping("/v1/instances/{name}/signing-key/{keyId}/set-active")
        InstanceResponse setActiveSigningKey(@PathVariable("name") String name,
                                             @PathVariable("keyId") String keyId,
                                             @RequestHeader("Authorization") String authorization);

        @DeleteMapping("/v1/instances/{name}/signing-key/{keyId}")
        InstanceResponse removeSigningKey(@PathVariable("name") String name,
                                          @PathVariable("keyId") String keyId,
                                          @RequestHeader("Authorization") String authorization);

        @GetMapping("/v1/instances/{name}/anchor")
        ResponseEntity<Resource> generateAnchor(@PathVariable("name") String name,
                                                @RequestHeader("Authorization") String authorization);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddSigningKeyRequest(
            @JsonProperty("key_id")
            String keyId,
            @JsonProperty("token_id")
            String tokenId,
            @JsonProperty("key_algorithm")
            String keyAlgorithm,
            @JsonProperty("as_active")
            boolean asActive
    ) {
    }

    public record InstancesResponse(
            @JsonProperty("available_instances")
            Set<String> availableInstances
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstanceResponse(
            String name,
            @JsonProperty("configuration_path")
            String configurationPath,
            @JsonProperty("validity_interval")
            int validityInterval,
            @JsonProperty("signing_keys_and_certs")
            List<KeyCert> signingKeysAndCerts,
            boolean configured,
            Anchor anchor,
            @JsonProperty("anchor_error")
            String anchorError
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyCert(
            boolean active,
            String key,
            String cert
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anchor(
            @JsonProperty("instance_identifier")
            String instanceIdentifier,
            @JsonProperty("generated_at")
            Instant generatedAt,
            String hash
    ) {
    }
}
