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
              <td>x</td>
              <td class="service-url" data-test="service-url">Message, hello</td>
              <td>12:00</td>
              <td>12:00</td>
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
            <tr>
              <td>x</td>
              <td class="service-url" data-test="service-url">
                <serviceIcon :service="service" />https://x-road.tee
              </td>
              <td>message, hello!</td>
              <td>12:00</td>
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
        {{$t('diagnostics.ocspResponders.certificationService')}}
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
            <tr>
              <td>o</td>
              <td class="service-url" data-test="service-url">https://vauva.fi</td>
              <td>hello?</td>
              <td>12:00</td>
              <td>12:00</td>
            </tr>
          </tbody>
        </table>
      </v-card-text>
    </v-card>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  data: () => ({}),
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
</style>
