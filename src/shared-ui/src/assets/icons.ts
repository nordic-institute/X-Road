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
export type IconsId =
  | 'Add'
  | 'Cancel'
  | 'Certificate'
  | 'Checked'
  | 'Checker'
  | 'Checkmark'
  | 'Close'
  | 'Copy'
  | 'Database-backup'
  | 'Database'
  | 'Datepicker'
  | 'Declined'
  | 'Download'
  | 'Dropdown-open'
  | 'Edit'
  | 'Error-notification'
  | 'Error'
  | 'Folder-outline'
  | 'Folder'
  | 'Import'
  | 'In-progress'
  | 'Key'
  | 'Menu'
  | 'Plus'
  | 'Search'
  | 'Security-Server'
  | 'Sorting-arrow'
  | 'Table-backup'
  | 'Tooltip'
  | 'Upload'
  | 'Warning';

export type IconsKey =
  | 'Add'
  | 'Cancel'
  | 'Certificate'
  | 'Checked'
  | 'Checker'
  | 'Checkmark'
  | 'Close'
  | 'Copy'
  | 'DatabaseBackup'
  | 'Database'
  | 'Datepicker'
  | 'Declined'
  | 'Download'
  | 'DropdownOpen'
  | 'Edit'
  | 'ErrorNotification'
  | 'Error'
  | 'FolderOutline'
  | 'Folder'
  | 'Import'
  | 'InProgress'
  | 'Key'
  | 'Menu'
  | 'Plus'
  | 'Search'
  | 'SecurityServer'
  | 'SortingArrow'
  | 'TableBackup'
  | 'Tooltip'
  | 'Upload'
  | 'Warning';

export enum Icons {
  Add = 'Add',
  Cancel = 'Cancel',
  Certificate = 'Certificate',
  Checked = 'Checked',
  Checker = 'Checker',
  Checkmark = 'Checkmark',
  Close = 'Close',
  Copy = 'Copy',
  DatabaseBackup = 'Database-backup',
  Database = 'Database',
  Datepicker = 'Datepicker',
  Declined = 'Declined',
  Download = 'Download',
  DropdownOpen = 'Dropdown-open',
  Edit = 'Edit',
  ErrorNotification = 'Error-notification',
  Error = 'Error',
  FolderOutline = 'Folder-outline',
  Folder = 'Folder',
  Import = 'Import',
  InProgress = 'In-progress',
  Key = 'Key',
  Menu = 'Menu',
  Plus = 'Plus',
  Search = 'Search',
  SecurityServer = 'Security-Server',
  SortingArrow = 'Sorting-arrow',
  TableBackup = 'Table-backup',
  Tooltip = 'Tooltip',
  Upload = 'Upload',
  Warning = 'Warning',
}

export const ICONS_CODEPOINTS: { [key in Icons]: string } = {
  [Icons.Add]: '61697',
  [Icons.Cancel]: '61698',
  [Icons.Certificate]: '61699',
  [Icons.Checked]: '61700',
  [Icons.Checker]: '61701',
  [Icons.Checkmark]: '61702',
  [Icons.Close]: '61703',
  [Icons.Copy]: '61704',
  [Icons.DatabaseBackup]: '61705',
  [Icons.Database]: '61706',
  [Icons.Datepicker]: '61707',
  [Icons.Declined]: '61708',
  [Icons.Download]: '61709',
  [Icons.DropdownOpen]: '61710',
  [Icons.Edit]: '61711',
  [Icons.ErrorNotification]: '61712',
  [Icons.Error]: '61713',
  [Icons.FolderOutline]: '61714',
  [Icons.Folder]: '61715',
  [Icons.Import]: '61716',
  [Icons.InProgress]: '61717',
  [Icons.Key]: '61718',
  [Icons.Menu]: '61719',
  [Icons.Plus]: '61720',
  [Icons.Search]: '61721',
  [Icons.SecurityServer]: '61722',
  [Icons.SortingArrow]: '61723',
  [Icons.TableBackup]: '61724',
  [Icons.Tooltip]: '61725',
  [Icons.Upload]: '61726',
  [Icons.Warning]: '61727',
};
