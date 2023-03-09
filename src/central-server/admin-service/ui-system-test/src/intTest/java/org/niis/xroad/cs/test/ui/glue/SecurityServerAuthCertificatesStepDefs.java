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

package org.niis.xroad.cs.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.test.ui.page.SecurityServerAuthCertificatesPageObj;
import org.niis.xroad.cs.test.ui.page.SecurityServerNavigationObj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.codeborne.selenide.CollectionCondition.containExactTextsCaseSensitive;
import static com.codeborne.selenide.CollectionCondition.exactTexts;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityServerAuthCertificatesStepDefs extends BaseUiStepDefs {

    private static final int MIN_CLIENTS = 2;
    private final SecurityServerNavigationObj securityServerNavigationObj = new SecurityServerNavigationObj();
    private final SecurityServerAuthCertificatesPageObj securityServerAuthCertificatesPageObj = new SecurityServerAuthCertificatesPageObj();

    @Step("navigates to security server authentication certificates tab")
    public void navigatesToClientsTab() {
        securityServerNavigationObj.authenticationCertificatesTab()
                .shouldBe(visible)
                .click();
    }

    @Step("A authentication certificate with authority name: {}, serial number: {} & subject: {} is listed")
    public void authenticationCertificateIsListed(String certAuthorityName, String serialNumber, String subject) {
        securityServerAuthCertificatesPageObj.listRowOf(certAuthorityName, serialNumber, subject)
                .shouldBe(visible)
                .find("td:nth-child(4)")
                .shouldBe(visible)
                .shouldHave(Condition.matchText("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Step("user can sort certificates list by {}")
    public void userCanSortByColumn(String clmnHeader) {
        securityServerAuthCertificatesPageObj.columnHeaders().shouldHave(containExactTextsCaseSensitive(clmnHeader));
        var idx = securityServerAuthCertificatesPageObj.columnHeaders().texts().indexOf(clmnHeader);
        assertSortFor(idx + 1);
    }

    public void assertSortFor(int clmnIdx) {
        var header = securityServerAuthCertificatesPageObj.columnHeader(clmnIdx);
        header.click();
        var values = securityServerAuthCertificatesPageObj.columnValues(clmnIdx).texts();
        assertThat(values).hasSizeGreaterThanOrEqualTo(MIN_CLIENTS).hasSameSizeAs(Set.copyOf(values));
        if (isAllNumbers(values)) {
            var numbers = values.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            assertThat(numbers).isSorted();
        } else {
            assertThat(values).isSorted();
        }
        header.click();
        securityServerAuthCertificatesPageObj.columnValues(clmnIdx).shouldHave(exactTexts(reverse(values)));

        header.click();
        securityServerAuthCertificatesPageObj.columnValues(clmnIdx).shouldHave(exactTexts(values));
    }

    private List<String> reverse(List<String> strings) {
        var reversed = new ArrayList<>(strings);
        Collections.reverse(reversed);
        return reversed;
    }

    private boolean isAllNumbers(List<String> strings) {
        return strings.stream()
                .allMatch(StringUtils::isNumeric);
    }
}

