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
    class="main-toolbar"
    app
    dark
    absolute
    color="#636161"
    flat
    height="32"
    max-height="32"
  >
    <div v-if="authenticated" class="auth-container">
      <div class="server-type">CAMDX SECURITY SERVER</div>
      <div
        v-show="currentSecurityServer.id"
        class="server-name"
        data-test="app-toolbar-server-name"
      >
        {{
          `${currentSecurityServer.instance_id} : ${currentSecurityServer.server_code}`
        }}
      </div>
      <div data-test="app-toolbar-server-address">
        {{ currentSecurityServer.server_address }}
      </div>
    </div>
    <div
      v-if="shouldShowNodeType"
      class="node-type"
      data-test="app-toolbar-node-type"
    >
      {{ $t(`toolbar.securityServerNodeType.${securityServerNodeType}`) }}
    </div>
  </v-app-bar>
</template>

<script lang="ts">
import Vue from 'vue';
import { NodeType } from '@/openapi-types';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useSystemStore } from '@/store/modules/system';

export default Vue.extend({
  name: 'Toolbar',
  computed: {
    ...mapState(useUser, ['authenticated', 'currentSecurityServer']),
    ...mapState(useSystemStore, ['securityServerNodeType']),
    shouldShowNodeType(): boolean {
      return this.securityServerNodeType !== NodeType.STANDALONE;
    },
  },
});
</script>

<style lang="scss">
.main-toolbar > .v-toolbar__content {
  justify-content: space-between;
}
</style>

<style lang="scss" scoped>
@import '~styles/colors';

.server-name {
  margin: 20px;
  margin-right: 10px;
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

.node-type {
  font-size: 12px;
  font-style: normal;
  font-weight: bold;
  color: $XRoad-WarmGrey30;
  margin-right: 64px;
  user-select: none;

  @media only screen and (max-width: 920px) {
    margin-right: 20px;
  }
}

.auth-container {
  font-size: 12px;
  line-height: 16px;
  text-align: center;
  color: $XRoad-WarmGrey30;
  display: flex;
  height: 100%;
  align-items: center;
}
</style>
