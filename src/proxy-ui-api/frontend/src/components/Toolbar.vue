<template>
  <v-toolbar dark color="primary" class="elevation-2">
    <v-img
      :src="require('../assets/xroad_logo_64.png')"
      height="64"
      width="128"
      max-height="64"
      max-width="128"
    ></v-img>
    <div class="separator"></div>
    <div class="white--text server-name">name of the server</div>
    <v-spacer></v-spacer>Username?
    <v-menu bottom left>
      <v-btn slot="activator" dark icon>
        <v-icon>person</v-icon>
      </v-btn>

      <v-list>
        <v-list-tile @click="logout">
          <v-list-tile-title>Logout</v-list-tile-title>
        </v-list-tile>

        <v-list-tile @click="demoLogout">
          <v-list-tile-title>Logout without UI</v-list-tile-title>
        </v-list-tile>
      </v-list>
    </v-menu>
  </v-toolbar>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'toolbar',
  methods: {
    refresh(): void {
      console.log('Refresh!');
    },
    logout(): void {
      console.log('logout');
      this.$store.dispatch('logout');
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
</style>
