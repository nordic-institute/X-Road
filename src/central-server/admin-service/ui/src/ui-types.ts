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
/*
 TypeScript typings that are used in UI, but not in backend.
 These are not in openapi definitions.
*/
import { Location } from 'vue-router';

// Interface for Tab data
export interface Tab {
  key: string; // Unique key needed for v-for looping
  name: string; // Localisation key for the name
  to: Location; // Contains the path or path name for router. Same type as https://router.vuejs.org/api/#to
  permissions?: string[]; // Permissions needed to view this tab
}

// Action info for notification. In practise the "action" is navigtion to a given route.
export interface NotificationAction {
  icon: string;
  text: string;
  route: string;
}

// Data for snackbar notification
export interface Notification {
  timeAdded: number;
  timeout: number;
  errorMessage?: string; // Localised error message
  successMessage?: string; // Localised success message
  show: boolean;
  count: number;
  validationErrors?: ValidationError[];
  errorCode?: string; // x-road error code
  metaData?: string[];
  errorId?: string;
  errorObjectAsString?: string;
  responseData?: string;
  url?: string;
  status?: string; // http status code
  action?: NotificationAction;
  preserve?: boolean;
}

export type ValidationError = {
  field: string;
  errorCodes: string[];
};

// Notification with an action is called with this
export interface ActionError {
  errorMessage?: string;
  action?: NotificationAction;
}

export interface DataQuery {
  itemsPerPage: number;
  page: number;
  sortBy?: string;
  sortOrder?: string;
  search?: string;
}

export interface PagingOptions {
  itemsPerPage: number;
  page: number;
  sortBy: { key: string; order?: boolean | 'asc' | 'desc' }[];
}

export interface DataTableHeader {
  title: string;
  align?: string;
  key: string;
  sortable?: boolean;
}

export enum Event {
  Add = 'add',
  Edit = 'edit',
  Delete = 'delete',
  Cancel = 'cancel',
  Select = 'select',
  Generate = 'generate',
  Confirm = 'confirm',
  Upload = 'upload',
  ModelValue = 'update:model-value',
}
