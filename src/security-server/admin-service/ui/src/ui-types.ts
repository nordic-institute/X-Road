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
/*
 TypeScript typings that are used in UI, but not in backend.
 These are not in openapi definitions.
*/
import { Client } from '@/openapi-types';
import { RouteLocationNamedRaw } from 'vue-router';

// Interface for Tab data
export interface Tab {
  key: string; // Unique key needed for v-for looping
  name: string; // Localisation key for the name
  to: RouteLocationNamedRaw; // Contains the path or path name for router. Same type as https://router.vuejs.org/api/#to
  permissions?: string[]; // Permissions needed to view this tab
}

// Extension for Client
export type ExtendedClient = Client & {
  visibleName: string;
  isFiltered?: boolean;
  type: string;
  id: string;
};

// Used in service clients views for listing services than can be granted access rights to
export interface ServiceCandidate {
  service_code: string;
  service_title?: string;
  id: string;
}

// The result of the FileUpload components fileChanged event
export type FileUploadResult = {
  buffer: ArrayBuffer;
  file: File;
};

// Data for snackbar notification
export interface Notification {
  timeAdded: number;
  timeout: number;
  errorMessage?: string; // Localised error message
  successMessage?: string; // Localised success message
  show: boolean;
  count: number;
  isWarning: boolean;
  validationErrors?: ValidationError[];
  errorCode?: string; // x-road error code
  metaData?: string[];
  errorId?: string;
  errorObjectAsString?: string;
  responseData?: string;
  url?: string;
  status?: string;
  preserve?: boolean;
}

export type SessionStatus = {
  valid: boolean;
};

export type ValidationError = {
  field: string;
  errorCodes: string[];
};

export type DataTableHeader = {
  key: string;
  title: string;
  align?: 'start' | 'end';
  sortable?: boolean;
};
