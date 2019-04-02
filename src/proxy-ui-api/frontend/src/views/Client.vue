<template>
  <div class="xr-tab-max-width">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.member_name}} (Owner)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details">Details</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="details">
        <v-card flat>
          <clientDetails :id="$route.query.id"/>
        </v-card>
      </v-tab-item>
      <v-tab-item key="internalServers">
        <v-card flat>
          <internalServers :id="$route.query.id"/>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import ClientDetails from '@/components/ClientDetails.vue';
import InternalServers from '@/components/InternalServers.vue';

export default Vue.extend({
  props: {},
  computed: {
    ...mapGetters(['client']),
  },
  components: {
    ClientDetails,
    InternalServers,
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
</style>

