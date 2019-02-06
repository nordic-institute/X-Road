<template>
  <v-app class="xr-app">
    <div>
      <transition name="fade" mode="out-in">
        <router-view/>
      </transition>
    </div>
    <snackbar ref="snackbar"></snackbar>
  </v-app>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';
import SnackbarMixin from './components/SnackbarMixin.vue';

export default Vue.extend({
  name: 'App',
  mixins: [SnackbarMixin],
  created() {
    // Add a response interceptor
    axios.interceptors.response.use(
      response => {
        return response;
      },
      error => {
        if (
          error.response.status === 401 &&
          error.response.config &&
          !error.response.config.__isRetryRequest
        ) {
          // if you ever get an unauthorized, logout the user
          this.$store.dispatch('clearAuth');
          this.$router.replace('/login');
        }
        // Do something with response error
        throw error;
      }
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
.theme--light.application.xr-app {
  background: white;
}
</style>

