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
  <v-app-bar app dark absolute color="#636161" flat height="32" max-height="32">
    <div class="auth-container" v-if="isAuthenticated">
      <div class="server-type">X-ROAD SECURITY SERVER</div>
      <div
        class="server-name"
        data-test="app-toolbar-server-name"
        v-show="currentSecurityServer.id"
      >
        {{
          `${currentSecurityServer.instance_id} : ${currentSecurityServer.server_code}`
        }}
      </div>
      <div data-test="app-toolbar-server-address">
        {{ currentSecurityServer.server_address }}
      </div>
    </div>
  </v-app-bar>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'toolbar',
  computed: {
    ...mapGetters(['currentSecurityServer', 'isAuthenticated']),
  },
  methods: {
    home(): void {
      this.$router
        .replace({
          name: this.$store.getters.firstAllowedTab.to.name,
        })
        .catch((err) => {
          // Ignore the error regarding navigating to the same path
          if (err.name === 'NavigationDuplicated') {
            // eslint-disable-next-line no-console
            console.log('Duplicate navigation');
          } else {
            // Throw for any other errors
            throw err;
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

.auth-container {
  font-size: 12px;
  line-height: 16px;
  text-align: center;
  color: #dedce4;
  display: flex;
  height: 100%;
  align-items: center;
  width: 100%;
}
</style>
