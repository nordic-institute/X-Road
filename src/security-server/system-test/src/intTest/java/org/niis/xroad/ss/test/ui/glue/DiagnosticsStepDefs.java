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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebElementCondition;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.DiagnosticsPageObj;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.or;
import static com.codeborne.selenide.Condition.partialText;
import static com.codeborne.selenide.Condition.text;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.given;

@SuppressWarnings("checkstyle:MagicNumber")
public class DiagnosticsStepDefs extends BaseUiStepDefs {
    private final DiagnosticsPageObj diagnosticsPage = new DiagnosticsPageObj();

    @Step("Diagnostics tab is {string}")
    public void diagnosticsTabIsVisible(String conditionStr) {
        WebElementCondition condition;
        if ("visible".equals(conditionStr)) {
            condition = Condition.visible;
        } else {
            condition = Condition.not(Condition.visible);
        }
        commonPageObj.menu.diagnosticsTab().shouldBe(condition);
    }

    @Step("Backup encryption is enabled")
    public void backupEncryptionIsEnabled() {
        diagnosticsPage.backupEncryptionStatus().shouldHave(text("Enabled"));
    }

    @Step("Backup encryption configuration has {int} key(s)")
    public void backupEncryptionHasNumOfKeys(int count) {
        diagnosticsPage.backupEncryptionKeyList().shouldHave(size(count));
    }


    @Step("Message log archive encryption is enabled")
    public void messageLogArchiveEncryptionIsEnabled() {
        diagnosticsPage.messageLogEncryptionStatus()
                .scrollIntoView(false)
                .shouldHave(text("Enabled"));
    }


    @Step("Message log database encryption is enabled")
    public void messageLogDatabaseEncryptionIsEnabled() {
        diagnosticsPage.messageLogDatabaseEncryptionStatus()
                .scrollIntoView(false)
                .shouldHave(text("Enabled"));
    }

    @Step("Message log grouping is set to {}")
    public void messageLogGroupingIs(String grouping) {
        diagnosticsPage.messageLogEncryptionStatus().shouldHave(text(grouping));
    }

    @Step("At least one member should have encryption key configured")
    public void memberWithConfiguredEncryptionKeyExists() {
        diagnosticsPage.memberMessageLogEncryptionKey().should(exist);
    }


    @Step("At least one member should use default encryption key")
    public void memberWithDefaultEncryptionKeyExists() {
        diagnosticsPage.memberMessageLogEncryptionKeyWithWarning().should(exist);
    }

    @Step("Java version status should be ok")
    public void javaVersionStatus() {
        diagnosticsPage.javaVersionMessage().shouldHave(partialText("ok"));
    }

    @Step("Global configuration status should be ok")
    public void globalConfigurationStatus() {
        given()
                .pollDelay(5, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .pollInSameThread()
                .conditionEvaluationListener(condition -> {
                    if (!condition.isSatisfied()) {
                        Selenide.refresh();
                    }
                })
                .atMost(60, TimeUnit.SECONDS)
                .await()
                .untilAsserted(() -> diagnosticsPage.globalConfigurationMessage()
                        .shouldHave(partialText("ok"), Duration.of(1, SECONDS)));
    }

    @Step("Timestamping status should be ok")
    public void timestampingStatus() {
        diagnosticsPage.timestampingMessage()
                .scrollIntoView(false)
                .shouldHave(partialText("ok"));
    }

    @Step("OCSP responders status should be ok")
    public void ocspRespondersStatus() {
        diagnosticsPage.ocspResponderMessage()
                .scrollIntoView(false)
                .shouldHave(or("Validate status",
                        partialText("ok"),
                        partialText("not sent yet")));
    }


}
