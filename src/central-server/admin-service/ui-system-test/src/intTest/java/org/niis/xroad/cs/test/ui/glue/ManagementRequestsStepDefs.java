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

import com.codeborne.selenide.ClickOptions;
import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.cs.openapi.model.ManagementRequestDetailedViewDto;
import org.niis.xroad.cs.test.ui.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.ui.constants.Constants;
import org.niis.xroad.cs.test.ui.page.ManagementRequestsPageObj;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.empty;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.cs.test.ui.constants.Constants.getSecurityServerId;
import static org.niis.xroad.cs.test.ui.glue.BaseUiStepDefs.StepDataKey.MANAGEMENT_REQUEST_ID;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ManagementRequestsStepDefs extends BaseUiStepDefs {
    private final ManagementRequestsPageObj managementRequestsPageObj = new ManagementRequestsPageObj();
    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;
    private ManagementRequestDetailedViewDto managementRequestDetailedView;

    @Step("User is able to sort the table by column {int}")
    public void userIsAbleToSortByColumn(int columnIndex) {
        final var column = managementRequestsPageObj.tableCol(columnIndex);
        Assertions.assertEquals("none", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("ascending", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("descending", column.getAttribute("aria-sort"));
    }

    @Step("{} is written in table search field")
    public void isWrittenInSearchField(String searchTerm) {
        managementRequestsPageObj.search().click();
        managementRequestsPageObj.searchInput().setValue(searchTerm);
    }

    @Step("User is able to view the Management request from Security server {} with owner code {}")
    public void userIsAbleToViewTheManagementRequest(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        managementRequestsPageObj.tableRowOf(securityServerId).should(appear);
    }

    @Step("User is able to click {} Management request {} from Security server {} with owner code {}")
    public void userIsAbleToClickTheManagementRequest(String status, String type, String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        managementRequestsPageObj.clickableRequestId(status, type, securityServerId).click();
    }

    private void detailsAboutTheRequest(String status, String title) {
        final var sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT);
        managementRequestsPageObj.titleOfSection(title).shouldBe(appear);
        managementRequestsPageObj.requestInformation.requestId().shouldBe(text(this.managementRequestDetailedView.getId().toString()));
        managementRequestsPageObj.requestInformation.received()
                .shouldBe(text(sdf.format(Date.from(this.managementRequestDetailedView.getCreatedAt().toInstant()))));
        managementRequestsPageObj.requestInformation.source()
                .shouldBe(text(this.managementRequestDetailedView.getOrigin().getValue()));
        managementRequestsPageObj.requestInformation.status().shouldBe(text(status));
        managementRequestsPageObj.requestInformation.comments().shouldBe(empty);
    }

    @Step("{} Add Client details page contains details about the {}")
    public void addClientDetailsAboutTheRequest(String status, String title) {
        final Integer managementRequestId = (Integer) getStepData(MANAGEMENT_REQUEST_ID).orElseThrow();
        this.managementRequestDetailedView = managementRequestsApi.getManagementRequest(managementRequestId).getBody();
        detailsAboutTheRequest(status, title);
    }

    @Step("{} Add Certificate details page contains details about the {}")
    public void addCertificateDetailsAboutTheRequest(String status, String title) {
        final Integer managementRequestId = (Integer) getStepData(MANAGEMENT_REQUEST_ID).orElseThrow();
        this.managementRequestDetailedView = managementRequestsApi.getManagementRequest(managementRequestId).getBody();
        detailsAboutTheRequest(status, title);
    }

    @Step("The details page is shown with title {}")
    public void detailsTitle(String title) {
        managementRequestsPageObj.titleOfDetails(title).shouldBe(appear);
    }

    @Step("Add Client details page contains information about the {}")
    public void addClientDetailsAboutTheAffectedSecurityServer(String title) {
        detailsAboutTheAffectedSecurityServer(title, false);
    }

    @Step("Add Certificate details page contains information about the {}")
    public void addCertificateDetailsAboutTheAffectedSecurityServer(String title) {
        detailsAboutTheAffectedSecurityServer(title, true);
    }

    private void detailsAboutTheAffectedSecurityServer(String title, boolean address) {
        final var securityServerId = this.managementRequestDetailedView.getSecurityServerId();
        managementRequestsPageObj.titleOfSection(title).shouldBe(appear);
        managementRequestsPageObj.securityServerInformation.ownerName()
                .shouldBe(text(this.managementRequestDetailedView.getSecurityServerOwner()));
        managementRequestsPageObj.securityServerInformation.ownerClass().shouldBe(text(securityServerId.getMemberClass()));
        managementRequestsPageObj.securityServerInformation.ownerCode().shouldBe(text(securityServerId.getMemberCode()));
        managementRequestsPageObj.securityServerInformation.serverCode().shouldBe(text(securityServerId.getServerCode()));
        if (address) {
            managementRequestsPageObj.securityServerInformation.address().shouldBe(text(this.managementRequestDetailedView.getAddress()));
        } else {
            managementRequestsPageObj.securityServerInformation.address().shouldBe(empty);
        }
    }

    @Step("The details page show certificate information about the {}")
    public void detailsAboutTheCertificate(String title) {
        final var sdf = new SimpleDateFormat(Constants.DATETIME_FORMAT);
        managementRequestsPageObj.titleOfSection(title).shouldBe(appear);
        final var certificate = this.managementRequestDetailedView.getCertificateDetails();
        managementRequestsPageObj.certificate.ca().shouldBe(text(certificate.getSubjectCommonName()));
        managementRequestsPageObj.certificate.serialNumber().shouldBe(text(certificate.getSerial()));
        managementRequestsPageObj.certificate.subject().shouldBe(text(certificate.getSubjectCommonName()));
        managementRequestsPageObj.certificate.expires().shouldBe(text(sdf.format(Date.from(certificate.getNotAfter().toInstant()))));
    }

    @Step("The details page show client information about the {}")
    public void detailsAboutTheClient(String title) {
        final var clientId = this.managementRequestDetailedView.getClientId();
        managementRequestsPageObj.titleOfSection(title).shouldBe(appear);
        managementRequestsPageObj.client.ownerName().shouldBe(text(this.managementRequestDetailedView.getClientOwnerName()));
        managementRequestsPageObj.client.ownerClass().shouldBe(text(clientId.getMemberClass()));
        managementRequestsPageObj.client.ownerCode().shouldBe(text(clientId.getMemberCode()));
        managementRequestsPageObj.client.subsystemCode().shouldBe(empty);
    }

    @Step("User is able click Approve button in row from Security server {} with owner code {}")
    public void userIsAbleToApproveManagementRequestInRow(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        managementRequestsPageObj.btnApproveManagementRequest(securityServerId).click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able click Approve button")
    public void userIsAbleToApproveManagementRequest() {
        commonPageObj.button.btnApprove().click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able click Decline button in row from Security server {} with owner code {}")
    public void userIsAbleToDeclineManagementRequestInRow(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        managementRequestsPageObj.btnDeclineManagementRequest(securityServerId).click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();


        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able click Decline button")
    public void userIsAbleToDeclineManagementRequest() {
        commonPageObj.button.btnDecline().click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Management request from Security server {} with owner code {} should removed in list")
    public void timestampingServiceShouldRemovedInList(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        managementRequestsPageObj.tableRowOf(securityServerId).shouldNotBe(visible);
    }

    @Step("Management Requests table with columns {}, {}, {}, {}, {}, {} is visible")
    public void managementRequestsTableIsVisible(String id, String created, String type, String serverOwnerName, String serverIdentifier,
                                                 String status) {
        managementRequestsPageObj.tableWithHeaders(id, created, type, serverOwnerName, serverIdentifier, status)
                .shouldBe(Condition.enabled);
    }

    @Step("Management Requests table is visible")
    public void managementRequestsTableIsVisible() {
        managementRequestsPageObj.table().shouldBe(Condition.enabled);
    }

    @Step("Show only pending requests is checked")
    public void showOnlyPendingRequestsIsChecked() {
        managementRequestsPageObj.showOnlyPendingRequests().click(ClickOptions.usingJavaScript());
    }

    @Step("The user can see the Approve, Decline actions for pending management requests")
    public void shouldShowApproveAndDeclineActions() {
        managementRequestsPageObj.btnApproveManagementRequest().shouldBe(visible);
        managementRequestsPageObj.btnDeclineManagementRequest().shouldBe(visible);
    }

    @Step("The user can not see the Approve, Decline actions for requests that have already been processed")
    public void shouldNotShowApproveAndDeclineActions() {
        managementRequestsPageObj.btnApproveManagementRequest().shouldNot(visible);
        managementRequestsPageObj.btnDeclineManagementRequest().shouldNot(visible);
    }
}
