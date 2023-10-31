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
import { Client } from '@/openapi-types';
import { AxiosResponse } from 'axios';

// Filters an array of objects excluding specified object key
export function selectedFilter<T extends object, K extends keyof T>(
  arr: T[],
  search: string,
  excluded?: K,
): T[] {
  // Clean the search string
  const mySearch = search.toString().toLowerCase();
  if (mySearch.trim() === '') {
    return arr;
  }

  const filtered = arr.filter((g) => {
    let filteredKeys = Object.keys(g) as K[];

    // If there is an excluded key remove it from the keys
    if (excluded) {
      filteredKeys = filteredKeys.filter((value) => {
        return value !== excluded;
      });
    }

    return filteredKeys.find((key: K) => {
      return String(g[key]).toLowerCase().includes(mySearch);
    });
  });

  return filtered;
}

// Checks if the given WSDL URL valid
export function isValidWsdlURL(str: string): boolean {
  const pattern = new RegExp('(^(https?):///?)[-a-zA-Z0-9]');
  return !!pattern.test(str);
}

// Checks if the given REST URL is valid
export function isValidRestURL(str: string): boolean {
  return isValidWsdlURL(str);
}

// Save response data as a file
export function saveResponseAsFile(
  response: AxiosResponse,
  defaultFileName = 'certs.tar.gz',
): void {
  let suggestedFileName;
  const disposition = response.headers['content-disposition'];

  if (disposition && disposition.indexOf('attachment') !== -1) {
    const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
    const matches = filenameRegex.exec(disposition);
    if (matches != null && matches[1]) {
      suggestedFileName = matches[1].replace(/['"]/g, '');
    }
  }
  const effectiveFileName =
    suggestedFileName === undefined ? defaultFileName : suggestedFileName;
  const blob = new Blob([response.data], {
    type: response.headers['content-type'],
  });

  // Create a link to DOM and click it. This will trigger the browser to start file download.
  const link = document.createElement('a');
  link.href = window.URL.createObjectURL(blob);
  link.setAttribute('download', effectiveFileName);
  link.setAttribute('data-test', 'generated-download-link');
  document.body.appendChild(link);
  link.click();

  // cleanup
  document.body.removeChild(link);
  URL.revokeObjectURL(link.href);
}

// Finds if an array of clients has a client with given member class, member code and subsystem code.
export function containsClient(
  clients: Client[],
  memberClass: string,
  memberCode: string,
  subsystemCode: string | undefined,
): boolean {
  if (!memberClass || !memberCode || !subsystemCode) {
    return false;
  }

  if (
    clients.some((e: Client) => {
      if (e.member_class.toLowerCase() !== memberClass.toLowerCase()) {
        return false;
      }

      if (e.member_code.toLowerCase() !== memberCode.toLowerCase()) {
        return false;
      }

      if (e.subsystem_code !== subsystemCode) {
        return false;
      }
      return true;
    })
  ) {
    return true;
  }

  return false;
}

// Create a client ID
export function createClientId(
  instanceId: string,
  memberClass: string,
  memberCode: string,
  subsystemCode?: string,
): string {
  if (subsystemCode) {
    return `${instanceId}:${memberClass}:${memberCode}:${subsystemCode}`;
  }

  return `${instanceId}:${memberClass}:${memberCode}`;
}

// Debounce function
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const debounce = <F extends (...args: any[]) => any>(
  func: F,
  waitFor: number,
): ((...args: Parameters<F>) => Promise<ReturnType<F>>) => {
  let timeout: number | undefined;

  return (...args: Parameters<F>): Promise<ReturnType<F>> =>
    new Promise((resolve) => {
      if (timeout) {
        clearTimeout(timeout);
      }

      timeout = window.setTimeout(() => resolve(func(...args)), waitFor);
    });
};

// Check if a string or array is empty, null or undefined
export function isEmpty(str: string | []): boolean {
  return !str || 0 === str.length;
}

// Convert a class with immutable fields to mutable
export type Mutable<T> = {
  -readonly [K in keyof T]: T[K];
};

// Helper to copy text to clipboard
export function toClipboard(val: string): void {
  // If a dialog is overlaying the entire page we need to put the textbox inside it, otherwise it doesn't get copied
  const container =
    document.getElementsByClassName('v-dialog--active')[0] || document.body;
  const tempValueContainer = document.createElement('input');
  tempValueContainer.setAttribute('type', 'text');
  tempValueContainer.style.zIndex = '300';
  tempValueContainer.style.opacity = '0';
  tempValueContainer.style.filter = 'alpha(opacity=0)';
  tempValueContainer.setAttribute(
    'data-test',
    'generated-temp-value-container',
  );
  tempValueContainer.value = val;
  container.appendChild(tempValueContainer);
  tempValueContainer.select();
  document.execCommand('copy');
  container.removeChild(tempValueContainer);
}

// Deep clones an object or array using JSON
export function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}
