/*
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

import java.util.LinkedList;

import static com.codeborne.selenide.Selenide.$x;
import static org.openqa.selenium.By.xpath;

@SuppressWarnings("InnerClassMayBeStatic")
public class MemberSubsystemsPageObj {
    private final AddDialog addDialog = new AddDialog();
    private final RenameDialog renameDialog = new RenameDialog();

    public SelenideElement tabSubsystems() {
        return $x("//a[@data-test='member-subsystems-tab-button']");
    }

    public SelenideElement listSubsystems() {
        return $x("//div[@data-test='subsystems-table']");
    }

    public SelenideElement listSubsystemsRowOf(String code, String name, String status) {

        var xpath = ".//div//table//tbody//tr[td/div[@data-test='subsystem-code' and span[text() = '%s']] %s]";

        var asserts = new LinkedList<String>();
        if (name != null) {
            asserts.add("td[contains(text(), '%s')]".formatted(name));
        }
        if (status != null) {
            asserts.add("td//div[contains(text(), '%s')]".formatted(status));
        }

        var additional = asserts.isEmpty() ? "" : ("and " + String.join(" and ", asserts));

        return listSubsystems().find(xpath(String.format(xpath, code, additional)));
    }

    public SelenideElement btnDeleteSubsystem(String code) {
        return listSubsystemsRowOf(code, null, null)
                .find(xpath(".//button[@data-test='delete-subsystem']"));
    }

    public SelenideElement btnRenameSubsystem(String code) {
        return listSubsystemsRowOf(code, null, null)
                .find(xpath(".//button[@data-test='rename-subsystem']"));
    }

    public SelenideElement btnAddSubsystem() {
        return $x("//button[@data-test='add-subsystem']");
    }

    public AddDialog addDialog() {
        return addDialog;
    }

    public RenameDialog renameDialog() {
        return renameDialog;
    }

    public class AddDialog {
        public SelenideElement subsystemCode() {
            return $x("//div[@data-test='add-subsystem-input']");
        }

        public SelenideElement subsystemName() {
            return $x("//div[@data-test='add-subsystem-name-input']");
        }
    }

    public class RenameDialog {
        public SelenideElement subsystemName() {
            return $x("//div[@data-test='subsystem-name-input']");
        }
    }
}
