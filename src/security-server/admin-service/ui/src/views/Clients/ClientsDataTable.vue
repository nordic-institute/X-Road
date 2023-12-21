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
  <v-container fluid class="xrd-view-common pa-7">
    <div class="table-toolbar pb-3 pt-5">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('tab.main.clients') }}</div>

        <xrd-search v-model="search" />
      </div>
      <div>
        <xrd-button
          v-if="showAddMember"
          data-test="add-member-button"
          class="add-member"
          outlined
          @click="addMember"
        >
          <xrd-icon-base class="xrd-large-button-icon">
            <xrd-icon-add />
          </xrd-icon-base>

          {{ $t('action.addMember') }}
        </xrd-button>
        <xrd-button
          v-if="showAddClient"
          data-test="add-client-button"
          @click="addClient"
        >
          <xrd-icon-base class="xrd-large-button-icon">
            <xrd-icon-add />
          </xrd-icon-base>
          {{ $t('action.addClient') }}
        </xrd-button>
      </div>
    </div>

    <!-- @vue-ignore -->
    <v-data-table
      :loading="clientsLoading"
      :headers="headers"
      :items="filteredClients"
      :must-sort="true"
      :items-per-page="-1"
      :sort-by="sortBy"
      :custom-key-sort="dummyKeySort"
      hide-default-footer
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      @update:sort-by="sort"
    >
      <!-- https://stackoverflow.com/questions/61344980/v-slot-directive-doesnt-support-any-modifier -->
      <template #[`item.visibleName`]="{ item }">
        <!-- Name - Owner member -->
        <template v-if="item.type === clientTypes.OWNER_MEMBER">
          <xrd-icon-base
            class="icon-member icon-size"
            @click="openClient(item)"
          >
            <xrd-icon-folder />
          </xrd-icon-base>
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
          <xrd-icon-base
            class="icon-member icon-size"
            @click="openClient(item)"
          >
            <xrd-icon-folder-outline />
          </xrd-icon-base>
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
          <xrd-icon-base class="icon-virtual-member icon-size">
            <xrd-icon-folder-outline />
          </xrd-icon-base>

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

      <template #[`item.id`]="{ item }">
        <span class="identifier-wrap">{{ item.id }}</span>
      </template>

      <template #[`item.status`]="{ item }">
        <client-status :status="item.status" />
      </template>

      <template #[`item.button`]="{ item }">
        <div class="button-wrap">
          <xrd-button
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
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <xrd-icon-add />
            </xrd-icon-base>
            {{ $t('action.addSubsystem') }}
          </xrd-button>

          <xrd-button
            v-if="
              item.type !== clientTypes.OWNER_MEMBER &&
              item.type !== clientTypes.VIRTUAL_MEMBER &&
              item.status === 'SAVED' &&
              showRegister
            "
            text
            :outlined="false"
            @click="registerClient(item)"
            >{{ $t('action.register') }}
          </xrd-button>
        </div>
      </template>

      <template #no-data>{{ $t('action.noData') }}</template>

      <template #bottom>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <v-alert v-if="search.length > 1 && getClients.length < 1" type="error">
      {{ $t('action.emptySearch', { msg: search }) }}
    </v-alert>

    <xrd-confirm-dialog
      v-if="confirmRegisterClient"
      title="clients.action.register.confirm.title"
      text="clients.action.register.confirm.text"
      :loading="registerClientLoading"
      @cancel="confirmRegisterClient = false"
      @accept="registerAccepted(selectedClient)"
    />
  </v-container>
</template>

<script lang="ts">
/**
 * This component renders the Clients data table.
 * Default sort and filter functions are replaced to achieve the end result where
 */
import { defineComponent } from 'vue';
import ClientStatus from './ClientStatus.vue';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { Permissions, RouteName, ClientTypes } from '@/global';
import { createClientId } from '@/util/helpers';
import { DataTableHeader, ExtendedClient } from '@/ui-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useClients } from '@/store/modules/clients';
import { XrdIconFolder, XrdIconFolderOutline } from '@niis/shared-ui';
import { AxiosError } from 'axios';

