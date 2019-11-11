<template>
  <v-container>
    <v-tabs v-model="tab" class="xrd-tabs" color="secondary" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{ $t(tab.name) }}</v-tab>
    </v-tabs>
    <router-view />
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  data() {
    return {
      tab: null,
      tabs: [
        {
          key: 'system',
          name: 'tab.settings.systemParameters',
          to: {
            name: RouteName.SystemParameters,
          },
        },
        {
          key: 'backup',
          name: 'tab.settings.backupAndRestore',
          to: {
            name: RouteName.BackupAndRestore,
          },
        },
      ],
    };
  },
});
</script>

