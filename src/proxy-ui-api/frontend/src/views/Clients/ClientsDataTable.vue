<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <v-layout
    align-center
    justify-center
    column
    fill-height
    elevation-0
    class="data-table-wrapper xrd-view-common"
  >
    <div class="table-toolbar pb-3 pt-5">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('tab.main.clients') }}</div>
        <v-text-field
          v-model="search"
          :label="$t('action.search')"
          data-test="search-clients-input"
          single-line
          hide-details
          class="search-input"
          autofocus
        >
          <v-icon slot="append">mdi-magnify</v-icon>
        </v-text-field>
      </div>
      <div>
        <LargeButton
          v-if="showAddMember"
          @click="addMember"
          data-test="add-member-button"
          class="add-member"
          outlined
          ><v-icon class="xrd-large-button-icon">icon-Add</v-icon>
          {{ $t('action.addMember') }}</LargeButton
        >
        <LargeButton
          v-if="showAddClient"
          @click="addClient"
          data-test="add-client-button"
          ><v-icon class="xrd-large-button-icon">icon-Add</v-icon>
          {{ $t('action.addClient') }}</LargeButton
        >
      </div>
    </div>

    <v-data-table
      :loading="clientsLoading"
      :headers="headers"
      :items="clients"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      :sort-by="['visibleName']"
      :custom-sort="customSort"
      :custom-filter="customFilter"
      hide-default-footer
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
    >
      <!-- https://stackoverflow.com/questions/61344980/v-slot-directive-doesnt-support-any-modifier -->
      <template v-slot:[`item.visibleName`]="{ item }">
        <!-- Name - Owner member -->
        <template v-if="item.type === clientTypes.OWNER_MEMBER">
          <i @click="openClient(item)">
            <v-icon class="icon-member icon-size">icon-Folder</v-icon></i
          >
          <span
            v-if="canOpenClient"
            class="member-name identifier-wrap clickable"
            @click="openClient(item)"
            >{{ item.visibleName }}
            <span class="owner-box">{{ $t('client.owner') }}</span></span
          >
          <span v-else class="member-name identifier-wrap owner-box"
            >{{ item.visibleName }} {{ $t('client.owner') }}</span
          >
        </template>
        <!-- Name - Member -->
        <template v-else-if="item.type === clientTypes.MEMBER">
          <i @click="openClient(item)">
            <v-icon class="icon-member icon-size"
              >icon-Folder-outline</v-icon
            ></i
          >
          <span
            v-if="canOpenClient"
            class="member-name identifier-wrap clickable"
            @click="openClient(item)"
            >{{ item.visibleName }}</span
          >
          <span v-else class="name identifier-wrap">{{
            item.visibleName
          }}</span>
        </template>
        <!-- Name - virtual member -->
        <template
          v-else-if="
            item.type === clientTypes.VIRTUAL_MEMBER ||
            item.type === clientTypes.MEMBER
          "
        >
          <v-icon class="icon-virtual-member icon-size"
            >icon-Folder-outline</v-icon
          >

          <span class="identifier-wrap member-name">{{
            item.visibleName
          }}</span>
        </template>
        <!-- Name - Subsystem -->
        <template v-else>
          <span
            v-if="canOpenClient"
            class="name identifier-wrap clickable"
            @click="openSubsystem(item)"
            >{{ item.visibleName }}</span
          >
          <span v-else class="name">{{ item.visibleName }}</span>
        </template>
      </template>

      <template v-slot:[`item.id`]="{ item }">
        <span class="identifier-wrap">{{ item.id }}</span>
      </template>

      <template v-slot:[`item.status`]="{ item }">
        <client-status :status="item.status" />
      </template>

      <template v-slot:[`item.button`]="{ item }">
        <div class="button-wrap">
          <LargeButton
            v-if="
              (item.type === clientTypes.OWNER_MEMBER ||
                item.type === clientTypes.MEMBER ||
                item.type === clientTypes.VIRTUAL_MEMBER) &&
              item.member_name &&
              showAddClient
            "
            text
            :outlined="false"
            @click="addSubsystem(item)"
            ><v-icon class="xrd-large-button-icon">icon-Add</v-icon
            >{{ $t('action.addSubsystem') }}</LargeButton
          >

          <LargeButton
            v-if="
              item.type !== clientTypes.OWNER_MEMBER &&
              item.type !== clientTypes.VIRTUAL_MEMBER &&
              item.status === 'SAVED' &&
              showRegister
            "
            text
            :outlined="false"
            @click="registerClient(item)"
            >{{ $t('action.register') }}</LargeButton
          >
        </div>
      </template>

      <template slot="no-data">{{ $t('action.noData') }}</template>
      <v-alert slot="no-results" :value="true" color="error">{{
        $t('action.emptySearch', { msg: search })
      }}</v-alert>
    </v-data-table>

    <ConfirmDialog
      :dialog="confirmRegisterClient"
      title="clients.action.register.confirm.title"
      text="clients.action.register.confirm.text"
      @cancel="confirmRegisterClient = false"
      @accept="registerAccepted(selectedClient)"
      :loading="registerClientLoading"
    />
  </v-layout>
