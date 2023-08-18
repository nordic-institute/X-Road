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

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Token info DTO.
 */
@ToString
@RequiredArgsConstructor
public final class TokenInfo implements Serializable {

    public static final String SOFTWARE_MODULE_TYPE = "softToken";

    private final TokenInfoProto message;

    public String getType() {
        return message.getType();
    }

    public String getFriendlyName() {
        return message.getFriendlyName();
    }

    public String getId() {
        return message.getId();
    }

    public boolean isReadOnly() {
        return message.getReadOnly();
    }

    public boolean isAvailable() {
        return message.getAvailable();
    }

    public boolean isActive() {
        return message.getActive();
    }

    public String getSerialNumber() {
        return message.getSerialNumber();
    }

    public String getLabel() {
        return message.getLabel();
    }

    public int getSlotIndex() {
        return message.getSlotIndex();
    }

    public TokenStatusInfo getStatus() {
        return message.getStatus();
    }

    public List<KeyInfo> getKeyInfo() {
        return message.getKeyInfoList().stream()
                .map(KeyInfo::new)
                .collect(Collectors.toList());
    }

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
