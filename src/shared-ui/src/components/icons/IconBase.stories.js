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
import IconCalendar from './IconCalendar.vue';
import IconCertificate from './IconCertificate.vue';
import IconDeclined from './IconDeclined.vue';
import IconKey from './IconKey.vue';
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
    iconColor: { control: 'text' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: {
    IconBase,
    IconCertificate,
    IconCalendar,
    IconDeclined,
    IconKey,
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
  <v-icon>icon-AddCertificate</v-icon>
  <v-icon>icon-ChangeOwner</v-icon>
  <v-icon>icon-Key</v-icon>
  <v-icon dark>icon-Add</v-icon>
  <v-icon dark>mdi-close-circle</v-icon>
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
  <icon-base icon-name="calendar" v-bind="$props"><icon-calendar /></icon-base>

  <icon-base icon-name="calendar" v-bind="$props"><IconDeclined /></icon-base>
  <icon-base icon-name="calendar" v-bind="$props"><IconKey /></icon-base>
  <icon-base icon-name="calendar" v-bind="$props"><icon-key /></icon-base>
  <icon-base icon-name="calendar" v-bind="$props"><icon-key /></icon-base>
  <!-- or make it a little bigger too :) -->
  <icon-base
    width="30"
    height="30"
    icon-name="calendar"
    v-bind="$props"
  ><icon-certificate /></icon-base>
  <icon-svg icon-name="addUser"></icon-svg>
  <icon-svg icon-name="addCertificate"></icon-svg>
  <icon-svg icon-name="removeUser"></icon-svg>
  <icon-svg icon-name="removeCertificate"></icon-svg>
  <icon-svg icon-name="changeOwner"></icon-svg>
  <br><br>

<br>SVG icons<br>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconAdd /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconAddUser /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconCancel /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconCertificate /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconChecked /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconChecker /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconCheckmark /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconClose /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconCopy /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconDatabase /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconDatabaseBackup /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconDeclined /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconFolder /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconFolderOutline /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconDownload /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconDropdownOpen /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconEdit /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconError /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconErrorNotification /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconImport /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconInProgress /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconKey /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconMenu /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconPlus /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconSearch /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconSecurityServer /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconSortingArrow /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconTableBackup /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconTooltip /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconUpload /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconWarning /></icon-base>

<br>Multicolor SVG icons<br>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconChangeOwner /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconAddUser /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconAddCertificate /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconRemoveCertificate /></icon-base>
<icon-base icon-name="folder outline" v-bind="$props"><XrdIconRemoveUser /></icon-base>

</p>`,
});

export const Primary = Template.bind({});
Primary.args = {
  primary: true,
  iconColor: 'blue',
};
