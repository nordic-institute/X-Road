<template>
  <simpleDialog
    :dialog="dialog"
    :width="750"
    title="serviceClients.addService"
    @save="save"
    @cancel="cancel"
    :disableSave="filterSelections().length === 0"
  >
    <div slot="content" v-if="this.serviceCandidates.length > 0">
      <v-text-field v-model="search"
                    :label="$t('serviceClients.searchPlaceHolder')"
                    single-line
                    hide-details
                    data-test="search-service-client"
                    class="search-input">
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
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
      SimpleDialog,
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
        const items = this.selections
          .filter( (selection) => selection.service_code.includes(this.search))
          .map( (selection): AccessRight => ({service_code: selection.service_code}));

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
      searchResults(): any {
        return this.serviceCandidates.filter( (candidate: any) => candidate.service_code.includes(this.search));
      },
      filterSelections(): AccessRight[] {
        return this.selections.filter( (ac: AccessRight) => ac.service_code.includes(this.search));
      },
    },
  });
</script>
<style lang="scss" scoped>
  @import '../../../assets/tables';
  @import '../../../assets/dialogs';
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
    background-color: $XRoad-Grey10;
  }
</style>
