/*
 * The MIT License
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
package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

public class BackupAndRestorePageObj {

    public SelenideElement btnCreateConfigurationBackup() {
        return $x("//button[@data-test='backup-create-configuration']");
    }

    public SelenideElement btnRestoreConfigurationFromBackup() {
        return $x("//button[@data-test='backup-restore']");
    }

    public SelenideElement btnUploadConfigurationBackup() {
        return $x("//button[@data-test='backup-upload']");
    }

    public SelenideElement btnDownloadConfigurationBackup() {
        return $x("//button[@data-test='backup-download']");
    }

    public SelenideElement btnDeleteConfigurationBackup() {
        return $x("//button[@data-test='backup-delete']");
    }

    public SelenideElement btnLoading() {
        return $x("//span[@class='v-btn__loader']");
    }

    public ElementsCollection backupList() {
        return $$x("//div[@data-test='backup-restore-view']//table/tbody/tr[.//td[not(contains(text(), 'No data available'))]]");
    }

    public SelenideElement inputConfigurationBackupBackupFile() {
        return $x("//input[@type='file']");
    }

    public SelenideElement inputSearch() {
        return $x("//div[@data-test='search-input']");
    }
}
