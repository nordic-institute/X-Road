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
  <v-app-bar app dark color="#202020" elevation="2">
    <v-img
      :src="require('../../assets/xroad_logo_64.png')"
      height="64"
      width="128"
      max-height="64"
      max-width="128"
      @click="home()"
      class="xrd-logo"
    ></v-img>
    <div class="auth-container" v-if="isAuthenticated">
      <div class="separator"></div>
      <div class="server-type">Security Server</div>
      <div
        class="white--text server-name"
        data-test="app-toolbar-server-name"
        v-show="currentSecurityServer.id"
        :title="currentSecurityServer.server_address"
      >
        {{
          `${currentSecurityServer.instance_id} : ${currentSecurityServer.server_code}`
        }}
      </div>
      <v-spacer></v-spacer>
      {{ username }}
      <v-menu bottom left>
        <template v-slot:activator="{ on }">
          <v-btn icon v-on="on">
            <v-icon>mdi-account-circle</v-icon>
          </v-btn>
        </template>

        <v-list>
          <v-list-item id="logout-list-tile" @click="logout">
            <v-list-item-title id="logout-title">{{
              $t('login.logOut')
            }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
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
    ...mapGetters(['username', 'currentSecurityServer', 'isAuthenticated']),
  },
  methods: {
    home(): void {
      this.$router.replace({ name: RouteName.Clients });
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
  font-size: 14px;
  margin: 20px;
}

.separator {
  width: 2px;
  height: 24px;
  margin-left: 6px;
  background-color: white;
}

.xrd-logo {
  cursor: pointer;
}

.server-type {
  height: 20px;
  width: 112px;
  border-radius: 4px;
  background-color: #00c8e6;
  text-align: center;
  margin-left: 22px;
  font-size: 12px;
  user-select: none;

  @media only screen and (max-width: 920px) {
    display: none;
  }
}

.auth-container {
  display: flex;
  height: 100%;
  align-items: center;
  width: 100%;
}
</style>
