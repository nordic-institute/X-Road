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
import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Step;
import org.junit.jupiter.api.Assertions;
import org.niis.xroad.cs.test.ui.page.TimestampingServicesPageObj;
import org.niis.xroad.cs.test.ui.utils.CertificateUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Condition.visible;

public class TrustServicesTimestampingServicesStepDefs extends BaseUiStepDefs {

    private final TimestampingServicesPageObj timestampingServicesPageObj = new TimestampingServicesPageObj();

    @SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MethodParamPad"})
    @Step("Timestamping service with URL {} is added")
    public void newTimestampingServiceIsAdded(String url) throws Exception {
        timestampingServicesPageObj.btnAddTimestampingService().click();

        commonPageObj.dialog.btnCancel().should(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        final byte[] certificate = CertificateUtils.generateAuthCert(url);

        timestampingServicesPageObj.addEditDialog.inputCertificateFile().uploadFile(CertificateUtils.getAsFile(certificate));
        timestampingServicesPageObj.addEditDialog.inputUrl().setValue(url);

        commonPageObj.dialog.btnSave().click();

        // 1. try
        //WebDriverWait wait = new WebDriverWait(Selenide.webdriver().driver().getWebDriver(), Duration.ofMillis(5000));
        //wait.pollingEvery(Duration.ofMillis(5)).until(ExpectedConditions.visibilityOf(timestampingServicesPageObj.loadingProgress()));

        // 2. try
        FluentWait wait = new FluentWait(Selenide.webdriver().driver().getWebDriver());
        wait.withTimeout(Duration.ofMillis(5000));
        wait.pollingEvery(Duration.ofMillis(250));
        wait.ignoring(NoSuchElementException.class);
        wait.ignoring(java.util.NoSuchElementException.class);
        wait.until(ExpectedConditions.visibilityOf(timestampingServicesPageObj.loadingProgress()));

        // 0. simple try
        //timestampingServicesPageObj.loadingProgress().should(appear);

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();





    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void waitForLoading() throws InterruptedException {
        int count = 0;
        while (timestampingServicesPageObj.loadingProgress().isDisplayed()  && count < 1) {
            Thread.sleep(1000);
            count++;
        }
    }

    @Step("Loading check")
    public void loadingCheck() {
        timestampingServicesPageObj.loadingProgress().should(appear);
    }

    @Step("Timestamping service with URL {} is visible in the Timestamping Services list")
    public void newTimestampingServiceIsVisibleInTheList(String url) {
        timestampingServicesPageObj.tableServicesRowOf(url).should(appear);
    }

    @Step("user is able to sort the table by column {int}")
    public void userIsAbleToSortByColumn(int columnIndex) {
        var column = timestampingServicesPageObj.tableServicesCol(columnIndex);
        Assertions.assertEquals("none", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("ascending", column.getAttribute("aria-sort"));
        column.click();
        Assertions.assertEquals("descending", column.getAttribute("aria-sort"));
    }

    @Step("Timestamping service table is visible")
    public void timestampingServiceTableIsVisible() {
        timestampingServicesPageObj.table().shouldBe(Condition.enabled);
    }

    @Step("User is able to view the certificate of Timestamping service with URL {}")
    public void userIsAbleToViewTheCertificate(String url) {
        timestampingServicesPageObj.btnViewTimestampingService(url).click();
        timestampingServicesPageObj.certificateView.certificateDetails().shouldBe(visible);
    }

    @Step("User is able click Edit button in Timestamping service with URL {}")
    public void userIsAbleToEditTimestampingService(String url) {
        timestampingServicesPageObj.btnEditTimestampingService(url).click();
    }

    @Step("User is able view the certificate of Timestamping service")
    public void userIsAbleViewTheCertificate() {
        timestampingServicesPageObj.addEditDialog.btnViewCertificate().click();
        timestampingServicesPageObj.certificateView.certificateDetails().shouldBe(visible);
        timestampingServicesPageObj.certificateView.btnClose().click();
    }

    @Step("User is able change the certificate of Timestamping service with URL {}")
    public void userIsAbleChangeTheCertificate(String url) throws Exception {
        timestampingServicesPageObj.addEditDialog.btnUploadCertificate().click();

        final byte[] certificate = CertificateUtils.generateAuthCert(url);
        timestampingServicesPageObj.addEditDialog.inputCertificateFile().uploadFile(CertificateUtils.getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able change the URL of Timestamping service to new URL {}")
    public void userIsAbleEditTheUrl(String newUrl) {
        commonPageObj.dialog.btnCancel().should(Condition.enabled);
        commonPageObj.dialog.btnSave().should(Condition.enabled);

        clearInput(timestampingServicesPageObj.addEditDialog.inputUrl());

        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        timestampingServicesPageObj.addEditDialog.inputUrl().setValue(newUrl);
        commonPageObj.dialog.btnSave().click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User is able to click delete button in Timestamping service with URL {}")
    public void userIsAbleToDeleteTimestampingService(String url) {
        timestampingServicesPageObj.btnDeleteTimestampingService(url).click();

        commonPageObj.dialog.btnCancel().shouldBe(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldBe(Condition.enabled).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Timestamping service with URL {} should removed in list")
    public void timestampingServiceShouldRemovedInList(String url) {
        timestampingServicesPageObj.tableServicesRowOf(url).shouldNotBe(visible);
    }
}
