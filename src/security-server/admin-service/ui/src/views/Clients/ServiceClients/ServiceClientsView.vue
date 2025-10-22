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
  <XrdSubView>
    <template #header>
      <XrdRoundedSearchField
        v-model="search"
        data-test="search-service-client"
        autofocus
        :label="$t('serviceClients.searchPlaceHolder')"
      />
      <v-spacer />
      <XrdBtn
        v-if="showAddSubjects"
        data-test="add-service-client"
        prepend-icon="add_circle"
        text="serviceClients.addServiceClient"
        @click="addServiceClient"
      />
    </template>

    <v-data-table
      data-test="service-clients-main-view-table"
      class="xrd"
      item-key="id"
      no-data-text="noData.noServiceClients"
      hide-default-footer
      :loading="loading"
      :headers="headers"
      :items="serviceClients"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      :loader-height="2"
    >
      <template #[`item.name`]="{ item }">
        <XrdLabelWithIcon
          icon="id_card"
          clickable
          @navigate="showAccessRights(item.id)"
        >
          <template #label>
            <client-name :service-client="item" />
          </template>
        </XrdLabelWithIcon>
      </template>
      <template #[`item.id`]="{ item }">
        <div @click="showAccessRights(item.id)">
          {{ item.id }}
        </div>
      </template>
    </v-data-table>
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { ServiceClient } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import ClientName from '@/components/client/ClientName.vue';
import {
  XrdSubView,
  XrdBtn,
  useNotifications,
  XrdLabelWithIcon,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServiceClients } from '@/store/modules/service-clients';

export default defineComponent({
  components: { ClientName, XrdSubView, XrdBtn, XrdLabelWithIcon },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
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
    this.doFetchServiceClients();
  },
  methods: {
    ...mapActions(useServiceClients, ['fetchServiceClients']),
    doFetchServiceClients() {
      this.loading = true;
      this.fetchServiceClients(this.id)
        .then((data) => (this.serviceClients = data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
    addServiceClient(): void {
      this.$router.push({
        name: RouteName.AddServiceClientAccessRight,
        params: { id: this.id },
      });
    },
    showAccessRights(serviceClientId: string) {
      this.$router.push({
        name: RouteName.ServiceClientAccessRights,
        params: { id: this.id, serviceClientId },
      });
    },
  },
});
</script>

<style lang="scss" scoped></style>
