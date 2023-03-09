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

import com.codeborne.selenide.CollectionCondition;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.test.ui.page.SecurityServerClientsPageObj;
import org.niis.xroad.cs.test.ui.page.SecurityServerNavigationObj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.Assertions.assertThat;

public class SecurityServerClientsStepDefs extends BaseUiStepDefs {

    private static final int MIN_CLIENTS = 2;
    private final SecurityServerNavigationObj securityServerNavigationObj = new SecurityServerNavigationObj();
    private final SecurityServerClientsPageObj securityServerClientsPageObj = new SecurityServerClientsPageObj();

    @Step("navigates to security server clients tab")
    public void navigatesToClientsTab() {
        securityServerNavigationObj.clientsTab()
                .shouldBe(visible)
                .click();
    }

    @Step("A client with name: {}, code: {}, class: {} & subsystem: {} is listed")
    public void clientIsListed(String memberName, String memberCode, String memberClass, String subsystem) {
        securityServerClientsPageObj.listRowOf(memberName, memberCode, memberClass, subsystem)
                .shouldBe(visible);
    }

    @Step("user can sort list by subsystem")
    public void userIsAbleToSortBySubsystem() {
        securityServerClientsPageObj.subsystemColumnHeader().click();
        var values = securityServerClientsPageObj.subsystemValues().texts();
        assertThat(values).hasSizeGreaterThanOrEqualTo(MIN_CLIENTS).hasSameSizeAs(Set.copyOf(values)).isSorted();

        securityServerClientsPageObj.subsystemColumnHeader().click();
        securityServerClientsPageObj.subsystemValues().shouldHave(CollectionCondition.exactTexts(reverse(values)));

        securityServerClientsPageObj.subsystemColumnHeader().click();
        securityServerClientsPageObj.subsystemValues().shouldHave(CollectionCondition.exactTexts(values));
    }

    private List<String> reverse(List<String> strings) {
        var reversed = new ArrayList<>(strings);
        Collections.reverse(reversed);
        return reversed;
    }
}

