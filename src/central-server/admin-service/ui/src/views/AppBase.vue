<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->

<template>
  <div>
    <router-view name="top" />
    <v-row align="center" justify="center" style="margin-top: 0">
      <transition name="fade" mode="out-in">
        <div class="base-full-width">
          <router-view name="subTabs" />
          <div class="sticky">
            <router-view name="alerts" />
          </div>
          <v-row
            align="center"
            justify="center"
            class="base-full-width bottom-pad"
          >
            <router-view />
          </v-row>
        </div>
      </transition>
    </v-row>

    <v-dialog v-if="showDialog" v-model="showDialog" width="500" persistent>
      <v-card class="xrd-card">
        <v-card-title>
          <span class="text-h5">{{ $t('logout.sessionExpired') }}</span>
        </v-card-title>
        <v-card-text class="logout-text pt-4">{{
          $t('logout.idleWarning')
        }}</v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <xrd-button @click="logout()">{{ $t('action.ok') }}</xrd-button>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { RouteName, Timeouts } from '@/global';
import { get } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { useAlerts } from '@/store/modules/alerts';

export default defineComponent({
  data() {
    return {
      sessionPollInterval: 0,
      alertsPollInterval: 0,
    };
  },
  computed: {
    ...mapState(useUser, ['isSessionAlive']),
    showDialog(): boolean {
      return this.isSessionAlive === false;
    },
  },
  created() {
    this.sessionPollInterval = setInterval(
      () => this.pollSessionStatus(),
      Timeouts.POLL_SESSION_TIMEOUT,
    );
    this.pollSessionStatus();
    this.fetchSystemStatus();
    this.checkAlerts();
    // Set interval to poll backend for session
  },
  methods: {
    ...mapActions(useUser, ['setSessionAlive']),
    ...mapActions(useUser, { storeLogout: 'logout' }),
    ...mapActions(useAlerts, ['checkAlerts']),
    ...mapActions(useSystem, [
      'fetchSystemStatus',
      'updateCentralServerAddress',
    ]),
    pollSessionStatus() {
      return get('/notifications/session-status')
        .then(() => {
          // Fetch any statuses from backend that are
          // needed with POLL_SESSION_TIMEOUT periods
          this.checkAlerts();
        })
        .catch((error) => {
          if (error?.response?.status === 401) {
            this.setSessionAlive(false);
            clearInterval(this.sessionPollInterval);
          }
        });
    },
    logout(): void {
      this.storeLogout();
      this.$router.replace({ name: RouteName.Login });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/shared';

.logout-text {
  font-size: 14px !important;
}

.sticky {
  position: -webkit-sticky;
  position: sticky;
  top: 4px;
  z-index: 7; // Vuetify drop menu has z-index 8 so this goes just under those. Modals/dialogs have z-index 202
}

.base-full-width {
  width: 100%;
  padding-bottom: 40px;
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
