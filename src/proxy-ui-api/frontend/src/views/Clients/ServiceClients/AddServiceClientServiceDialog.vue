<template>
  <simpleDialog
    :dialog="dialog"
    :width="750"
    title="serviceClients.addService"
    @save="save"
    @cancel="cancel"
    :disableSave="selections.length === 0"
  >
    <div slot="content" v-if="this.serviceCandidates.length > 0">

      <div class="table-toolbar" >
        <v-text-field v-model="search"
                      :label="$t('serviceClients.searchPlaceHolder')"
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
          <tr v-for="service in this.searchResults()" v-bind:key="service.id">
            <td class="selection-checkbox">
              <div class="checkbox-wrap">
                <v-checkbox :value="service" v-model="selections"></v-checkbox>
              </div>
            </td>
            <td>{{service.service_code}}</td>
            <td>{{service.title}}</td>
          </tr>
        </tbody>
      </table>

    </div>

    <div slot="content" v-else>
      <h3>{{$t('serviceClients.noAvailableServices')}}</h3>
    </div>


  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import {AccessRight} from '@/types';

export default Vue.extend({
  components: {
    SimpleDialog
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    serviceCandidates: {
      type: Array,
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
      this.$emit('save', this.selections);
      this.clear();
    },
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    clear(): void {
      this.selections = [];
    },
    searchResults(): any {
      return this.serviceCandidates.filter( (candidate: any) => candidate.service_code.includes(this.search));
    },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/dialogs';

.selection-checkbox {
  width: 40px;
}
</style>
