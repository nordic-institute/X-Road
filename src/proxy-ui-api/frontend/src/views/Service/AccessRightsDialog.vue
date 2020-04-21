<template>
  <v-dialog :value="dialog" width="850" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{$t('accessRights.addServiceClientsTitle')}}</span>
        <v-spacer />
        <i @click="cancel()" id="close-x" data-test="cancel"></i>
      </v-card-title>

      <v-card-text style="height: 500px;" class="elevation-0">
        <v-expansion-panels class="elevation-0" v-model="expandPanel" multiple>
          <v-expansion-panel class="elevation-0">
            <v-expansion-panel-header></v-expansion-panel-header>
            <v-expansion-panel-content class="elevation-0">
              <template v-slot:header>
                <v-spacer />
                <div class="exp-title">{{$t('localGroup.searchOptions')}}</div>
              </template>

              <div>
                <div class="flex-wrap">
                  <div class="input-row">
                    <v-text-field
                      v-model="name"
                      :label="$t('name')"
                      single-line
                      hide-details
                      data-test="name"
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="instance"
                      :items="xroadInstances"
                      :label="$t('instance')"
                      class="flex-input"
                      data-test="instance"
                      clearable
                    ></v-select>
                  </div>

                  <div class="input-row">
                    <v-select
                      v-model="memberClass"
                      :items="memberClasses"
                      :label="$t('member_class')"
                      data-test="memberClass"
                      class="flex-input"
                      clearable
                    ></v-select>
                    <v-text-field
                      v-model="memberCode"
                      label="Member group code"
                      single-line
                      hide-details
                      data-test="memberCode"
                      class="flex-input"
                    ></v-text-field>
                  </div>

                  <div class="input-row">
                    <v-text-field
                      v-model="subsystemCode"
                      :label="$t('subsystem_code')"
                      single-line
                      hide-details
                      data-test="subsystemCode"
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="serviceClientType"
                      :items="ServiceClientTypeItems"
                      label="Subject type"
                      class="flex-input"
                      data-test="serviceClientType"
                      clearable
                    ></v-select>
                  </div>
                </div>

                <div class="search-wrap">
                  <large-button @click="search()" data-test="search-button">{{$t('action.search')}}</large-button>
                </div>
              </div>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- Table -->
        <table class="xrd-table members-table fixed_header">
          <thead>
            <tr>
              <th class="first-column"></th>
              <th>{{$t('services.memberNameGroupDesc')}}</th>
              <th>{{$t('services.idGroupCode')}}</th>
              <th>{{$t('type')}}</th>
            </tr>
          </thead>
          <tbody v-if="serviceClientCandidates && serviceClientCandidates.length > 0">
            <tr v-for="sc in serviceClientCandidates" v-bind:key="sc.id">
              <td class="first-column">
                <div class="checkbox-wrap">
                  <v-checkbox @change="checkboxChange(sc, $event)" color="primary" data-test="sc-checkbox"></v-checkbox>
                </div>
              </td>
              <td>{{sc.name}}</td>
              <td
                v-if="sc.service_client_type === serviceClientTypes.LOCALGROUP"
              >{{sc.local_group_code}}</td>
              <td v-else>{{sc.id}}</td>
              <td>{{sc.service_client_type}}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="serviceClientCandidates.length < 1 && !noResults" class="empty-row"></div>

        <div v-if="noResults" class="empty-row">
          <p>{{$t('localGroup.noResults')}}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <large-button class="button-margin" data-test="cancel-button" outlined @click="cancel()">{{$t('action.cancel')}}</large-button>

        <large-button :disabled="!canSave" data-test="save" @click="save()">{{$t('localGroup.addSelected')}}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import {ServiceClient} from '@/types';

enum ServiceClientTypes {
  GLOBALGROUP = 'GLOBALGROUP',
  LOCALGROUP = 'LOCALGROUP',
  SUBSYSTEM = 'SUBSYSTEM',
}

function initialState() {
  return {
    name: '',
    serviceClientType: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    serviceClientTypes: ServiceClientTypes,
    expandPanel: [0],
    serviceClientCandidates: [],
    selectedIds: [] as string[],
    noResults: false,
    checkbox1: true,
  };
}

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
    existingServiceClients: {
      type: Array,
      required: true,
    },
  },

  data() {
    return initialState();
  },
  computed: {
    ...mapGetters(['xroadInstances', 'memberClasses']),
    canSave(): boolean {
      if (this.selectedIds.length > 0) {
        return true;
      }
      return false;
    },
    ServiceClientTypeItems(): object[] {
      // Returns items for subject type select with translated texts
      return [
        {
          text: this.$t('serviceClientType.globalGroup'),
          value: this.serviceClientTypes.GLOBALGROUP,
        },
        {
          text: this.$t('serviceClientType.localGroup'),
          value: this.serviceClientTypes.LOCALGROUP,
        },
        {
          text: this.$t('serviceClientType.subsystem'),
          value: this.serviceClientTypes.SUBSYSTEM,
        },
      ];
    },
  },
  methods: {
    checkboxChange(subject: any, event: any): void {
      if (event === true) {
        this.selectedIds.push(subject);
      } else {
        const index = this.selectedIds.indexOf(subject);
        if (index > -1) {
          this.selectedIds.splice(index, 1);
        }
      }
    },
    search(): void {
      this.noResults = false;
      let query = `/clients/${this.clientId}/service-client-candidates?name=${this.name}&member_group_code=${this.memberCode}&subsystem_code=${this.subsystemCode}`;

      // These checks are needed because instance, subject type and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        query = query + `&instance=${this.instance}`;
      }

      if (this.memberClass) {
        query = query + `&member_class=${this.memberClass}`;
      }

      if (this.serviceClientType) {
        query = query + `&service_client_type=${this.serviceClientType}`;
      }

      const isExistingServiceClient = (sc: ServiceClient): boolean => {
        return !this.existingServiceClients.some( (existing: any) => sc.id === existing.id
          && sc.service_client_type === existing.service_client_type);
      };

      api
        .get(query)
        .then((res) => {
          if (this.existingServiceClients?.length > 0) {
            // Filter out subjects that are already added
            this.serviceClientCandidates = res.data.filter(isExistingServiceClient);
          } else {
            // Show results straight if there is nothing to filter
            this.serviceClientCandidates = res.data;
          }

          if (this.serviceClientCandidates.length < 1) {
            this.noResults = true;
          }
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('serviceClientsAdded', this.selectedIds);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      Object.assign(this.$data, initialState());
    },
  },
  created() {
    this.$store.dispatch('fetchXroadInstances');
    this.$store.dispatch('fetchMemberClasses');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';
@import '../../assets/add-dialogs';

.first-column {
  width: 40px;
}
</style>

