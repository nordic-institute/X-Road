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
    save-button-text="localGroup.addSelected"
    scrollable
    :title="title"
    :disable-save="!canSave"
    :loading="addingLocalGroupMembers"
    @cancel="cancel"
    @save="save"
  >
    <template #content="{ dialogHandler }">
      <XrdExpandable :is-open="true" class="border">
        <template #link="{ toggle, opened }">
          <div class="font-weight-medium cursor-pointer" :class="{ 'on-surface': opened, 'on-surface-variant': !opened }" @click="toggle">
            {{ $t('localGroup.searchOptions') }}
          </div>
        </template>
        <template #content>
          <v-container class="px-4">
            <v-row>
              <v-col>
                <v-text-field v-model="name" class="xrd" autofocus clearable hide-details :label="$t('general.name')" />
              </v-col>
              <v-col>
                <v-select
                  v-model="instance"
                  data-test="select-member-instance"
                  class="xrd"
                  hide-details
                  clearable
                  :items="xroadInstances"
                  :label="$t('general.instance')"
                />
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-select
                  v-model="memberClass"
                  data-test="select-member-class"
                  class="xrd"
                  clearable
                  hide-details
                  :items="memberClasses"
                  :label="$t('general.memberClass')"
                />
              </v-col>
              <v-col>
                <v-text-field v-model="memberCode" class="xrd" clearable hide-details :label="$t('general.memberCode')" />
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-text-field v-model="subsystemCode" class="xrd" clearable hide-details :label="$t('general.subsystemCode')" />
              </v-col>
              <v-col />
            </v-row>
            <v-row>
              <v-col class="text-end">
                <XrdBtn
                  class="flow-right"
                  text="action.search"
                  prepend-icon="search"
                  :loading="searchingClients"
                  @click="search(dialogHandler)"
                />
              </v-col>
            </v-row>
          </v-container>
        </template>
      </XrdExpandable>

      <v-data-table
        class="xrd xrd-rounded-12 border mt-6"
        items-per-page="-1"
        hide-default-footer
        :headers="headers"
        :items="members"
        :no-data-text="noResults ? 'localGroup.noResults' : 'noData.noData'"
      >
        <template #item.checkbox="{ item }">
          <v-checkbox-btn
            data-test="add-local-group-member-checkbox"
            class="xrd"
            density="compact"
            @update:model-value="checkboxChange(item.id as string, $event)"
          />
        </template>
        <template #item.name="{ item }">
          <subsystem-name :name="item.subsystem_name ?? item.subsystem_code" />
        </template>
      </v-data-table>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { Client, GroupMember } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useGeneral } from '@/store/modules/general';
import { XrdExpandable, XrdSimpleDialog, useNotifications, DialogSaveHandler, XrdBtn } from '@niis/shared-ui';
import SubsystemName from '@/components/client/SubsystemName.vue';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useClients } from '@/store/modules/clients';
import { useLocalGroups } from '@/store/modules/local-groups';

const initialState = () => {
  return {
    name: '',
    instance: undefined,
    memberClass: undefined,
    memberCode: '',
    subsystemCode: '',
    members: [] as Client[],
    selectedIds: [] as string[],
    noResults: false,
  };
};

export default defineComponent({
  components: { XrdSimpleDialog, SubsystemName, XrdExpandable, XrdBtn },
  props: {
    filtered: {
      type: Array as PropType<GroupMember[]>,
      default: undefined,
    },
    title: {
      type: String,
      default: 'localGroup.addMembers',
    },
    localGroupId: {
      type: String,
      required: true,
    },
  },
  emits: ['cancel', 'members-added'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return { ...initialState() };
  },
  computed: {
    ...mapState(useGeneral, ['xroadInstances', 'memberClasses']),
    ...mapState(useClients, ['searchingClients']),
    ...mapState(useLocalGroups, ['addingLocalGroupMembers']),
    canSave(): boolean {
      return this.selectedIds.length > 0;
    },
    headers() {
      return [
        {
          title: '',
          key: 'checkbox',
          sortable: false,
          cellProps: { class: 'xrd-checkbox-column' },
        },
        { title: this.$t('general.name'), value: 'name' },
        { title: this.$t('localGroup.id'), value: 'id' },
      ] as DataTableHeader[];
    },
  },
  created() {
    this.fetchXroadInstances();
    this.fetchMemberClasses();
  },

  methods: {
    ...mapActions(useGeneral, ['fetchMemberClasses', 'fetchXroadInstances']),
    ...mapActions(useClients, ['searchClients']),
    ...mapActions(useLocalGroups, ['addLocalGroupMembers']),
    checkboxChange(id: string, event: unknown): void {
      if (event) {
        this.selectedIds.push(id);
      } else {
        const index = this.selectedIds.indexOf(id);
        if (index > -1) {
          this.selectedIds.splice(index, 1);
        }
      }
    },
    search(handler: DialogSaveHandler): void {
      this.noResults = false;

      const query = {
        name: this.name,
        member_code: this.memberCode,
        subsystem_code: this.subsystemCode,
        show_members: false,
        internal_search: false,
      } as Record<string, string | boolean>;

      // These checks are needed because instance and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        query.instance = this.instance;
      }

      if (this.memberClass) {
        query.member_class = this.memberClass;
      }

      this.members = [];
      this.selectedIds = [];
      this.searchClients(query)
        .then((data) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out members that are already added
            this.members = data.filter((member) => {
              return !this.filtered?.find((item) => {
                return item.id === member.id;
              });
            });
          } else {
            // Show results straight if there is nothing to filter
            this.members = data;
          }

          if (this.members.length < 1) {
            this.noResults = true;
          }
        })
        .catch((error) => handler.addError(error));
    },

    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(evt: Event, handler: DialogSaveHandler): void {
      this.addLocalGroupMembers(this.localGroupId, this.selectedIds)
        .then(() => this.clearForm())
        .then(() => this.$emit('members-added', this.selectedIds))
        .catch((error) => handler.addError(error));
    },

    clearForm(): void {
      // Reset initial state
      Object.assign(this.$data, initialState());
    },
  },
});
</script>

<style lang="scss" scoped></style>
