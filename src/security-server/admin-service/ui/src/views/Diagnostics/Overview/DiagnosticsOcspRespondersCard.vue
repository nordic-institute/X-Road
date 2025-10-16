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
    data-test="diagnostics-ocsp-responders"
    title="diagnostics.ocspResponders.title"
    class="overview-card"
  >
    <XrdEmptyPlaceholder
      :loading="ocspLoading"
      :data="ocspResponderDiagnostics"
      :no-items-text="$t('noData.noData')"
    />

    <div
      v-for="ocspDiags in ocspResponderDiagnostics"
      :key="ocspDiags.distinguished_name"
      class="ocsp-block"
    >
      <div class="ml-4">
        <span
          >{{
            $t('diagnostics.ocspResponders.certificationService')
          }}&nbsp;</span
        >
        <span class="font-weight-bold">{{ ocspDiags.distinguished_name }}</span>
      </div>
      <v-table class="xrd">
        <thead>
          <tr>
            <th class="status-column">
              {{ $t('diagnostics.status') }}
            </th>
            <th class="url-column">
              {{ $t('diagnostics.serviceUrl') }}
            </th>
            <th>{{ $t('diagnostics.message') }}</th>
            <th class="time-column">
              {{ $t('diagnostics.previousUpdate') }}
            </th>
            <th class="time-column">
              {{ $t('diagnostics.nextUpdate') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ocsp in ocspDiags.ocsp_responders" :key="ocsp.url">
            <td>
              <StatusAvatar :status="statusIconType(ocsp.status_class)" />
            </td>
            <td class="url-column" data-test="service-url">
              {{ ocsp.url }}
            </td>
            <td data-test="ocsp-responders-message">
              {{ statusMessage(ocsp) }}
            </td>
            <td class="time-column">
              {{ $filters.formatHoursMins(ocsp.prev_update_at ?? '') }}
            </td>
            <td class="time-column">
              {{ $filters.formatHoursMins(ocsp.next_update_at) }}
            </td>
          </tr>
          <XrdEmptyPlaceholderRow
            :colspan="4"
            :loading="ocspLoading"
            :data="ocspDiags"
            :no-items-text="$t('noData.noCertificateAuthorities')"
          />
        </tbody>
      </v-table>
    </div>
  </XrdCard>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { defineComponent } from 'vue';
import { DiagnosticStatusClass, type OcspResponder } from '@/openapi-types';
import { XrdCard, Status, useNotifications } from '@niis/shared-ui';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';

export default defineComponent({
  components: { StatusAvatar, XrdCard },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data: () => ({
    ocspLoading: false,
  }),
  computed: {
    ...mapState(useDiagnostics, ['ocspResponderDiagnostics']),
  },
  created() {
    this.ocspLoading = true;
    this.fetchOcspResponderDiagnostics()
      .catch((error) => {
        this.addError(error);
      })
      .finally(() => {
        this.ocspLoading = false;
      });
  },
  methods: {
    ...mapActions(useDiagnostics, ['fetchOcspResponderDiagnostics']),
    statusIconType(status: string): Status | undefined {
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
    statusMessage(ocsp: OcspResponder): string {
      if (ocsp.status_class === DiagnosticStatusClass.FAIL) {
        return this.$t(
          `error_code.${ocsp.error?.code}`,
          ocsp.error?.metadata,
        );
      } else {
        return this.$t(
          `diagnostics.ocspResponders.ocspStatus.${ocsp.status_class}`,
        );
      }
    },
  },
});
</script>
<style lang="scss" scoped>
.ocsp-block:not(:last-child) {
  margin-bottom: 32px;
}
</style>
