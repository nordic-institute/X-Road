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
      data-test="global-alert-global-configuration"
      :value="showGlobalConfAlert"
      color="red"
    >
      <span class="alert-text">{{
        $t('globalAlert.globalConfigurationInvalid')
      }}</span>
    </v-alert>
    <v-alert
      data-test="global-alert-soft-token-pin"
      :value="showSoftTokenPinEnteredAlert"
      color="red"
    >
      <span class="alert-text">{{
        $t('globalAlert.softTokenPinNotEntered')
      }}</span>
    </v-alert>
    <v-alert
      data-test="global-alert-soft-token-pin"
      :value="showRestoreInProgress"
      color="red"
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
import { mapGetters } from 'vuex';
import { formatDateTime } from '@/filters';
export default Vue.extend({
  name: 'AlertsContainer',
  computed: {
    hasAlerts(): boolean {
      return (
        this.showGlobalConfAlert ||
        this.showSoftTokenPinEnteredAlert ||
        this.showRestoreInProgress
      );
    },
    ...mapGetters([
      'showGlobalConfAlert',
      'showSoftTokenPinEnteredAlert',
      'showRestoreInProgress',
      'restoreStartTime',
      'isAuthenticated',
      'needsInitialization',
    ]),
  },
  methods: {
    formatDateTime,
  },
});
</script>

<style scoped lang="scss">
.alerts-container {
  width: 100%;
  padding: 0;
  & > * {
    margin-top: 4px;
    margin-bottom: 0;
    border-radius: 0;
  }
  & > :first-child {
    margin-top: 4px;
  }
}
.alert-text {
  color: white;
  text-align: center;
  display: block;
}
</style>
