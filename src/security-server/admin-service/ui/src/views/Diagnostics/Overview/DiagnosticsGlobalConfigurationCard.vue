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
  <XrdCard data-test="diagnostics-global-configuration" title="diagnostics.globalConfiguration.title" class="overview-card">
    <v-table class="xrd">
      <thead>
        <tr>
          <th class="status-column">{{ $t('diagnostics.status') }}</th>
          <th>{{ $t('diagnostics.message') }}</th>
          <th>
            {{ $t('diagnostics.globalConfiguration.last_successful_url') }}
          </th>
          <th class="time-column">
            {{ $t('diagnostics.previousUpdate') }}
          </th>
          <th class="time-column">
            {{ $t('diagnostics.nextUpdate') }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="globalConf">
          <td>
            <StatusAvatar :status="statusIconType" />
          </td>

          <td data-test="global-configuration-message">
            {{ getStatusMessage }}
          </td>
          <td>
            {{ globalConf.last_successful_url }}
          </td>
          <td class="time-column">
            {{ $filters.formatHoursMins(globalConf.prev_update_at) }}
          </td>
          <td class="time-column">
            {{ $filters.formatHoursMins(globalConf.next_update_at) }}
          </td>
        </tr>
        <XrdEmptyPlaceholderRow :colspan="4" :loading="globalConfLoading" :data="globalConf" :no-items-text="$t('noData.noData')" />
      </tbody>
    </v-table>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { defineComponent } from 'vue';
import { DiagnosticStatusClass } from '@/openapi-types';
import { XrdCard, useNotifications, XrdEmptyPlaceholderRow } from '@niis/shared-ui';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';

export default defineComponent({
  components: { StatusAvatar, XrdCard, XrdEmptyPlaceholderRow },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data: () => ({
    globalConfLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['globalConf']),
    getStatusMessage(): string {
      if (!this.globalConf) {
        return '';
      }
      if (this.globalConf.status_class === DiagnosticStatusClass.FAIL) {
        return this.$t(`error_code.${this.globalConf.error?.code}`, this.globalConf.error?.metadata);
      } else {
        return this.$t(`diagnostics.globalConfiguration.configurationStatus.${this.globalConf.status_class}`);
      }
    },
    statusIconType() {
      if (!this.globalConf || !this.globalConf.status_class) {
        return undefined;
      }
      switch (this.globalConf.status_class) {
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
  created() {
    this.globalConfLoading = true;
    this.fetchGlobalConfDiagnostics()
      .catch((error) => {
        this.addError(error);
      })
      .finally(() => {
        this.globalConfLoading = false;
      });
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchGlobalConfDiagnostics']),
  },
});
</script>
<style lang="scss" scoped></style>
