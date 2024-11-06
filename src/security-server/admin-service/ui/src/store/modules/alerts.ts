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

import { defineStore } from 'pinia';
import { useNotifications } from './notifications';
import * as api from '@/util/api';

export interface AlertStatus {
  currentTime?: string;
  backupRestoreRunningSince?: string;
  globalConfValid: boolean;
  softTokenPinEntered: boolean;
  certificateRenewalJobSuccess: boolean;
  authCertificateIdsWithErrors: string[];
  signCertificateIdsWithErrors: string[];
}

type AlertsResponse = {
  global_conf_valid: boolean;
  soft_token_pin_entered: boolean;
  backup_restore_running_since?: string;
  current_time: string;
  certificate_renewal_job_success: boolean;
  auth_certificate_ids_with_errors: string[];
  sign_certificate_ids_with_errors: string[];
};

export const useAlerts = defineStore('alerts', {
  state: () => {
    return {
      alertStatus: {
        globalConfValid: true,
        softTokenPinEntered: true,
      } as AlertStatus,
      queried: false,
    };
  },
  getters: {
    restoreStartTime(state): string {
      return state.alertStatus.backupRestoreRunningSince || '';
    },
    showGlobalConfAlert(state): boolean {
      return state.queried && !state.alertStatus.globalConfValid;
    },
    showRestoreInProgress(state): boolean {
      return state.alertStatus.backupRestoreRunningSince !== undefined;
    },
    showSoftTokenPinEnteredAlert(state): boolean {
      return state.queried && !state.alertStatus.softTokenPinEntered;
    },
    showCertificateRenewalJobFailureAlert(state): boolean {
      return state.queried && !state.alertStatus.certificateRenewalJobSuccess;
    },
    authCertificateIdsWithErrors(state): string[] {
      return state.alertStatus.authCertificateIdsWithErrors || '';
    },
    signCertificateIdsWithErrors(state): string[] {
      return state.alertStatus.signCertificateIdsWithErrors || '';
    }
  },

  actions: {
    checkAlertStatus(): void {
      api
        .get<AlertsResponse>('/notifications/alerts')
        .then((resp) => {
          this.alertStatus = {
            globalConfValid: resp.data.global_conf_valid,
            softTokenPinEntered: resp.data.soft_token_pin_entered,
            backupRestoreRunningSince: resp.data.backup_restore_running_since,
            currentTime: resp.data.current_time,
            certificateRenewalJobSuccess: resp.data.certificate_renewal_job_success,
            authCertificateIdsWithErrors: resp.data.auth_certificate_ids_with_errors,
            signCertificateIdsWithErrors: resp.data.sign_certificate_ids_with_errors,
          };

          this.queried = true;
        })
        .catch((error) => {
          const notifications = useNotifications();
          notifications.showError(error);
        });
    },
    clearAlerts(): void {
      // Clear the store state
      this.$reset();
    },
  },
});
