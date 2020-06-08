<template>
  <div>
    <router-view name="top" />
    <v-layout align-center justify-center>
      <v-layout mt-5 align-center justify-center class="base-full-width frame">
        <transition name="fade" mode="out-in">
          <router-view />
        </transition>
      </v-layout>
    </v-layout>

    <v-dialog v-model="logoutDialog" width="500" persistent>
      <v-card class="xrd-card">
        <v-card-title>
          <span class="headline">{{ $t('logout.sessionExpired') }}</span>
        </v-card-title>
        <v-card-text class="pt-4">{{ $t('logout.idleWarning') }}</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            color="primary"
            rounded
            dark
            class="mb-2 rounded-button elevation-0"
            @click="closeLogoutDialog()"
            >{{ $t('action.ok') }}</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  data() {
    return {
      sessionPollInterval: 0,
      alertsPollInterval: 0,
      logoutDialog: false,
    };
  },
  created() {
    // Set interval to poll backend for session
    this.sessionPollInterval = setInterval(() => this.pollSessionStatus(), 30000);
    this.$store.dispatch('checkAlertStatus'); // Poll immediately to get initial alerts state
    this.alertsPollInterval = setInterval(() => this.$store.dispatch('checkAlertStatus'), 30000);
  },
  methods: {
    closeLogoutDialog() {
      this.logoutDialog = false;
      this.logout();
    },
    pollSessionStatus() {
      return api.get('/notifications/session-status').catch((error) => {
        if (error.response && error.response.status === 401) {
          this.logoutDialog = true;
          clearInterval(this.sessionPollInterval);
          clearInterval(this.alertsPollInterval);
        }
      });
    },
    logout(): void {
      this.$store.dispatch('logout');
      this.$router.replace({ name: RouteName.Login });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/shared';
.base-full-width {
  width: 100%;
  max-width: $view-area-max-width;
  margin: 10px;
}
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

.frame {
  padding-bottom: 40px;
}
</style>
