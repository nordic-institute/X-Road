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

// Filters an array of objects excluding specified object key
import { NavigationFailure } from 'vue-router';
import { ClientId, ErrorInfo, ManagementRequestType } from '@/openapi-types';
import i18n from '@/i18n';
import { AxiosError, AxiosResponse } from 'axios';

export function selectedFilter<T, K extends keyof T>(
  arr: T[],
  search: string,
  excluded?: K,
): T[] {
  // Clean the search string
  const mysearch = search.toString().toLowerCase();
  if (mysearch.trim() === '') {
    return arr;
  }

  return arr.filter((g) => {
    let filteredKeys = Object.keys(g) as K[];

    // If there is an excluded key remove it from the keys
    if (excluded) {
      filteredKeys = filteredKeys.filter((value) => {
        return value !== excluded;
      });
    }

    return filteredKeys.find((key: K) => {
      return String(g[key]).toLowerCase().includes(mysearch);
    });
  });
}

// Checks if the given URL is valid
export function isValidUrl(str: string): boolean {
  try {
    const url = new URL(str);
    const protocolPattern = /^https?:$/;
    const hostPattern =
      /^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]*[A-Za-z0-9])$/;
    return protocolPattern.test(url.protocol) && hostPattern.test(url.hostname);
  } catch (_) {
    return false;
  }
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

export function swallowRedirectedNavigationError(
  error: NavigationFailure,
): void {
  // NavigationFailureType.redirected = 2, but does not work here?
  if (2 == error.type) {
    // ignore errors caused by redirect in beforeEach route guard
    // eslint-disable-next-line no-console
    console.debug('Redirected navigation error ignored', error);
    return;
  }
  throw error;
}

export function getErrorInfo(axiosError: AxiosError): ErrorInfo {
  return axiosError?.response?.data || { status: 0 };
}

/*
 * isFieldError  -- checks if ErrorInfo contains Spring  Validation failure data
 * @params:
 *    errorInfo:  ErrorInfo  returned from Spring backend
 */
export function isFieldError(errorInfo: ErrorInfo): boolean {
  const errorStatus = errorInfo?.status;
  return 400 === errorStatus && 'validation_failure' === errorInfo?.error?.code;
}

/*
 *  getTranslatedFieldErrors -  array of translations of the given Validation failure field errors
 *  @params:
 *     fieldName: key to the fieldError record for the needed form field. generated by Spring Validation
 *     fieldError: Record of field validation error id's obtained e.g. from  <AxiosError>.response?.data.error?.validation_errors
 *                 that's generated by Spring validators
 *
 */

export function getTranslatedFieldErrors(
  fieldName: string,
  fieldError: Record<string, string[]>,
): string[] {
  const errors: string[] = fieldError[fieldName];
  if (errors) {
    return errors.map((errorKey: string) => {
      return i18n.t(`validationError.${errorKey}Field`).toString();
    });
  } else {
    return [];
  }
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

      timeout = setTimeout(() => resolve(func(...args)), waitFor);
    });
};

// Check if a string or array is empty, null or undefined
export function isEmpty(str: string | []): boolean {
  return !str || 0 === str.length;
}

// Get identifier in format CS:ORG:MEMBER[:SUBSYSTEM]
export function toIdentifier(client: ClientId): string {
  let identifier = toMemberId(client);
  if (client.subsystem_code) {
    identifier += ':' + client.subsystem_code;
  }
  return identifier;
}

export function toMemberId(client: ClientId): string {
  return `${client.instance_id}:${toShortMemberId(client)}`;
}

export function toShortMemberId(client: ClientId): string {
  return `${client.member_class}:${client.member_code}`;
}

export function managementTypeToText(
  type: ManagementRequestType | undefined,
): string {
  switch (type) {
    case ManagementRequestType.OWNER_CHANGE_REQUEST:
      return i18n.t('managementRequests.changeOwner') as string;
    case ManagementRequestType.AUTH_CERT_DELETION_REQUEST:
      return i18n.t('managementRequests.removeCertificate') as string;
    case ManagementRequestType.CLIENT_DELETION_REQUEST:
      return i18n.t('managementRequests.removeClient') as string;
    case ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST:
      return i18n.t('managementRequests.addCertificate') as string;
    case ManagementRequestType.CLIENT_REGISTRATION_REQUEST:
      return i18n.t('managementRequests.addClient') as string;
    default:
      return '';
  }
}
