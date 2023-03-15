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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.test.ui.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.ui.page.ManagementRequestsPageObj;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto.SECURITY_SERVER;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.CLIENT_DELETION_REQUEST;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Slf4j
public class ManagementRequestsStepDefs extends BaseUiStepDefs {

    private final ManagementRequestsPageObj managementRequestsPageObj = new ManagementRequestsPageObj();
    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;

    @Step("User is able to sort the table by column {int}")
    public void userIsAbleToSortByColumn(int columnIndex) {
        var column = managementRequestsPageObj.tableServicesCol(columnIndex);
        Assertions.assertEquals("none", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("ascending", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("descending", column.getAttribute("aria-sort"));
    }

    @Step("Management Requests table with columns {}, {} is visible")
    public void managementRequestsTableIsVisible(String id, String created) {
        managementRequestsPageObj.tableWithHeaders(id, created).shouldBe(Condition.enabled);
    }

    @Step("Management Requests table is visible")
    public void managementRequestsTableIsVisible() {
        managementRequestsPageObj.table().shouldBe(Condition.enabled);
    }

    @Step("Show only pending requests is checked")
    public void showOnlyPendingRequestsIsChecked() {
        managementRequestsPageObj.showOnlyPendingRequests().shouldBe(Condition.checked);
    }

    @Step("Show only pending requests is not checked")
    public void showOnlyPendingRequestsIsNotChecked() {
        managementRequestsPageObj.showOnlyPendingRequests().shouldNotBe(Condition.checked);
    }

    @Step("Add Management request")
    public void addManagementRequest2() {
        final ClientDeletionRequestDto managementRequest = new ClientDeletionRequestDto();
        managementRequest.setType(CLIENT_DELETION_REQUEST);
        managementRequest.setOrigin(SECURITY_SERVER);
        managementRequest.setSecurityServerId("securityServerId");
        managementRequest.setClientId("clientId");

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
    }
}
