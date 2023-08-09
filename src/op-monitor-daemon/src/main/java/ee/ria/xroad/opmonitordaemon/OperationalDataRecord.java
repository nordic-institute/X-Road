/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.opmonitordaemon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Min;

import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType;


/**
 * Represents a single database record of operational monitoring data.
 * Each such record describes a single X-Road request and the metadata
 * related to storing monitoring data.
 */
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class OperationalDataRecord {

    // The unique ID of the record in the database.
    @Getter
    @Setter
    @JsonIgnore
    private Long id;

    // The Unix timestamp (in seconds) of receiving the data at the monitoring
    // daemon. This timestamp is used for limiting the set of records returned.
    @Getter
    @Setter
    @Min(0)
    private Long monitoringDataTs;

    // The following fields correspond to the schema defined in the file
    // src/op-monitor-daemon/src/main/resources/store_operational_data_request_schema.yaml.
    // Refer to the schema for detailed documentation.

    @Getter
    @Setter
    private String securityServerInternalIp;

    @Getter
    @JsonDeserialize(using = SecurityServerTypeTypeAdapter.class)
    private String securityServerType;

    /**
     * set security server type
     *
     * @throws IllegalArgumentException if serverType is not valid
     */
    public void setSecurityServerType(String serverType) {
        if (SecurityServerType.fromString(serverType) == null) {
            throw new IllegalArgumentException("Invalid value of securityServerType");
        }
        securityServerType = serverType;
    }

    @Getter
    @Setter
    @Min(0)
    // The Unix timestamp in milliseconds.
    private Long requestInTs;

    @Getter
    @Setter
    @Min(0)
    // The Unix timestamp in milliseconds.
    private Long requestOutTs;

    @Getter
    @Setter
    @Min(0)
    // The Unix timestamp in milliseconds.
    private Long responseInTs;

    @Getter
    @Setter
    @Min(0)
    // The Unix timestamp in milliseconds.
    private Long responseOutTs;

    @Getter
    @Setter
    private String clientXRoadInstance;

    @Getter
    @Setter
    private String clientMemberClass;

    @Getter
    @Setter
    private String clientMemberCode;

    @Getter
    @Setter
    private String clientSubsystemCode;

    @Getter
    @Setter
    private String serviceXRoadInstance;

    @Getter
    @Setter
    private String serviceMemberClass;

    @Getter
    @Setter
    private String serviceMemberCode;

    @Getter
    @Setter
    private String serviceSubsystemCode;

    @Getter
    @Setter
    private String serviceCode;

    @Getter
    @Setter
    private String serviceVersion;

    @Getter
    @Setter
    private String representedPartyClass;

    @Getter
    @Setter
    private String representedPartyCode;

    @Getter
    @Setter
    private String messageId;

    @Getter
    @Setter
    private String messageUserId;

    @Getter
    @Setter
    private String messageIssue;

    @Getter
    @Setter
    private String messageProtocolVersion;

    @Getter
    @Setter
    private String clientSecurityServerAddress;

    @Getter
    @Setter
    private String serviceSecurityServerAddress;

    @Getter
    @Setter
    private Long requestSize;

    @Getter
    @Setter
    private Long responseSize;

    @Getter
    @Setter
    private Long requestMimeSize;

    @Getter
    @Setter
    private Integer requestAttachmentCount;

    @Getter
    @Setter
    private Long responseMimeSize;

    @Getter
    @Setter
    private Integer responseAttachmentCount;

    @Getter
    @Setter
    private Boolean succeeded;

    @Getter
    @Setter
    private String faultCode;

    @Getter
    @Setter
    private String faultString;

    @Getter
    @Setter
    @JsonProperty("xRequestId") // Jackson does not deserialize xRequestId without explicitly telling this
    private String xRequestId;

    @Getter
    @Setter
    private Integer statusCode;

    @Getter
    @Setter
    private String serviceType;
}
