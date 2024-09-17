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

package org.eclipse.edc.iam.verifiablecredentials.revocation.statuslist2021;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.iam.verifiablecredentials.revocation.BaseRevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.BitString;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.statuslist2021.StatusList2021Credential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.statuslist2021.StatusList2021Status;
import org.eclipse.edc.spi.result.Result;

import static org.eclipse.edc.spi.result.Result.success;


/**
 * StatusList revocation service implementing the <a href="https://w3c.github.io/cg-reports/credentials/CG-FINAL-vc-status-list-2021-20230102/">StatusList2021</a>
 * specification.
 */
public class StatusList2021RevocationService extends BaseRevocationListService<StatusList2021Credential, StatusList2021Status> {

    public StatusList2021RevocationService(ObjectMapper objectMapper, long cacheValidity) {
        super(objectMapper, cacheValidity, StatusList2021Credential.class);
    }

    @Override
    protected StatusList2021Status getCredentialStatus(CredentialStatus credentialStatus) {
        return StatusList2021Status.from(credentialStatus);
    }

    @Override
    protected Result<String> getStatusEntryValue(StatusList2021Status credentialStatus) {
        var index = credentialStatus.getStatusListIndex();
        var slCredUrl = credentialStatus.getStatusListCredential();
        var credential = getCredential(slCredUrl);


        var bitStringResult = BitString.Parser.newInstance().parse(credential.encodedList());

        if (bitStringResult.failed()) {
            return bitStringResult.mapEmpty();
        }
        var bitString = bitStringResult.getContent();

        // check that the value at index in the bitset is "1"
        if (bitString.get(index)) {
            return success(credentialStatus.getStatusListPurpose());
        }
        return success(null);
    }

    @Override
    protected Result<Void> validateStatusPurpose(StatusList2021Status credentialStatus) {
        var slCred = getCredential(credentialStatus.getStatusListCredential());

        // check that the "statusPurpose" values match
        var purpose = credentialStatus.getStatusListPurpose();
        var slCredPurpose = slCred.statusPurpose();
        if (!purpose.equalsIgnoreCase(slCredPurpose)) {
            return Result.failure("Credential's statusPurpose value must match the status list's purpose: '%s' != '%s'".formatted(purpose, slCredPurpose));
        }

        return success();
    }

    @Override
    protected int getStatusIndex(StatusList2021Status credentialStatus) {
        return credentialStatus.getStatusListIndex();
    }
}
