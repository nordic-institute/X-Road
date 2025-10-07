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
  <v-card
    variant="flat"
    class="xrd-card diagnostic-card"
    :class="{ disabled: !messageLogEnabled }"
  >
    <v-card-text class="xrd-card-text">
      <v-row no-gutters class="px-4">
        <v-col>
          <h3 :class="{ disabled: !messageLogEnabled }">
            {{ $t('diagnostics.timestamping.title') }}
          </h3>
        </v-col>
        <v-col v-if="!messageLogEnabled" class="text-right disabled">
          {{ $t('diagnostics.addOnStatus.messageLogDisabled') }}
        </v-col>
      </v-row>

      <table class="xrd-table">
        <thead>
          <tr>
            <th class="status-column">{{ $t('diagnostics.status') }}</th>
            <th class="url-column">{{ $t('diagnostics.serviceUrl') }}</th>
            <th>{{ $t('diagnostics.message') }}</th>
            <th class="time-column">
              {{ $t('diagnostics.previousUpdate') }}
            </th>
            <th class="time-column"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="timestampingService in timestampingServices"
            :key="timestampingService.url"
          >
            <td>
              <xrd-status-icon
                :status="statusIconTypeTSP(timestampingService.status_class)"
              />
            </td>
            <td
              class="url-column"
              :class="{ disabled: !messageLogEnabled }"
              data-test="service-url"
            >
              {{ timestampingService.url }}
            </td>
            <td
              :class="{ disabled: !messageLogEnabled }"
              data-test="timestamping-message"
            >
              {{ getStatusMessage(timestampingService) }}
            </td>
            <td class="time-column" :class="{ disabled: !messageLogEnabled }">
              {{ $filters.formatHoursMins(timestampingService.prev_update_at) }}
            </td>
            <td></td>
          </tr>
          <XrdEmptyPlaceholderRow
            :colspan="5"
            :loading="timestampingLoading || addonStatusLoading"
            :data="timestampingServices"
            :no-items-text="$t('noData.noTimestampingServices')"
          />
        </tbody>
      </table>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useNotifications } from '@/store/modules/notifications';
import { defineComponent } from 'vue';
import {
  DiagnosticStatusClass,
  TimestampingServiceDiagnostics,
} from '@/openapi-types';
import { i18n } from '@niis/shared-ui';

export default defineComponent({
  props: {
    addonStatusLoading: {
      type: Boolean,
    },
  },
  data: () => ({
    timestampingLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['messageLogEnabled', 'timestampingServices']),
  },
  created() {
    this.timestampingLoading = true;
    this.fetchTimestampingServiceDiagnostics()
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.timestampingLoading = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchTimestampingServiceDiagnostics']),
    statusIconTypeTSP(status: string): string {
      if (!status) {
        return '';
      }
      if (this.messageLogEnabled) {
        return this.statusIconType(status);
      } else {
        return this.statusIconType(status) + '-disabled';
      }
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
    getStatusMessage(
      timestampingService: TimestampingServiceDiagnostics,
    ): string {
      if (timestampingService.status_class === DiagnosticStatusClass.FAIL) {
        return i18n.global.t(
          `error_code.${timestampingService.error?.code}`,
          timestampingService.error?.metadata,
        );
      } else {
        return i18n.global.t(
          `diagnostics.timestamping.timestampingStatus.${timestampingService.status_class}`,
        );
      }
    },
  },
});
</script>
<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables';

h3 {
  color: colors.$Black100;
  font-size: 24px;
  font-weight: 400;
  letter-spacing: normal;
  line-height: 2rem;
}

.disabled {
  cursor: not-allowed;
  background: colors.$Black10;
  color: colors.$WarmGrey100;
}

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

.url-column {
  width: 240px;
}

.time-column {
  width: 160px;
}
</style>
