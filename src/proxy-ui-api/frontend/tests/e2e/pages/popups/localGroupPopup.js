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

var localGroupPopupCommands = {
  waitForDescription: function (value) {
    this.waitForValue('@localGroupDescription', value);
    return this;
  },
  modifyCode: function (code) {
    this.waitForNonEmpty('@localGroupCode');
    this.clearValue2('@localGroupCode');
    this.setValue('@localGroupCode', code);
    return this;
  },
  modifyDescription: function (description) {
    this.waitForNonEmpty('@localGroupDescription');
    this.clearValue2('@localGroupDescription');
    this.setValue('@localGroupDescription', description);
    return this;
  },
  deleteThisGroup: function () {
    this.click('@localGroupDeleteButton');
    return this;
  },
  openAddMembers: function () {
    this.click('@localGroupAddMembersButton');
    return this;
  },
  searchMembers: function () {
    this.click('@localGroupSearchWrap');
    this.click('@localGroupSearchButton');
    return this;
  },
  addSelectedMembers: function () {
    this.click('@localGroupAddSelectedButton');
    return this;
  },
  cancelAddMembersDialog: function () {
    this.click('@localGroupCancelAddButton');
    return this;
  },
  selectNewTestComMember: function () {
    this.click('@localGroupTestComCheckbox');
    return this;
  },
  selectMember: function (member) {
    this.api.click(
      `//tr[.//*[contains(text(), "${member}")]]//*[contains(@class, "v-input--selection-controls__ripple")]`,
    );
    return this;
  },
  clickRemoveAll: function () {
    this.click('@localGroupRemoveAllButton');
    return this;
  },
  clickRemoveTestComMember: function () {
    this.click('@localGroupTestComRemoveButton');
    return this;
  },
  confirmMemberRemove: function () {
    this.click('@localGroupRemoveYesButton');
    return this;
  },
  cancelMemberRemove: function () {
    this.click('@localGroupRemoveCancelButton');
    return this;
  },
  confirmDelete: function () {
    this.click('@localGroupRemoveYesButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@localGroupRemoveCancelButton');
    return this;
  },
  close: function () {
    this.click('@localGroupPopupCloseButton');
    return this;
  },
};

const localGroupPopup = {
  selector:
    '//div[contains(@class, "xrd-tab-max-width") and .//div[contains(@class, "cert-hash") and @data-test="local-group-title"]]',
  commands: [localGroupPopupCommands],
  elements: {
    groupIdentifier: '//span[contains(@class, "identifier-wrap")]', // Title in the "xrd-sub-view-title" component
    localGroupAddMembersButton: '//button[@data-test="add-members-button"]',
    localGroupRemoveAllButton:
      '//button[@data-test="remove-all-members-button"]',
    localGroupDeleteButton: '//button[@data-test="delete-local-group-button"]',
    localGroupAddSelectedButton:
      '//button[.//*[contains(text(), "Add selected")]]',
    localGroupSearchButton: '//button[.//*[contains(text(), "Search")]]',
    localGroupCancelAddButton: '//button[.//*[contains(text(), "Cancel")]]',
    localGroupTestComCheckbox:
      '//tr[.//*[contains(text(), "TestCom")]]//*[contains(@class, "v-input--selection-controls__ripple")]',
    localGroupRemoveMemberButton:
      '//button[.//*[contains(text(), "Add group")]]',
    localGroupSearchWrap: '//div[contains(@class, "search-wrap")]',
    localGroupRemoveYesButton: '//button[@data-test="dialog-save-button"]',
    localGroupRemoveCancelButton: '//button[@data-test="dialog-cancel-button"]',
    localGroupTestComRemoveButton:
      '//tr[.//*[contains(text(), "TestCom")]]//button[.//*[contains(text(), "Remove")]]',
    localGroupTestGovRemoveButton:
      '//tr[.//*[contains(text(), "TestGov")]]//button[.//*[contains(text(), "Remove")]]',
    localGroupTestOrgRemoveButton:
      '//tr[.//*[contains(text(), "TestOrg")]]//button[.//*[contains(text(), "Remove")]]',
    localGroupDescription:
      '//input[@data-test="local-group-edit-description-input"]',
    localGroupPopupCloseButton: '//button[.//*[contains(text(), "Close")]]',
  },
};

module.exports = localGroupPopup;
