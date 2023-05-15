/*
 * The MIT License
 * <p>
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

package org.niis.xroad.cs.test.glue;

import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.MemberClassDescriptionDto;
import org.niis.xroad.cs.openapi.model.MemberClassDto;
import org.niis.xroad.cs.test.api.FeignMemberClassesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.ERROR_RESPONSE_BODY;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE_STATUS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class MemberClassesApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignMemberClassesApi memberClassesApi;

    @Step("member class {string} is created")
    public void memberClassIsCreated(String memberClassCode) {
        memberClassIsCreatedWithDescriptionClassDescription(memberClassCode, "Description for member class " + memberClassCode);
    }

    @Step("member class {string} is created with description {string}")
    public void memberClassIsCreatedWithDescriptionClassDescription(String code, String description) {
        try {
            final MemberClassDto dto = new MemberClassDto()
                    .code(code)
                    .description(description);

            final ResponseEntity<MemberClassDto> response = memberClassesApi.addMemberClass(dto);

            validateMemberClassResponse(response, CREATED, code, description);
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    private void validateMemberClassResponse(ResponseEntity<MemberClassDto> response, HttpStatus expectedStatus,
                                             String code, String description) {
        validate(response)
                .assertion(equalsStatusCodeAssertion(expectedStatus))
                .assertion(equalsAssertion(code, "body.code"))
                .assertion(equalsAssertion(description, "body.description"))
                .execute();
    }

    @Step("member class {string} has description {string}")
    public void memberClassHasDescription(String code, String description) {
        final ResponseEntity<List<MemberClassDto>> response = memberClassesApi.getMemberClasses();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1,
                        "body.?[code=='" + code + "' and description=='" + description + "'].size()"))
                .execute();
    }

    @Step("member class {string} description is updated to {string}")
    public void memberClassDescriptionIsUpdated(String code, String description) {
        final MemberClassDescriptionDto dto = new MemberClassDescriptionDto()
                .description(description);

        final ResponseEntity<MemberClassDto> response = memberClassesApi.updateMemberClass(code, dto);

        validateMemberClassResponse(response, OK, code, description);
    }

    @Step("member class list contains {int} items")
    public void memberClassListContainsItems(int count) {
        final ResponseEntity<List<MemberClassDto>> response = memberClassesApi.getMemberClasses();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(count, "body.size()"))
                .execute();
    }

    @Step("member class {string} is deleted")
    public void memberClassIsDeleted(String code) {
        try {
            final ResponseEntity<Void> response = memberClassesApi.deleteMemberClass(code);

            validate(response)
                    .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                    .execute();
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }
}
