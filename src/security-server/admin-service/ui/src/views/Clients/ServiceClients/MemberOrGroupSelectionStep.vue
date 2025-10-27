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
        data-test="search-service-client"
        density="compact"
        class="xrd xrd-search-field mb-4"
        prepend-inner-icon="search"
        single-line
        hide-details
        autofocus
        :label="$t('serviceClients.memberGroupStep')"
      >
      </v-text-field>

      <v-table data-test="service-clients-table" class="xrd">
        <thead>
          <tr>
            <th class="checkbox-column"></th>
            <th>{{ $t('serviceClients.name') }}</th>
            <th>{{ $t('serviceClients.id') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="candidate in filteredCandidates" :key="candidate.id">
            <td class="xrd-checkbox-column">
              <v-radio
                :key="candidate.id"
                v-model="selection"
                data-test="candidate-selection"
                class="xrd"
                :disabled="isDisabled(candidate)"
                @click="updateSelection(candidate)"
              />
            </td>
            <td class="identifier-wrap">
              <client-name :service-client="candidate" />
            </td>
            <td class="identifier-wrap">{{ candidate.id }}</td>
          </tr>
        </tbody>
      </v-table>
    </XrdFormBlock>
    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="text"
        text="action.cancel"
        @click="cancel"
      />
      <v-spacer />
      <XrdBtn
        data-test="next-button"
        text="action.next"
        :disabled="!selection"
        @click="$emit('set-step', selection)"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { ServiceClient } from '@/openapi-types';
import {
  XrdWizardStep,
  useNotifications,
  XrdFormBlock,
  XrdBtn,
} from '@niis/shared-ui';
import { mapActions } from 'pinia';
import ClientName from '@/components/client/ClientName.vue';
import { useServiceClients } from '@/store/modules/service-clients';

export default defineComponent({
  components: { ClientName, XrdWizardStep, XrdFormBlock, XrdBtn },
  props: {
    id: {
      type: String,
      required: true,
    },
    serviceClients: {
      type: Array as PropType<ServiceClient[]>,
      required: true,
    },
  },
  emits: ['set-step'],
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      search: '' as string,
      serviceClientCandidates: [] as ServiceClient[],
      selection: undefined as undefined | ServiceClient,
    };
  },
  computed: {
    filteredCandidates(): ServiceClient[] {
      return this.serviceClientCandidates.filter(
        (candidate: ServiceClient): boolean => {
          const searchWordLowerCase = this.search.toLowerCase();

          // local group id is number. Convert it to string so it's easier to search it
          const id =
            candidate.service_client_type === 'LOCALGROUP'
              ? candidate.id.toString()
              : candidate.id.toLowerCase();
          return (
            candidate.name?.toLowerCase().includes(searchWordLowerCase) ||
            id.includes(searchWordLowerCase)
          );
        },
      );
    },
  },
  created(): void {
    this.fetchData();
  },
  methods: {
    ...mapActions(useServiceClients, ['fetchCandidates']),
    updateSelection(value: ServiceClient): void {
      this.selection = value;
    },
    fetchData(): void {
      this.fetchCandidates(this.id)
        .then((data) => (this.serviceClientCandidates = data))
        .catch((error) => this.addError(error));
    },
    cancel(): void {
      this.$router.back();
    },
    isDisabled(scCandidate: ServiceClient): boolean {
      return this.serviceClients.some(
        (sc: ServiceClient): boolean => sc.id === scCandidate.id,
      );
    },
  },
});
</script>

<style lang="scss" scoped></style>
