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
public class GlobalGroupDetailsPageObj {
    private static final int MEMBER_ID_PARTS = 3;
    private static final int SUBSYSTEM_CODE_IDX = 3;
    private static final int SUBSYSTEM_ID_PARTS = 4;
    private final AddMembersDialogObj addMembersDialogObj = new AddMembersDialogObj();
    private final DeleteMemberDialogObj deleteMemberDialogObj = new DeleteMemberDialogObj();

    public SelenideElement btnAddMembersButton() {
        final var xpath = "//article[@data-test='global-resources-view']//button[@data-test='add-member-button']";
        return $x(xpath);
    }

    public SelenideElement membersCount() {
        final var xpath = "//article[@data-test='global-resources-view']//span[@data-test='member-count']";
        return $x(xpath);
    }

    public AddMembersDialogObj getAddMembersDialogObj() {
        return addMembersDialogObj;
    }

    public SelenideElement memberRow(final String identifier) {
        final var parts = identifier.split(":");
        switch (parts.length) {
            case MEMBER_ID_PARTS:
                return memberRow(parts[0], parts[1], parts[2]);
            case SUBSYSTEM_ID_PARTS:
                return memberRow(parts[0], parts[1], parts[2], parts[SUBSYSTEM_CODE_IDX]);
            default:
                throw new IllegalArgumentException("Identifier: " + identifier + " should have 3 or 4 parts");
        }
    }

    public SelenideElement btnDeleteMember(final String identifier) {
        return memberRow(identifier).$("button[data-test='delete-member-button']");
    }

    private SelenideElement memberRow(final String instance, final String clazz, final String code) {
        final var xpath = "//div[@data-test='global-group-members']"
                + "//tr[.//span[@data-test='instance' and contains(text(), '%s')] "
                + "and .//span[@data-test='class' and contains(text(), '%s')]"
                + "and .//span[@data-test='code' and contains(text(), '%s')]"
                + "and .//span[@data-test='subsystem' and not(normalize-space(text()))]]";
        return $x(String.format(xpath, instance, clazz, code));
    }

    private SelenideElement memberRow(final String instance, final String clazz, final String code, final String subsystem) {
        final var xpath = "//div[@data-test='global-group-members']"
                + "//tr[.//span[@data-test='instance' and contains(text(), '%s')] "
                + "and .//span[@data-test='class' and contains(text(), '%s')]"
                + "and .//span[@data-test='code' and contains(text(), '%s')]"
                + "and .//span[@data-test='subsystem' and contains(text(), '%s')]]";
        return $x(String.format(xpath, instance, clazz, code, subsystem));
    }

    public DeleteMemberDialogObj getDeleteMemberDialogObj() {
        return deleteMemberDialogObj;
    }

    public class DeleteMemberDialogObj {
        public SelenideElement btnDelete() {
            final var xpath = "//button[@data-test='dialog-save-button']";
            return $x(xpath);
        }

        public SelenideElement btnClose() {
            final var xpath = "//button[@data-test='dialog-cancel-button']";
            return $x(xpath);
        }

        public SelenideElement inputCode() {
            final var xpath = "//input[@data-test='verify-member-code']";
            return $x(xpath);
        }
    }

    public class AddMembersDialogObj {
        public SelenideElement selectableRow(final String identifier) {
            final var parts = identifier.split(":");
            switch (parts.length) {
                case MEMBER_ID_PARTS:
                    return selectableRow(parts[0], parts[1], parts[2]);
                case SUBSYSTEM_ID_PARTS:
                    return selectableRow(parts[0], parts[1], parts[2], parts[SUBSYSTEM_CODE_IDX]);
                default:
                    throw new IllegalArgumentException("Identifier: " + identifier + " should have 3 or 4 parts");
            }
        }

        public SelenideElement rowCheckbox(final String identifier) {
            return selectableRow(identifier).$("div[data-test='members-checkbox']");
        }

        private SelenideElement selectableRow(final String instance, final String clazz, final String code) {
            final var xpath = "//div[@data-test='select-members-list']"
                    + "//tr[.//div[@data-test='instance' and contains(text(), '%s')] "
                    + "and .//div[@data-test='class' and contains(text(), '%s')]"
                    + "and .//div[@data-test='code' and contains(text(), '%s')]"
                    + "and .//div[@data-test='subsystem' and not(normalize-space(text()))]]";
            return $x(String.format(xpath, instance, clazz, code));
        }

        private SelenideElement selectableRow(final String instance, final String clazz, final String code, final String subsystem) {
            final var xpath = "//div[@data-test='select-members-list']"
                    + "//tr[.//div[@data-test='instance' and contains(text(), '%s')] "
                    + "and .//div[@data-test='class' and contains(text(), '%s')]"
                    + "and .//div[@data-test='code' and contains(text(), '%s')]"
                    + "and .//div[@data-test='subsystem' and contains(text(), '%s')]]";
            return $x(String.format(xpath, instance, clazz, code, subsystem));
        }

        public SelenideElement btnAddMembers() {
            final var xpath = "//button[@data-test='member-subsystem-add-button']";
            return $x(xpath);
        }

        public SelenideElement btnClose() {
            final var xpath = "//button[@data-test='cancel-button']";
            return $x(xpath);
        }

        public SelenideElement inputFilter() {
            final var xpath = "//input[@data-test='member-subsystem-search-field']";
            return $x(xpath);
        }
    }
}
