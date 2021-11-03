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
  <v-container class="xrd-view-common" data-test="diagnostics-view">
    <div class="inner-wrap">
      <div class="xrd-view-title pt-6">{{ $t('tab.main.diagnostics') }}</div>
      <v-layout align-center justify-center column fill-height elevation-0>
        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span class="headline" data-test="diagnostics-java-version">{{
              $t('diagnostics.javaVersion.title')
            }}</span>
          </v-card-title>

          <v-card-text class="xrd-card-text">
            <table class="xrd-table">
              <thead>
                <tr>
                  <th class="status-column">{{ $t('diagnostics.status') }}</th>
                  <th>{{ $t('diagnostics.message') }}</th>
                  <th class="level-column">
                    {{ $t('diagnostics.javaVersion.vendor') }}
                  </th>
                  <th class="level-column">
                    {{ $t('diagnostics.javaVersion.title') }}
                  </th>
                  <th class="level-column">
                    {{ $t('diagnostics.javaVersion.earliest') }}
                  </th>
                  <th class="level-column">
                    {{ $t('diagnostics.javaVersion.latest') }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td data-test="java-icon">
                    <xrd-status-icon
                      v-if="securityServerVersion.using_supported_java_version"
                      status="ok"
                    />
                    <xrd-status-icon v-else status="error" />
                  </td>
                  <td
                    v-if="securityServerVersion.using_supported_java_version"
                    data-test="java-message"
                  >
                    {{ $t('diagnostics.javaVersion.ok') }}
                  </td>
                  <td v-else data-test="java-message">
                    {{ $t('diagnostics.javaVersion.notSupported') }}
                  </td>
                  <td data-test="java-vendor">
                    {{ securityServerVersion.java_vendor }}
                  </td>
                  <td data-test="java-version">
                    {{ securityServerVersion.java_version }}
                  </td>
                  <td data-test="java-min">
                    {{ securityServerVersion.min_java_version }}
                  </td>
                  <td data-test="java-max">
                    {{ securityServerVersion.max_java_version }}
                  </td>
                </tr>
              </tbody>
            </table>
          </v-card-text>
        </v-card>

        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span
              class="headline"
              data-test="diagnostics-global-configuration"
              >{{ $t('diagnostics.globalCongiguration.title') }}</span
            >
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
                    <xrd-status-icon
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
            <span class="headline" data-test="diagnostics-timestamping">{{
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
                  <td></td>
                </tr>
              </tbody>
            </table>
          </v-card-text>
        </v-card>

        <v-card flat class="xrd-card diagnostic-card">
          <v-card-title>
            <span class="headline" data-test="diagnostics-ocsp-responders">{{
              $t('diagnostics.ocspResponders.title')
            }}</span>
          </v-card-title>
          <v-card-text class="xrd-card-text">
            <div
              v-for="ocspDiags in ocspResponderDiagnostics"
              :key="ocspDiags.distinguished_name"
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
                  <tr v-for="ocsp in ocspDiags.ocsp_responders" :key="ocsp.url">
                    <td>
                      <xrd-status-icon
                        :status="statusIconType(ocsp.status_class)"
                      />
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
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';
import {
  TimestampingServiceDiagnostics,
  OcspResponderDiagnostics,
  GlobalConfDiagnostics,
} from '@/openapi-types';

export default Vue.extend({
  data: () => ({
    timestampingServices: [] as TimestampingServiceDiagnostics[],
    globalConf: undefined as GlobalConfDiagnostics | undefined,
    ocspResponderDiagnostics: [] as OcspResponderDiagnostics[],
    globalConfLoading: false,
    timestampingLoading: false,
    ocspLoading: false,
  }),
  computed: {
    ...mapGetters(['securityServerVersion']),
  },

  created() {
    this.fetchData();
  },
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
@import '~styles/shared';
@import '~styles/colors';
@import '~styles/tables';

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

.level-column {
  @media only screen and (min-width: 1200px) {
    width: 20%;
  }
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
  font-size: $XRoad-DefaultFontSize;
  line-height: 20px;
  color: $XRoad-Black100;

  span {
    font-style: normal;
    font-weight: normal;
    font-size: $XRoad-DefaultFontSize;
    line-height: 20px;
  }
}
</style>
