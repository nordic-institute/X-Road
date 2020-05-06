<template>
  <div class="wrapper xrd-view-common">
    <v-tabs
      v-model="tab"
      class="xrd-tabs"
      color="secondary"
      grow
      slider-size="4"
    >
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">
        {{ $t(tab.name) }}
      </v-tab>
    </v-tabs>
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';

export default Vue.extend({
  data() {
    return {
      tab: null,
    };
  },
  computed: {
    tabs(): Tab[] {
      const allTabs = [
        {
          key: 'system',
          name: 'tab.settings.systemParameters',
          to: {
            name: RouteName.SystemParameters,
          },
          permission: Permissions.VIEW_SYS_PARAMS,
        },
        {
          key: 'backup',
          name: 'tab.settings.backupAndRestore',
          to: {
            name: RouteName.BackupAndRestore,
          },
          permission: Permissions.BACKUP_CONFIGURATION,
        },
      ];
      return this.$store.getters.getAllowedTabs(allTabs);
    },
  },
});
</script>
<style lang="scss" scoped>
.content {
  width: 1000px;
}
</style>
