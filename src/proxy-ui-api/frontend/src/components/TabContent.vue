<template>
  <div>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="system">System Parameters</v-tab>
      <v-tab key="backup">Back Up and Restore</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
      <v-tab key="localGroups">Local Groups</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="system">
        <v-card flat>
          <v-card
            class="mt-5 text-xs-center"
            color="grey lighten-2"
            height="250px"
          >System placeholder</v-card>
        </v-card>
      </v-tab-item>
      <v-tab-item key="backup">
        <v-card flat>
          <v-card-text>Backup placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="internalServers">
        <v-card flat>
          <v-card-text>Internal servers placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="localGroups">
        <v-card flat>
          <v-card-text>Local Groups placeholder</v-card-text>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import DataTable from './DataTable.vue';

export default Vue.extend({
  components: {
    DataTable,
  },
  data() {
    return {
      tab: null,
      items: ['boo', 'lorem', 'things'],
      text:
        'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.',
    };
  },
});
</script>

<style lang="scss" >
.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}
</style>

