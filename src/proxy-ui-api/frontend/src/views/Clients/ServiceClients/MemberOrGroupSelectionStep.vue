<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <div class="search-field">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.memberGroupStep')"
        single-line
        hide-details
        data-test="search-service-client"
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
    </div>

    <v-radio-group>
      <table class="xrd-table service-clients-table">
        <thead>
          <tr>
            <th class="checkbox-column"></th>
            <th>{{ $t('serviceClients.name') }}</th>
            <th>{{ $t('serviceClients.id') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="candidate in this.filteredCandidates()"
            v-bind:key="candidate.id"
          >
            <td class="checkbox-column">
              <div class="checkbox-wrap">
                <v-radio
                  v-on:change="selectCandidate(candidate)"
                  :disabled="isDisabled(candidate)"
                  :key="candidate.id"
                  :value="candidate"
                  data-test="candidate-selection"
                />
              </div>
            </td>
            <td>
              {{
                candidate.service_client_type === 'LOCALGROUP'
                  ? candidate.local_group_code
                  : candidate.name
              }}
            </td>
            <td>{{ candidate.id }}</td>
          </tr>
        </tbody>
      </table>
    </v-radio-group>

    <div class="button-footer full-width">
      <div class="button-group">
        <large-button outlined @click="cancel" data-test="cancel-button">{{
          $t('action.cancel')
        }}</large-button>
      </div>

      <div>
        <large-button
          :disabled="!selection"
          @click="$emit('set-step')"
          data-test="next-button"
          >{{ $t('action.next') }}</large-button
        >
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { ServiceClient } from '@/openapi-types';
import * as api from '@/util/api';
import { Prop } from 'vue/types/options';
import LargeButton from '@/components/ui/LargeButton.vue';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    id: {
      type: String as Prop<string>,
      required: true,
    },
    serviceClients: {
      type: Array as Prop<ServiceClient[]>,
      required: true,
    },
  },
  data() {
    return {
      search: '' as string,
      serviceClientCandidates: [] as ServiceClient[],
      selection: undefined as undefined | ServiceClient,
    };
  },
  methods: {
    fetchData(): void {
      api
        .get<ServiceClient[]>(
          `/clients/${encodePathParameter(this.id)}/service-client-candidates`,
        )
        .then((response) => (this.serviceClientCandidates = response.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    cancel(): void {
      this.$router.go(-1);
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
    selectCandidate(candidate: ServiceClient): void {
      this.selection = candidate;
      this.$emit('candidate-selection', candidate);
    },
    isDisabled(scCandidate: ServiceClient): boolean {
      return this.serviceClients.some(
        (sc: ServiceClient): boolean => sc.id === scCandidate.id,
      );
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/global-style';
@import '../../../assets/shared';
@import '../../../assets/wizards';

.search-field {
  max-width: 300px;
  margin-bottom: 40px;
}
</style>
