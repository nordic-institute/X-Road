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
  <v-dialog
    v-if="opened"
    :value="opened"
    width="824"
    scrollable
    persistent
    @keydown.esc="cancel"
  >
    <v-card class="xrd-card">
      <v-card-title>
        <slot name="title">
          <span class="dialog-title-text">
            {{ $t('globalGroup.dialog.addMembers.title') }}
          </span>
        </slot>
        <v-spacer />
        <xrd-close-button id="dlg-close-x" @click="cancel()" />
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0">
        <v-text-field
          v-model="search"
          data-test="member-subsystem-search-field"
          class="search-input"
          append-icon="icon-Search"
          single-line
          hide-details
          autofocus
          :label="$t('systemSettings.selectSubsystem.search')"
        />

        <!-- Table -->
        <v-data-table
          v-model="selectedClients"
          class="elevation-0 data-table"
          data-test="select-members-list"
          item-key="client_id.encoded_id"
          show-select
          :loading="loading"
          :headers="headers"
          :items="selectableClients"
          :server-items-length="totalItems"
          :options.sync="pagingSortingOptions"
          :loader-height="2"
          :footer-props="{ itemsPerPageOptions: [10, 25, 50] }"
          @update:options="changeOptions"
        >
          <template #[`item.data-table-select`]="{ isSelected, select }">
            <v-simple-checkbox
              data-test="members-checkbox"
              :ripple="false"
              :value="isSelected"
              @input="select($event)"
            ></v-simple-checkbox>
          </template>
          <template #[`item.member_name`]="{ item }">
            <div>{{ item.member_name }}</div>
          </template>
          <template #[`item.client_id.member_code`]="{ item }">
            <div data-test="code">{{ item.client_id.member_code }}</div>
          </template>
          <template #[`item.client_id.member_class`]="{ item }">
            <div data-test="class">{{ item.client_id.member_class }}</div>
          </template>
          <template #[`item.client_id.subsystem_code`]="{ item }">
            <div data-test="subsystem">{{ item.client_id.subsystem_code }}</div>
          </template>
          <template #[`item.client_id.instance_id`]="{ item }">
            <div data-test="instance">{{ item.client_id.instance_id }}</div>
          </template>
          <template #[`item.client_id.type`]="{ item }">
            <div>{{ item.client_id.type }}</div>
          </template>
        </v-data-table>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <xrd-button
          data-test="cancel-button"
          class="button-margin"
          outlined
          :disabled="adding"
          @click="cancel()"
        >
          {{ $t('action.cancel') }}
        </xrd-button>

        <xrd-button
          data-test="member-subsystem-add-button"
          :loading="adding"
          :disabled="anyClientsSelected"
          @click="addMembers"
        >
          {{ $t('action.add') }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { Client, PagedClients } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { clientStore } from '@/store/modules/clients';
import { notificationsStore } from '@/store/modules/notifications';
import { DataOptions, DataTableHeader } from 'vuetify';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { debounce, toIdentifier } from '@/util/helpers';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      opened: false,
      loading: false,
      adding: false,
      pagingSortingOptions: {} as DataOptions,
      clients: {} as PagedClients,
      search: '',
      selectedClients: [] as Client[],
    };
  },
  computed: {
    ...mapStores(clientStore),
    ...mapStores(useGlobalGroupsStore),
    anyClientsSelected(): boolean {
      return !this.selectedClients || this.selectedClients.length === 0;
    },
    totalItems(): number {
      return this.clients.paging_metadata?.total_items || 0;
    },
    selectableClients(): Client[] {
      return this.clients.clients || [];
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('systemSettings.selectSubsystem.name') as string,
          align: 'start',
          value: 'member_name',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
        {
          text: this.$t('systemSettings.selectSubsystem.memberCode') as string,
          align: 'start',
          value: 'client_id.member_code',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
        {
          text: this.$t('systemSettings.selectSubsystem.memberClass') as string,
          align: 'start',
          value: 'client_id.member_class',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
        {
          text: this.$t(
            'systemSettings.selectSubsystem.subsystemCode',
          ) as string,
          align: 'start',
          value: 'client_id.subsystem_code',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
        {
          text: this.$t(
            'systemSettings.selectSubsystem.xroadInstance',
          ) as string,
          align: 'start',
          value: 'client_id.instance_id',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
        {
          text: this.$t('systemSettings.selectSubsystem.type') as string,
          align: 'start',
          value: 'client_id.type',
          class: 'xrd-table-header text-uppercase',
          sortable: false,
        },
      ];
    },
  },
  watch: {
    search: {
      handler() {
        this.pagingSortingOptions.page = 1;
        this.debouncedFetchItems();
      },
      deep: true,
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchClients();
    }, 600),
    async fetchClients() {
      this.loading = true;
      return this.clientStore
        .getByExcludingGroup(
          this.groupCode,
          this.search,
          this.pagingSortingOptions,
        )
        .then((resp) => {
          this.clients = resp;
        })
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
    open() {
      this.opened = true;
    },
    changeOptions() {
      this.fetchClients();
    },
    cancel(): void {
      if (this.adding) {
        return;
      }
      this.$emit('cancel');
      this.clearForm();
      this.opened = false;
    },
    addMembers(): void {
      this.adding = true;
      const clientIds = this.selectedClients.map((client) =>
        toIdentifier(client.client_id),
      );
      this.globalGroupStore
        .addGroupMembers(this.groupCode, clientIds)
        .then((resp) => this.$emit('added', resp.data.items))
        .then(() => (this.opened = false))
        .then(() => this.showSuccessMessage(clientIds))
        .then(() => this.clearForm())
        .catch((error) => this.showError(error))
        .finally(() => (this.adding = false));
    },
    showSuccessMessage(identifiers: string[]) {
      this.showSuccess(
        this.$t('globalGroup.dialog.addMembers.success', {
          identifiers: identifiers.join(', '),
        }),
      );
    },
    clearForm(): void {
      this.selectedClients = [];
      this.pagingSortingOptions.page = 1;
      this.search = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';

.checkbox-column {
  width: 50px;
}

.search-input {
  width: 300px;
}

.dialog-title-text {
  color: $XRoad-WarmGrey100;
  font-weight: bold;
  font-size: 24px;
  line-height: 32px;
}
</style>
