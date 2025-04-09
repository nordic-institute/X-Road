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
    <v-card-title class="text-h5" data-test="diagnostics-proxy-memory">
      {{ $t('diagnostics.proxyMemoryUsage.title') }}
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <table class="xrd-table">
        <thead>
          <tr>
            <th class="status-column">{{ $t('diagnostics.status') }}</th>
            <th>{{ $t('diagnostics.message') }}</th>
            <th>
              {{ $t('diagnostics.proxyMemoryUsage.usagePercent') }}
            </th>
            <th>
              {{ $t('diagnostics.proxyMemoryUsage.max') }}
            </th>
            <th>
              {{ $t('diagnostics.proxyMemoryUsage.threshold') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td data-test="status-icon">
              <xrd-status-icon
                v-if="proxyMemoryUsageStatus?.is_used_over_threshold"
                status="pending"
              />
              <xrd-status-icon v-else status="ok" />
            </td>
            <td
              v-if="proxyMemoryUsageStatus?.is_used_over_threshold"
              data-test="proxy-memory-usage-status-message"
            >
              {{ $t('diagnostics.proxyMemoryUsage.alertOverThreshold') }}
            </td>
            <td v-else data-test="proxy-memory-usage-status-message">
              {{ $t('diagnostics.proxyMemoryUsage.ok') }}
            </td>
            <td data-test="proxy-memory-usage">
              {{ $filters.bytes(proxyMemoryUsageStatus?.used_memory) }} {{ `(${proxyMemoryUsageStatus?.usage_percent}%)` }}
            </td>
            <td data-test="proxy-memory-max">
              {{ $filters.bytes(proxyMemoryUsageStatus?.max_memory) }}
            </td>
            <td data-test="proxy-memory-threshold">
              {{ threshold() }}
            </td>
          </tr>
        </tbody>
      </table>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapActions, mapState } from "pinia";
import { defineComponent } from 'vue';
import { useDiagnostics } from "@/store/modules/diagnostics";
import { useNotifications } from "@/store/modules/notifications";
import { ByteFormat } from "@/filters";

export default defineComponent({
  data: () => ({
    proxyMemoryUsageStatusLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['proxyMemoryUsageStatus']),
  },
  created() {
    this.proxyMemoryUsageStatusLoading = true;
    this.fetchProxyMemoryDiagnostics()
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.proxyMemoryUsageStatusLoading = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchProxyMemoryDiagnostics']),
    threshold(): string {
      return !!this.proxyMemoryUsageStatus?.threshold ? `${this.proxyMemoryUsageStatus.threshold}%` : 'Not set.';
    },
  }
});
</script>
<style lang="scss" scoped>
@use '@/assets/colors';
@use '@/assets/tables';

h3 {
  color: colors.$Black100;
  font-size: 24px;
  font-weight: 400;
  letter-spacing: normal;
  line-height: 2rem;
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

</style>
