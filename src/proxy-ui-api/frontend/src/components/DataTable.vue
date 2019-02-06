<template>
  <v-layout align-center justify-center column fill-height elevation-0 class="full-width">
    <v-toolbar flat color="white">
      <v-text-field
        v-model="search"
        append-icon="search"
        label="Search"
        single-line
        hide-details
        class="search-input"
      ></v-text-field>
      <v-spacer></v-spacer>
      <v-btn color="primary" @click="addClient" round dark class="mb-2 rounded-button">Add client</v-btn>
    </v-toolbar>
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="cities"
      :search="search"
      class="elevation-0 data-table"
    >
      <template slot="items" slot-scope="props">
        <td>{{ props.item.name }}</td>
        <td class="text-xs-left">{{ props.item.id }}</td>
        <td class="layout px-0">
          <v-spacer></v-spacer>
          <v-btn
            small
            outline
            round
            color="primary"
            class="mr-2 text-capitalize table-button"
            @click="addSubsystem(props.item)"
          >Add Subsystem</v-btn>
        </td>
      </template>
      <template slot="no-data">No data</template>
      <v-alert
        slot="no-results"
        :value="true"
        color="error"
        icon="warning"
      >Your search for "{{ search }}" found no results.</v-alert>
    </v-data-table>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  data: () => ({
    search: '',
    headers: [
      {
        text: 'Name',
        align: 'left',
        value: 'name',
        class: 'xr-table-header',
      },
      { text: 'ID', align: 'left', value: 'id', class: 'xr-table-header' },
      { text: '', value: 'id', sortable: false, class: 'xr-table-header' },
    ],
    editedIndex: -1,
  }),

  computed: {
    ...mapGetters(['cities', 'loading']),
    formTitle(): string {
      return this.editedIndex === -1 ? 'New Item' : 'Edit Item';
    },
  },

  methods: {
    addClient(): void {
      console.log('edit');
      this.$router.push('/client');
    },

    addSubsystem(item: any) {
      this.$router.push('/subsystem');
    },
  },
});
</script>

<style lang="scss">
.xr-table-header {
  border-bottom: 1px solid #9c9c9c;
}
</style>

<style lang="scss" scoped>
.search-input {
  max-width: 300px;
}

.data-table {
  width: 100%;
}

.full-width {
  width: 100%;
  max-width: 1280px;
}

.table-button {
  height: 24px;
  border-radius: 6px;
}
</style>
