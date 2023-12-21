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
        :label="$t('serviceClients.memberGroupStep')"
        single-line
        hide-details
        autofocus
        data-test="search-service-client"
        variant="underlined"
        density="compact"
        class="search-input"
        append-inner-icon="mdi-magnify"
      >
      </v-text-field>
    </div>

    <v-radio-group
      v-model="selection"
      @update:model-value="$emit('candidate-selection', $event)"
    >
      <table class="xrd-table service-clients-table">
        <thead>
          <tr>
            <th class="checkbox-column"></th>
            <th>{{ $t('serviceClients.name') }}</th>
            <th>{{ $t('serviceClients.id') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="candidate in filteredCandidates()" :key="candidate.id">
            <td class="checkbox-column">
              <div class="checkbox-wrap">
                <v-radio
                  :key="candidate.id"
                  :disabled="isDisabled(candidate)"
                  :value="candidate"
                  data-test="candidate-selection"
                />
              </div>
            </td>
            <td class="identifier-wrap">
              {{
                candidate.service_client_type === 'LOCALGROUP'
                  ? candidate.local_group_code
                  : candidate.name
              }}
            </td>
            <td class="identifier-wrap">{{ candidate.id }}</td>
          </tr>
        </tbody>
      </table>
    </v-radio-group>

    <div class="button-footer full-width">
      <xrd-button outlined data-test="cancel-button" @click="cancel">{{
        $t('action.cancel')
      }}</xrd-button>

      <xrd-button
        :disabled="!selection"
        data-test="next-button"
        @click="$emit('set-step')"
        >{{ $t('action.next') }}</xrd-button
      >
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { ServiceClient } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
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
  emits: ['candidate-selection', 'set-step'],
  data() {
    return {
      search: '' as string,
      serviceClientCandidates: [] as ServiceClient[],
      selection: undefined as undefined | ServiceClient,
    };
  },
  created(): void {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchData(): void {
      api
        .get<ServiceClient[]>(
          `/clients/${encodePathParameter(this.id)}/service-client-candidates`,
        )
        .then((response) => (this.serviceClientCandidates = response.data))
        .catch((error) => this.showError(error));
    },
    cancel(): void {
      this.$router.back();
    },
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
    isDisabled(scCandidate: ServiceClient): boolean {
      return this.serviceClients.some(
        (sc: ServiceClient): boolean => sc.id === scCandidate.id,
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
@import '@/assets/wizards';

.search-field {
  max-width: 300px;
  margin-bottom: 20px;
  margin-left: 20px;
}
</style>
