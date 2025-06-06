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
    <div class="base-full-width">
      <router-view name="subTabs" />

      <div class="sticky">
        <router-view name="alerts" />
      </div>
      <v-row align="center" class="base-full-width" no-gutters>
        <v-col class="d-flex justify-center align-center">
          <xrd-sub-view-container>
            <router-view />
          </xrd-sub-view-container>
        </v-col>
      </v-row>
    </div>

    <v-dialog v-model="showDialog" width="500" persistent>
      <v-card class="xrd-card">
        <v-card-title>
          <span class="text-h5">{{ $t('logout.sessionExpired') }}</span>
        </v-card-title>
        <v-card-text class="pt-4">{{ $t('logout.idleWarning') }}</v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <xrd-button data-test="session-expired-ok-button" @click="logout()">
            {{ $t('action.ok') }}
          </xrd-button>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import * as api from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useAlerts } from '@/store/modules/alerts';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  data() {
    return {
      sessionPollInterval: 0,
      alertsPollInterval: 0,
    };
  },
  computed: {
    ...mapState(useUser, ['sessionAlive']),
    showDialog(): boolean {
      return this.sessionAlive === false;
    },
  },
  created() {
    // Set interval to poll backend for session
    this.sessionPollInterval = window.setInterval(
      () => this.pollSessionStatus(),
      30000,
    );
    this.checkAlertStatus(); // Poll immediately to get initial alerts state
  },
  methods: {
    ...mapActions(useAlerts, ['checkAlertStatus']),
    ...mapActions(useUser, ['logoutUser', 'setSessionAlive']),
    async pollSessionStatus() {
      return api
        .get('/notifications/session-status')
        .then(() => {
          // Check alert status after a successful session-status call
          this.checkAlertStatus();
        })
        .catch((error) => {
          if (error?.response?.status === 401) {
            this.setSessionAlive(false);
            clearInterval(this.sessionPollInterval);
            clearInterval(this.alertsPollInterval);
          }
        });
    },
    logout(): void {
      this.logoutUser();
      this.$router.replace({ name: RouteName.Login });
    },
  },
});
</script>

<style lang="scss" scoped>
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
</style>
