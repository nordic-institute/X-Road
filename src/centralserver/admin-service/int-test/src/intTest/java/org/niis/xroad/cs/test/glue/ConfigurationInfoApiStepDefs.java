/*
 * The MIT License
 *
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

import io.cucumber.java.en.Step;
import org.niis.xroad.centralserver.openapi.model.ConfigurationAnchorDto;
import org.niis.xroad.centralserver.openapi.model.ConfigurationPartDto;
import org.niis.xroad.centralserver.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.centralserver.openapi.model.GlobalConfDownloadUrlDto;
import org.niis.xroad.cs.test.api.FeignConfigurationPartsApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourceAnchorApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourcesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Set;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public class ConfigurationInfoApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignConfigurationSourceAnchorApi configurationSourceAnchorApi;
    @Autowired
    private FeignConfigurationSourcesApi configurationSourcesApi;
    @Autowired
    private FeignConfigurationPartsApi configurationPartsApi;

    @Step("internal configuration parts exists")
    public void viewInternalConfParts() {
        final ResponseEntity<Set<ConfigurationPartDto>> response = configurationPartsApi
                .getConfigurationParts(ConfigurationTypeDto.INTERNAL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("SHARED-ID", "body[0].contentIdentifier",
                        "Response contains content identifier"))
                .assertion(equalsAssertion("file.xml", "body[0].fileName",
                        "Response contains file name "))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"), "body[0].fileUpdatedAt",
                        "Response contains date at which file was updated"))
                .assertion(equalsAssertion(2, "body[0].version",
                        "Response contains version "))
                .execute();
    }

    @Step("internal configuration source anchor info exists")
    public void viewInternalSourceAnchor() {
        final ResponseEntity<ConfigurationAnchorDto> response = configurationSourceAnchorApi
                .getAnchor(ConfigurationTypeDto.INTERNAL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("4D:72:A8:60:90:88:A2:5B:9C:6B:91:86:3C:D7:44:CE:9E:E1:1C:27:8E:33:F4:E5:31:68:F2:EC",
                        "body.hash",
                        "Response contains file hash"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"), "body.createdAt",
                        "Response contains created at date"))
                .execute();
    }

    @Step("internal configuration source global download url exists")
    public void viewInternalSourceDownloadUrl() {
        final ResponseEntity<GlobalConfDownloadUrlDto> response = configurationSourcesApi
                .getDownloadUrl(ConfigurationTypeDto.INTERNAL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("http://cs/internalconf", "body.url",
                        "Response contains global download url"))
                .execute();
    }

}
