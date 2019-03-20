<template>
  <div class="content">
    <v-flex mb-4>
      <h1 class="display-1 mb-3">Name (Member)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details">Details</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="details">
        <v-card flat>
          <clientDetails/>
        </v-card>
      </v-tab-item>
      <v-tab-item key="internalServers">
        <v-card flat>
          <v-card-text>Internal servers placeholder</v-card-text>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import ClientDetails from '@/components/ClientDetails.vue';

export default Vue.extend({
  components: {
    ClientDetails,
  },
  data() {
    return {
      tab: null,
    };
  },
});
</script>

<style lang="scss" >
@import '../assets/tables';

.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}

.content {
  width: 100%;
}
</style>

