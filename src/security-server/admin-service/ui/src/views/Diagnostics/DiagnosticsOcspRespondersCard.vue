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
    <v-card-title class="text-h5" data-test="diagnostics-ocsp-responders">
      {{ $t('diagnostics.ocspResponders.title') }}
    </v-card-title>
    <v-card-text class="xrd-card-text">
      <XrdEmptyPlaceholder
        :loading="ocspLoading"
        :data="ocspResponderDiagnostics"
        :no-items-text="$t('noData.noData')"
      />

      <div
        v-for="ocspDiags in ocspResponderDiagnostics"
        :key="ocspDiags.distinguished_name"
      >
        <div class="sub-title">
          <span>{{
            $t('diagnostics.ocspResponders.certificationService')
          }}</span>
          {{ ocspDiags.distinguished_name }}
        </div>
        <table class="xrd-table">
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
                <xrd-status-icon :status="statusIconType(ocsp.status_class)" />
              </td>
              <td class="url-column" data-test="service-url">
                {{ ocsp.url }}
              </td>
              <td data-test="ocsp-responders-message">
                {{
                  $t(
                    `diagnostics.ocspResponders.ocspStatus.${ocsp.status_code}`,
                  )
                }}
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
        </table>
      </div>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useNotifications } from '@/store/modules/notifications';
import { defineComponent } from 'vue';

export default defineComponent({
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
        this.showError(error);
      })
      .finally(() => {
        this.ocspLoading = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useDiagnostics, ['fetchOcspResponderDiagnostics']),
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
@import '@/assets/colors';
@import '@/assets/tables';

h3 {
  color: $XRoad-Black100;
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

.url-column {
  width: 240px;
}

.time-column {
  width: 160px;
}

.sub-title {
  margin-top: 30px;
  margin-left: 16px;

  font-style: normal;
  font-weight: bold;
  font-size: $XRoad-DefaultFontSize;
  line-height: 20px;
  color: $XRoad-Black100;

  span {
    font-style: normal;
    font-weight: normal;
    font-size: $XRoad-DefaultFontSize;
    line-height: 20px;
    padding-right: 16px;
  }
}
</style>
