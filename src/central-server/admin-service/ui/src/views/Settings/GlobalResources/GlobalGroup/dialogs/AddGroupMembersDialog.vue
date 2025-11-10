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
  <XrdSimpleDialog
    title="globalGroup.dialog.addMembers.title"
    width="824"
    save-button-text="action.add"
    scrollable
    submittable
    :loading="loading"
    :disable-save="anyClientsSelected"
    @save="addMembers"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <!-- Table -->
      <v-data-table-server
        v-model="selectedClients"
        data-test="select-members-list"
        item-value="client_id.encoded_id"
        max-height="420"
        class="xrd border xrd-rounded-12"
        show-select
        :loading="fetchingClients"
        :headers="headers"
        :items="selectableClients"
        :items-length="totalItems"
        :page="pagingOptions.page"
        :loader-height="2"
        :items-per-page-options="itemsPerPageOptions"
        @update:options="changeOptions"
      >
        <template #top>
          <v-text-field
            v-model="search"
            class="xrd w-50 mb-6 ml-4"
            data-test="member-subsystem-search-field"
            prepend-inner-icon="search"
            single-line
            hide-details
            autofocus
            :label="$t('systemSettings.selectSubsystem.search')"
            @update:model-value="debouncedFetchItems"
          />
        </template>
        <template #bottom>
          <XrdPagination />
        </template>
      </v-data-table-server>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useI18n } from 'vue-i18n';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { XrdSimpleDialog, useBasicForm, XrdPagination } from '@niis/shared-ui';

import { PagedClients } from '@/openapi-types';
import { useClient } from '@/store/modules/clients';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { PagingOptions } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { debounce } from '@/util/helpers';

// To provide the Vue instance to debounce

const props = defineProps({
  groupCode: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['save', 'cancel']);

const { loading, addError, addSuccessMessage } = useBasicForm();
const { getByExcludingGroup } = useClient();
const { addGroupMembers } = useGlobalGroups();

const { t } = useI18n();

const fetchingClients = ref(false);
const pagingOptions = ref({ itemsPerPage: 10, page: 1 } as PagingOptions);
const itemsPerPageOptions = ref(defaultItemsPerPageOptions(50));
const clients = ref({} as PagedClients);
const search = ref('');
const selectedClients = ref([]);

const anyClientsSelected = computed(
  () => !selectedClients.value || selectedClients.value.length === 0,
);
const totalItems = computed(
  () => clients.value.paging_metadata?.total_items || 0,
);
const selectableClients = computed(() => clients.value.clients || []);
const headers = computed<DataTableHeader[]>(() => [
  {
    title: t('systemSettings.selectSubsystem.name') as string,
    align: 'start',
    value: 'member_name',
    sortable: false,
  },
  {
    title: t('systemSettings.selectSubsystem.memberCode') as string,
    align: 'start',
    value: 'client_id.member_code',
    sortable: false,
    cellProps: { 'data-test': 'code' },
  },
  {
    title: t('systemSettings.selectSubsystem.memberClass') as string,
    align: 'start',
    value: 'client_id.member_class',
    sortable: false,
    cellProps: { 'data-test': 'class' },
  },
  {
    title: t('systemSettings.selectSubsystem.subsystemCode') as string,
    align: 'start',
    value: 'client_id.subsystem_code',
    sortable: false,
    cellProps: { 'data-test': 'subsystem' },
  },
  {
    title: t('systemSettings.selectSubsystem.xroadInstance') as string,
    align: 'start',
    value: 'client_id.instance_id',
    sortable: false,
    cellProps: { 'data-test': 'instance' },
  },
  {
    title: t('systemSettings.selectSubsystem.type') as string,
    align: 'start',
    value: 'client_id.type',
    sortable: false,
  },
]);

const debouncedFetchItems = debounce(() => {
  // Debounce is used to reduce unnecessary api calls
  pagingOptions.value.page = 1;
  fetchClients();
}, 600);

function fetchClients() {
  fetchingClients.value = true;
  getByExcludingGroup(props.groupCode, search.value, pagingOptions.value)
    .then((resp) => (clients.value = resp))
    .catch((error) => addError(error))
    .finally(() => (fetchingClients.value = false));
}

function changeOptions(options: PagingOptions) {
  pagingOptions.value = options;
  fetchClients();
}

function addMembers() {
  loading.value = true;

  addGroupMembers(props.groupCode, selectedClients.value)
    .then((resp) => emit('save', resp.data.items))
    .then(() => showSuccessMessage(selectedClients.value))
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
}

function showSuccessMessage(identifiers: string[]) {
  addSuccessMessage('globalGroup.dialog.addMembers.success', {
    identifiers: identifiers.join(', '),
  });
}
</script>
