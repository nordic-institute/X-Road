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

package org.eclipse.edc.iam.verifiablecredentials.revocation.bitstring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.iam.verifiablecredentials.revocation.BaseRevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.BitString;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.StatusMessage;
import org.eclipse.edc.spi.result.Result;

import java.util.Base64;

import static org.eclipse.edc.spi.result.Result.success;

/**
 * StatusList revocation service implementing the <a href="https://www.w3.org/TR/vc-bitstring-status-list/">BitStringStatusList</a>
 * specification.
 */
public class BitstringStatusListRevocationService extends BaseRevocationListService<BitstringStatusListCredential, BitstringStatusListStatus> {

    public BitstringStatusListRevocationService(ObjectMapper mapper, long cacheValidity) {
        super(mapper, cacheValidity, BitstringStatusListCredential.class);
    }

    @Override
    protected Result<Void> preliminaryChecks(BitstringStatusListStatus credentialStatus) {
        var statusSize = credentialStatus.getStatusSize();
        if (statusSize != 1) { //todo: support more statusSize entries in the future
            return Result.failure("Unsupported statusSize: currently only statusSize = 1 is supported. The VC contained statusSize = %d".formatted(statusSize));
        }
        return success();
    }

    @Override
    protected Result<String> getStatusEntryValue(BitstringStatusListStatus credentialStatus) {
        var bitStringCredential = getCredential(credentialStatus.getStatusListCredential());

        var bitString = bitStringCredential.encodedList();
        var decoder = Base64.getDecoder();
        if (bitString.charAt(0) == 'u') { // base64 url
            decoder = Base64.getUrlDecoder();
            bitString = bitString.substring(1); //chop off header
        } else if (bitString.charAt(0) == 'z') { //base58btc
            return Result.failure("The encoded list is using the Base58-BTC alphabet ('z' multibase header), which is not supported.");
        }

        var compressedBitstring = BitString.Parser.newInstance().decoder(decoder).parse(bitString);
        if (compressedBitstring.failed()) {
            return compressedBitstring.mapEmpty();
        }
        var bitstring = compressedBitstring.getContent();

        // todo: check that encodedList / statusSize == minimumLength (defaults to 131_072 = encodedList minimum length in bits),
        // otherwise raise error
        // todo: how to determine minimumLength? via config?

        var statusFlag = bitstring.get(credentialStatus.getStatusListIndex());

        var statusPurpose = credentialStatus.getStatusListPurpose();
        // if the purpose is "message", we need to check the statusMessage object for the actual string
        if (statusPurpose.equalsIgnoreCase("message")) {
            var statusString = statusFlag ? "0x1" : "0x0"; //todo: change this when statusSize > 1 is supported
            statusPurpose = credentialStatus.getStatusMessage().stream().filter(sm -> sm.status().equals(statusString)).map(StatusMessage::message).findAny().orElse(statusPurpose);

            return success(statusPurpose);
        } else if (statusFlag) {
            // currently, this supports only a statusSize of 1
            return success(statusPurpose);
        }

        return success(null);
    }

    @Override
    protected Result<Void> validateStatusPurpose(BitstringStatusListStatus credentialStatus) {
        var statusPurpose = credentialStatus.getStatusListPurpose();

        var credentialUrl = credentialStatus.getStatusListCredential();
        var statusListCredential = getCredential(credentialUrl);
        var credentialStatusPurpose = statusListCredential.statusPurpose();

        if (!statusPurpose.equalsIgnoreCase(credentialStatusPurpose)) {
            return Result.failure("Credential's statusPurpose value must match the statusPurpose of the Bitstring Credential: '%s' != '%s'".formatted(statusPurpose, credentialStatusPurpose));
        }

        return success();
    }

    @Override
    protected int getStatusIndex(BitstringStatusListStatus credentialStatus) {
        return credentialStatus.getStatusListIndex();
    }

    @Override
    protected BitstringStatusListStatus getCredentialStatus(CredentialStatus credentialStatus) {
        return BitstringStatusListStatus.from(credentialStatus);
    }

}
