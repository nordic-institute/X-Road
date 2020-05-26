<template>
  <div>
    <div class="search-field">
      <v-text-field v-model="search"
                    :label="$t('serviceClients.serviceSelectionStep')"
                    single-line
                    hide-details
                    data-test="search-service-client"
                    class="search-input">
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
    </div>

    <table class="xrd-table">
      <thead>
        <tr>
          <th class="selection-checkbox"></th>
          <th>{{$t('serviceClients.serviceCode')}}</th>
          <th>{{$t('serviceClients.title')}}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="accessRight in searchResults()"
            v-bind:key="accessRight.id"
            class="service-row"
            data-test="access-right-toggle">
          <td class="selection-checkbox">
            <div>
              <v-checkbox
                v-model="selections"
                :value="accessRight"
                data-test="access-right-checkbox-input"
              /></div>
          </td>
          <td>{{accessRight.service_code}}</td>
          <td>{{accessRight.title}}</td>
        </tr>
      </tbody>
    </table>

    <div class="button-footer full-width">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          data-test="cancel-button"
        >{{$t('action.cancel')}}</large-button>
      </div>

      <div>

        <large-button
          @click="$emit('set-step')"
          data-test="next-button"
          outlined
          class="previous-button"
        >{{$t('action.previous')}}</large-button>

        <large-button
          data-test="finish-button"
          @click="saveServices">
          {{$t('serviceClients.addSelected')}}
        </large-button>
      </div>
    </div>

  </div>
</template>

<script lang="ts">

import Vue from 'vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import {Prop} from 'vue/types/options';
import {ServiceCandidate} from '@/ui-types';
import {AccessRight, AccessRights, ServiceClient} from '@/openapi-types';
import * as api from '@/util/api';
import LargeButton from '@/components/ui/LargeButton.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    LargeButton,
  },
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
  data() {
    return {
      selections: [] as ServiceCandidate[],
      search: '' as string,
    };
  },
  methods: {
    saveServices(): void {
      const items = this.selections
        .filter( (selection) => selection.service_code.includes(this.search))
        .map( (selection): AccessRight => ({service_code: selection.service_code})) as AccessRight[];

      const accessRightsObject: AccessRights = { items };

      api
        .post(`/clients/${this.id}/service-clients/${this.serviceClientCandidateSelection?.id}/access-rights`,
          accessRightsObject)
        .then( () => {
          this.$store.dispatch('showSuccess', 'serviceClients.addServiceClientAccessRightSuccess');
          this.$router.push(`/subsystem/serviceclients/${this.id}`);
        })
        .catch( (error: any) => this.$store.dispatch('showError', error));

      this.clear();
    },
    searchResults(): ServiceCandidate[] {
      return this.serviceCandidates
        .filter( (candidate: ServiceCandidate) => candidate.service_code.includes(this.search));
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
  @import '../../../assets/global-style';
  @import '../../../assets/shared';
  @import '../../../assets/wizards';

  .search-field {
    max-width: 300px;
    margin-bottom: 40px;
  }
</style>
