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
import { App } from 'vue';
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
import './plugins/i18n';
import './assets/icons.css';

const SharedComponents = {
  install: (app: App) => {
    app.component('XrdConfirmDialog', ConfirmDialog);
    app.component('XrdExpandable', Expandable);
    app.component('XrdFileUpload', FileUpload);
    app.component('XrdFormLabel', FormLabel);
    app.component('XrdHelpDialog', HelpDialog);
    app.component('XrdHelpIcon', HelpIcon);
    app.component('XrdCloseButton', CloseButton);
    app.component('XrdButton', Button);
    app.component('XrdSimpleDialog', SimpleDialog);
    app.component('XrdStatusIcon', StatusIcon);
    app.component('XrdSubViewTitle', SubViewTitle);
    app.component('XrdSearch', XrdSearch);
    app.component('XrdTable', XrdTable);
    app.component('XrdIconBase', IconBase);
    app.component('XrdIconAdd', XrdIconAdd);
    app.component('XrdIconAddCertificate', XrdIconAddCertificate);
    app.component('XrdIconAddUser', XrdIconAddUser);
    app.component('XrdIconCancel', XrdIconCancel);
    app.component('XrdIconCertificate', XrdIconCertificate);
    app.component('XrdIconChangeOwner', XrdIconChangeOwner);
    app.component('XrdIconChecked', XrdIconChecked);
    app.component('XrdIconChecker', XrdIconChecker);
    app.component('XrdIconCheckmark', XrdIconCheckmark);
    app.component('XrdIconClose', XrdIconClose);
    app.component('XrdIconCopy', XrdIconCopy);
    app.component('XrdIconDatabase', XrdIconDatabase);
    app.component('XrdIconDatabaseBackup', XrdIconDatabaseBackup);
    app.component('XrdIconDeclined', XrdIconDeclined);
    app.component('XrdIconFolder', XrdIconFolder);
    app.component('XrdIconFolderOutline', XrdIconFolderOutline);
    app.component('XrdIconDownload', XrdIconDownload);
    app.component('XrdIconDropdownOpen', XrdIconDropdownOpen);
    app.component('XrdIconEdit', XrdIconEdit);
    app.component('XrdIconError', XrdIconError);
    app.component('XrdIconErrorNotification', XrdIconErrorNotification);
    app.component('XrdIconImport', XrdIconImport);
    app.component('XrdIconInProgress', XrdIconInProgress);
    app.component('XrdIconKey', XrdIconKey);
    app.component('XrdIconMenu', XrdIconMenu);
    app.component('XrdIconPlus', XrdIconPlus);
    app.component('XrdIconRemoveCertificate', XrdIconRemoveCertificate);
    app.component('XrdIconRemoveUser', XrdIconRemoveUser);
    app.component('XrdIconSearch', XrdIconSearch);
    app.component('XrdIconSecurityServer', XrdIconSecurityServer);
    app.component('XrdIconSortingArrow', XrdIconSortingArrow);
    app.component('XrdIconTableBackup', XrdIconTableBackup);
    app.component('XrdIconTooltip', XrdIconTooltip);
    app.component('XrdIconUpload', XrdIconUpload);
    app.component('XrdIconWarning', XrdIconWarning);
    app.component('XrdSubViewContainer', XrdSubViewContainer);
    app.component('XrdEmptyPlaceholder', EmptyPlaceholder);
    app.component('XrdEmptyPlaceholderRow', EmptyPlaceholderRow);
    app.component('XrdBackupsDataTable', BackupsDataTable);
    app.component('XrdBackupsToolbar', BackupsToolbar);
  },
};

export default SharedComponents;