export default defineComponent({
  components: {
    XrdIconFolder,
    XrdIconFolderOutline,
    ClientStatus,
    VDataTable,
  },

  data: () => ({
    search: '',
    clientTypes: ClientTypes,
    confirmRegisterClient: false,
    registerClientLoading: false,
    selectedClient: undefined as undefined | ExtendedClient,
    filteredClients: [] as ExtendedClient[],
    sortBy: [{ key: 'visibleName', order: 'asc' }],
    // Currently the new version of v-data-table is missing the customSort property,
    // so we'll ignore its internal sorting for now and do our own sorting externally
    // https://github.com/vuetifyjs/vuetify/issues/16654
    dummyKeySort: {
      visibleName: () => 0,
      id: () => 0,
      status: () => 0,
    },
  }),

  computed: {
    ...mapState(useClients, [
      'getClients',
      'clientsLoading',
      'ownerMember',
      'realMembers',
    ]),
    ...mapState(useUser, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('client.name') as string,
          align: 'start',
          key: 'visibleName',
        },
        {
          title: this.$t('client.id') as string,
          align: 'start',
          key: 'id',
        },
        {
          title: this.$t('client.status') as string,
          align: 'start',
          key: 'status',
        },
        {
          title: '',
          key: 'button',
          sortable: false,
        },
      ];
    },
    showAddClient(): boolean {
      return this.hasPermission(Permissions.ADD_CLIENT);
    },
    showAddMember(): boolean {
      return (
        this.hasPermission(Permissions.ADD_CLIENT) &&
        this.realMembers?.length < 2
      );
    },
    showRegister(): boolean {
      return this.hasPermission(Permissions.SEND_CLIENT_REG_REQ);
    },
    canOpenClient(): boolean {
      return this.hasPermission(Permissions.VIEW_CLIENT_DETAILS);
    },
  },

  watch: {
    search(newValue: string) {
      const filteredClients = this.getClients.filter((client) =>
        this.customFilter('', newValue, client),
      );
      this.filteredClients = this.customSort(
        filteredClients,
        this.sortBy[0].key,
        this.sortBy[0].order === 'desc',
      );
    },
  },

  created() {
    this.fetchData();
    this.sort(this.sortBy);
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useClients, ['fetchClients']),

    openClient(item: ExtendedClient): void {
      if (!item.id) {
        // Should not happen
        throw new Error('Invalid client');
      }
      this.$router.push({
        name: RouteName.MemberDetails,
        params: { id: item.id },
      });
    },

    openSubsystem(item: ExtendedClient): void {
      if (!item.id) {
        // Should not happen
        throw new Error('Invalid client');
      }
      this.$router.push({
        name: RouteName.SubsystemDetails,
        params: { id: item.id },
      });
    },

    addClient(): void {
      this.$router.push({
        name: RouteName.AddClient,
      });
    },

    addMember(): void {
      if (!this.ownerMember?.instance_id) {
        // Should not happen
        throw new Error('Invalid owner member');
      }

      this.$router.push({
        name: RouteName.AddMember,
        params: {
          ownerInstanceId: this.ownerMember.instance_id,
          ownerMemberClass: this.ownerMember.member_class,
          ownerMemberCode: this.ownerMember.member_code,
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

    registerAccepted(item: ExtendedClient | undefined) {
      this.registerClientLoading = true;

      // This should not happen, but better to throw error than create an invalid client id
      if (!item?.instance_id) {
        throw new Error('Missing instance id');
      }

      const clientId = createClientId(
        item?.instance_id,
        item?.member_class,
        item?.member_code,
        item?.subsystem_code,
      );

      api
        .put(`/clients/${encodePathParameter(clientId)}/register`, {})
        .then(
          () => {
            this.showSuccess(this.$t('clients.action.register.success'));
          },
          (error) => {
            this.showError(error);
          },
        )
        .finally(() => {
          this.fetchData();
          this.confirmRegisterClient = false;
          this.registerClientLoading = false;
        });
    },

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    customFilter: (value: string, search: string, item: any) => {
      // Override for the default filter function.
      if (search.length === 0 || search?.trim() === '') {
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

    sort(event: { key: string; order: string }[]) {
      this.filteredClients = this.customSort(
        this.filteredClients,
        event[0].key,
        event[0].order === 'desc',
      );
      this.sortBy = [{ key: event[0].key, order: event[0].order }];
    },

    customSort(
      items: ExtendedClient[],
      sortBy: string,
      sortDesc: boolean,
    ): ExtendedClient[] {
      const index = sortBy as keyof ExtendedClient;
      const sortDirection = !sortDesc ? 1 : -1;

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

    fetchData() {
      this.fetchClients()
        .catch((error: AxiosError) => {
          this.showError(error);
        })
        .finally(() => {
          this.filteredClients = this.getClients;
          this.sort(this.sortBy);
        });
    },
  },
});
</script>

<style lang="scss">
@import '@/assets/colors';

.xrd-table-header {
  border-bottom: 1px solid $XRoad-WarmGrey30 !important;
}

// Override Vuetify default table cell height
.v-data-table > .v-table__wrapper > table > tbody > tr > td,
.v-data-table > .v-table__wrapper > table > thead > tr > td,
.v-data-table > .v-table__wrapper > table > tfoot > tr > td {
  height: 56px;
  color: $XRoad-Black100;
}

// Override Vuetify table row hover color
.v-data-table > .v-table__wrapper > table > tbody > tr:hover {
  background: $XRoad-Purple10 !important;
}
</style>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

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

.data-table {
  width: 100%;
}

.name {
  margin-left: 40px;
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
