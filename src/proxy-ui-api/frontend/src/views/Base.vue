<template>
  <div>
    <toolbar/>
    <router-view name="top"/>

    <v-layout mt-5 class="full-width">
      <transition name="fade" mode="out-in">
        <router-view/>
      </transition>
    </v-layout>

    <v-dialog v-model="logoutDialog" width="500" lazy>
      <v-card class="xroad-card">
        <v-card-title>
          <span class="headline">Session expired</span>
        </v-card-title>
        <v-card-text>You have been idle for 30 minutes and your session has expired. For security reasons, you will be logged out.</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            color="primary"
            round
            outline
            class="mb-2 rounded-button"
            @click="closeLogoutDialog()"
          >Ok</v-btn>
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
  watch: {
    logoutDialog(val) {
      // Watch for changes to logout dialog model.
      // Do logout if the user closes the dialog by ok button or clicking outside it.
      if (!val) {
        this.logout();
      }
    },
  },
  created() {
    // Set interval to poll backend for session
    this.interval = setInterval(() => this.pollSessionStatus(), 30000);
  },
  methods: {
    closeLogoutDialog() {
      this.logoutDialog = false;
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
// Override vuetify styling in alert dialog card
.xroad-card {
  .v-card__title {
    border-bottom: solid #663cdc 2px;
  }
}
</style>

<style lang="scss" scoped>
.base-full-width {
  width: 100%;
  max-width: 1280px;
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
</style>
