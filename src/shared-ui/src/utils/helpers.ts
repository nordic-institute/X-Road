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

// Helper to copy text to clipboard
import dayjs from 'dayjs';
import { PublicPathState } from 'vee-validate';
import { AxiosResponse } from 'axios';

export async function toClipboard(val: string): Promise<void> {
  return navigator.clipboard.writeText(val);
}

// Add colon for every two characters.  xxxxxx -> xx:xx:xx
export function colonize(value: string): string {
  if (!value) {
    return '';
  }

  const colonized = value.replace(/(.{2})/g, '$1:');

  if (colonized[colonized.length - 1] === ':') {
    return colonized.slice(0, -1);
  }

  return colonized;
}

const DATE_FORMAT = 'YYYY-MM-DD';
const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';
const DATE_TIME_FORMAT_WITH_SECONDS = DATE_TIME_FORMAT + ':ss';

export function formatDateTime(valueAsText: string | undefined, withSeconds = false): string {
  if (!valueAsText) {
    return '-';
  }
  const time = dayjs(valueAsText);
  return time.isValid() ? time.format(withSeconds ? DATE_TIME_FORMAT_WITH_SECONDS : DATE_TIME_FORMAT) : '-';
}

export function formatDate(valueAsText: string | undefined, withSeconds = false): string {
  if (!valueAsText) {
    return '-';
  }
  const date = dayjs(valueAsText);
  return date.isValid() ? date.format(DATE_FORMAT) : '-';
}

export function veePropMapper(state: PublicPathState<never>) {
  return {
    'error-messages': state.errors,
  };
}

export function veeDefaultFieldConfig() {
  return {
    props: veePropMapper,
  };
}

// Save response data as a file
export function saveResponseAsFile(response: AxiosResponse, defaultFileName = 'certs.tar.gz'): void {
  let suggestedFileName;
  const disposition = response.headers['content-disposition'];

  if (disposition && disposition.indexOf('attachment') !== -1) {
    const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
    const matches = filenameRegex.exec(disposition);
    if (matches != null && matches[1]) {
      suggestedFileName = matches[1].replace(/['"]/g, '');
    }
  }
  const effectiveFileName = suggestedFileName === undefined ? defaultFileName : suggestedFileName;
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

// Checks if the given WSDL URL valid
export function isValidWsdlURL(str: string): boolean {
  const pattern = new RegExp('(^(https?):///?)[-a-zA-Z0-9]');
  return pattern.test(str);
}

// Checks if the given REST URL is valid
export function isValidRestURL(str: string): boolean {
  return isValidWsdlURL(str);
}
