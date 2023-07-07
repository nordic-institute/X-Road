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
// This file is the entry point for the library build
import { VueConstructor } from 'vue';
import ConfirmDialog from './components/ConfirmDialog.vue';
import Expandable from './components/Expandable.vue';
import FileUpload from './components/FileUpload.vue';
import FormLabel from './components/FormLabel.vue';
import HelpDialog from './components/HelpDialog.vue';
import HelpIcon from './components/HelpIcon.vue';
import CloseButton from './components/CloseButton.vue';
import Button from './components/Button.vue';
import SimpleDialog from './components/SimpleDialog.vue';
import StatusIcon from './components/StatusIcon.vue';
import SubViewTitle from './components/SubViewTitle.vue';
import XrdSearch from './components/XrdSearch.vue';
import XrdTable from './components/XrdTable.vue';
import XrdSubViewContainer from './components/XrdSubViewContainer.vue';
import EmptyPlaceholder from './components/EmptyPlaceholder.vue';
import EmptyPlaceholderRow from './components/EmptyPlaceholderRow.vue';

// Icons
import IconBase from './components/icons/IconBase.vue';
import XrdIconAdd from './components/icons/XrdIconAdd.vue';
import XrdIconAddCertificate from './components/icons/XrdIconAddCertificate.vue';
import XrdIconAddUser from './components/icons/XrdIconAddUser.vue';
import XrdIconCancel from './components/icons/XrdIconCancel.vue';
import XrdIconCertificate from './components/icons/XrdIconCertificate.vue';
import XrdIconChangeOwner from './components/icons/XrdIconChangeOwner.vue';
import XrdIconChecked from './components/icons/XrdIconChecked.vue';
import XrdIconChecker from './components/icons/XrdIconChecker.vue';
import XrdIconCheckmark from './components/icons/XrdIconCheckmark.vue';
import XrdIconClose from './components/icons/XrdIconClose.vue';
import XrdIconCopy from './components/icons/XrdIconCopy.vue';
import XrdIconDatabase from './components/icons/XrdIconDatabase.vue';
import XrdIconDatabaseBackup from './components/icons/XrdIconDatabaseBackup.vue';
import XrdIconDeclined from './components/icons/XrdIconDeclined.vue';
import XrdIconFolder from './components/icons/XrdIconFolder.vue';
import XrdIconFolderOutline from './components/icons/XrdIconFolderOutline.vue';
import XrdIconDownload from './components/icons/XrdIconDownload.vue';
import XrdIconDropdownOpen from './components/icons/XrdIconDropdownOpen.vue';
import XrdIconEdit from './components/icons/XrdIconEdit.vue';
import XrdIconError from './components/icons/XrdIconError.vue';
import XrdIconErrorNotification from './components/icons/XrdIconErrorNotification.vue';
import XrdIconImport from './components/icons/XrdIconImport.vue';
import XrdIconInProgress from './components/icons/XrdIconInProgress.vue';
import XrdIconKey from './components/icons/XrdIconKey.vue';
import XrdIconMenu from './components/icons/XrdIconMenu.vue';
import XrdIconPlus from './components/icons/XrdIconPlus.vue';
import XrdIconRemoveCertificate from './components/icons/XrdIconRemoveCertificate.vue';
import XrdIconRemoveUser from './components/icons/XrdIconRemoveUser.vue';
import XrdIconSearch from './components/icons/XrdIconSearch.vue';
import XrdIconSecurityServer from './components/icons/XrdIconSecurityServer.vue';
import XrdIconSortingArrow from './components/icons/XrdIconSortingArrow.vue';
import XrdIconTableBackup from './components/icons/XrdIconTableBackup.vue';
import XrdIconTooltip from './components/icons/XrdIconTooltip.vue';
import XrdIconUpload from './components/icons/XrdIconUpload.vue';
import XrdIconWarning from './components/icons/XrdIconWarning.vue';
import BackupsDataTable from './components/BackupAndRestore/BackupsDataTable.vue';
import BackupsToolbar from './components/BackupAndRestore/BackupsToolbar.vue';

