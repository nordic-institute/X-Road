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
  <v-system-bar
    height="32"
    :color="isInitialized ? 'system-bar' : 'system-bar-init'"
  >
    <div v-if="isAuthenticated" class="auth-container">
      <div class="server-type">
        {{ $t('global.appTitle').toUpperCase() }}
      </div>
      <div
        v-show="!isInitialized"
        class="initialization-phase-title"
        data-test="app-toolbar-server-init-phase-id"
      >
        {{ $t('init.initialConfiguration') }}
      </div>
      <div
        v-show="isInitialized"
        class="server-name"
        data-test="app-toolbar-server-instance-address"
      >
        {{ serverName }}
      </div>
      <div
        v-show="isHighAvailabilityConfigured"
        class="node-name"
        data-test="app-toolbar-node-name"
      >
        {{ `${systemStatus.high_availability_status?.node_name}` }}
      </div>
    </div>
  </v-system-bar>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useSystem } from '@/store/modules/system';

const systemStore = useSystem();

const initializationParameters = computed(
  () => systemStore.getSystemStatus?.initialization_status,
);
const serverName = computed(() =>
  initializationParameters.value
    ? `${initializationParameters.value.instance_identifier} : ${initializationParameters.value.central_server_address}`
    : '',
);
const isInitialized = computed(() => systemStore.isServerInitialized);
const isAuthenticated = computed(() => true);
const systemStatus = computed(() => systemStore.getSystemStatus);
const isHighAvailabilityConfigured = computed(
  () => systemStore.getSystemStatus?.high_availability_status?.is_ha_configured,
);
</script>

<style lang="scss" scoped>
.auth-container {
  padding-left: 88px;
  font-size: 12px;
  line-height: 16px;
  color: #dedce4;
  display: flex;
  height: 100%;
  align-items: center;
  width: 100%;

  .initialization-phase-title {
    margin: 20px;
  }

  .server-name {
    margin: 20px;
    margin-right: 10px;
  }

  .node-name {
    margin-left: auto;
    margin-right: 70px;
    display: flex;
    align-items: center;
  }

  .server-type {
    font-style: normal;
    font-weight: bold;
    user-select: none;

    @media only screen and (max-width: 920px) {
      display: none;
    }
  }
}
</style>
