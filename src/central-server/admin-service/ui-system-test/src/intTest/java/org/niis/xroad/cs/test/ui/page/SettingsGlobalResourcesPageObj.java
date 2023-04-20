/**
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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

@SuppressWarnings("InnerClassMayBeStatic")
public class SettingsGlobalResourcesPageObj {
    public final GlobalGroupList globalGroupList = new GlobalGroupList();
    public final GlobalGroupForm globalGroupForm = new GlobalGroupForm();

    private final CommonPageObj commonPageObj = new CommonPageObj();

    public SelenideElement globalResourcesView() {
        return $x("//div[@data-test='global-resources-view']");
    }

    public class GlobalGroupList {

        public SelenideElement globalGroupRow(String code) {
            var xpath = "//div[@data-test='global-groups-table']//div[@data-test='group-code' and contains(text(), '%s')]";
            return $x(String.format(xpath, code)).ancestor("tr");
        }

        public SelenideElement globalGroupCodeBtn(String code) {
            var xpath = "//div[@data-test='global-groups-table']//div[@data-test='group-code' and contains(text(), '%s')]";
            return $x(String.format(xpath, code)).ancestor(".xrd-clickable");
        }

        public SelenideElement btnAddGlobalGroup() {
            return $x("//button[@data-test='add-global-group-button']");
        }
    }

    public class GlobalGroupForm {

        public SelenideElement inputGroupCode() {
            return $x("//input[@data-test='add-global-group-code-input']");
        }

        public SelenideElement inputGroupDescription() {
            return $x("//input[@data-test='add-global-group-description-input']");
        }

        public SelenideElement btnConfirm() {
            return commonPageObj.dialog.btnSave();
        }
    }
}
