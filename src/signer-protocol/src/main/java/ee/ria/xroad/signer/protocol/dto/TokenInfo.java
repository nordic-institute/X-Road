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
package ee.ria.xroad.signer.protocol.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.ToString;
import lombok.Value;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Token info DTO.
 */
@Value
@ToString(onlyExplicitlyIncluded = true)
public class TokenInfo implements Serializable {

    public static final String SOFTWARE_MODULE_TYPE = "softToken";

    @JsonIgnore
    TokenInfoProto message;

    @ToString.Include
    public String getType() {
        return message.getType();
    }

    @ToString.Include
    public String getFriendlyName() {
        if (message.hasFriendlyName()) {
            return message.getFriendlyName();
        }
        return null;
    }

    @ToString.Include
    public String getId() {
        return message.getId();
    }

    @ToString.Include
    public boolean isReadOnly() {
        return message.getReadOnly();
    }

    @ToString.Include
    public boolean isAvailable() {
        return message.getAvailable();
    }

    @ToString.Include
    public boolean isActive() {
        return message.getActive();
    }

    @ToString.Include
    public String getSerialNumber() {
        if (message.hasSerialNumber()) {
            return message.getSerialNumber();
        }
        return null;
    }

    @ToString.Include
    public String getLabel() {
        if (message.hasLabel()) {
            return message.getLabel();
        }
        return null;
    }

    @ToString.Include
    public int getSlotIndex() {
        return message.getSlotIndex();
    }

    @ToString.Include
    public TokenStatusInfo getStatus() {
        var status = message.getStatus();
        return status != TokenStatusInfo.TOKEN_STATUS_UNSPECIFIED ? status : null;
    }

    @ToString.Include
    public List<KeyInfo> getKeyInfo() {
        return message.getKeyInfoList().stream()
                .map(KeyInfo::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @ToString.Include
    public Map<String, String> getTokenInfo() {
        return message.getTokenInfoMap();
    }

    public TokenInfoProto asMessage() {
        return message;
    }

    /**
     * Logic to determine if a token is saved to configuration.
     * True if there is at least one key which is saved to configuration
     */
    public boolean isSavedToConfiguration() {
        return getKeyInfo().stream()
                .anyMatch(KeyInfo::isSavedToConfiguration);
    }
}
