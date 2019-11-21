<template>
  <v-toolbar dark color="#202020" class="elevation-2">
    <v-img
      :src="require('../../assets/xroad_logo_64.png')"
      height="64"
      width="128"
      max-height="64"
      max-width="128"
      @click="home()"
      class="xrd-logo"
    ></v-img>
    <div class="separator"></div>
    <div class="server-type">Security Server</div>
    <div class="white--text server-name">name of the server</div>
    <v-spacer></v-spacer>Username?
    <v-menu bottom left>
      <template v-slot:activator="{ on }">
        <v-btn icon v-on="on">
          <v-icon>mdi-account-circle</v-icon>
        </v-btn>
      </template>

      <v-list>
        <v-list-item id="logout-list-tile" @click="logout">
          <v-list-item-title id="logout-title">{{$t('login.logOut')}}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </v-toolbar>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'toolbar',
  methods: {
    home(): void {
      this.$router.replace({ name: RouteName.Clients });
    },
    logout(): void {
      this.$store.dispatch('logout');
      this.$router.replace({ name: RouteName.Login });
    },
    demoLogout(): void {
      this.$store.dispatch('demoLogout');
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
</style>
