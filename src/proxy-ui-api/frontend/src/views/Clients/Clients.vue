<template>
  <v-layout align-center justify-center column>
    <clientsDataTable />
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import ClientsDataTable from './ClientsDataTable.vue';
export default Vue.extend({
  components: {
    ClientsDataTable,
  },
  data: () => ({}),

  methods: {
    fetchClients() {
      this.$store.dispatch('fetchClients').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
  created() {
    this.fetchClients();
  },
});
</script>
