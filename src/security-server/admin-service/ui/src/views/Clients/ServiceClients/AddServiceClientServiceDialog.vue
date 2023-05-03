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
  <xrd-simple-dialog
    :dialog="dialog"
    :width="750"
    title="serviceClients.addService"
    scrollable
    :disable-save="filterSelections().length === 0"
    @save="save"
    @cancel="cancel"
  >
    <div v-if="serviceCandidates.length > 0" slot="content">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.searchPlaceHolder')"
        single-line
        autofocus
        hide-details
        data-test="search-service-client"
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
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
            v-for="accessRight in searchResults()"
            :key="accessRight.id"
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
            <td>{{ accessRight.service_code }}</td>
            <td>{{ accessRight.service_title }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else slot="content">
      <p>{{ $t('serviceClients.noAvailableServices') }}</p>
    </div>
  </xrd-simple-dialog>
</template>
<script lang="ts">
import Vue from 'vue';
import { AccessRight } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import { ServiceCandidate } from '@/ui-types';
export default Vue.extend({
  props: {
    dialog: {
      type: Boolean as Prop<boolean>,
      required: true,
    },
    serviceCandidates: {
      type: Array as Prop<ServiceCandidate[]>,
      required: true,
    },
  },
  data() {
    return {
      selections: [] as AccessRight[],
      search: '' as string,
    };
  },
  methods: {
    save(): void {
      const items = this.selections
        .filter((selection) => selection.service_code.includes(this.search))
        .map(
          (selection): AccessRight => ({
            service_code: selection.service_code,
          }),
        );

      this.$emit('save', items);
      this.clear();
    },
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    clear(): void {
      this.selections = [];
    },
    searchResults(): ServiceCandidate[] {
      const cleanedSearch = this.search.toLowerCase();

      return this.serviceCandidates.filter((candidate: ServiceCandidate) => {
        return (
          candidate.service_code.toLowerCase().includes(cleanedSearch) ||
          candidate.service_title?.toLowerCase().includes(cleanedSearch)
        );
      });
    },
    filterSelections(): AccessRight[] {
      return this.selections.filter((ac: AccessRight) =>
        ac.service_code.includes(this.search),
      );
    },
  },
});
</script>
<style lang="scss" scoped>
@import '../../../assets/tables';

.selection-checkbox {
  width: 40px;
}
.search-input {
  margin: 30px 0;
  width: 50%;
  min-width: 200px;
}
.service-row:hover {
  cursor: pointer;
  background-color: $XRoad-Purple10;
}
</style>
