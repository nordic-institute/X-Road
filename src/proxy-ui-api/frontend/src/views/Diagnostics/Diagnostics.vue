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
  <div class="xrd-view-common">
    <alerts-container />

    <div class="inner-wrap">
      <div class="xrd-view-title pt-6">{{ $t('tab.main.diagnostics') }}</div>
      <v-layout align-center justify-center column fill-height elevation-0>
        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span class="headline">{{
              $t('diagnostics.globalCongiguration.title')
            }}</span>
          </v-card-title>
          <v-card-text class="xrd-card-text">
            <table class="xrd-table">
              <thead>
                <tr>
                  <th class="status-column">{{ $t('diagnostics.status') }}</th>
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
                <tr v-if="globalConf">
                  <td>
                    <StatusIcon
                      :status="statusIconType(globalConf.status_class)"
                    />
                  </td>

                  <td>
                    {{
                      $t(
                        'diagnostics.globalCongiguration.configurationStatus.' +
                          globalConf.status_code,
                      )
                    }}
                  </td>
                  <td class="time-column">
                    {{ globalConf.prev_update_at | formatHoursMins }}
                  </td>
                  <td class="time-column">
                    {{ globalConf.next_update_at | formatHoursMins }}
                  </td>
                </tr>
              </tbody>
            </table>
          </v-card-text>
        </v-card>

        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span class="headline">{{
              $t('diagnostics.timestamping.title')
            }}</span>
          </v-card-title>

          <v-card-text class="xrd-card-text">
            <table class="xrd-table">
              <thead>
                <tr>
                  <th class="status-column">{{ $t('diagnostics.status') }}</th>
                  <th class="url-column">{{ $t('diagnostics.serviceUrl') }}</th>
                  <th>{{ $t('diagnostics.message') }}</th>
                  <th class="time-column">
                    {{ $t('diagnostics.nextUpdate') }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="timestampingService in timestampingServices"
                  v-bind:key="timestampingService.url"
                >
                  <td>
                    <StatusIcon
                      :status="statusIconType(timestampingService.status_class)"
                    />
                  </td>
                  <td class="url-column" data-test="service-url">
                    {{ timestampingService.url }}
                  </td>
                  <td>
                    {{
                      $t(
                        'diagnostics.timestamping.timestampingStatus.' +
                          timestampingService.status_code,
                      )
                    }}
                  </td>
                  <td class="time-column">
                    {{ timestampingService.prev_update_at | formatHoursMins }}
                  </td>
                </tr>
              </tbody>
            </table>
          </v-card-text>
        </v-card>

        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span class="headline">{{
              $t('diagnostics.ocspResponders.title')
            }}</span>
          </v-card-title>
          <v-card-text class="xrd-card-text">
            <div
              v-for="ocspDiags in ocspResponderDiagnostics"
              v-bind:key="ocspDiags.distinguished_name"
            >
              <div class="cert-service-name">
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
                  <tr
                    v-for="ocsp in ocspDiags.ocsp_responders"
                    v-bind:key="ocsp.url"
                  >
                    <td>
                      <StatusIcon :status="statusIconType(ocsp.status_class)" />
                    </td>
                    <td class="url-column" data-test="service-url">
                      {{ ocsp.url }}
                    </td>
                    <td>
                      {{
                        $t(
                          'diagnostics.ocspResponders.ocspStatus.' +
                            ocsp.status_code,
                        )
                      }}
                    </td>
                    <td class="time-column">
                      {{ ocsp.prev_update_at | formatHoursMins }}
                    </td>
                    <td class="time-column">
                      {{ ocsp.next_update_at | formatHoursMins }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </v-card-text>
        </v-card>
      </v-layout>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {
  TimestampingServiceDiagnostics,
  OcspResponderDiagnostics,
  GlobalConfDiagnostics,
} from '@/openapi-types';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';

export default Vue.extend({
  components: {
    AlertsContainer,
  },
  data: () => ({
    timestampingServices: [] as TimestampingServiceDiagnostics[],
    globalConf: undefined as GlobalConfDiagnostics | undefined,
    ocspResponderDiagnostics: [] as OcspResponderDiagnostics[],
    globalConfLoading: false,
    timestampingLoading: false,
    ocspLoading: false,
  }),
  methods: {
    fetchData(): void {
      this.globalConfLoading = true;
      this.timestampingLoading = true;
      this.ocspLoading = true;

      api
        .get<TimestampingServiceDiagnostics[]>(
          `/diagnostics/timestamping-services`,
        )
        .then((res) => {
          this.timestampingServices = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.timestampingLoading = false;
        });

      api
        .get<GlobalConfDiagnostics>('/diagnostics/globalconf')
        .then((res) => {
          this.globalConf = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.globalConfLoading = false;
        });

      api
        .get<OcspResponderDiagnostics[]>('/diagnostics/ocsp-responders')
        .then((res) => {
          this.ocspResponderDiagnostics = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.ocspLoading = false;
        });
    },

    statusIconType(status: string): string {
      if (!status) {
        return '';
      }
      switch (status) {
        case 'OK':
          return 'green';
        case 'WAITING':
          return 'orange-ring';
        case 'FAIL':
          return 'red';
        default:
          return 'red';
      }
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/shared';
@import '~styles/colors';
@import '~styles/tables';

.xrd-view-common {
  width: 100%;
}

.inner-wrap {
  max-width: 1000px;
  margin-left: auto;
  margin-right: auto;

  @media only screen and (max-width: 1030px) {
    margin-left: 10px;
    margin-right: 10px;
  }
}

.xrd-card-text {
  padding-left: 0;
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;

  &:first-of-type {
    margin-top: 40px;
  }

  margin-bottom: 30px;

  .v-card__title {
    color: $XRoad-Black100;
    height: 30px;
    padding: 16px;
    font-weight: 700;
    font-size: 18px;
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

.cert-service-name {
  margin-top: 30px;
  margin-left: 16px;

  font-style: normal;
  font-weight: bold;
  font-size: 14px;
  line-height: 20px;
  color: $XRoad-Black100;

  span {
    font-style: normal;
    font-weight: normal;
    font-size: 14px;
    line-height: 20px;
  }
}
</style>
