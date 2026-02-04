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
  <AppToolbar />
  <router-view name="navigation" />

  <v-main class="bg-surface pr-10 pb-8">
    <AlertsContainer />
    <div class="mb-6 pa-0 mr-auto">
      <router-view />
    </div>
    <router-view name="footer" />
  </v-main>
</template>

<script lang="ts" setup>
import { useAlerts } from '@/store/modules/alerts';
import { useUser } from '@/store/modules/user';

import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import AppToolbar from '@/layouts/AppToolbar.vue';

const userStore = useUser();
const { checkAlertStatus } = useAlerts();

// Set interval to poll backend for session
const sessionPollInterval = window.setInterval(() => pollSessionStatus(), 30000);
pollSessionStatus();

async function pollSessionStatus() {
  return userStore
    .fetchSessionStatus()
    .then(() => {
      // Check alert status after a successful session-status call
      checkAlertStatus();
    })
    .finally(() => {
      if (!userStore.sessionAlive) {
        clearInterval(sessionPollInterval);
      }
    });
}
</script>
