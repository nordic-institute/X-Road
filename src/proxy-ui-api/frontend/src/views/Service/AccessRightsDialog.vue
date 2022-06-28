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
  <v-dialog v-if="dialog" :value="dialog" width="842" scrollable persistent>
    <v-card class="xrd-card px-0 mx-0" height="90vh">
      <v-card-title>
        <span class="headline" data-test="access-rights-dialog-title">
          {{ title }}
        </span>
        <v-spacer />
        <i id="close-x" data-test="cancel" @click="cancel()"></i>
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0 px-0">
        <v-expansion-panels
          v-model="expandPanel"
          class="elevation-0 px-0"
          multiple
        >
          <v-expansion-panel class="elevation-0 px-0">
            <v-expansion-panel-header></v-expansion-panel-header>
            <v-expansion-panel-content class="elevation-0">
              <template #header>
                <v-spacer />
                <div class="exp-title">
                  {{ $t('localGroup.searchOptions') }}
                </div>
              </template>

              <div>
                <div class="flex-wrap">
                  <div class="input-row">
                    <v-text-field
                      v-model="name"
                      :label="$t('general.name')"
                      hide-details
                      autofocus
                      outlined
                      clearable
                      data-test="name"
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="instance"
                      :items="xroadInstances"
                      :label="$t('general.instance')"
                      class="flex-input"
                      data-test="instance"
                      outlined
                      clearable
                    ></v-select>
                  </div>

                  <div class="input-row">
                    <v-select
                      v-model="memberClass"
                      :items="memberClasses"
                      :label="$t('general.memberClass')"
                      data-test="memberClass"
                      class="flex-input"
                      clearable
                      outlined
                    ></v-select>
                    <v-text-field
                      v-model="memberCode"
                      :label="$t('serviceClients.memberGroupCodeLabel')"
                      hide-details
                      clearable
                      data-test="memberCode"
                      class="flex-input"
                      outlined
                    ></v-text-field>
                  </div>

                  <div class="input-row">
                    <v-text-field
                      v-model="subsystemCode"
                      :label="$t('general.subsystemCode')"
                      hide-details
                      clearable
                      data-test="subsystemCode"
                      class="flex-input"
                      outlined
                    ></v-text-field>

                    <v-select
                      v-model="serviceClientType"
                      :items="ServiceClientTypeItems"
                      label="Subject type"
                      class="flex-input"
                      data-test="serviceClientType"
                      outlined
                      clearable
                    ></v-select>
                  </div>
                </div>

                <div class="search-wrap">
                  <xrd-button
                    :loading="loading"
                    data-test="search-button"
                    @click="search()"
                    >{{ $t('action.search') }}</xrd-button
                  >
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
              <th>{{ $t('services.memberNameGroupDesc') }}</th>
              <th>{{ $t('services.idGroupCode') }}</th>
              <th>{{ $t('general.type') }}</th>
            </tr>
          </thead>
          <tbody
            v-if="serviceClientCandidates && serviceClientCandidates.length > 0"
          >
            <tr v-for="sc in serviceClientCandidates" :key="sc.id">
              <td class="first-column">
                <div class="checkbox-wrap">
                  <v-checkbox
                    color="primary"
                    data-test="sc-checkbox"
                    @change="checkboxChange(sc, $event)"
                  ></v-checkbox>
                </div>
              </td>
              <td>{{ sc.name }}</td>
              <td
                v-if="sc.service_client_type === serviceClientTypes.LOCALGROUP"
              >
                {{ sc.local_group_code }}
              </td>
              <td v-else>{{ sc.id }}</td>
              <td>{{ sc.service_client_type }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="empty-row">
          <p>{{ $t('action.searching') }}</p>
        </div>
        <div
          v-else-if="serviceClientCandidates.length < 1 && !noResults"
          class="empty-row"
        ></div>

        <div v-if="noResults" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <xrd-button
          class="button-margin"
          data-test="cancel-button"
          outlined
          @click="cancel()"
          >{{ $t('action.cancel') }}</xrd-button
        >

        <xrd-button :disabled="!canSave" data-test="save" @click="save()">{{
          $t('localGroup.addSelected')
        }}</xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import * as api from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { ServiceClient } from '@/openapi-types';

enum ServiceClientTypes {
  GLOBALGROUP = 'GLOBALGROUP',
  LOCALGROUP = 'LOCALGROUP',
  SUBSYSTEM = 'SUBSYSTEM',
}

const initialState = () => {
  return {
    name: '',
    serviceClientType: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    serviceClientTypes: ServiceClientTypes,
    expandPanel: [0],
    serviceClientCandidates: [] as ServiceClient[],
    selectedIds: [] as ServiceClient[],
    noResults: false,
    checkbox1: true,
    loading: false,
  };
};

export default Vue.extend({
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
      type: Array as PropType<ServiceClient[]>,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
  },

  data() {
    return { ...initialState() };
  },
  computed: {
    ...mapState(useGeneral, ['xroadInstances', 'memberClasses']),
    canSave(): boolean {
      return this.selectedIds.length > 0;
    },
    ServiceClientTypeItems(): Record<string, unknown>[] {
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
  created() {
    this.fetchXroadInstances();
    this.fetchMemberClasses();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useGeneral, ['fetchMemberClasses', 'fetchXroadInstances']),
    checkboxChange(subject: ServiceClient, event: boolean): void {
      if (event) {
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
      let query = `/clients/${this.clientId}/service-client-candidates?member_name_group_description=${this.name}&member_group_code=${this.memberCode}&subsystem_code=${this.subsystemCode}`;

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
        return !this.existingServiceClients.some(
          (existing) =>
            sc.id === existing.id &&
            sc.service_client_type === existing.service_client_type,
        );
      };

      this.loading = true;
      this.serviceClientCandidates = [];
      this.selectedIds = [];

      api
        .get<ServiceClient[]>(query)
        .then((res) => {
          if (this.existingServiceClients?.length > 0) {
            // Filter out subjects that are already added
            this.serviceClientCandidates = res.data.filter(
              isExistingServiceClient,
            );
          } else {
            // Show results straight if there is nothing to filter
            this.serviceClientCandidates = res.data;
          }

          if (this.serviceClientCandidates.length < 1) {
            this.noResults = true;
          }
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },

    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('service-clients-added', this.selectedIds);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      Object.assign(this.$data, initialState());
    },
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
