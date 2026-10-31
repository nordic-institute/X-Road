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

package org.niis.xroad.cs.admin.rest.api.openapi.validator;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationEntryDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationRequestDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDidRegistrationRequestValidationTest {
    private static final String MEMBER_ID = "MEMBER:DEV/COM/111";
    private static final String DID = "did:web:ss.example.org:v1:member:DEV:COM:111";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private List<String> entryViolations(List<MemberDidRegistrationEntryDto> entries) {
        var request = new MemberDidRegistrationRequestDto().entries(entries);
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .filter(path -> path.startsWith("entries"))
                .toList();
    }

    private static MemberDidRegistrationEntryDto entry(String memberId, String did) {
        return new MemberDidRegistrationEntryDto().memberId(memberId).did(did);
    }

    @Test
    void aWellFormedEntryHasNoViolations() {
        assertThat(entryViolations(List.of(entry(MEMBER_ID, DID)))).isEmpty();
    }

    @Test
    void anEmptyBatchIsAViolation() {
        assertThat(entryViolations(List.of())).containsExactly("entries");
    }

    @Test
    void controlCharactersInTheMemberIdAreAViolation() {
        assertThat(entryViolations(List.of(entry("MEMBER:DEV/COM/\t111", DID)))).containsExactly("entries[0].memberId");
    }

    @Test
    void anOverlongMemberIdIsAViolation() {
        assertThat(entryViolations(List.of(entry("m".repeat(1024), DID)))).containsExactly("entries[0].memberId");
    }

    @Test
    void aDidWithControlCharactersOrAnyLengthPassesBeanValidationAndIsClassifiedPerEntry() {
        assertThat(entryViolations(List.of(entry(MEMBER_ID, "did:web:ss\n.example.org")))).isEmpty();
        assertThat(entryViolations(List.of(entry(MEMBER_ID, "d".repeat(4001))))).isEmpty();
    }
}
