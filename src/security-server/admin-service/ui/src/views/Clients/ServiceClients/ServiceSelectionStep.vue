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
  <XrdWizardStep>
    <XrdFormBlock>
      <v-text-field
        v-model="search"
        data-test="search-service-client-service"
        class="xrd xrd-search-field"
        density="compact"
        prepend-inner-icon="search"
        single-line
        hide-details
        autofocus
        :label="$t('serviceClients.serviceSelectionStep')"
      />

      <v-table class="xrd">
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
            <td class="xrd-checkbox-column">
              <v-checkbox
                v-model="selections"
                :value="accessRight"
                data-test="access-right-checkbox-input"
                class="xrd"
                hide-details
              />
            </td>
            <td class="identifier-wrap">{{ accessRight.service_code }}</td>
            <td class="identifier-wrap">{{ accessRight.service_title }}</td>
          </tr>
        </tbody>
      </v-table>

      <p
        v-if="serviceCandidates.length === 0"
        class="mt-4 body-regular text-center"
      >
        {{ $t('serviceClients.noAvailableServices') }}
      </p>

      <p
        v-if="
          serviceCandidates.length > 0 &&
          searchResults &&
          searchResults.length === 0
        "
        class="mt-4 body-regular text-center"
      >
        <i18n-t scope="global" keypath="action.emptySearch">
          <template #msg>
            <span class="font-weight-medium">{{ search }}</span>
          </template>
        </i18n-t>
      </p>
    </XrdFormBlock>
    <template #footer>
      <XrdBtn
        variant="text"
        text="action.cancel"
        data-test="cancel-button"
        @click="cancel"
      />
      <v-spacer />
      <XrdBtn
        data-test="previous-button"
        variant="outlined"
        text="action.previous"
        class="mr-2"
        @click="$emit('set-step')"
      />

      <XrdBtn
        data-test="finish-button"
        :disabled="!selections || selections.length === 0"
        text="serviceClients.addSelected"
        @click="saveServices"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { ServiceCandidate } from '@/ui-types';
import { AccessRight, AccessRights, ServiceClient } from '@/openapi-types';
import { XrdWizardStep, useNotifications, XrdFormBlock, XrdBtn } from '@niis/shared-ui';
import { mapActions } from 'pinia';
import { useServiceClients } from '@/store/modules/service-clients';
import { RouteName } from '@/global';

export default defineComponent({
  components: { XrdWizardStep, XrdFormBlock, XrdBtn },
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
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
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
    ...mapActions(useServiceClients, ['saveAccessRights']),
    saveServices(): void {
      const items = this.selections
        .filter((selection) => selection.service_code.includes(this.search))
        .map(
          (selection): AccessRight => ({
            service_code: selection.service_code,
          }),
        ) as AccessRight[];

      const accessRightsObject: AccessRights = { items };

      this.saveAccessRights(
        this.id,
        this.serviceClientCandidateSelection.id,
        accessRightsObject,
      )
        .then(() => {
          this.addSuccessMessage(
            'serviceClients.addServiceClientAccessRightSuccess',
            {},
            true,
          );
          this.$router.push({
            name: RouteName.SubsystemServiceClients,
            params: {
              id: this.id,
            },
          });
        })
        .catch((error) => this.addError(error));

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

<style lang="scss" scoped></style>
