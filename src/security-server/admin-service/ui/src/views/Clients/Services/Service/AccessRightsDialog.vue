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
  <XrdSimpleDialog
    data-test="add-subjects-dialog"
    width="840"
    height="752"
    scrollable
    :title="title"
    :disable-save="selectedIds.length < 1"
    :loading="adding"
    @cancel="cancel"
    @save="save"
  >
    <template #content="{ dialogHandler }">
      <XrdExpandable is-open>
        <template #link="{ toggle, opened }">
          <div
            class="font-weight-medium cursor-pointer"
            :class="{ 'on-surface': opened, 'on-surface-variant': !opened }"
            @click="toggle"
          >
            {{ $t('localGroup.searchOptions') }}
          </div>
        </template>

        <template #content>
          <v-container class="px-4">
            <v-row>
              <v-col>
                <v-text-field
                  v-model="name"
                  data-test="name-text-field"
                  class="xrd"
                  hide-details
                  autofocus
                  clearable
                  :label="$t('general.name')"
                />
              </v-col>
              <v-col>
                <v-select
                  v-model="instance"
                  data-test="instance-select"
                  class="xrd"
                  clearable
                  hide-details
                  :items="xroadInstances"
                  :label="$t('general.instance')"
                />
              </v-col>
            </v-row>

            <v-row>
              <v-col>
                <v-select
                  v-model="memberClass"
                  data-test="member-class-select"
                  class="xrd"
                  clearable
                  hide-details
                  :items="memberClasses"
                  :label="$t('general.memberClass')"
                />
              </v-col>
              <v-col>
                <v-text-field
                  v-model="memberCode"
                  data-test="member-code-text-field"
                  class="xrd"
                  hide-details
                  clearable
                  :label="$t('serviceClients.memberGroupCodeLabel')"
                />
              </v-col>
            </v-row>

            <v-row>
              <v-col>
                <v-text-field
                  v-model="subsystemCode"
                  data-test="subsystem-code-text-field"
                  class="xrd"
                  hide-details
                  clearable
                  :label="$t('general.subsystemCode')"
                />
              </v-col>
              <v-col>
                <v-select
                  v-model="serviceClientType"
                  data-test="service-client-type-select"
                  class="xrd"
                  clearable
                  hide-details
                  :label="$t('serviceClients.subjectType')"
                  :items="ServiceClientTypeItems"
                />
              </v-col>
            </v-row>
            <div class="pt-4 d-flex flex-row">
              <v-spacer />
              <XrdBtn
                :loading="loading"
                data-test="search-button"
                text="action.search"
                @click="search(dialogHandler)"
              />
            </div>
          </v-container>
        </template>
      </XrdExpandable>
      <v-data-table
        class="xrd border xrd-rounded-12 mt-6"
        items-per-page="-1"
        no-data-text="localGroup.noResults"
        select-strategy="single"
        hide-default-footer
        :loading="loading"
        :items="serviceClientCandidates"
        :headers="headers"
      >
        <template #item.select="{ item }">
          <v-checkbox-btn
            data-test="service-client-checkbox"
            class="xrd"
            hide-details
            @update:model-value="checkboxChange(item, $event)"
          />
        </template>
        <template #item.name="{ item }">
          <client-name :service-client="item" />
        </template>
        <template #item.id="{ item }">
          {{
            item.service_client_type === serviceClientTypes.LOCALGROUP
              ? item.local_group_code
              : item.id
          }}
        </template>
      </v-data-table>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useGeneral } from '@/store/modules/general';
import { ServiceClient, ServiceClientType } from '@/openapi-types';
import {
  XrdExpandable,
  XrdSimpleDialog,
  XrdBtn,
  AddError,
  DialogSaveHandler,
} from '@niis/shared-ui';
import ClientName from '@/components/client/ClientName.vue';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServiceClients } from '@/store/modules/service-clients';

const initialState = () => {
  return {
    name: '',
    serviceClientType: undefined,
    instance: undefined,
    memberClass: undefined,
    memberCode: '',
    subsystemCode: '',
    serviceClientTypes: ServiceClientType,
    serviceClientCandidates: [] as ServiceClient[],
    selectedIds: [] as ServiceClient[],
    noResults: false,
    checkbox1: true,
    loading: false,
  };
};

export default defineComponent({
  components: { XrdSimpleDialog, ClientName, XrdExpandable, XrdBtn },
  props: {
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
    adding: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'service-clients-added'],
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
          title: this.$t('serviceClientType.globalGroup'),
          value: this.serviceClientTypes.GLOBALGROUP,
        },
        {
          title: this.$t('serviceClientType.localGroup'),
          value: this.serviceClientTypes.LOCALGROUP,
        },
        {
          title: this.$t('serviceClientType.subsystem'),
          value: this.serviceClientTypes.SUBSYSTEM,
        },
      ];
    },
    headers() {
      return [
        { title: '', key: 'select' },
        { title: this.$t('services.memberNameGroupDesc'), key: 'name' },
        { title: this.$t('general.type'), key: 'id' },
        { title: this.$t('general.type'), key: 'service_client_type' },
      ] as DataTableHeader[];
    },
  },
  created() {
    this.fetchXroadInstances();
    this.fetchMemberClasses();
  },
  methods: {
    ...mapActions(useGeneral, ['fetchMemberClasses', 'fetchXroadInstances']),
    ...mapActions(useServiceClients, ['fetchCandidates']),
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
    search(handler: DialogSaveHandler): void {
      this.noResults = false;

      const params = {} as Record<string, string>;
      params.member_name_group_description = this.name || '';
      params.member_group_code = this.memberCode || '';
      params.subsystem_code = this.subsystemCode || '';

      // These checks are needed because instance, subject type and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        params.instance = this.instance;
      }

      if (this.memberClass) {
        params.member_class = this.memberClass;
      }

      if (this.serviceClientType) {
        params.service_client_type = this.serviceClientType;
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

      this.fetchCandidates(this.clientId, params)
        .then((data) => {
          if (this.existingServiceClients?.length > 0) {
            // Filter out subjects that are already added
            this.serviceClientCandidates = data.filter(isExistingServiceClient);
          } else {
            // Show results straight if there is nothing to filter
            this.serviceClientCandidates = data;
          }

          if (this.serviceClientCandidates.length < 1) {
            this.noResults = true;
          }
        })
        .catch((error) => handler.addError(error))
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

<style lang="scss" scoped></style>
