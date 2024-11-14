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

import {
  AddOnStatus,
  BackupEncryptionStatus,
  GlobalConfDiagnostics,
  MessageLogEncryptionStatus,
  OcspResponderDiagnostics,
  TimestampingServiceDiagnostics,
} from '@/openapi-types';
import * as api from '@/util/api';
import { defineStore } from 'pinia';

export interface DiagnosticsState {
  addOnStatus: AddOnStatus;
  timestampingServices: TimestampingServiceDiagnostics[];
  globalConf: GlobalConfDiagnostics;
  ocspResponderDiagnostics: OcspResponderDiagnostics[];
  backupEncryptionDiagnostics: BackupEncryptionStatus;
  messageLogEncryptionDiagnostics: MessageLogEncryptionStatus;
}

export const useDiagnostics = defineStore('diagnostics', {
  state: (): DiagnosticsState => {
    return {
      addOnStatus: undefined as AddOnStatus | undefined,
      timestampingServices: [] as TimestampingServiceDiagnostics[],
      globalConf: undefined as GlobalConfDiagnostics | undefined,
      ocspResponderDiagnostics: [] as OcspResponderDiagnostics[],
      backupEncryptionDiagnostics: undefined as
        | BackupEncryptionStatus
        | undefined,
      messageLogEncryptionDiagnostics: undefined as
        | MessageLogEncryptionStatus
        | undefined,
    };
  },
  getters: {
    messageLogEnabled(state): boolean {
      if (state.addOnStatus) {
        return state.addOnStatus.messagelog_enabled;
      }
      return false;
    },
  },
  actions: {
    async fetchAddonStatus() {
      return api.get<AddOnStatus>('/diagnostics/addon-status').then((res) => {
        this.addOnStatus = res.data;
      });
    },
    async fetchTimestampingServiceDiagnostics() {
      return api
        .get<
          TimestampingServiceDiagnostics[]
        >(`/diagnostics/timestamping-services`)
        .then((res) => {
          this.timestampingServices = res.data;
        });
    },
    async fetchGlobalConfDiagnostics() {
      return api
        .get<GlobalConfDiagnostics>('/diagnostics/globalconf')
        .then((res) => {
          this.globalConf = res.data;
        });
    },
    async fetchOcspResponderDiagnostics() {
      return api
        .get<OcspResponderDiagnostics[]>('/diagnostics/ocsp-responders')
        .then((res) => {
          this.ocspResponderDiagnostics = res.data;
        });
    },
    async fetchBackupEncryptionDiagnostics() {
      return api
        .get<BackupEncryptionStatus>('/diagnostics/backup-encryption-status')
        .then((res) => {
          this.backupEncryptionDiagnostics = res.data;
        });
    },
    async fetchMessageLogEncryptionDiagnostics() {
      return api
        .get<MessageLogEncryptionStatus>(
          '/diagnostics/message-log-encryption-status',
        )
        .then((res) => {
          this.messageLogEncryptionDiagnostics = res.data;
        });
    },
  },
});
