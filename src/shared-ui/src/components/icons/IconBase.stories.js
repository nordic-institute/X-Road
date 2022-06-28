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
import IconBase from './IconBase.vue';
import IconSvg from './IconSvg.vue';
// New svg icons
import XrdIconAdd from './XrdIconAdd.vue';
import XrdIconCancel from './XrdIconCancel.vue';
import XrdIconCertificate from './XrdIconCertificate.vue';
import XrdIconChecked from './XrdIconChecked.vue';
import XrdIconChecker from './XrdIconChecker.vue';
import XrdIconCheckmark from './XrdIconCheckmark.vue';
import XrdIconClose from './XrdIconClose.vue';
import XrdIconCopy from './XrdIconFolder.vue';
import XrdIconDatabase from './XrdIconDatabase.vue';
import XrdIconDatabaseBackup from './XrdIconDatabaseBackup.vue';
import XrdIconDeclined from './XrdIconDeclined.vue';
//
import XrdIconDownload from './XrdIconDownload.vue';
import XrdIconDropdownOpen from './XrdIconDropdownOpen.vue';
import XrdIconEdit from './XrdIconEdit.vue';
import XrdIconError from './XrdIconError.vue';
import XrdIconErrorNotification from './XrdIconErrorNotification.vue';

import XrdIconImport from './XrdIconImport.vue';
import XrdIconInProgress from './XrdIconInProgress.vue';
import XrdIconKey from './XrdIconKey.vue';
import XrdIconMenu from './XrdIconMenu.vue';
import XrdIconPlus from './XrdIconPlus.vue';
import XrdIconSearch from './XrdIconSearch.vue';
import XrdIconSecurityServer from './XrdIconSecurityServer.vue';
import XrdIconSortingArrow from './XrdIconSortingArrow.vue';
import XrdIconTableBackup from './XrdIconTableBackup.vue';
import XrdIconTooltip from './XrdIconTooltip.vue';
import XrdIconUpload from './XrdIconUpload.vue';
import XrdIconWarning from './XrdIconWarning.vue';

import XrdIconFolder from './XrdIconFolder.vue';
import XrdIconFolderOutline from './XrdIconFolderOutline.vue';

import XrdIconAddUser from './XrdIconAddUser.vue';
import XrdIconAddCertificate from './XrdIconAddCertificate.vue';
import XrdIconChangeOwner from './XrdIconChangeOwner.vue';
import XrdIconRemoveUser from './XrdIconRemoveUser.vue';
import XrdIconRemoveCertificate from './XrdIconRemoveCertificate.vue';

