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
import type { App } from 'vue';

//resources&assets
import '@fontsource/open-sans';
import '@mdi/font/css/materialdesignicons.css';
import './assets/icons.css';

//plugins
import vuetify from './plugins/vuetify';
import createSharedI18n from './plugins/i18n';

//components
import XrdButton from './components/XrdButton.vue';
import XrdCloseButton from './components/XrdCloseButton.vue';
import XrdAlert from './components/XrdAlert.vue';
import XrdEmptyPlaceholder from './components/XrdEmptyPlaceholder.vue';
import XrdEmptyPlaceholderRow from './components/XrdEmptyPlaceholderRow.vue';
import XrdExpandable from './components/XrdExpandable.vue';
import XrdFileUpload from './components/XrdFileUpload.vue';
import XrdFormLabel from './components/XrdFormLabel.vue';
import XrdHelpIcon from './components/XrdHelpIcon.vue';
import XrdSimpleDialog from './components/XrdSimpleDialog.vue';
import XrdConfirmDialog from './components/XrdConfirmDialog.vue';
import XrdHelpDialog from './components/XrdHelpDialog.vue';
import XrdStatusIcon from './components/XrdStatusIcon.vue';
import XrdSubViewContainer from './components/XrdSubViewContainer.vue';
import XrdSubViewTitle from './components/XrdSubViewTitle.vue';
import XrdSearch from './components/XrdSearch.vue';
import XrdTable from './components/XrdTable.vue';
import XrdBackupsDataTable from './components/backups-and-restore/XrdBackupsDataTable.vue';
import XrdBackupsToolbar from './components/backups-and-restore/XrdBackupsToolbar.vue';

//icons
import XrdIconAdd from "./components/icons/XrdIconAdd.vue";
import XrdIconBase from "./components/icons/XrdIconBase.vue";
import XrdIconChecked from "./components/icons/XrdIconChecked.vue";
import XrdIconChecker from "./components/icons/XrdIconChecker.vue";
import XrdIconClose from "./components/icons/XrdIconClose.vue";
import XrdIconCopy from "./components/icons/XrdIconCopy.vue";
import XrdIconFolderOutline from "./components/icons/XrdIconFolderOutline.vue";

function createSharedUi() {
  return {
    install(app: App) {
      app
        .use(vuetify)
        .component('XrdIconAdd', XrdIconAdd)
        .component('XrdIconBase', XrdIconBase)
        .component('XrdIconChecked', XrdIconChecked)
        .component('XrdIconChecker', XrdIconChecker)
        .component('XrdIconClose', XrdIconClose)
        .component('XrdIconCopy', XrdIconCopy)
        .component('XrdIconFolderOutline', XrdIconFolderOutline)
        .component('XrdButton', XrdButton)
        .component('XrdCloseButton', XrdCloseButton)
        .component('XrdAlert', XrdAlert)
        .component('XrdEmptyPlaceholder', XrdEmptyPlaceholder)
        .component('XrdEmptyPlaceholderRow', XrdEmptyPlaceholderRow)
        .component('XrdExpandable', XrdExpandable)
        .component('XrdFileUpload', XrdFileUpload)
        .component('XrdFormLabel', XrdFormLabel)
        .component('XrdHelpIcon', XrdHelpIcon)
        .component('XrdSimpleDialog', XrdSimpleDialog)
        .component('XrdConfirmDialog', XrdConfirmDialog)
        .component('XrdHelpDialog', XrdHelpDialog)
        .component('XrdStatusIcon', XrdStatusIcon)
        .component('XrdSubViewContainer', XrdSubViewContainer)
        .component('XrdSubViewTitle', XrdSubViewTitle)
        .component('XrdSearch', XrdSearch)
        .component('XrdTable', XrdTable)
        .component('XrdBackupsDataTable', XrdBackupsDataTable)
        .component('XrdBackupsToolbar', XrdBackupsToolbar);
    },
  };
}

export {
  createSharedUi,
  createSharedI18n,
  XrdButton,
  XrdCloseButton,
  XrdAlert,
  XrdEmptyPlaceholder,
  XrdEmptyPlaceholderRow,
  XrdExpandable,
  XrdFileUpload,
  XrdFormLabel,
  XrdHelpIcon,
  XrdSimpleDialog,
  XrdConfirmDialog,
  XrdHelpDialog,
  XrdStatusIcon,
  XrdSubViewContainer,
  XrdSubViewTitle,
  XrdSearch,
  XrdTable,
  XrdBackupsDataTable,
  XrdBackupsToolbar,
  XrdIconAdd,
  XrdIconBase,
  XrdIconChecked,
  XrdIconChecker,
  XrdIconClose,
  XrdIconCopy,
  XrdIconFolderOutline
};
