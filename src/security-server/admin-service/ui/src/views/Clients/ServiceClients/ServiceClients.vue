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
  <div>
    <div class="xrd-table-toolbar pt-4">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.searchPlaceHolder')"
        single-line
        hide-details
        data-test="search-service-client"
        class="search-input"
        variant="underlined"
        density="compact"
        autofocus
        append-inner-icon="mdi-magnify"
      >
      </v-text-field>
      <xrd-button
        v-if="showAddSubjects"
        color="primary"
        data-test="add-service-client"
        class="ma-0 elevation-0"
        @click="addServiceClient"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-add />
        </xrd-icon-base>
        {{ $t('serviceClients.addServiceClient') }}
      </xrd-button>
    </div>

    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="serviceClients"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table mt-10"
      item-key="id"
      :loader-height="2"
      hide-default-footer
      :no-data-text="$t('noData.noServiceClients')"
      data-test="service-clients-main-view-table"
    >
      <template #[`item.name`]="{ item }">
        <div
          class="clickable-link"
          data-test="open-access-rights"
          @click="showAccessRights(item.id)"
        >
          {{ item.name }}
        </div>
      </template>

      <template #[`item.id`]="{ item }">
        <div @click="showAccessRights(item.id)">
          {{ item.id }}
        </div>
      </template>

      <template #bottom>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import * as api from '@/util/api';
import { ServiceClient } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import { Permissions } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';

export default defineComponent({
  components: {
    VDataTable,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      serviceClients: [] as ServiceClient[],
      search: '',
      loading: false,
    };
  },
  computed: {
    ...mapState(useClient, ['client']),
    ...mapState(useUser, ['hasPermission']),
    showAddSubjects(): boolean {
      return this.hasPermission(Permissions.EDIT_ACL_SUBJECT_OPEN_SERVICES);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('serviceClients.name') as string,
          align: 'start',
          key: 'name',
        },
        {
          title: this.$t('serviceClients.id') as string,
          align: 'start',
          key: 'id',
        },
      ];
    },
  },
  created() {
    this.fetchServiceClients();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchServiceClients() {
      this.loading = true;
      api
        .get<ServiceClient[]>(
          `/clients/${encodePathParameter(this.id)}/service-clients`,
          {},
        )
        .then((response) => (this.serviceClients = response.data))
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
    addServiceClient(): void {
      this.$router.push(`/subsystem/serviceclients/${this.id}/add`);
    },
    filteredServiceClients(): ServiceClient[] {
      return this.serviceClients.filter((sc: ServiceClient) => {
        const searchWordLowerCase = this.search.toLowerCase();
        return (
          sc.name?.toLowerCase().includes(searchWordLowerCase) ||
          sc.id.toLowerCase().includes(searchWordLowerCase)
        );
      });
    },
    showAccessRights(serviceClientId: string) {
      this.$router.push(
        `/subsystem/${this.id}/serviceclients/${serviceClientId}`,
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
@import '@/assets/colors';

.search-input {
  max-width: 300px;
}

.clickable-link {
  color: $XRoad-Link;
  cursor: pointer;
}
</style>
