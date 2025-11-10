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
  <XrdView title="tab.main.clients">
    <template #append-header>
      <div class="ml-6">
        <v-text-field
          v-model="search"
          data-test="search-query-field"
          class="xrd"
          width="320"
          prepend-inner-icon="search"
          single-line
          :label="$t('action.search')"
        />
      </div>
      <div class="ml-auto">
        <XrdBtn
          v-if="showAddMember"
          data-test="add-member-button"
          class="add-member"
          variant="outlined"
          text="action.addMember"
          prepend-icon="create_new_folder"
          @click="addMember"
        />
        <XrdBtn
          v-if="showAddClient"
          data-test="add-client-button"
          class="ml-2"
          variant="flat"
          text="action.addClient"
          prepend-icon="add_circle"
          @click="addClient"
        />
      </div>
    </template>

    <!-- @vue-ignore -->
    <v-data-table
      class="xrd xrd-rounded-16"
      item-key="id"
      must-sort
      hide-default-footer
      :loading="clientsLoading"
      :headers="headers"
      :items="filteredClients"
      :items-per-page="-1"
      :sort-by="sortBy"
      :custom-key-sort="dummyKeySort"
      @update:sort-by="sort"
    >
      <!-- https://stackoverflow.com/questions/61344980/v-slot-directive-doesnt-support-any-modifier -->
      <template #[`item.visibleName`]="{ item }">
        <!-- Name - Owner member -->
        <template v-if="item.type === clientTypes.OWNER_MEMBER">
          <XrdLabelWithIcon
            data-test="btn-client-details"
            icon="folder_open filled"
            semi-bold
            :label="item.visibleName"
            :clickable="canOpenClient"
            @navigate="openClient(item)"
          >
            <template #append-label>
              <v-chip
                class="xrd text-info font-weight-medium ml-3"
                variant="flat"
                density="compact"
                color="info-container"
              >
                {{ $t('client.owner') }}
              </v-chip>
            </template>
          </XrdLabelWithIcon>
        </template>
        <!-- Name - Member -->
        <template v-else-if="item.type === clientTypes.MEMBER">
          <XrdLabelWithIcon
            data-test="btn-client-details"
            icon="folder_open"
            semi-bold
            :label="item.visibleName"
            :clickable="canOpenClient"
            @navigate="openClient(item)"
          />
        </template>
        <!-- Name - virtual member -->
        <template
          v-else-if="
            item.type === clientTypes.VIRTUAL_MEMBER ||
            item.type === clientTypes.MEMBER
          "
        >
          <XrdLabelWithIcon
            icon="folder_open"
            semi-bold
            :label="item.visibleName"
          />
        </template>
        <!-- Name - Subsystem -->
        <template v-else>
          <XrdLabel
            data-test="btn-client-details"
            class="ml-6"
            semi-bold
            :clickable="canOpenClient"
            @navigate="openSubsystem(item)"
          >
            <template #label>
              <SubsystemName class="client-name" :name="item.visibleName" />
            </template>
          </XrdLabel>
        </template>
      </template>

      <template #[`item.id`]="{ item }">
        <span class="identifier-wrap">{{ item.id }}</span>
      </template>

      <template #[`item.status`]="{ item }">
        <ClientStatus :status="item.status" />
      </template>

      <template #[`item.button`]="{ item }">
        <XrdBtn
          v-if="
            (item.type === clientTypes.OWNER_MEMBER ||
              item.type === clientTypes.MEMBER ||
              item.type === clientTypes.VIRTUAL_MEMBER) &&
            item.member_name &&
            showAddClient
          "
          variant="text"
          color="tertiary"
          text="action.addSubsystem"
          @click="addSubsystem(item)"
        />

        <XrdBtn
          v-if="
            item.type !== clientTypes.OWNER_MEMBER &&
            item.type !== clientTypes.VIRTUAL_MEMBER &&
            item.status === 'SAVED' &&
            showRegister
          "
          variant="text"
          color="tertiary"
          text="action.register"
          @click="registerClient(item)"
        />
      </template>

      <template v-if="search.length > 1" #no-data>
        <i18n-t scope="global" keypath="action.emptySearch">
          <template #msg>
            <span class="font-weight-medium">
              {{ search }}
            </span>
          </template>
        </i18n-t>
      </template>
      <template v-else #no-data>{{ $t('action.noData') }}</template>
    </v-data-table>

    <XrdConfirmDialog
      v-if="confirmRegisterClient"
      title="clients.action.register.confirm.title"
      text="clients.action.register.confirm.text"
      :loading="registerClientLoading"
      @cancel="confirmRegisterClient = false"
      @accept="registerAccepted(selectedClient)"
    />
  </XrdView>
</template>

<script lang="ts">
/**
 * This component renders the Clients data table.
 * Default sort and filter functions are replaced to achieve the end result where
 */
import { defineComponent } from 'vue';
import ClientStatus from './ClientStatus.vue';
import { ClientTypes, Permissions, RouteName } from '@/global';
import { createClientId } from '@/util/helpers';
import { ExtendedClient } from '@/ui-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useClients } from '@/store/modules/clients';
import {
  XrdView,
  XrdLabelWithIcon,
  XrdBtn,
  XrdLabel,
  useNotifications,
  XrdConfirmDialog,
} from '@niis/shared-ui';
import { AxiosError } from 'axios';
import SubsystemName from '@/components/client/SubsystemName.vue';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: {
    SubsystemName,
    ClientStatus,
    XrdView,
    XrdBtn,
    XrdLabelWithIcon,
    XrdLabel,
    XrdConfirmDialog,
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
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
          cellProps: { 'data-test': 'client-name' },
        },
        {
          title: this.$t('client.id') as string,
          align: 'start',
          key: 'id',
          cellProps: { 'data-test': 'client-id' },
        },
        {
          title: this.$t('client.status') as string,
          align: 'start',
          key: 'status',
          cellProps: { 'data-test': 'client-status' },
        },
        {
          title: '',
          key: 'button',
          sortable: false,
          align: 'end',
          cellProps: { 'data-test': 'client-actions' },
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
            this.addSuccessMessage('clients.action.register.success');
          },
          (error) => {
            this.addError(error);
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

      function orUndefinedStr(name?: string): string {
        return name || 'undefined';
      }

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
            orUndefinedStr(clientA.visibleName).localeCompare(
              orUndefinedStr(clientB.visibleName),
            ) * groupSortDirection
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
                      orUndefinedStr(clientA.visibleName).localeCompare(
                        orUndefinedStr(clientB.visibleName),
                      ) * sortDirection
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
          this.addError(error);
        })
        .finally(() => {
          this.filteredClients = this.getClients;
          this.sort(this.sortBy);
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