</template>

<script lang="ts">
/**
 * This component renders the Clients data table.
 * Default sort and filter functions are replaced to achieve the end result where
 */
import Vue from 'vue';
import ClientStatus from './ClientStatus.vue';
import { mapGetters } from 'vuex';
import { Permissions, RouteName, ClientTypes } from '@/global';
import { createClientId } from '@/util/helpers';
import { ExtendedClient } from '@/ui-types';
import { DataTableHeader } from 'vuetify';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    ClientStatus,
  },

  data: () => ({
    search: '' as string,
    clientTypes: ClientTypes,
    pagination: {
      sortBy: 'visibleName' as string,
    },
    confirmRegisterClient: false as boolean,
    registerClientLoading: false as boolean,
    selectedClient: undefined as undefined | ExtendedClient,
  }),

  computed: {
    ...mapGetters(['clients', 'clientsLoading', 'ownerMember']),
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('client.name') as string,
          align: 'start',
          value: 'visibleName',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('client.id') as string,
          align: 'start',
          value: 'id',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('client.status') as string,
          align: 'start',
          value: 'status',
          class: 'xrd-table-header',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header',
        },
      ];
    },
    showAddClient(): boolean {
      return this.$store.getters.hasPermission(Permissions.ADD_CLIENT);
    },
    showAddMember(): boolean {
      return (
        this.$store.getters.hasPermission(Permissions.ADD_CLIENT) &&
        this.$store.getters.realMembers?.length < 2
      );
    },
    showRegister(): boolean {
      return this.$store.getters.hasPermission(Permissions.SEND_CLIENT_REG_REQ);
    },
    canOpenClient(): boolean {
      return this.$store.getters.hasPermission(Permissions.VIEW_CLIENT_DETAILS);
    },
  },

  methods: {
    openClient(item: ExtendedClient): void {
      if (!item.id) {
        // Should not happen
        throw new Error('Invalid client');
      }
      this.$router.push({
        name: RouteName.Client,
        params: { id: item.id },
      });
    },

    openSubsystem(item: ExtendedClient): void {
      if (!item.id) {
        // Should not happen
        throw new Error('Invalid client');
      }
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

    addMember(): void {
      this.$router.push({
        name: RouteName.AddMember,
        params: {
          instanceId: this.ownerMember.instance_id,
          memberClass: this.ownerMember.member_class,
          memberCode: this.ownerMember.member_code,
        },
      });
    },

    addSubsystem(item: ExtendedClient): void {
      if (!item.instance_id || !item.member_name) {
        // Should not happen
        throw new Error('Invalid client');
      }

      this.$router.push({
        name: RouteName.AddSubsystem,
        params: {
          instanceId: item.instance_id,
          memberClass: item.member_class,
          memberCode: item.member_code,
          memberName: item.member_name,
        },
      });
    },

    registerClient(item: ExtendedClient): void {
      this.selectedClient = item;
      this.confirmRegisterClient = true;
    },

    registerAccepted(item: ExtendedClient) {
      this.registerClientLoading = true;

      // This should not happen, but better to throw error than create an invalid client id
      if (!item.instance_id) {
        throw new Error('Missing instance id');
      }

      const clientId = createClientId(
        item.instance_id,
        item.member_class,
        item.member_code,
        item.subsystem_code,
      );

      api
        .put(`/clients/${encodePathParameter(clientId)}/register`, {})
        .then(
          () => {
            this.$store.dispatch(
              'showSuccess',
              'clients.action.register.success',
            );
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.fetchClients();
          this.confirmRegisterClient = false;
          this.registerClientLoading = false;
        });
    },

    customFilter: (
      value: unknown,
      search: string | null,
      item: ExtendedClient,
    ): boolean => {
      // Override for the default filter function.
      if (search === null || search.length === 0 || search?.trim() === '') {
        return true;
      }

      search = search.toString().toLowerCase();

      const isFiltered =
        item.visibleName.toLowerCase().includes(search) ||
        item.id.toLowerCase().includes(search) ||
        false;

      if (item.type !== ClientTypes.SUBSYSTEM) {
        item.isFiltered = !isFiltered;
        return true; //We will filter these in sorting due to structure requirements
      }

      return isFiltered;
    },

    customSort(
      items: ExtendedClient[],
      sortBy: string[],
      sortDesc: boolean[],
    ): ExtendedClient[] {
      const index = sortBy[0] as keyof ExtendedClient;
      const sortDirection = !sortDesc[0] ? 1 : -1;

      // Filter out all subsystems for later use
      const subsystems = items.filter(
        (client) => client.type === ClientTypes.SUBSYSTEM,
      );

      // First we order and filter the groups (filtering is based on the isFiltered attribute as well as if subsystems are visible)
      const groups = items
        .filter((client) => client.type !== ClientTypes.SUBSYSTEM)
        .filter(
          (client) =>
            !this.search ||
            !client.isFiltered ||
            subsystems.some((item) => item.id.startsWith(`${client.id}:`)),
        )
        .sort((clientA, clientB) => {
          if (clientA.owner || clientB.owner) {
            return clientA.owner ? -1 : 1;
          }

          const groupSortDirection =
            index !== 'visibleName' ? 1 : sortDirection;

          return (
            clientA.visibleName.localeCompare(clientB.visibleName) *
            groupSortDirection
          );
        });

      // Do local sorting inside the groups
      return groups
        .map<ExtendedClient[]>((group) => {
          return [
            group,
            ...subsystems
              .filter((client) => client.id.startsWith(`${group.id}:`))
              .sort((clientA, clientB) => {
                switch (index) {
                  case 'visibleName':
                    return (
                      clientA.visibleName.localeCompare(clientB.visibleName) *
                      sortDirection
                    );
                  case 'id':
                    return clientA.id.localeCompare(clientB.id) * sortDirection;
                  case 'status':
                    return (
                      (clientA.status || '').localeCompare(
                        clientB.status || '',
                      ) * sortDirection
                    );
                  default:
                    // Just don't sort if the sorting field is unknown
                    return 0;
                }
              }),
          ];
        })
        .reduce(
          (previousValue, currentValue) => [...previousValue, ...currentValue],
          [],
        );
    },

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

<style lang="scss">
@import '~styles/colors';
.xrd-table-header {
  border-bottom: 1px solid #dedce4 !important;
}

// Override Vuetify default table cell height
.v-data-table > .v-data-table__wrapper > table > tbody > tr > td,
.v-data-table > .v-data-table__wrapper > table > thead > tr > td,
.v-data-table > .v-data-table__wrapper > table > tfoot > tr > td {
  height: 56px;
  color: $XRoad-Black100;
}
</style>

<style lang="scss" scoped>
@import '~styles/colors';
.icon-member {
  padding-left: 0;
  color: $XRoad-Link;
  cursor: pointer;
}

.icon-virtual-member {
  padding-left: 0;
  color: $XRoad-Black100;
}

.icon-size {
  font-size: 20px;
  padding-bottom: 4px;
}

.table-toolbar {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-bottom: 24px;
}

.search-input {
  max-width: 300px;
}

.data-table-wrapper {
  width: 100%;
  max-width: 1000px;
}

.data-table {
  width: 100%;
}

.name {
  margin-left: 34px;
  margin-top: auto;
  margin-bottom: auto;
  text-align: center;
  font-weight: 600;

  &.clickable {
    cursor: pointer;
    text-decoration: none;
    color: $XRoad-Link;
  }
}

.member-name {
  @extend .name;
  margin-left: 14px;
}

.owner-box {
  border: solid 1px;
  border-radius: 5px;
  padding-left: 3px;
  padding-right: 3px;
  height: 16px;
  text-transform: uppercase;
  font-style: normal;
  font-weight: bold;
  font-size: 12px;
  line-height: 20px;
  letter-spacing: 0.4px;
  color: #575169;
  margin-left: 16px;
  padding-top: 1px;

  &.clickable {
    text-decoration: none;
    color: $XRoad-Link;
    cursor: pointer;
  }
}

.name-member {
  margin-left: 14px;
  margin-top: auto;
  margin-bottom: auto;
  text-align: center;
  font-weight: 600;
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.add-member {
  margin-right: 20px;
}
</style>
