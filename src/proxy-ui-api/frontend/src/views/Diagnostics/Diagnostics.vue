<template>
  <v-layout align-center justify-center column fill-height elevation-0>
    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.globalCongiguration.title')}}</span>
      </v-card-title>
      <v-card-text class="pt-4">
        <table class="xrd-table">
          <thead>
            <tr>
              <th>{{$t('diagnostics.status')}}</th>
              <th>{{$t('diagnostics.message')}}</th>
              <th>{{$t('diagnostics.previousUpdate')}}</th>
              <th>{{$t('diagnostics.nextUpdate')}}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>{{globalconf.status_class}}</td>
              <td class="service-url" data-test="service-url">{{globalconf.status_code}}</td>
              <td>{{globalconf.prev_update_at}}</td>
              <td>{{globalconf.next_update_at}}</td>
            </tr>
          </tbody>
        </table>
      </v-card-text>
    </v-card>

    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.timestamping.title')}}</span>
      </v-card-title>
      <v-card-text class="pt-4">
        <table class="xrd-table">
          <thead>
            <tr>
              <th>{{$t('diagnostics.status')}}</th>
              <th>{{$t('diagnostics.serviceUrl')}}</th>
              <th>{{$t('diagnostics.message')}}</th>
              <th>{{$t('diagnostics.nextUpdate')}}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="timestampingService in timestapmingServices"
              v-bind:key="timestampingService.url"
            >
              <td>{{timestampingService.status_class }}</td>
              <td class="service-url" data-test="service-url">
                <serviceIcon :service="service" />
                {{timestampingService.url}}
              </td>
              <td>{{$t('diagnostics.timestamping.timestampingStatus.'+timestampingService.status_code)}}</td>
              <td>{{timestampingService.prev_update_at}}</td>
            </tr>
          </tbody>
        </table>
      </v-card-text>
    </v-card>

    <v-card class="xrd-card diagnostic-card">
      <v-card-title>
        <span class="headline">{{$t('diagnostics.ocspResponders.title')}}</span>
      </v-card-title>
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
                <th>{{$t('diagnostics.status')}}</th>
                <th>{{$t('diagnostics.serviceUrl')}}</th>
                <th>{{$t('diagnostics.message')}}</th>
                <th>{{$t('diagnostics.previousUpdate')}}</th>
                <th>{{$t('diagnostics.nextUpdate')}}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ocsp in ocspDiags.ocsp_responders" v-bind:key="ocsp.url">
                <td>{{ocsp.status_class}}</td>
                <td class="service-url" data-test="service-url">{{ocsp.url}}</td>
                <td>{{$t('diagnostics.ocspResponders.ocspStatus.'+ocsp.status_code)}}</td>
                <td>{{ocsp.prev_update_at}}</td>

                <td>{{ocsp.next_update_at}}</td>
              </tr>
            </tbody>
          </table>
          {{ocsp}}
        </div>
      </v-card-text>
    </v-card>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';

export default Vue.extend({
  data: () => ({
    timestapmingServices: [],
    globalconf: undefined,
    ocspResponderDiagnostics: [],
  }),

  computed: {
    ocsps() {
      const map1 = this.ocspResponderDiagnostics
        .map((obj) => {
          return obj.ocsp_responders;
        })
        .flat();
      return map1;
    },
  },

  methods: {
    fetchData(): void {
      api
        .get(`/diagnostics/timestamping-services`)
        .then((res) => {
          this.timestapmingServices = res.data;
        })
        .catch((error) => {
          throw error;
        });

      api
        .get(`/diagnostics/globalconf`)
        .then((res) => {
          console.log(res);
          this.globalconf = res.data;
        })
        .catch((error) => {
          throw error;
        });

      api
        .get(`/diagnostics/ocsp-responders`)
        .then((res) => {
          console.log(res);
          this.ocspResponderDiagnostics = res.data;
        })
        .catch((error) => {
          throw error;
        });
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

  .v-card__title {
    height: 50px;
    padding: 10px;
    font-size: 16px;
    border-bottom: solid $XRoad-Purple 1px;
  }

  margin-bottom: 30px;
}

.cert-service-name {
  margin-top: 30px;

  span {
    font-weight: bold;
  }
}
</style>
