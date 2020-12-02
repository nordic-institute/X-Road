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
    <div class="search-field">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.serviceSelectionStep')"
        single-line
        hide-details
        data-test="search-service-client"
        class="search-input"
        autofocus
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
    </div>

    <table class="xrd-table">
      <thead>
        <tr>
          <th class="selection-checkbox"></th>
          <th>{{ $t('serviceClients.serviceCode') }}</th>
          <th>{{ $t('serviceClients.title') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="accessRight in searchResults"
          v-bind:key="accessRight.id"
          class="service-row"
          data-test="access-right-toggle"
        >
          <td class="selection-checkbox">
            <div>
              <v-checkbox
                v-model="selections"
                :value="accessRight"
                data-test="access-right-checkbox-input"
              />
            </div>
          </td>
          <td class="identifier-wrap">{{ accessRight.service_code }}</td>
          <td class="identifier-wrap">{{ accessRight.title }}</td>
        </tr>
      </tbody>
    </table>
    <div class="empty" v-if="serviceCandidates.length === 0">
      {{ $t('serviceClients.noAvailableServices') }}
    </div>

    <div
      class="empty"
      v-if="
        serviceCandidates.length > 0 &&
        searchResults &&
        searchResults.length === 0
      "
    >
      {{ $t('action.emptySearch', { msg: search }) }}
    </div>

    <div class="button-footer full-width">
      <div class="button-group">
        <large-button outlined @click="cancel" data-test="cancel-button">{{
          $t('action.cancel')
        }}</large-button>
      </div>

      <div>
        <large-button
          @click="$emit('set-step')"
          data-test="next-button"
          outlined
          class="previous-button"
          >{{ $t('action.previous') }}</large-button
        >

        <large-button
          data-test="finish-button"
          @click="saveServices"
          :disabled="!selections || selections.length === 0"
        >
          {{ $t('serviceClients.addSelected') }}
        </large-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { ServiceCandidate } from '@/ui-types';
import { AccessRight, AccessRights, ServiceClient } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  props: {
    id: {
      type: String as Prop<string>,
      required: true,
    },
    serviceCandidates: {
      type: Array as Prop<ServiceCandidate[]>,
      required: true,
    },
    serviceClientCandidateSelection: {
      type: Object as Prop<ServiceClient>,
      required: true,
    },
  },
  computed: {
    searchResults(): ServiceCandidate[] {
      return this.serviceCandidates.filter((candidate: ServiceCandidate) =>
        candidate.service_code
          .toLowerCase()
          .includes(this.search.toLowerCase()),
      );
    },
  },
  data() {
    return {
      selections: [] as ServiceCandidate[],
      search: '' as string,
    };
  },
  methods: {
    saveServices(): void {
      const items = this.selections
        .filter((selection) => selection.service_code.includes(this.search))
        .map(
          (selection): AccessRight => ({
            service_code: selection.service_code,
          }),
        ) as AccessRight[];

      const accessRightsObject: AccessRights = { items };

      api
        .post(
          `/clients/${encodePathParameter(
            this.id,
          )}/service-clients/${encodePathParameter(
            this.serviceClientCandidateSelection.id,
          )}/access-rights`,
          accessRightsObject,
        )
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'serviceClients.addServiceClientAccessRightSuccess',
          );
          this.$router.push(`/subsystem/serviceclients/${this.id}`);
        })
        .catch((error) => this.$store.dispatch('showError', error));

      this.clear();
    },
    clear(): void {
      this.selections = [];
    },
    cancel(): void {
      this.$router.go(-1);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/shared';
@import '../../../assets/wizards';

.search-field {
  max-width: 300px;
  margin-bottom: 40px;
}

.empty {
  margin-top: 20px;
}
</style>
