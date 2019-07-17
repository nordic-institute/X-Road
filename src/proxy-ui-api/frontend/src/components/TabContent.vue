<template>
  <div>
    <v-tabs slot="extension" v-model="tab" class="xrd-tabs" color="white" grow>
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

export default Vue.extend({
  data() {
    return {
      tab: null,
    };
  },
});
</script>

