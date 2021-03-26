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
    <div class="table-toolbar pt-4">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.searchPlaceHolder')"
        single-line
        hide-details
        data-test="search-service-client"
        class="search-input"
        autofocus
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
      <xrd-button
        v-if="showAddSubjects"
        color="primary"
        @click="addServiceClient"
        data-test="add-service-client"
        class="ma-0 elevation-0"
        ><v-icon class="xrd-large-button-icon">icon-Add</v-icon
        >{{ $t('serviceClients.addServiceClient') }}
      </xrd-button>
    </div>

    <div class="xrd-card">
      <table class="xrd-table xrd-table-highlightable service-clients-table">
        <thead>
          <tr>
            <th>{{ $t('serviceClients.name') }}</th>
            <th>{{ $t('serviceClients.id') }}</th>
          </tr>
        </thead>
        <template v-if="serviceClients.length > 0">
          <tbody>
            <tr
              v-for="sc in this.filteredServiceClients()"
              v-bind:key="sc.id"
              @click="showAccessRights(sc.id)"
              data-test="open-access-rights"
            >
              <td class="identifier-wrap clickable-link">{{ sc.name }}</td>
              <td class="identifier-wrap">{{ sc.id }}</td>
            </tr>
          </tbody>
        </template>
      </table>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';
import { ServiceClient } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import { Permissions } from '@/global';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      serviceClients: [] as ServiceClient[],
      search: '' as string,
    };
  },
  computed: {
    ...mapGetters(['client']),
    showAddSubjects(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_ACL_SUBJECT_OPEN_SERVICES,
      );
    },
  },
  methods: {
    fetchServiceClients() {
      api
        .get<ServiceClient[]>(
          `/clients/${encodePathParameter(this.id)}/service-clients`,
          {},
        )
        .then((response) => (this.serviceClients = response.data))
        .catch((error) => this.$store.dispatch('showError', error));
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
  created() {
    this.fetchServiceClients();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/colors';

.search-input {
  max-width: 300px;
}

.service-clients-table {
  margin-top: 40px;
}

.xrd-card {
  background-color: white;
  border-radius: 4px;
}

.clickable-link {
  color: $XRoad-Link;
  cursor: pointer;
}
</style>
