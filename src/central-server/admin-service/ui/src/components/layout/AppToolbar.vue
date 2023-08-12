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
  <v-app-bar
    app
    dark
    absolute
    :color="isInitialized ? colors.Purple100 : colors.Purple70"
    flat
    height="32"
    max-height="32"
  >
    <div v-if="isAuthenticated" class="auth-container">
      <div class="server-type">CAMDX CENTRAL SERVER</div>
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
        {{
          `${initializationParameters.instance_identifier} : ${initializationParameters.central_server_address}`
        }}
      </div>
      <div
        v-show="isHighAvailabilityConfigured"
        class="node-name"
        data-test="app-toolbar-node-name"
      >
        {{ `${systemStatus.high_availability_status.node_name}` }}
      </div>
    </div>
  </v-app-bar>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';
import { mapState } from 'pinia';
import { systemStore } from '@/store/modules/system';

export default Vue.extend({
  name: 'Toolbar',
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    ...mapState(systemStore, ['getSystemStatus']),
    ...mapState(systemStore, ['getSystemStatus', 'isServerInitialized']),
    initializationParameters() {
      return this.getSystemStatus?.initialization_status;
    },
    isInitialized(): boolean {
      return this.isServerInitialized;
    },
    isAuthenticated(): boolean {
      return true;
    },
    systemStatus() {
      return this.getSystemStatus;
    },
    isHighAvailabilityConfigured() {
      return this.getSystemStatus?.high_availability_status?.is_ha_configured;
    },
  },
});
</script>

<style lang="scss" scoped>
.auth-container {
  font-size: 12px;
  line-height: 16px;
  text-align: center;
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
    margin-left: 64px;
    user-select: none;

    @media only screen and (max-width: 920px) {
      display: none;
    }
  }
}
</style>
