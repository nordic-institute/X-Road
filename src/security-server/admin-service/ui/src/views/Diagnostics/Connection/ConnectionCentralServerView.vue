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
          <th class="fixed-width-10"/>
          <th class="fixed-width-25"/>
          <th class="status-column">{{ $t('diagnostics.status') }}</th>
          <th class="fixed-width-55">{{ $t('diagnostics.message') }}</th>
          <th class="fixed-width-5"/>
        </tr>
        </thead>
        <tbody>
        <tr
          v-for="(item, index) in globalConfStatuses"
          :key="item.download_url"
        >
          <td v-if="index === 0" :rowspan="globalConfStatuses.length">
            {{ $t('diagnostics.connection.centralServer.globalConf') }}
          </td>

          <td>
            <span v-if="!globalConfLoading">
              {{ item.download_url }}
            </span>
          </td>

          <td>
            <xrd-status-icon v-if="!globalConfLoading"
                             :status="statusIconType(item.connection_status.status_class)"
            />
          </td>

          <!-- Column 4 -->
          <td>
            <span v-if="globalConfLoading">
            </span>
            <span v-else-if="item.connection_status.status_class === 'OK'">
            {{ $t('diagnostics.javaVersion.ok') }}
            </span>
            <span v-else>
            {{ globalConfErrorMessage(item.connection_status.error) }}
          </span>
          </td>

          <!-- Column 5: only render on first row, merge down using rowspan -->
          <td v-if="index === 0" :rowspan="globalConfStatuses.length">
            <xrd-button
              large
              variant="text"
              @click="testGlobalConfDownload()"
            >
              {{ $t('diagnostics.connection.centralServer.test') }}
            </xrd-button>
          </td>
        </tr>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="globalConfLoading"
          :data="globalConfStatuses"
          :no-items-text="$t('noData.noTimestampingServices')"
        />
        <tr>
          <td colspan="2">
            {{ $t('diagnostics.connection.centralServer.authCertRequest') }}
          </td>
          <td>
            <span v-if="!authCertLoading">
            <xrd-status-icon :status="statusIconType(authCertReqStatus?.status_class)"/>
            </span>
          </td>
          <td>
            <span v-if="authCertLoading">
            </span>
          <span v-else-if="authCertReqStatus?.status_class === 'OK'">
            {{ $t('diagnostics.javaVersion.ok') }}
          </span>
          <span v-else>
            {{ authCertErrorMessage }}
          </span>
          </td>
          <td>
            <xrd-button
              large
              variant="text"
              @click="testAuthCertRequest()"
            >
              {{ $t('diagnostics.connection.centralServer.test') }}
            </xrd-button>
          </td>
        </tr>
        <XrdEmptyPlaceholderRow
          :colspan="5"
          :loading="authCertLoading"
          :data="authCertReqStatus"
          :no-items-text="$t('noData.noTimestampingServices')"
        />
        </tbody>

      </table>
    </v-card-text>

  </v-card>
</template>
<script lang="ts">
import {mapActions, mapState} from 'pinia';
import {defineComponent} from 'vue';
import {useDiagnostics} from "@/store/modules/diagnostics";
import {useNotifications} from "@/store/modules/notifications";
import type {CodeWithDetails} from "@/openapi-types";

export default defineComponent({
  name: 'ConnectionCentralServerView',
  data: () => ({
    authCertLoading: false,
    globalConfLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['authCertReqStatus', 'globalConfStatuses']),

    authCertErrorMessage() {
      const err = this.authCertReqStatus?.error
      return this.formatErrorForUi(err)
    },
  },
  created() {
    this.testAuthCertRequest();
    this.testGlobalConfDownload();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchAuthCertReqStatus', 'fetchGlobalConfStatuses']),

    globalConfErrorMessage(error: CodeWithDetails) {
      return this.formatErrorForUi(error)
    },

    formatErrorForUi(err?: {
      code?: string
      metadata?: string[]
      validation_errors?: Record<string, string[]>
    }) {
      if (!err) return ''

      const {code, metadata = [], validation_errors = {}} = err

      const buildKey = (rawKey?: string) => {
        if (!rawKey) return ''
        return rawKey.includes('.') ? rawKey : `error_code.${rawKey}`
      }

      const codeKey = buildKey(code)
      const codeText = codeKey ? (this.$t(codeKey) as string) : ''

      const metaText = metadata.length ? metadata.join(', ') : ''

      const header = [codeText, metaText].filter(Boolean).join(' : ')

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

    testAuthCertRequest() {
      this.authCertLoading = true;
      this.fetchAuthCertReqStatus()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.authCertLoading = false;
        });
    },
    testGlobalConfDownload() {
      this.globalConfLoading = true;
      this.fetchGlobalConfStatuses()
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.globalConfLoading = false;
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

.fixed-width-10 {
  width: 10%;
  max-width: 10%;
  word-break: break-word;
}

.fixed-width-25 {
  width: 25%;
  max-width: 25%;
  word-break: break-word;
}

.fixed-width-55 {
  width: 55%;
  max-width: 55%;
  word-break: break-word;
}

.level-column {
  @media only screen and (min-width: 1200px) {
    width: 20%;
  }
}
</style>
