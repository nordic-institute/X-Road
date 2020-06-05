<template>
  <v-app class="xrd-app">
    <app-toolbar />
    <v-content app>
      <transition name="fade" mode="out-in">
        <router-view />
      </transition>
    </v-content>
    <snackbar />
    <app-footer />
  </v-app>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
import Snackbar from '@/components/ui/Snackbar.vue';
import { RouteName } from '@/global';
import AppFooter from '@/components/layout/AppFooter.vue';
import AppToolbar from '@/components/layout/AppToolbar.vue';

export default Vue.extend({
  name: 'App',
  components: {
    AppToolbar,
    AppFooter,
    Snackbar,
  },
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
