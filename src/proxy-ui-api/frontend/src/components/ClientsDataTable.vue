<template>
  <v-layout align-center justify-center column fill-height elevation-0 class="full-width">
    <div class="table-toolbar">
      <v-text-field v-model="search" label="Search" single-line hide-details class="search-input">
        <v-icon slot="append" small>fas fa-search</v-icon>
      </v-text-field>
      <v-btn
        v-if="showAddClient()"
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
      :items="clients"
      :search="search"
      :pagination.sync="pagination"
      :disable-initial-sort="true"
      :must-sort="true"
      :customSort="customSort"
      :customFilter="customFilter"
      hide-actions
      expand
      class="elevation-0 data-table"
      item-key="id"
      ref="dTable"
    >
      <template slot="items" slot-scope="props">
        <tr @click="props.expanded = !props.expanded">
          <!-- Name -->
          <td class="td-name px-2">
            <!-- Name - Owner member -->
            <template v-if="props.item.type == 'owner'">
              <v-icon color="grey darken-2" class="pl-1" small>fas fa-folder-open</v-icon>
              <span
                class="font-weight-bold name"
                @click="openClient(props.item)"
              >{{props.item.name}} (Owner)</span>
            </template>
            <!-- Name - member -->
            <template v-else-if="props.item.type == 'client'">
              <v-icon color="grey darken-2" class="pl-1" small>far fa-folder-open</v-icon>
              <span class="font-weight-bold name-member">{{props.item.name}}</span>
            </template>
            <!-- Name - Subsystem -->
            <template v-else>
              <v-icon
                color="grey darken-2"
                class="pl-1"
                :class="{ 'pl-5': treeMode }"
                small
              >far fa-address-card</v-icon>
              <span
                class="font-weight-bold name"
                @click="openSubsystem(props.item)"
              >{{props.item.name}}</span>
            </template>
          </td>
          <!-- Id -->
          <td class="text-xs-left">
            <template v-if="props.item.type !== 'client'">
              <span>{{props.item.id}}</span>
            </template>
          </td>
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
              v-if="(props.item.type == 'client' || props.item.type == 'owner') && showAddClient()"
              small
              outline
              round
              color="primary"
              class="text-capitalize table-button xr-small-button"
              @click="addSubsystem(props.item)"
            >Add Subsystem</v-btn>
          </td>
        </tr>
      </template>

      <template slot="no-data">No data</template>
      <v-alert
        slot="no-results"
        :value="true"
        color="error"
      >Your search for "{{ search }}" found no results.</v-alert>
    </v-data-table>
  </v-layout>
</template>

<script lang="ts">
/**
 * This component renders the Clients data table.
 * Default sort and filter functions are replaced to achieve the end result where
 */
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { getObjectValueByPath, getNestedValue } from '@/util/helpers';
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  data: () => ({
    search: '',
    pagination: {
      sortBy: 'sortNameAsc',
      rowsPerPage: -1,
    },
    headers: [
      {
        text: 'Name',
        align: 'left',
        value: 'sortNameAsc',
        class: 'xr-table-header',
      },
      { text: 'ID', align: 'left', value: 'id', class: 'xr-table-header' },
      {
        text: 'Status',
        align: 'left',
        value: 'status',
        class: 'xr-table-header',
      },
      { text: '', value: '', sortable: false, class: 'xr-table-header' },
    ],

    editedIndex: -1,
  }),

  computed: {
    ...mapGetters(['clients', 'loading']),
    formTitle(): string {
      return this.editedIndex === -1 ? 'New Item' : 'Edit Item';
    },
    treeMode(): boolean {
      // Switch between the "tree" view and the "flat" view
      if (this.search) {
        return false;
      } else if (this.pagination.sortBy === 'status') {
        return false;
      }
      return true;
    },
  },

  methods: {
    showAddClient(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_CLIENT);
    },
    getClientIcon(type: string) {
      if (!type) {
        return '';
      }
      switch (type.toLowerCase()) {
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
      if (!status) {
        return '';
      }
      switch (status.toLowerCase()) {
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

    openClient(item: any): void {
      this.$router.push({
        name: RouteName.Client,
        params: { id: item.id },
      });
    },

    openSubsystem(item: any): void {
      this.$router.push({
        name: RouteName.Subsystem,
        params: { id: item.id },
      });
    },

    addClient(): void {
      this.$router.push({
        name: RouteName.AddClient,
      });
    },

    addSubsystem(item: any) {
      this.$router.push({
        name: RouteName.AddSubsystem,
      });
    },

    customFilter: (items: any, search: any, filter: any, headers: any[]) => {
      // Override for the default filter function.
      // This is done to filter by the name (that is visible to user) instead of sortNameAsc or sortNameDesc.
      // base copied from here: https://github.com/vuetifyjs/vuetify/blob/master/packages/vuetify/src/components/VDataTable/VDataTable.js
      search = search.toString().toLowerCase();
      if (search.trim() === '') {
        return items;
      }

      const props = headers.map((h) => h.value);
      // Replace "sort name" with name
      props[0] = 'name';
      // pop the empty "button column" header value
      props.pop();

      return items.filter((item: any) =>
        props.some((prop) =>
          filter(getObjectValueByPath(item, prop, item[prop]), search),
        ),
      );
    },

    customSort(items: any[], index: string, isDesc: boolean) {
      // Override of the default sorting function for the Name column to use sortNameAsc or sortNameDesc instead.
      // This is needed to achieve the order where member is always over the subsystem regardless of the sort direction.
      items.sort((a, b) => {
        if (index === 'sortNameAsc') {
          if (!isDesc) {
            return a[index] < b[index] ? -1 : 1;
          } else {
            // When sorting descending by name, replace the sort data
            return b.sortNameDesc < a.sortNameDesc ? -1 : 1;
          }
        } else {
          if (!isDesc) {
            return a[index] < b[index] ? -1 : 1;
          } else {
            return b[index] < a[index] ? -1 : 1;
          }
        }
      });
      return items;
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

.search-input {
  max-width: 300px;
}

.data-table {
  width: 100%;
}

.full-width {
  width: 100%;
  max-width: 1280px;
  padding-left: 20px;
  padding-right: 20px;
}

.table-button {
  margin-top: auto;
  margin-bottom: auto;
}

.name {
  text-decoration: underline;
  margin-left: 14px;
  margin-top: auto;
  margin-bottom: auto;
  text-align: center;
  cursor: pointer;
}

.name-member {
  margin-left: 14px;
  margin-top: auto;
  margin-bottom: auto;
  text-align: center;
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
