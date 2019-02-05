<template>
  <v-layout align-center justify-center column fill-height elevation-0 class="full-width">
    <div class="table-toolbar">
      <v-text-field
        v-model="search"
        append-icon="search"
        label="Search"
        single-line
        hide-details
        class="search-input"
      ></v-text-field>

      <v-btn
        color="primary"
        @click="addClient"
        elevation-0
        round
        dark
        class="ma-0 rounded-button elevation-0"
      >Add client</v-btn>
    </div>
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="clientsFlat"
      :search="search"
      :disable-initial-sort="true"
      expand
      class="elevation-0 data-table"
      item-key="name"
      ref="dTable"
      :rows-per-page-items="rowsPerPage"
    >
      <template slot="items" slot-scope="props">
        <tr @click="props.expanded = !props.expanded">
          <!-- Name -->
          <td class="td-name px-2">
            <template v-if="props.item.type == 'owner'">
              <v-icon color="grey darken-2" class="pl-1" small>fas fa-folder-open</v-icon>
              <span
                class="font-weight-bold name"
                @click="openClient(props.item)"
              >{{props.item.name}} (Owner)</span>
            </template>

            <template v-else-if="props.item.type == 'client'">
              <v-icon color="grey darken-2" class="pl-1" small>far fa-folder-open</v-icon>
              <span
                class="font-weight-bold name"
                @click="openClient(props.item)"
              >{{props.item.name}}</span>
            </template>

            <template v-else>
              <!-- <v-icon color="grey darken-2" class="pl-5 fal">fa-address-card</v-icon> -->
              <v-icon color="grey darken-2" class="pl-5" small>far fa-address-card</v-icon>
              <span
                class="font-weight-bold name"
                @click="openSubsystem(props.item)"
              >{{props.item.name}}</span>
            </template>
          </td>
          <!-- Id -->
          <td class="text-xs-left">{{ props.item.id }}</td>
          <!-- Status -->
          <td class="text-xs-left">
            <div class="status-wrapper">
              <div :class="getStatusIconClass(props.item.status)"></div>
              <div class="status-text">{{ props.item.status | capitalize }}</div>
            </div>
          </td>
          <td class="layout px-2">
            <v-spacer></v-spacer>
            <v-btn
              v-if="props.item.type == 'client' || props.item.type == 'owner'"
              small
              outline
              round
              color="primary"
              class="text-capitalize table-button"
              @click="addSubsystem(props.item)"
            >Add Subsystem</v-btn>
          </td>
        </tr>
      </template>
      <!--
      <template slot="items" slot-scope="props">
        <tr @click="props.expanded = !props.expanded">
          <td>
            <v-icon color="grey darken-2">business</v-icon>
            {{ props.item.name }}
          </td>
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
        </tr>
      </template>
      -->
      <table slot="expand" slot-scope="props" class="expand-table">
        <!-- <v-card flat>
          <v-card-text>Peek-a-boo! {{props.item}}</v-card-text>
        </v-card>-->
        <tr v-for="sub in props.item.subsystems" v-bind:key="sub.id">
          <td class="expand-name">
            <v-icon color="grey darken-2">computer</v-icon>
            {{ sub.name }}
          </td>
          <td class="expand-id">{{ sub.id }}</td>
        </tr>
      </table>

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
    rowsPerPage: [
      10,
      25,
      50,
      { text: '$vuetify.dataIterator.rowsPerPageAll', value: -1 },
    ],
    headers: [
      {
        text: 'Name',
        align: 'left',
        value: 'name',
        class: 'xr-table-header',
      },
      { text: 'ID', align: 'left', value: 'id', class: 'xr-table-header' },
      {
        text: 'Status',
        align: 'left',
        value: 'status',
        class: 'xr-table-header',
      },
      { text: '', value: 'id', sortable: false, class: 'xr-table-header' },
    ],
    editedIndex: -1,
  }),

  computed: {
    ...mapGetters(['clients', 'loading', 'clientsFlat']),
    formTitle(): string {
      return this.editedIndex === -1 ? 'New Item' : 'Edit Item';
    },
  },

  methods: {
    getClientIcon(type: string) {
      switch (type) {
        case 'client':
          return 'status-green';
        case 'owner':
          return 'status-green-ring';
        case 'subsystem':
          return 'status-orange-ring';
        default:
          return '';
      }
    },
    getStatusIconClass(status: string): string {
      switch (status) {
        case 'registered':
          return 'status-green';
        case 'registration in progress':
          return 'status-green-ring';
        case 'saved':
          return 'status-orange-ring';
        case 'deletion in progress':
          return 'status-red-ring';
        case 'global error':
          return 'status-red';
        default:
          return '';
      }
    },

    openClient(item: object): void {
      console.log('edit');
      this.$router.push('/client');
    },

    openSubsystem(item: object): void {
      this.$router.push('/subsystem');
    },

    addClient(): void {
      this.$router.push('/add-client');
    },

    addSubsystem(item: any) {
      this.$router.push('/add-subsystem');
    },

    customFilter(items: Array<any>, search: string, filter: string) {
      search = search.toString().toLowerCase();
      //return items.filter(row => filter(row['type'], search));
      return items;
    },
  },

  mounted() {
    /*for (let i = 0; i < this.clients.length; i += 1) {
      const item = this.clients[i];
      this.$set(this.$refs.dTable.expanded, item.name, true);
    }*/
  },
});
</script>

<style lang="scss">
.xr-table-header {
  border-bottom: 1px solid #9c9c9c;
}
</style>

<style lang="scss" scoped>
.expand-table {
  // border: solid 2px red;
  width: 100%;
}

.table-toolbar {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  padding-left: 24px;
  margin-bottom: 24px;
}
.expand-name {
  width: 38%;
  padding-left: 60px !important;
}

.expand-id {
}

.expand-row {
  //padding: 40px;
}

.expand-row {
  td {
    //background-color: green;
    //width: 50%;
  }
}
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
  margin-right: 4px;
  margin-top: auto;
  margin-bottom: auto;
}

.name {
  text-decoration: underline;
  margin-left: 14px;
  font-smargin-top: auto;
  margin-bottom: auto;
  text-align: center;
  cursor: pointer;
}

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

%status-icon-shared {
  height: 8px;
  width: 8px;
  border-radius: 50%;
  margin-right: 16px;
}

%status-ring-icon-shared {
  height: 10px;
  width: 10px;
  border-radius: 50%;
  margin-right: 16px;
  border: 2px solid;
}

.status-red {
  @extend %status-icon-shared;
  background: #d0021b;
}

.status-red-ring {
  @extend %status-ring-icon-shared;
  border-color: #d0021b;
}

.status-green {
  @extend %status-icon-shared;
  background: #7ed321;
}

.status-green-ring {
  @extend %status-ring-icon-shared;
  border-color: #7ed321;
}

.status-orange-ring {
  @extend %status-ring-icon-shared;
  border-color: #f5a623;
}
</style>
