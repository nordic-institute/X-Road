/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.edr.dto;

import lombok.Getter;

import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.niis.xroad.edc.spi.XrdConstants.XRD_NAMESPACE;

public class NegotiateAssetRequestDto {

    public static final String XRD_EDR_REQUEST_SIMPLE_DTO_TYPE = "NegotiateAssetRequestDto";
    public static final String XRD_EDR_REQUEST_DTO_TYPE = XRD_NAMESPACE + "NegotiateAssetRequestDto";
    public static final String XRD_EDR_REQUEST_DTO_CLIENT_ID = XRD_NAMESPACE + "clientId";
    public static final String XRD_EDR_REQUEST_ASSET_ID = EDC_NAMESPACE + "assetId";
    public static final String XRD_EDR_REQUEST_DTO_COUNTERPARTY_ADDRESS = EDC_NAMESPACE + "counterPartyAddress";
    public static final String XRD_EDR_REQUEST_DTO_COUNTERPARTY_ID = EDC_NAMESPACE + "counterPartyId";

    @Getter
    private String clientId;
    @Getter
    private String assetId;
    @Getter
    private String counterPartyId;
    @Getter
    private String counterPartyAddress;

    public static final class Builder {
        private final NegotiateAssetRequestDto dto;

        private Builder() {
            dto = new NegotiateAssetRequestDto();
        }

        public Builder clientId(String clientId) {
            dto.clientId = clientId;
            return this;
        }

        public Builder assetId(String assetId) {
            dto.assetId = assetId;
            return this;
        }

        public Builder counterPartyId(String counterPartyId) {
            dto.counterPartyId = counterPartyId;
            return this;
        }

        public Builder counterPartyAddress(String counterPartyAddress) {
            dto.counterPartyAddress = counterPartyAddress;
            return this;
        }

        public NegotiateAssetRequestDto build() {
            return dto;
        }

        public static Builder newInstance() {
            return new Builder();
        }

    }

}
