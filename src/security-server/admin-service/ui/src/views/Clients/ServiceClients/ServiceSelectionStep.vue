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
        data-test="search-service-client-service"
        variant="underlined"
        density="compact"
        class="search-input"
        autofocus
        append-inner-icon="mdi-magnify"
      >
      </v-text-field>
    </div>
    <div class="scrollable">
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
                  hide-details
                />
              </div>
            </td>
            <td class="identifier-wrap">{{ accessRight.service_code }}</td>
            <td class="identifier-wrap">{{ accessRight.service_title }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="serviceCandidates.length === 0" class="empty">
      {{ $t('serviceClients.noAvailableServices') }}
    </div>

    <div
      v-if="
        serviceCandidates.length > 0 &&
        searchResults &&
        searchResults.length === 0
      "
      class="empty"
    >
      {{ $t('action.emptySearch', { msg: search }) }}
    </div>

    <div class="button-footer full-width">
      <xrd-button outlined data-test="cancel-button" @click="cancel">{{
        $t('action.cancel')
      }}</xrd-button>

      <xrd-button
        data-test="previous-button"
        outlined
        class="previous-button"
        @click="$emit('set-step')"
        >{{ $t('action.previous') }}</xrd-button
      >

      <xrd-button
        data-test="finish-button"
        :disabled="!selections || selections.length === 0"
        @click="saveServices"
      >
        {{ $t('serviceClients.addSelected') }}
      </xrd-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { ServiceCandidate } from '@/ui-types';
import { AccessRight, AccessRights, ServiceClient } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  props: {
    id: {
      type: String as PropType<string>,
      required: true,
    },
    serviceCandidates: {
      type: Array as PropType<ServiceCandidate[]>,
      required: true,
    },
    serviceClientCandidateSelection: {
      type: Object as PropType<ServiceClient>,
      required: true,
    },
  },
  emits: ['set-step'],
  data() {
    return {
      selections: [] as ServiceCandidate[],
      search: '' as string,
    };
  },
  computed: {
    searchResults(): ServiceCandidate[] {
      const cleanedSearch = this.search.toLowerCase();
      return this.serviceCandidates.filter((candidate: ServiceCandidate) => {
        return (
          candidate.service_code.toLowerCase().includes(cleanedSearch) ||
          candidate.service_title?.toLowerCase().includes(cleanedSearch)
        );
      });
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
          this.showSuccess(
            this.$t('serviceClients.addServiceClientAccessRightSuccess'),
          );
          this.$router.push(`/subsystem/serviceclients/${this.id}`);
        })
        .catch((error) => this.showError(error));

      this.clear();
    },
    clear(): void {
      this.selections = [];
    },
    cancel(): void {
      this.$router.back();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/wizards';

.search-field {
  max-width: 300px;
  margin-bottom: 30px;
  margin-left: 20px;
}

.empty {
  margin: 30px;
  text-align: center;
}

.scrollable {
  overflow-y: auto;
  max-height: 55vh;
}
</style>
