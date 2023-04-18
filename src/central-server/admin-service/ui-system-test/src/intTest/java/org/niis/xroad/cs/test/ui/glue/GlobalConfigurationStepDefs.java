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
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Step;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.core.EvaluatedCondition;
import org.niis.xroad.cs.test.ui.page.GlobalConfigurationPageObj;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.ClassLoader.getSystemResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.given;

public class GlobalConfigurationStepDefs extends BaseUiStepDefs {

    private static final String CONTENT_IDENTIFIER = "CONTENT_IDENTIFIER";
    private static final String DOWNLOADED_FILE = "DOWNLOADED_FILE";
    private static final String ANCHOR_DETAILS = "ANCHOR_DETAILS";
    private static final String CFG_PART_UPDATED = "CFG_PART_UPDATED";
    private final GlobalConfigurationPageObj globalConfigurationPageObj = new GlobalConfigurationPageObj();

    @Step("Internal configuration sub-tab is selected")
    public void selectInternalCfgTab() {
        globalConfigurationPageObj.internalConfiguration().click();
    }

    @Step("External configuration sub-tab is selected")
    public void selectExternalCfgTab() {
        globalConfigurationPageObj.externalConfiguration().click();
    }

    @Step("Trusted Anchors sub-tab is selected")
    public void selectTrustedAnchorsTab() {
        globalConfigurationPageObj.trustedAnchors().click();
    }

    @Step("Details for Token: {} is expanded")
    public void expandToken(final String tokenKey) {
        globalConfigurationPageObj.tokenLabel(tokenKey).click();
    }

    @Step("User logs in token: {} with PIN: {}")
    public void loginToken(final String tokenKey, final String tokenPin) {
        globalConfigurationPageObj.loginButton(tokenKey)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.tokenLoginDialog.inputPin().setValue(tokenPin);
        globalConfigurationPageObj.tokenLoginDialog.btnLogin()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User logs out token: {}")
    public void logoutToken(final String tokenKey) {
        globalConfigurationPageObj.logoutButton(tokenKey)
                .shouldBe(Condition.enabled)
                .click();


        globalConfigurationPageObj.tokenLogoutDialog.btnLogout()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Token: {} is logged-out")
    public void tokenIsLoggedOut(final String tokenKey) {
        globalConfigurationPageObj.loginButton(tokenKey).shouldBe(Condition.enabled);
    }

    @Step("Token: {} is logged-in")
    public void tokenIsLoggedIn(final String tokenKey) {
        globalConfigurationPageObj.logoutButton(tokenKey).shouldBe(Condition.enabled);
    }

    @Step("Add key button is disabled for token: {}")
    public void addKeyDisabled(final String tokenKey) {
        globalConfigurationPageObj.addSigningKey(tokenKey).shouldBe(Condition.disabled);
    }

    @Step("Add key button is enabled for token: {}")
    public void addKeyEnabled(final String tokenKey) {
        globalConfigurationPageObj.addSigningKey(tokenKey).shouldBe(Condition.enabled);
    }

    @Step("Signing key: {} can be activated for token: {}")
    public void canBeActivated(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.signingKeyLabel(tokenName, keyLabel).shouldBe(Condition.visible);
        globalConfigurationPageObj.btnActivateSigningKey(tokenName, keyLabel)
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled);
    }

    @Step("Signing key: {} can be deleted for token: {}")
    public void canBeDeleted(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.signingKeyLabel(tokenName, keyLabel).shouldBe(Condition.visible);
        globalConfigurationPageObj.btnDeleteSigningKey(tokenName, keyLabel)
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled);
    }

    @Step("Signing key: {} can't be activated for token: {}")
    public void cantBeActivated(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.signingKeyLabel(tokenName, keyLabel).shouldBe(Condition.visible);
        globalConfigurationPageObj.btnActivateSigningKey(tokenName, keyLabel).shouldNotBe(Condition.visible);
    }

