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
  <v-dialog v-if="dialog" :value="dialog" width="824" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <slot name="title">
          <span class="dialog-title-text">
            {{ $t('systemSettings.selectSubsystem.title') }}
          </span>
        </slot>
        <v-spacer />
        <xrd-close-button id="dlg-close-x" @click="cancel()" />
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0">
        <v-text-field
          v-model="search"
          :label="$t('systemSettings.selectSubsystem.search')"
          single-line
          hide-details
          class="search-input"
          autofocus
          append-icon="icon-Search"
          data-test="management-subsystem-search-field"
        >
        </v-text-field>

        <!-- Table -->
        <v-data-table
          v-model="selectedSubsystems"
          class="elevation-0 data-table"
          item-key="client_id.encoded_id"
          show-select
          single-select
          :loading="loading"
          :headers="headers"
          :items="selectableSubsystems"
          :server-items-length="totalItems"
          :options.sync="pagingSortingOptions"
          :loader-height="2"
          :footer-props="{ itemsPerPageOptions: [10, 25, 50] }"
          @update:options="changeOptions"
        >
          <template #[`item.data-table-select`]="{ isSelected, select }">
            <v-simple-checkbox
              data-test="management-subsystem-checkbox"
              :ripple="false"
              :value="isSelected"
              @input="select($event)"
            ></v-simple-checkbox>
          </template>
          <template #[`item.member_name`]="{ item }">
            <div>{{ item.member_name }}</div>
          </template>
          <template #[`item.client_id.member_code`]="{ item }">
            <div>{{ item.client_id.member_code }}</div>
          </template>
          <template #[`item.client_id.member_class`]="{ item }">
            <div>{{ item.client_id.member_class }}</div>
          </template>
          <template #[`item.client_id.subsystem_code`]="{ item }">
            <div>{{ item.client_id.subsystem_code }}</div>
          </template>
          <template #[`item.client_id.instance_id`]="{ item }">
            <div>{{ item.client_id.instance_id }}</div>
          </template>
          <template #[`item.client_id.type`]="{ item }">
            <div>{{ item.client_id.type }}</div>
          </template>
        </v-data-table>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <xrd-button
          class="button-margin"
          outlined
          data-test="cancel-button"
          @click="cancel()"
        >
          {{ $t('action.cancel') }}
        </xrd-button>

        <xrd-button
          :disabled="!selectedSubsystems || selectedSubsystems.length === 0"
          data-test="management-subsystem-select-button"
          @click="selectSubSystem()"
        >
          {{ $t('action.select') }}
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
import { debounce, toIdentifier } from '@/util/helpers';
import { DataOptions, DataTableHeader } from 'vuetify';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  name: 'SelectSubsystemDialog',
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    defaultSubsystemId: {
      type: String,
      required: true,
    },
  },

  data() {
    return {
      loading: false,
      pagingSortingOptions: {} as DataOptions,
      clients: {} as PagedClients,
      search: '',
      selectedSubsystems: [] as Client[],
    };
  },
  computed: {
    ...mapStores(clientStore),
    totalItems(): number {
      return this.clients.paging_metadata?.total_items || 0;
    },
    selectableSubsystems(): Client[] {
      return this.clients.clients || [];
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('systemSettings.selectSubsystem.name') as string,
          align: 'start',
          value: 'member_name',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t('systemSettings.selectSubsystem.memberCode') as string,
          align: 'start',
          value: 'client_id.member_code',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t('systemSettings.selectSubsystem.memberClass') as string,
          align: 'start',
          value: 'client_id.member_class',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t(
            'systemSettings.selectSubsystem.subsystemCode',
          ) as string,
          align: 'start',
          value: 'client_id.subsystem_code',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t(
            'systemSettings.selectSubsystem.xroadInstance',
          ) as string,
          align: 'start',
          value: 'client_id.instance_id',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t('systemSettings.selectSubsystem.type') as string,
          align: 'start',
          value: 'client_id.type',
          class: 'xrd-table-header text-uppercase',
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
    fetchClients() {
      this.loading = true;
      this.clientStore
        .getByClientType('SUBSYSTEM', this.search, this.pagingSortingOptions)
        .then((resp) => {
          this.clients = resp;
          this.setSelectedSubsystems();
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    changeOptions: async function () {
      this.fetchClients();
    },
    setSelectedSubsystems() {
      const filteredList = this.selectableSubsystems?.filter(
        (subsystem) =>
          `SUBSYSTEM:${toIdentifier(subsystem.client_id)}` ===
          this.defaultSubsystemId,
      );

      if (filteredList) {
        this.selectedSubsystems = filteredList;
      }
    },
    cancel(): void {
      this.$emit('cancel');
      this.clearForm();
    },
    selectSubSystem(): void {
      this.$emit('select', this.selectedSubsystems);
      this.clearForm();
    },
    clearForm(): void {
      this.pagingSortingOptions.page = 1;
      this.selectedSubsystems = [];
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
