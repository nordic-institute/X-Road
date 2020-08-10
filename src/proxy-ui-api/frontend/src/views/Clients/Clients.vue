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
});
</script>
