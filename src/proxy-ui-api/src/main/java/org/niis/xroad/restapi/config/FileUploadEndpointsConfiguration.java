package org.niis.xroad.restapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * Configuration which knows the endpoints that are used for file uploads.
 * These endpoints will have a larger request size limit than others.
 */
@ConfigurationProperties(prefix = "file-upload-endpoints")
@Configuration
public class FileUploadEndpointsConfiguration {

    private List<EndpointDefinition> endpointDefinitions;

    public FileUploadEndpointsConfiguration(List<EndpointDefinition> endpointDefinitions) {
        this.endpointDefinitions = endpointDefinitions;
    }

    public FileUploadEndpointsConfiguration() {

    }

    public List<EndpointDefinition> getEndpointDefinitions() {
        return endpointDefinitions;
    }

    public void setEndpointDefinitions(List<EndpointDefinition> endpointDefinitions) {
        this.endpointDefinitions = endpointDefinitions;
    }

    public static class EndpointDefinition {

        private HttpMethod httpMethod;
        private String pathInfoEnding;

        public EndpointDefinition(HttpMethod httpMethod, String pathInfoEnding) {
            this.httpMethod = httpMethod;
            this.pathInfoEnding = pathInfoEnding;
        }

        public EndpointDefinition() {
        }

        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getPathInfoEnding() {
            return pathInfoEnding;
        }

        public void setPathInfoEnding(String pathInfoEnding) {
            this.pathInfoEnding = pathInfoEnding;
        }
    }

}