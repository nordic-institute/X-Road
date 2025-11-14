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
import { ClientId, ManagementRequestType } from '@/openapi-types';
import { i18n } from '@niis/shared-ui';

// Deep clones an object or array using JSON
export function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

// Debounce function
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const debounce = <F extends (...args: any[]) => any>(
  func: F,
  waitFor: number,
): ((...args: Parameters<F>) => Promise<ReturnType<F>>) => {
  let timeout: ReturnType<typeof setTimeout> | undefined;

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

type MrIconColorText = { text: string; color: string; icon: string };

function mrIconColorText(textKey: string, icon: string, color: string): MrIconColorText {
  const { t } = i18n.global;
  return { text: t(textKey), icon, color };
}

export function managementTypeToIconTextColor(type: ManagementRequestType | undefined): MrIconColorText | undefined {
  switch (type) {
    case ManagementRequestType.OWNER_CHANGE_REQUEST:
      return mrIconColorText('managementRequests.changeOwner', 'folder_managed', 'border');
    case ManagementRequestType.AUTH_CERT_DELETION_REQUEST:
      return mrIconColorText('managementRequests.removeCertificate', 'scan_delete', 'error');
    case ManagementRequestType.CLIENT_DELETION_REQUEST:
      return mrIconColorText('managementRequests.removeClient', 'person_cancel', 'error');
    case ManagementRequestType.CLIENT_DISABLE_REQUEST:
      return mrIconColorText('managementRequests.clientDisable', 'error', 'error');
    case ManagementRequestType.CLIENT_ENABLE_REQUEST:
      return mrIconColorText('managementRequests.clientEnable', 'check_circle', 'success');
    case ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST:
      return mrIconColorText('managementRequests.addCertificate', 'note_add', 'success');
    case ManagementRequestType.CLIENT_REGISTRATION_REQUEST:
      return mrIconColorText('managementRequests.addClient', 'person_add', 'success');
    case ManagementRequestType.ADDRESS_CHANGE_REQUEST:
      return mrIconColorText('managementRequests.changeAddress', 'link', 'info');
    case ManagementRequestType.CLIENT_RENAME_REQUEST:
      return mrIconColorText('managementRequests.renameClient', 'edit_square', 'info');
    case ManagementRequestType.MAINTENANCE_MODE_ENABLE_REQUEST:
      return mrIconColorText('managementRequests.maintenanceModeEnable', 'build_circle', 'success');
    case ManagementRequestType.MAINTENANCE_MODE_DISABLE_REQUEST:
      return mrIconColorText('managementRequests.maintenanceModeDisable', 'build_circle', 'error');
    default:
      return undefined;
  }
}

// Upper case every word
export function upperCaseWords(value: string): string {
  if (!value) {
    return '';
  }
  return value
    .toLowerCase()
    .split(' ')
    .map((s) => s.charAt(0).toUpperCase() + s.substring(1))
    .join(' ');
}

export function toPagingOptions(...options: number[]): { title: string; value: number }[] {
  const all = 'global.all';
  return options.map((value) => {
    return { title: value === -1 ? all : value.toString(), value };
  });
}
