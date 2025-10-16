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
    data-test="diagnostics-proxy-memory"
    title="diagnostics.proxyMemoryUsage.title"
    class="overview-card"
  >
    <v-table class="xrd">
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
            <StatusAvatar
              :status="
                proxyMemoryUsageStatus?.is_used_over_threshold
                  ? 'pending'
                  : 'ok'
              "
            />
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
            {{ $filters.bytes(proxyMemoryUsageStatus?.used_memory) }}
            {{ `(${proxyMemoryUsageStatus?.usage_percent}%)` }}
          </td>
          <td data-test="proxy-memory-max">
            {{ $filters.bytes(proxyMemoryUsageStatus?.max_memory) }}
          </td>
          <td data-test="proxy-memory-threshold">
            {{ threshold() }}
          </td>
        </tr>
      </tbody>
    </v-table>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { defineComponent } from 'vue';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { XrdCard, useNotifications } from '@niis/shared-ui';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';

export default defineComponent({
  components: { StatusAvatar, XrdCard },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
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
        this.addError(error);
      })
      .finally(() => {
        this.proxyMemoryUsageStatusLoading = false;
      });
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchProxyMemoryDiagnostics']),
    threshold(): string {
      return this.proxyMemoryUsageStatus?.threshold
        ? `${this.proxyMemoryUsageStatus.threshold}%`
        : 'Not set.';
    },
  },
});
</script>
<style lang="scss" scoped></style>
