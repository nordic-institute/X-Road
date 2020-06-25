import { Client } from '@/openapi-types';
import { AxiosResponse } from 'axios';

// Filters an array of objects excluding specified object key
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

  const filtered = arr.filter((g) => {
    let filteredKeys = Object.keys(g) as K[];

    // If there is an excluded key remove it from the keys
    if (excluded) {
      filteredKeys = filteredKeys.filter((value) => {
        return value !== excluded;
      });
    }

    return filteredKeys.find((key: K) => {
      return String(g[key])
        .toLowerCase()
        .includes(mysearch);
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
) {
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
  const blob = new Blob([response.data]);

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
  subsystemCode: string,
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
) => {
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
