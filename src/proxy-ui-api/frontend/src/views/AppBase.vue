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
    <v-layout align-center justify-center>
      <v-layout align-center justify-center class="base-full-width">
        <transition name="fade" mode="out-in">
          <router-view />
        </transition>
      </v-layout>
    </v-layout>

    <v-dialog v-model="showDialog" width="500" persistent>
      <v-card class="xrd-card">
        <v-card-title>
          <span class="headline">{{ $t('logout.sessionExpired') }}</span>
        </v-card-title>
        <v-card-text class="pt-4">{{ $t('logout.idleWarning') }}</v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <large-button @click="logout()">{{ $t('action.ok') }}</large-button>
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
    };
  },
  computed: {
    showDialog(): boolean {
      return this.$store.getters.isSessionAlive === false;
    },
  },
  created() {
    // Set interval to poll backend for session
    this.sessionPollInterval = setInterval(
      () => this.pollSessionStatus(),
      30000,
    );
    this.$store.dispatch('checkAlertStatus'); // Poll immediately to get initial alerts state
  },
  methods: {
    pollSessionStatus() {
      return api
        .get('/notifications/session-status')
        .then(() => {
          // Check alert status after a successfull session-status call
          this.$store.dispatch('checkAlertStatus');
        })
        .catch((error) => {
          if (error?.response?.status === 401) {
            this.$store.commit('setSessionAlive', false);
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
