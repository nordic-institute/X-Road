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
  <v-container
    v-if="isAuthenticated && !needsInitialization && hasAlerts"
    fluid
    class="alerts-container"
  >
    <v-alert
      data-test="global-alert-restore"
      :value="showRestoreInProgress"
      color="red"
      border="left"
      colored-border
      class="alert"
      icon="icon-Error-notification"
    >
      <span class="alert-text">{{
        $t('globalAlert.backupRestoreInProgress', {
          startTime: formatDateTime(restoreStartTime),
        })
      }}</span>
    </v-alert>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { formatDateTime } from '@/filters';

export default Vue.extend({
  name: 'AlertsContainer',
  computed: {
    hasAlerts(): boolean {
      return this.showRestoreInProgress;
    },
    isAuthenticated(): boolean {
      // Mock. Add vuex getter later.
      return true;
    },
    showRestoreInProgress(): boolean {
      // Mock. Add vuex getter later.
      return true;
    },
    needsInitialization(): boolean {
      // Mock. Add vuex getter later.
      return true;
    },
    restoreStartTime(): boolean {
      // Mock. Add vuex getter later.
      return true;
    },
  },
  methods: {
    formatDateTime,
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.alerts-container {
  width: 1000px;
  padding: 0;

  & > * {
    margin-top: 0;
    margin-bottom: 4px;
    border-radius: 0;
  }

  & > :first-child {
    margin-top: 4px;
  }

  .alert {
    margin-top: 8px;
    border-radius: 4px;
  }

  .alert-text {
    color: $XRoad-Black100;
    display: block;
  }
}
</style>