    @Step("Signing key: {} can't be deleted for token: {}")
    public void cantBeDeleted(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.signingKeyLabel(tokenName, keyLabel).shouldBe(Condition.visible);
        globalConfigurationPageObj.btnDeleteSigningKey(tokenName, keyLabel).shouldNotBe(Condition.visible);
    }

    @Step("User activates signing key: {} for token: {}")
    public void activateSigningKey(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.btnActivateSigningKey(tokenName, keyLabel)
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.activateSigningKeyDialog.btnActivate()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User deletes signing key: {} for token: {}")
    public void deleteSigningKey(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.btnDeleteSigningKey(tokenName, keyLabel)
                .shouldBe(Condition.visible)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.deleteSigningKeyDialog.btnDelete()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Signing key: {} is not present for token: {}")
    public void signingKeyNotPresent(final String keyLabel, final String tokenName) {
        globalConfigurationPageObj.signingKeyLabel(tokenName, keyLabel).shouldNotBe(Condition.visible);
    }

    @Step("User adds signing key for token: {} with name: {}")
    public void addSigningKey(final String tokenKey, final String keyLabel) {
        globalConfigurationPageObj.addSigningKey(tokenKey)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.addSigningKeyDialog.inputLabel().setValue(keyLabel);
        globalConfigurationPageObj.addSigningKeyDialog.btnSave()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Configuration anchor is recreated")
    public void recreateAnchor() {
        scenarioContext.putStepData(ANCHOR_DETAILS,
                Pair.of(
                        Optional.of(globalConfigurationPageObj.anchor.txtHash())
                                .filter(SelenideElement::isDisplayed)
                                .map(SelenideElement::text)
                                .orElse(null),
                        Optional.of(globalConfigurationPageObj.anchor.txtCreatedAt())
                                .filter(SelenideElement::isDisplayed)
                                .map(SelenideElement::text)
                                .orElse(null)
                )
        );
        globalConfigurationPageObj.anchor.btnRecreate()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("Updated anchor information is displayed")
    public void updatedAnchorInfoDisplayed() {
        globalConfigurationPageObj.anchor.txtHash()
                .shouldNotBe(Condition.empty);

        globalConfigurationPageObj.anchor.txtCreatedAt()
                .shouldNotBe(Condition.empty);
        final Pair<String, String> oldDetails = scenarioContext.getStepData(ANCHOR_DETAILS);
        assertThat(globalConfigurationPageObj.anchor.txtHash().text())
                .as("new anchor hash should be different from previous")
                .isNotEqualTo(oldDetails.getLeft());
        assertThat(globalConfigurationPageObj.anchor.txtCreatedAt().text())
                .as("new anchor creation date should be different from previous")
                .isNotEqualTo(oldDetails.getRight());
    }

    @Step("User clicks configuration anchor download button")
    public void downloadConfigurationAnchor() throws FileNotFoundException {
        final var file = globalConfigurationPageObj.anchor.btnDownload()
                .shouldBe(Condition.enabled)
                .download();
        scenarioContext.putStepData(DOWNLOADED_FILE, file);
    }

    @Step("Configuration anchor is successfully downloaded")
    public void isAnchorDownloaded() {
        final File file = scenarioContext.getStepData(DOWNLOADED_FILE);
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("xml");
    }

    @Step("There is entry for configuration part: {}")
    public void isConfigurationPartPresent(String contentIdentifier) {
        scenarioContext.putStepData(CONTENT_IDENTIFIER, contentIdentifier);
        globalConfigurationPageObj.configurationParts.textContentIdentifier(contentIdentifier)
                .shouldBe(Condition.visible)
                .shouldNotBe(Condition.empty);

        scenarioContext.putStepData(CFG_PART_UPDATED,
                globalConfigurationPageObj.configurationParts.textUpdatedAt(contentIdentifier).text());
    }

    @Step("Configuration part was updated")
    public void doesntHaveUpdatedAtForConfigurationPart() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);
        final String oldUpdated = scenarioContext.getStepData(CFG_PART_UPDATED);
        final var newUpdated = globalConfigurationPageObj.configurationParts.textUpdatedAt(contentIdentifier).text();

        assertThat(newUpdated).isNotEqualTo(oldUpdated);

        scenarioContext.putStepData(CFG_PART_UPDATED, newUpdated);
    }

    @Step("User clicks download button for it")
    public void startConfigurationPartDownload() throws FileNotFoundException {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);

        var file = globalConfigurationPageObj.configurationParts.btnDownload(contentIdentifier)
                .download();
        scenarioContext.putStepData(DOWNLOADED_FILE, file);
    }

    @Step("User can download it")
    public void canDownloadConfigurationFile() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);
        globalConfigurationPageObj.configurationParts.btnDownload(contentIdentifier)
                .shouldBe(Condition.enabled);
    }

    @Step("Configuration part is generated")
    public void isConfigurationPartGenerated() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);

        final int pollInterval = 10;
        final int pollDelay = 5;
        final int maxWaitTime = 65;
        given()
                .pollDelay(pollDelay, TimeUnit.SECONDS)
                .pollInterval(pollInterval, TimeUnit.SECONDS)
                .pollInSameThread()
                .conditionEvaluationListener(this::refreshOnFailure)
                .atMost(maxWaitTime, TimeUnit.SECONDS)
                .await().untilAsserted(() -> assertThat(cfgPartUpdatedAt(contentIdentifier)).isNotEqualTo("-"));
    }

    private void refreshOnFailure(final EvaluatedCondition evaluatedCondition) {
        if (!evaluatedCondition.isSatisfied()) {
            Selenide.refresh();
        }
    }

    private String cfgPartUpdatedAt(final String contentIdentifier) {
        return globalConfigurationPageObj.configurationParts.textUpdatedAt(contentIdentifier).text();
    }

    @Step("User can't download it")
    public void cantDownloadConfigurationFile() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);
        globalConfigurationPageObj.configurationParts.btnDownload(contentIdentifier)
                .shouldNotBe(Condition.visible);
    }

    @Step("User can upload configuration file for it")
    public void canUploadConfigurationFile() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);
        globalConfigurationPageObj.configurationParts.btnUpload(contentIdentifier)
                .shouldBe(Condition.enabled);
    }

    @Step("User can't upload configuration file for it")
    public void cantUploadConfigurationFile() {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);
        globalConfigurationPageObj.configurationParts.btnUpload(contentIdentifier)
                .shouldNotBe(Condition.visible);
    }

    @Step("User uploads file {} for it")
    public void openUploadDialog(String filename) throws URISyntaxException, InterruptedException {
        final String contentIdentifier = scenarioContext.getStepData(CONTENT_IDENTIFIER);

        globalConfigurationPageObj.configurationParts.btnUpload(contentIdentifier)
                .shouldBe(Condition.enabled)
                .click();

        globalConfigurationPageObj.configurationParts.btnCancelUpload()
                .shouldBe(Condition.enabled);
        globalConfigurationPageObj.configurationParts.btnConfirmUpload()
                .shouldNotBe(Condition.enabled);
        TimeUnit.SECONDS.sleep(2); //avoid same updated at
        globalConfigurationPageObj.configurationParts.inputConfigurationFile().uploadFile(getConfigurationFile(filename));

        globalConfigurationPageObj.configurationParts.btnConfirmUpload()
                .shouldBe(Condition.enabled)
                .click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    private File getConfigurationFile(String filename) throws URISyntaxException {
        return Paths.get(getSystemResource("files/" + filename).toURI()).toFile();
    }


    @Step("Configuration part file is successfully downloaded")
    public void wasConfigurationFileDownloaded() {
        final File file = scenarioContext.getStepData(DOWNLOADED_FILE);
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("xml");
    }
}
