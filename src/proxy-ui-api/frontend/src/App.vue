<template>
  <v-app class="xrd-app">
    <div>
      <transition name="fade" mode="out-in">
        <router-view />
      </transition>
    </div>
    <snackbar ref="snackbar"></snackbar>
  </v-app>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
import SnackbarMixin from './components/SnackbarMixin.vue';
import { RouteName } from '@/global';
import * as Helpers from '@/util/helpers';

export default Vue.extend({
  name: 'App',
  mixins: [SnackbarMixin],
  created() {
    // Add a response interceptor
    axios.interceptors.response.use(
      (response) => {
        return response;
      },
      (error) => {
        // Check that it's proper "unauthorized error".
        // Also the response from from session timeout polling is handled elsewhere
        if (
          error.response.status === 401 &&
          error.response.config &&
          !error.response.config.__isRetryRequest &&
          !error.request.responseURL.includes('notifications/session-status')
        ) {
          // if you ever get an unauthorized, logout the user
          this.$store.dispatch('clearAuth');
          this.$router.replace({ name: RouteName.Login });
        }
        // Do something with response error
        throw error;
      },
    );

    // Custom validators for vee-validate
    this.$validator.extend('restUrl', {
      getMessage: (field, args) => this.$t('validation.invalidRest') as string,
      validate: (value, args) => {
        if (Helpers.isValidRestURL(value)) {
          return true;
        }
        return false;
      },
    });

    this.$validator.extend('wsdlUrl', {
      getMessage: (field, args) => this.$t('validation.invalidWsdl') as string,
      validate: (value, args) => {
        if (Helpers.isValidWsdlURL(value)) {
          return true;
        }
        return false;
      },
    });
  },
});
</script>

<style lang="scss">
@import './assets/global-style.scss';
</style>

<style lang="scss" scoped>
.fade-enter-active,
.fade-leave-active {
  transition-duration: 0.2s;
  transition-property: opacity;
  transition-timing-function: ease;
}

.fade-enter,
.fade-leave-active {
  opacity: 0;
}

// Set the app background color
.theme--light.v-application.xrd-app {
  background: white;
}
</style>