export default {
  title: 'X-Road/Icon',
  component: IconBase,
  argTypes: {
    color: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: {
    IconBase,
    IconSvg,
    XrdIconAdd,
    XrdIconAddCertificate,
    XrdIconAddUser,
    XrdIconCancel,
    XrdIconCertificate,
    XrdIconChangeOwner,
    XrdIconChecked,
    XrdIconChecker,
    XrdIconCheckmark,
    XrdIconClose,
    XrdIconCopy,
    XrdIconDatabase,
    XrdIconDatabaseBackup,
    XrdIconDeclined,
    XrdIconFolder,
    XrdIconFolderOutline,
    XrdIconDownload,
    XrdIconDropdownOpen,
    XrdIconEdit,
    XrdIconError,
    XrdIconErrorNotification,
    XrdIconImport,
    XrdIconInProgress,
    XrdIconKey,
    XrdIconMenu,
    XrdIconPlus,
    XrdIconRemoveCertificate,
    XrdIconRemoveUser,
    XrdIconSearch,
    XrdIconSecurityServer,
    XrdIconSortingArrow,
    XrdIconTableBackup,
    XrdIconTooltip,
    XrdIconUpload,
    XrdIconWarning,
  },
  template: `<p>
  <i class="icon-Add icon"></i>
  <icon-base icon-name="icon-Edit"></icon-base>
  <icon-base
    width="16"
    height="16"
    icon-name="small 16x16"
    v-bind="$props"
  ><XrdIconFolderOutline /></icon-base>

  <v-icon>icon-Edit</v-icon>
  <!-- or you can use the default, which is 18 -->
  <icon-svg icon-name="addUser"></icon-svg>
  <icon-svg icon-name="addCertificate"></icon-svg>
  <icon-svg icon-name="removeUser"></icon-svg>
  <icon-svg icon-name="removeCertificate"></icon-svg>
  <icon-svg icon-name="changeOwner"></icon-svg>
  <br><br>

  <br>Font icons - Used in Security Server - old "deprecated" way - 
  Check the src/assets/icons.html file for the generated example file<br>
  <v-icon v-bind="$props">icon-Add</v-icon>
  <v-icon v-bind="$props">icon-Cancel</v-icon>
  <v-icon v-bind="$props">icon-Certificate</v-icon>
  <v-icon v-bind="$props">icon-Checked</v-icon>
  <v-icon v-bind="$props">icon-Checker</v-icon>
  <v-icon v-bind="$props">icon-Checkmark</v-icon>
  <v-icon v-bind="$props">icon-Close</v-icon>
  <v-icon v-bind="$props">icon-Copy</v-icon>
  <v-icon v-bind="$props">icon-Database-backup</v-icon>
  <v-icon v-bind="$props">icon-Database</v-icon>
  <v-icon v-bind="$props">icon-Datepicker</v-icon>
  <v-icon v-bind="$props">icon-Declined</v-icon>
  <v-icon v-bind="$props">icon-Download</v-icon>
  <v-icon v-bind="$props">icon-Dropdown-open</v-icon>
  <v-icon v-bind="$props">icon-Edit</v-icon>
  <v-icon v-bind="$props">icon-Error-notification</v-icon>
  <v-icon v-bind="$props">icon-Error</v-icon>
  <v-icon v-bind="$props">icon-Folder-outline</v-icon>
  <v-icon v-bind="$props">icon-Folder</v-icon>
  <v-icon v-bind="$props">icon-Import</v-icon>
  <v-icon v-bind="$props">icon-In-progress</v-icon>
  <v-icon v-bind="$props">icon-Key</v-icon>
  <v-icon v-bind="$props">icon-Menu</v-icon>
  <v-icon v-bind="$props">icon-Plus</v-icon>
  <v-icon v-bind="$props">icon-Search</v-icon>
  <v-icon v-bind="$props">icon-Security-Server</v-icon>
  <v-icon v-bind="$props">icon-Sorting-arrow</v-icon>
  <v-icon v-bind="$props">icon-Table-backup</v-icon>
  <v-icon v-bind="$props">icon-Tooltip</v-icon>
  <v-icon v-bind="$props">icon-Upload</v-icon>
  <v-icon v-bind="$props">icon-Warning</v-icon>

  <br>

<br>SVG icons - Used in Central Server new UI and the preferred "new" way<br>
<icon-base icon-name="XrdIconAdd" v-bind="$props"><XrdIconAdd /></icon-base>
<icon-base icon-name="XrdIconCancel" v-bind="$props"><XrdIconCancel /></icon-base>
<icon-base icon-name="XrdIconCertificate" v-bind="$props"><XrdIconCertificate /></icon-base>
<icon-base icon-name="XrdIconChecked" v-bind="$props"><XrdIconChecked /></icon-base>
<icon-base icon-name="XrdIconChecker" v-bind="$props"><XrdIconChecker /></icon-base>
<icon-base icon-name="XrdIconCheckmark" v-bind="$props"><XrdIconCheckmark /></icon-base>
<icon-base icon-name="XrdIconClose" v-bind="$props"><XrdIconClose /></icon-base>
<icon-base icon-name="XrdIconCopy" v-bind="$props"><XrdIconCopy /></icon-base>
<icon-base icon-name="XrdIconDatabase" v-bind="$props"><XrdIconDatabase /></icon-base>
<icon-base icon-name="XrdIconDatabaseBackup" v-bind="$props"><XrdIconDatabaseBackup /></icon-base>
<icon-base icon-name="XrdIconDeclined" v-bind="$props"><XrdIconDeclined /></icon-base>
<icon-base icon-name="XrdIconFolder" v-bind="$props"><XrdIconFolder /></icon-base>
<icon-base icon-name="XrdIconFolderOutline" v-bind="$props"><XrdIconFolderOutline /></icon-base>
<icon-base icon-name="XrdIconDownload" v-bind="$props"><XrdIconDownload /></icon-base>
<icon-base icon-name="XrdIconDropdownOpen" v-bind="$props"><XrdIconDropdownOpen /></icon-base>
<icon-base icon-name="XrdIconEdit" v-bind="$props"><XrdIconEdit /></icon-base>
<icon-base icon-name="XrdIconError" v-bind="$props"><XrdIconError /></icon-base>
<icon-base icon-name="XrdIconErrorNotification" v-bind="$props"><XrdIconErrorNotification /></icon-base>
<icon-base icon-name="XrdIconImport" v-bind="$props"><XrdIconImport /></icon-base>
<icon-base icon-name="XrdIconInProgress" v-bind="$props"><XrdIconInProgress /></icon-base>
<icon-base icon-name="XrdIconKey" v-bind="$props"><XrdIconKey /></icon-base>
<icon-base icon-name="XrdIconMenu" v-bind="$props"><XrdIconMenu /></icon-base>
<icon-base icon-name="XrdIconPlus" v-bind="$props"><XrdIconPlus /></icon-base>
<icon-base icon-name="XrdIconSearch" v-bind="$props"><XrdIconSearch /></icon-base>
<icon-base icon-name="XrdIconSecurityServer" v-bind="$props"><XrdIconSecurityServer /></icon-base>
<icon-base icon-name="XrdIconSortingArrow" v-bind="$props"><XrdIconSortingArrow /></icon-base>
<icon-base icon-name="XrdIconTableBackup" v-bind="$props"><XrdIconTableBackup /></icon-base>
<icon-base icon-name="XrdIconTooltip" v-bind="$props"><XrdIconTooltip /></icon-base>
<icon-base icon-name="XrdIconUpload" v-bind="$props"><XrdIconUpload /></icon-base>
<icon-base icon-name="XrdIconWarning" v-bind="$props"><XrdIconWarning /></icon-base>
<br>
<br>Multicolor SVG icons<br>
<icon-base icon-name="XrdIconChangeOwner" v-bind="$props"><XrdIconChangeOwner /></icon-base>
<icon-base icon-name="XrdIconAddUser" v-bind="$props"><XrdIconAddUser /></icon-base>
<icon-base icon-name="XrdIconAddCertificate" v-bind="$props"><XrdIconAddCertificate /></icon-base>
<icon-base icon-name="XrdIconRemoveCertificate" v-bind="$props"><XrdIconRemoveCertificate /></icon-base>
<icon-base icon-name="XrdIconRemoveUser" v-bind="$props"><XrdIconRemoveUser /></icon-base>
</p>`,
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  color: 'blue',
};
