<template>
  <v-layout align-center justify-center column fill-height elevation-0>
    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.globalCongiguration.title')}}</span>
      </v-card-title>
      <ProgressLinear :active="globalConfLoading" />
      <v-card-text class="pt-4">
        <table class="xrd-table">
          <thead>
            <tr>
              <th class="status-column">{{$t('diagnostics.status')}}</th>
              <th>{{$t('diagnostics.message')}}</th>
              <th class="time-column">{{$t('diagnostics.previousUpdate')}}</th>
              <th class="time-column">{{$t('diagnostics.nextUpdate')}}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="globalConf">
              <td>
                <StatusIcon :status="statusIconType(globalConf.status_class)" />
              </td>

              <td>{{$t('diagnostics.globalCongiguration.configurationStatus.'+globalConf.status_code)}}</td>
              <td class="time-column">{{globalConf.prev_update_at | formatHoursMins}}</td>
              <td class="time-column">{{globalConf.next_update_at | formatHoursMins}}</td>
            </tr>
          </tbody>
        </table>
      </v-card-text>
    </v-card>

    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.timestamping.title')}}</span>
      </v-card-title>
      <ProgressLinear :active="timestampingLoading" />

      <v-card-text class="pt-4">
        <table class="xrd-table">
          <thead>
            <tr>
              <th class="status-column">{{$t('diagnostics.status')}}</th>
              <th class="url-column">{{$t('diagnostics.serviceUrl')}}</th>
              <th>{{$t('diagnostics.message')}}</th>
              <th class="time-column">{{$t('diagnostics.nextUpdate')}}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="timestampingService in timestampingServices"
              v-bind:key="timestampingService.url"
            >
              <td>
                <StatusIcon :status="statusIconType(timestampingService.status_class)" />
              </td>
              <td class="url-column" data-test="service-url">{{timestampingService.url}}</td>
              <td>{{$t('diagnostics.timestamping.timestampingStatus.'+timestampingService.status_code)}}</td>
              <td class="time-column">{{timestampingService.prev_update_at | formatHoursMins}}</td>
            </tr>
          </tbody>
        </table>
      </v-card-text>
    </v-card>

    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.ocspResponders.title')}}</span>
      </v-card-title>
      <ProgressLinear :active="ocspLoading" />
      <v-card-text class="pt-4">
        <div
          v-for="ocspDiags in ocspResponderDiagnostics"
          v-bind:key="ocspDiags.distinguished_name"
        >
          <div class="cert-service-name">
            <span>{{$t('diagnostics.ocspResponders.certificationService')}}</span>
            {{ocspDiags.distinguished_name}}
          </div>
          <table class="xrd-table">
            <thead>
              <tr>
                <th class="status-column">{{$t('diagnostics.status')}}</th>
                <th class="url-column">{{$t('diagnostics.serviceUrl')}}</th>
                <th>{{$t('diagnostics.message')}}</th>
                <th class="time-column">{{$t('diagnostics.previousUpdate')}}</th>
                <th class="time-column">{{$t('diagnostics.nextUpdate')}}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ocsp in ocspDiags.ocsp_responders" v-bind:key="ocsp.url">
                <td>
                  <StatusIcon :status="statusIconType(ocsp.status_class)" />
                </td>
                <td class="url-column" data-test="service-url">{{ocsp.url}}</td>
                <td>{{$t('diagnostics.ocspResponders.ocspStatus.'+ocsp.status_code)}}</td>
                <td class="time-column">{{ocsp.prev_update_at | formatHoursMins}}</td>
                <td class="time-column">{{ocsp.next_update_at | formatHoursMins}}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </v-card-text>
    </v-card>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {
  TimestampingServiceDiagnostics,
  OcspResponderDiagnostics,
  GlobalConfDiagnostics,
} from '@/openapi-types';
import StatusIcon from '@/components/ui/StatusIcon.vue';
import ProgressLinear from '@/components/ui/ProgressLinear.vue';

export default Vue.extend({
  components: {
    StatusIcon,
    ProgressLinear,
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
        .get(`/diagnostics/timestamping-services`)
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
        .get(`/diagnostics/globalconf`)
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
        .get(`/diagnostics/ocsp-responders`)
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
@import '../../assets/shared';
@import '../../assets/colors';
@import '../../assets/tables';

.diagnostic-card {
  &:first-of-type {
    margin-top: 40px;
  }

  width: 100%;
  margin-bottom: 30px;

  .v-card__title {
    height: 50px;
    padding: 10px;
    font-size: 16px;
    border-bottom: solid $XRoad-Purple 1px;
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

  span {
    font-weight: bold;
  }
}
</style>
