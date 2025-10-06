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
  <v-card variant="flat" class="xrd-card diagnostic-card">
    <v-card-title class="text-h5" data-test="diagnostics-java-version">
      {{ $t('diagnostics.connection.centralServer.title') }}
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <table class="xrd-table">
        <thead>
        <tr>
          <th class="fixed-width-18" />
          <th class="fixed-width-5" />
          <th class="status-column">{{ $t('diagnostics.status') }}</th>
          <th class="fixed-width-67">{{ $t('diagnostics.message') }}</th>
          <th class="fixed-width-5" />
        </tr>
        </thead>
        <tbody>
        <!-- First row -->
        <tr>
          <td rowspan="2">
            {{ $t('diagnostics.connection.centralServer.globalConf') }}
          </td>
          <td>
            {{ $t('diagnostics.connection.centralServer.globalConfHttp') }}
          </td>
          <td>
            <xrd-status-icon :status="statusIconType(centralServerGlobalConfStatus?.status_class)" />
          </td>
          <td v-if="centralServerGlobalConfStatus?.status_class === 'OK'">
            {{ $t('diagnostics.javaVersion.ok') }}
          </td>
          <td v-else>
            {{ globalConfMessage }}
          </td>
          <td>
            <xrd-button
              large
              variant="text"
              @click="testGlobalConfConnection()"
            >
              {{ $t('diagnostics.connection.centralServer.test') }}
            </xrd-button>
          </td>
        </tr>

        <!-- Second row -->
        <tr>
          <!-- no first <td> here, because it's merged above -->
          <td>
            {{ $t('diagnostics.connection.centralServer.globalConfHttps') }}
          </td>
          <td>
            <xrd-status-icon :status="statusIconType(centralServerGlobalConfHttpsStatus?.status_class)" />
          </td>
          <td v-if="centralServerGlobalConfHttpsStatus?.status_class === 'OK'">
            {{ $t('diagnostics.javaVersion.ok') }}
          </td>
          <td v-else>
            {{ globalConfHttpsMessage }}
          </td>
          <td>
            <xrd-button
              large
              variant="text"
              @click="testGlobalConfHttpsConnection()"
            >
              {{ $t('diagnostics.connection.centralServer.test') }}
            </xrd-button>
          </td>
        </tr>

        <!-- Third row remains unchanged -->
        <tr>
          <td>
            {{ $t('diagnostics.connection.centralServer.authCertRequest') }}
          </td>
          <td/>
          <td>
            <xrd-status-icon :status="statusIconType(centralServerConnectionStatus?.status_class)" />
          </td>
          <td v-if="centralServerConnectionStatus?.status_class === 'OK'">
            {{ $t('diagnostics.javaVersion.ok') }}
          </td>
          <td v-else>
            {{ connectionMessage }}
          </td>
          <td>
            <xrd-button
              large
              variant="text"
              data-test="send-test-mail"
              @click="testConnection()"
            >
              {{ $t('diagnostics.connection.centralServer.test') }}
            </xrd-button>
          </td>
        </tr>
        </tbody>
      </table>
    </v-card-text>

  </v-card>
</template>
<script lang="ts">
import {mapActions, mapState} from 'pinia';
import { defineComponent } from 'vue';
import {useDiagnostics} from "@/store/modules/diagnostics";
import {useNotifications} from "@/store/modules/notifications";

export default defineComponent({
  name: 'ConnectionCentralServerView',
  data: () => ({
    connectionLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['centralServerConnectionStatus', 'centralServerGlobalConfStatus',
      'centralServerGlobalConfHttpsStatus']),
    globalConfMessage() {
      const err = this.centralServerGlobalConfStatus?.error
      return this.formatErrorForUi(err)
    },

    globalConfHttpsMessage() {
      const err = this.centralServerGlobalConfHttpsStatus?.error
      return this.formatErrorForUi(err)
    },

    connectionMessage() {
      const err = this.centralServerConnectionStatus?.error
      return this.formatErrorForUi(err)
    },
  },
  created() {
    this.testConnection();
    this.testGlobalConfConnection();
    this.testGlobalConfHttpsConnection();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchCentralServerConnectionStatus', 'fetchCentralServerGlobalConfStatus',
      'fetchCentralServerGlobalConfHttpsStatus']),

    formatErrorForUi(err?: {
      code?: string
      metadata?: string[]
      validation_errors?: Record<string, string[]>
    }) {
      if (!err) return ''

      const { code, metadata = [], validation_errors = {} } = err

      // 👇 Helper function to build translation key
      const buildKey = (rawKey?: string) => {
        if (!rawKey) return ''
        return rawKey.includes('.') ? rawKey : `error_code.${rawKey}`
      }

      // Code translation
      const codeKey = buildKey(code)
      const codeText = codeKey ? (this.$t(codeKey) as string) : ''

      // Metadata
      const metaText = metadata.length ? metadata.join(', ') : ''

      // Header: code + metadata with " : "
      const header = [codeText, metaText].filter(Boolean).join(' : ')

      // Validation errors
      const veEntries = Object.entries(validation_errors)
      const veText = veEntries.length
        ? veEntries
          .map(([field, msgs]) => {
            const labelKey = buildKey(field)
            const label = this.$te(labelKey) ? (this.$t(labelKey) as string) : field
            return `${label}: ${msgs.join(', ')}`
          })
          .join(' | ')
        : ''

      return [header, veText].filter(Boolean).join(' | ')
    },

    testConnection() {
      this.connectionLoading = true;
      this.fetchCentralServerConnectionStatus()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.connectionLoading = false;
        });
    },
    testGlobalConfConnection() {
      this.connectionLoading = true;
      this.fetchCentralServerGlobalConfStatus()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.connectionLoading = false;
        });
    },
    testGlobalConfHttpsConnection() {
      this.connectionLoading = true;
      this.fetchCentralServerGlobalConfHttpsStatus()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.connectionLoading = false;
        });
    },
    statusIconType(status: string): string {
      if (!status) {
        return '';
      }
      switch (status) {
        case 'OK':
          return 'ok';
        case 'WAITING':
          return 'progress-register';
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
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables';

.xrd-card-text {
  padding-left: 0;
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;
  margin-bottom: 30px;

  &:first-of-type {
    margin-top: 40px;
  }
}

.status-column {
  width: 80px;
}

.fixed-width-5 {
  width: 5%;
  max-width: 5%;
  word-break: break-word;
}

.fixed-width-18 {
  width: 18%;
  max-width: 18%;
  word-break: break-word;
}

.fixed-width-67 {
  width: 67%;
  max-width: 67%;
  word-break: break-word;
}

.level-column {
  @media only screen and (min-width: 1200px) {
    width: 20%;
  }
}
</style>
