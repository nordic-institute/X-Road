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
  <xrd-app-base>
    <template #top>
      <router-view name="top" />
    </template>
    <template #subTabs>
      <router-view name="subTabs" />
    </template>
    <template #alerts>
      <router-view name="alerts" />
    </template>
    <router-view />
  </xrd-app-base>
</template>

<script lang="ts" setup>
import { Timeouts } from '@/global';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { useAlerts } from '@/store/modules/alerts';
import { XrdAppBase } from '@niis/shared-ui';

const userStore = useUser();
const { checkAlerts } = useAlerts();
const { fetchSystemStatus } = useSystem();

const sessionPollInterval = setInterval(
  () => pollSessionStatus(),
  Timeouts.POLL_SESSION_TIMEOUT,
);
pollSessionStatus();
fetchSystemStatus();
checkAlerts();

async function pollSessionStatus() {
  return userStore
    .fetchSessionStatus()
    .then(() => {
      // Fetch any statuses from backend that are
      // needed with POLL_SESSION_TIMEOUT periods
      checkAlerts();
    })
    .finally(() => {
      if (!userStore.isSessionAlive) {
        clearInterval(sessionPollInterval);
      }
    });
}
</script>