// Import vee-validate so it's configured on the library build
import './plugins/vee-validate';
import './i18n';
import './assets/icons.css';

const SharedComponents = {
  install(Vue: VueConstructor): void {
    Vue.component('XrdConfirmDialog', ConfirmDialog);
    Vue.component('XrdExpandable', Expandable);
    Vue.component('XrdFileUpload', FileUpload);
    Vue.component('XrdFormLabel', FormLabel);
    Vue.component('XrdHelpDialog', HelpDialog);
    Vue.component('XrdHelpIcon', HelpIcon);
    Vue.component('XrdCloseButton', CloseButton);
    Vue.component('XrdButton', Button);
    Vue.component('XrdSimpleDialog', SimpleDialog);
    Vue.component('XrdStatusIcon', StatusIcon);
    Vue.component('XrdSubViewTitle', SubViewTitle);
    Vue.component('XrdSearch', XrdSearch);
    Vue.component('XrdTable', XrdTable);
    Vue.component('XrdIconBase', IconBase);
    Vue.component('XrdIconAdd', XrdIconAdd);
    Vue.component('XrdIconAddCertificate', XrdIconAddCertificate);
    Vue.component('XrdIconAddUser', XrdIconAddUser);
    Vue.component('XrdIconCancel', XrdIconCancel);
    Vue.component('XrdIconCertificate', XrdIconCertificate);
    Vue.component('XrdIconChangeOwner', XrdIconChangeOwner);
    Vue.component('XrdIconChecked', XrdIconChecked);
    Vue.component('XrdIconChecker', XrdIconChecker);
    Vue.component('XrdIconCheckmark', XrdIconCheckmark);
    Vue.component('XrdIconClose', XrdIconClose);
    Vue.component('XrdIconCopy', XrdIconCopy);
    Vue.component('XrdIconDatabase', XrdIconDatabase);
    Vue.component('XrdIconDatabaseBackup', XrdIconDatabaseBackup);
    Vue.component('XrdIconDeclined', XrdIconDeclined);
    Vue.component('XrdIconFolder', XrdIconFolder);
    Vue.component('XrdIconFolderOutline', XrdIconFolderOutline);
    Vue.component('XrdIconDownload', XrdIconDownload);
    Vue.component('XrdIconDropdownOpen', XrdIconDropdownOpen);
    Vue.component('XrdIconEdit', XrdIconEdit);
    Vue.component('XrdIconError', XrdIconError);
    Vue.component('XrdIconErrorNotification', XrdIconErrorNotification);
    Vue.component('XrdIconImport', XrdIconImport);
    Vue.component('XrdIconInProgress', XrdIconInProgress);
    Vue.component('XrdIconKey', XrdIconKey);
    Vue.component('XrdIconMenu', XrdIconMenu);
    Vue.component('XrdIconPlus', XrdIconPlus);
    Vue.component('XrdIconRemoveCertificate', XrdIconRemoveCertificate);
    Vue.component('XrdIconRemoveUser', XrdIconRemoveUser);
    Vue.component('XrdIconSearch', XrdIconSearch);
    Vue.component('XrdIconSecurityServer', XrdIconSecurityServer);
    Vue.component('XrdIconSortingArrow', XrdIconSortingArrow);
    Vue.component('XrdIconTableBackup', XrdIconTableBackup);
    Vue.component('XrdIconTooltip', XrdIconTooltip);
    Vue.component('XrdIconUpload', XrdIconUpload);
    Vue.component('XrdIconWarning', XrdIconWarning);
    Vue.component('XrdSubViewContainer', XrdSubViewContainer);
    Vue.component('XrdEmptyPlaceholder', EmptyPlaceholder);
    Vue.component('XrdEmptyPlaceholderRow', EmptyPlaceholderRow);
    Vue.component('XrdBackupsDataTable', BackupsDataTable);
    Vue.component('XrdBackupsToolbar', BackupsToolbar);
  },
};

export default SharedComponents;
