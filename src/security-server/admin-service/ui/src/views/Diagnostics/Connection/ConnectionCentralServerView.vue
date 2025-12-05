<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <XrdCard title="diagnostics.connection.centralServer.title" class="overview-card">
    <v-table class="xrd">
      <thead>
        <tr>
          <th class="fixed-width-10" />
          <th class="fixed-width-30" />
          <th class="status-column">{{ $t('diagnostics.status') }}</th>
          <th class="fixed-width-50">{{ $t('diagnostics.message') }}</th>
          <th class="fixed-width-5" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, index) in globalConfStatuses" :key="item.download_url">
          <td v-if="index === 0" :rowspan="globalConfStatuses.length" class="font-weight-bold">
            {{ $t('diagnostics.connection.centralServer.globalConf') }}
          </td>
          <td>
            <span v-if="!globalConfLoading">
              {{ item.download_url }}
            </span>
          </td>
          <td data-test="central-server-global-conf-status">
            <StatusAvatar :status="statusIconType(item.connection_status.status_class)" />
          </td>
          <td data-test="central-server-global-conf-message">
            <span v-if="globalConfLoading" />
            <span v-if="item.connection_status.status_class === 'OK'">
              {{ $t('diagnostics.connection.ok') }}
            </span>
            <span v-else class="error-text">
              {{ globalConfErrorMessage(item.connection_status.error) }}
            </span>
          </td>
          <td v-if="index === 0" :rowspan="globalConfStatuses.length">
            <XrdBtn variant="text" text="diagnostics.connection.test" @click="testGlobalConfDownload()" data-test="central-server-global-conf-test-button" />
          </td>
        </tr>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="globalConfLoading"
          :data="globalConfStatuses"
          :no-items-text="$t('noData.noData')"
        />
        <tr>
          <td colspan="2" class="font-weight-bold">
            {{ $t('diagnostics.connection.centralServer.authCertRequest') }}
          </td>
          <td data-test="central-server-auth-cert-status">
            <StatusAvatar :status="statusIconType(authCertReqStatus?.status_class)" />
          </td>
          <td data-test="central-server-auth-cert-message">
            <span v-if="authCertLoading" />
            <span v-if="authCertReqStatus?.status_class === 'OK'">
              {{ $t('diagnostics.connection.ok') }}
            </span>
            <span v-else class="error-text">
              {{ authCertErrorMessage }}
            </span>
          </td>
          <td>
            <XrdBtn variant="text" text="diagnostics.connection.test" @click="testAuthCertRequest()" data-test="central-server-auth-cert-test-button" />
          </td>
        </tr>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="authCertLoading"
          :data="authCertReqStatus"
          :no-items-text="$t('noData.noData')"
        />
      </tbody>
    </v-table>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { defineComponent } from 'vue';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useNotifications, XrdCard, XrdEmptyPlaceholderRow, XrdBtn } from '@niis/shared-ui';
import type { CodeWithDetails } from '@/openapi-types';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';
import { formatErrorForUi, statusIconType } from "@/util/formatting";

export default defineComponent({
  name: 'ConnectionCentralServerView',
  components: {
    XrdCard,
    StatusAvatar,
    XrdEmptyPlaceholderRow,
    XrdBtn,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data: () => ({
    authCertLoading: false,
    globalConfLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['authCertReqStatus', 'globalConfStatuses']),

    authCertErrorMessage() {
      const err = this.authCertReqStatus?.error;
      return formatErrorForUi(err);
    },
  },
  created() {
    this.testAuthCertRequest();
    this.testGlobalConfDownload();
  },
  methods: {
    statusIconType,
    ...mapActions(useDiagnostics, ['fetchAuthCertReqStatus', 'fetchGlobalConfStatuses']),

    globalConfErrorMessage(error: CodeWithDetails) {
      return this.formatErrorForUi(error);
    },

    formatErrorForUi(err?: { code?: string; metadata?: string[]; validation_errors?: Record<string, string[]> }) {
      if (!err) return '';

      const { code, metadata = [], validation_errors = {} } = err;
      const buildKey = (rawKey?: string) => {
        if (!rawKey) return '';
        return rawKey.includes('.') ? rawKey : `error_code.${rawKey}`;
      };
      const codeKey = buildKey(code);
      const codeText = codeKey ? this.$t(codeKey) : '';
      const metaText = metadata.length ? metadata.join(', ') : '';
      const header = [codeText, metaText].filter(Boolean).join(' : ');
      const veEntries = Object.entries(validation_errors);
      const veText = veEntries.length
        ? veEntries
            .map(([field, msgs]) => {
              const labelKey = buildKey(field);
              const label = this.$te(labelKey) ? (this.$t(labelKey) as string) : field;
              return `${label}: ${msgs.join(', ')}`;
            })
            .join(' | ')
        : '';

      return [header, veText].filter(Boolean).join(' | ');
    },

    testAuthCertRequest() {
      this.authCertLoading = true;
      this.fetchAuthCertReqStatus()
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => {
          this.authCertLoading = false;
        });
    },
    testGlobalConfDownload() {
      this.globalConfLoading = true;
      this.fetchGlobalConfStatuses()
        .catch((error) => {
          this.addError(error);
        })
        .finally(() => {
          this.globalConfLoading = false;
        });
    },
    statusIconType(status?: string) {
      if (!status) {
        return undefined;
      }
      switch (status) {
        case 'OK':
          return 'ok';
        case 'FAIL':
          return 'error';
        default:
          return 'error';
      }
    },
  },
});
</script>
<style lang="scss" scoped>
.fixed-width-5 {
  width: 5%;
  max-width: 5%;
  word-break: break-word;
}

.fixed-width-10 {
  width: 10%;
  max-width: 10%;
  word-break: break-word;
}

.fixed-width-30 {
  width: 30%;
  max-width: 30%;
  word-break: break-word;
}

.fixed-width-50 {
  width: 50%;
  max-width: 50%;
  word-break: break-word;
}
.error-text {
  color: rgb(var(--v-theme-error)) !important;
}
</style>
