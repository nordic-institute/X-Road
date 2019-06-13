<template>
  <div>
    <toolbar/>
    <router-view name="top"/>
    <v-layout align-center justify-center>
      <v-layout mt-5 align-center justify-center class="base-full-width frame">
        <transition name="fade" mode="out-in">
          <router-view/>
        </transition>
      </v-layout>
    </v-layout>

    <v-dialog v-model="logoutDialog" width="500" lazy persistent>
      <v-card class="xroad-card">
        <v-card-title>
          <span class="headline">{{$t('logout.sessionExpired')}}</span>
        </v-card-title>
        <v-card-text class="pt-4">{{$t('logout.idleWarning')}}</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            color="primary"
            round
            dark
            class="mb-2 rounded-button elevation-0"
            @click="closeLogoutDialog()"
          >{{$t('action.ok')}}</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import axios from 'axios';
import { RouteName } from '@/global';
import Toolbar from '../components/Toolbar.vue';
export default Vue.extend({
  components: {
    Toolbar,
  },
  data() {
    return {
      interval: 0,
      logoutDialog: false,
    };
  },
  created() {
    // Set interval to poll backend for session
    this.interval = setInterval(() => this.pollSessionStatus(), 30000);
  },
  methods: {
    closeLogoutDialog() {
      this.logoutDialog = false;
      this.logout();
    },
    pollSessionStatus() {
      return axios.get('/notifications/session-status').catch((error) => {
        if (error.response && error.response.status === 401) {
          this.logoutDialog = true;
          clearInterval(this.interval);
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

<style lang="scss">
</style>

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