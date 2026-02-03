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
import * as api from '@/util/api';

export interface InitializationStepInfo {
  step: InitializationStep;
  status: InitializationStepStatus;
  started_at?: string;
  completed_at?: string;
  error_message?: string;
  error_code?: string;
  retryable: boolean;
}

export interface InitializationStatusV2 {
  overall_status: InitializationOverallStatus;
  anchor_imported: boolean;
  steps: InitializationStepInfo[];
  pending_steps: InitializationStep[];
  failed_steps: InitializationStep[];
  completed_steps: InitializationStep[];
  fully_initialized: boolean;
  security_server_id?: string;
  token_pin_policy_enforced?: boolean;
}

export interface InitStepResult {
  step: InitializationStep;
  status: InitializationStepStatus;
  success: boolean;
  message?: string;
  error_code?: string;
}

export interface ServerConfInitRequest {
  security_server_code: string;
  owner_member_class: string;
  owner_member_code: string;
  ignore_warnings?: boolean;
}

export interface SoftTokenInitRequest {
  software_token_pin: string;
}

export type InitializationStep = 'SERVERCONF' | 'SOFTTOKEN' | 'GPG_KEY' | 'MLOG_ENCRYPTION';

export type InitializationStepStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'SKIPPED' | 'UNKNOWN';

export type InitializationOverallStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PARTIALLY_COMPLETED' | 'FAILED' | 'COMPLETED';

const V2_BASE = '/v2/initialization';

export const useInitializationV2 = defineStore('initializationV2', {
  state: () => ({
    status: null as InitializationStatusV2 | null,
  }),

  getters: {
    overallStatus(): InitializationOverallStatus | null {
      return this.status?.overall_status ?? null;
    },
    isFullyInitialized(): boolean {
      return this.status?.fully_initialized ?? false;
    },
    pendingSteps(): InitializationStep[] {
      return this.status?.pending_steps ?? [];
    },
    failedSteps(): InitializationStep[] {
      return this.status?.failed_steps ?? [];
    },
    completedSteps(): InitializationStep[] {
      return this.status?.completed_steps ?? [];
    },
    anchorImported(): boolean {
      return this.status?.anchor_imported ?? false;
    },
  },

  actions: {
    getStepStatus(step: InitializationStep): InitializationStepStatus {
      const info = this.status?.steps.find((s) => s.step === step);
      return info?.status ?? 'NOT_STARTED';
    },

    getStepInfo(step: InitializationStep): InitializationStepInfo | undefined {
      return this.status?.steps.find((s) => s.step === step);
    },

    async fetchStatus(): Promise<InitializationStatusV2> {
      const response = await api.get<InitializationStatusV2>(`${V2_BASE}/status`);
      this.status = response.data;
      return response.data;
    },

    async initServerConf(payload: ServerConfInitRequest): Promise<InitStepResult> {
      const response = await api.post<InitStepResult>(`${V2_BASE}/serverconf`, payload);
      await this.fetchStatus();
      return response.data;
    },

    async initSoftToken(pin: string): Promise<InitStepResult> {
      const payload: SoftTokenInitRequest = { software_token_pin: pin };
      const response = await api.post<InitStepResult>(`${V2_BASE}/softtoken`, payload);
      await this.fetchStatus();
      return response.data;
    },

    async initGpgKey(): Promise<InitStepResult> {
      const response = await api.post<InitStepResult>(`${V2_BASE}/gpg-key`);
      await this.fetchStatus();
      return response.data;
    },

    async initMessageLogEncryption(): Promise<InitStepResult> {
      const response = await api.post<InitStepResult>(`${V2_BASE}/messagelog-encryption`);
      await this.fetchStatus();
      return response.data;
    },
  },
});
