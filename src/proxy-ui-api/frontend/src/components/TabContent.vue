<template>
  <div>
    <v-tabs slot="extension" v-model="tab" class="xrd-tabs" color="secondary" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="system">System Parameters</v-tab>
      <v-tab key="backup">Back Up and Restore</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="system">
        <v-card flat>
          <v-card-text>System placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="backup">
        <v-card flat>
          <v-card-text>Backup placeholder</v-card-text>
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

