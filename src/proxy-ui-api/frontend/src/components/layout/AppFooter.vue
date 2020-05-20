<template>
  <v-footer color="grey lighten-3">
    <v-layout align-center justify-center>
      <v-row class="xrd-tab-max-width">
        <v-col cols="6" sm="3" class="pt-2">
          <v-img
            :src="require('../../assets/xroad_logo_64_purple.png')"
            height="64"
            width="128"
            max-height="64"
            max-width="128"
          ></v-img>
        </v-col>
        <v-col cols="3" class="footer-col pt-5">
          <v-row>
            <v-col>
              <span class="footer-title">{{$t('footer.software.title')}}</span>
            </v-col>
          </v-row>
          <v-row>
            <v-col class="py-0">
              {{$t('footer.software.versionPrefix')}}&nbsp;
              <span data-test="app-footer-server-version">{{securityServerVersion.info || ''}}</span>
            </v-col>
          </v-row>
          <v-row>
            <v-col>
              <a class="footer-link" target="_blank"
                 href="https://x-road.global/feedback">
                {{$t('footer.software.feedback')}}
              </a>
            </v-col>
          </v-row>
        </v-col>
        <v-col class="footer-col pt-5">
          <v-row>
            <v-col>
              <span class="footer-title">{{$t('footer.copyright.title')}}</span>
            </v-col>
          </v-row>
          <v-row>
            <v-col class="py-0">
              {{$t('footer.copyright.company')}}
            </v-col>
          </v-row>
          <v-row>
            <v-col>
              <a class="footer-link" href="https://niis.org/" target="_blank">
                {{$t('footer.copyright.companyUrlTitle')}}
              </a>
            </v-col>
          </v-row>
        </v-col>
      </v-row>
    </v-layout>
  </v-footer>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  name: 'app-footer',
  computed: {
    ...mapGetters(['securityServerVersion']),
  },
});
</script>

<style lang="scss" scoped>
  $text-color: darken(#9e9e9e, 15);

  .footer-title {
    color: $text-color;
    text-transform: uppercase;
    font-size: 0.9rem;
    font-weight: bold;
  }

  .footer-col {
    color: $text-color;
    font-size: 0.875rem;
  }

  .footer-link {
    color: $text-color;
  }
</style>
