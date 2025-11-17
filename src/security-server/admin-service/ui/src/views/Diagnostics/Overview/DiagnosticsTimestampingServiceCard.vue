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
  <XrdCard
    data-test="diagnostics-mail-notification"
    title="diagnostics.timestamping.title"
    class="overview-card"
    :class="{ disabled: !messageLogEnabled }"
  >
    <template v-if="!messageLogEnabled" #append-title>
      <XrdStatusChip
        type="inactive"
        text="diagnostics.addOnStatus.messageLogDisabled"
      />
    </template>
    <v-table class="xrd">
      <thead>
        <tr>
          <th class="status-column">{{ $t('diagnostics.status') }}</th>
          <th class="url-column">{{ $t('diagnostics.serviceUrl') }}</th>
          <th class="cost-type-column">{{ $t('diagnostics.costType') }}</th>
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
            <StatusAvatar
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
            class="cost-type-column"
            :class="{ disabled: !messageLogEnabled }"
            data-test="service-cost-type"
          >
            {{
              $t('systemParameters.costType.' + timestampingService.cost_type)
            }}
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
    </v-table>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { defineComponent } from 'vue';
import {
  DiagnosticStatusClass,
  TimestampingServiceDiagnostics,
} from '@/openapi-types';
import {
  i18n,
  XrdCard,
  XrdStatusChip,
  Status,
  useNotifications,
  XrdEmptyPlaceholderRow,
} from '@niis/shared-ui';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';

export default defineComponent({
  components: { StatusAvatar, XrdCard, XrdStatusChip, XrdEmptyPlaceholderRow },
  props: {
    addonStatusLoading: {
      type: Boolean,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
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
        this.addError(error);
      })
      .finally(() => {
        this.timestampingLoading = false;
      });
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchTimestampingServiceDiagnostics']),
    statusIconTypeTSP(status: string): Status | undefined {
      if (!status) {
        return undefined;
      }
      const type = this.statusIconType(status);
      if (!type) {
        return undefined;
      }
      if (this.messageLogEnabled) {
        return type;
      } else {
        return (type + '-disabled') as Status;
      }
    },
    statusIconType(
      status: string,
    ): 'ok' | 'error' | 'progress-register' | undefined {
      if (!status) {
        return undefined;
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
        return this.$t(
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
.disabled {
  :deep(.v-card-title),
  :deep(.v-table__wrapper) {
    background-color: rgba(var(--v-theme-on-surface-variant), 0.08) !important;
  }

  :deep(.component-title-text),
  th,
  td {
    opacity: 0.6;
  }
}
</style>
