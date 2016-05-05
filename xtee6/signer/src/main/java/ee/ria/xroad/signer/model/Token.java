/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.signer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;

import org.apache.commons.lang3.ObjectUtils;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;

/**
 * Model object representing a token.
 */
@Data
public final class Token {

    /** The module type as configured in Signer's module configuration. */
    private final String type;

    /** The token id. */
    private final String id;

    /** The module id. */
    private String moduleId;

    /** The name to display in UI. */
    private String friendlyName;

    /** True, if token is read-only */
    private boolean readOnly;

    /** True, if token is available (in module) */
    private boolean available;

    /** True, if password is inserted */
    private boolean active;

    /** The token serial number (optional). */
    private String serialNumber;

    /** The token label (optional). */
    private String label;

    /** The pin index to further specify the token (optional). */
    private int slotIndex;

    /** Whether batch signing should be enabled for this token. */
    private boolean batchSigningEnabled = false;

    /** Holds the currect status of the token. */
    private TokenStatusInfo status = TokenStatusInfo.OK;

    /** Contains the the keys of this token. */
    private final List<Key> keys = new ArrayList<>();

    /** Contains label-value pairs of information about token. */
    private final Map<String, String> tokenInfo = new LinkedHashMap<>();

    /**
     * Adds a key to this token.
     * @param key the key to add
     */
    public void addKey(Key key) {
        keys.add(key);
    }

    /**
     * Sets the token info.
     * @param info the token info
     */
    public void setInfo(Map<String, String> info) {
        this.tokenInfo.clear();
        this.tokenInfo.putAll(info);
    }

    /**
     * Converts this object to value object.
     * @return the value object
     */
    public TokenInfo toDTO() {
        return new TokenInfo(type, friendlyName, id, readOnly, available,
                active, serialNumber, label, slotIndex, status,
                Collections.unmodifiableList(getKeysAsDTOs()),
                Collections.unmodifiableMap(tokenInfo));
    }

    /**
     * @param token a token
     * @return true, if this token matches another token
     */
    public boolean matches(TokenType token) {
        if (token == null) {
            return false;
        }

        if (id.equals(token.getId())) {
            return true;
        }

        return token.getModuleType() != null
                && token.getModuleType().equals(type)
                && ObjectUtils.equals(token.getSerialNumber(), serialNumber)
                && ObjectUtils.equals(token.getLabel(), label)
                && ObjectUtils.equals(token.getSlotIndex(), slotIndex);
    }

    private List<KeyInfo> getKeysAsDTOs() {
        return keys.stream().map(k -> k.toDTO()).collect(Collectors.toList());
    }
}
