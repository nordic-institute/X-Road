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
    title="filters.chooseFilters"
    data-test="group-members-filter-dialog"
    save-button-text="filters.apply"
    width="824"
    submittable
    @save="apply"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock>
        <!-- By type -->
        <v-row>
          <v-col cols="3">
            {{ $t('filters.groupMembers.byType') }}
          </v-col>
          <v-col cols="6">
            <v-checkbox
              v-model="typeMemberModel"
              class="xrd"
              hide-details
              :label="$t('filters.groupMembers.member')"
            />
            <v-checkbox
              v-model="typeSubsystemModel"
              class="xrd"
              hide-details
              :label="$t('filters.groupMembers.subsystem')"
            />
          </v-col>
          <v-col cols="3" />
        </v-row>

        <v-divider class="mt-6 mb-6 opacity-20" />

        <v-row>
          <v-col cols="3">
            <div class="field-title mt-6 mb-6">
              {{ $t('filters.groupMembers.byInstance') }}
            </div>
          </v-col>
          <v-col cols="6">
            <v-select
              v-model="instanceModel"
              class="xrd"
              :items="instances"
              :label="$t('filters.groupMembers.instance')"
            />
            <v-select
              v-model="memberClassModel"
              class="xrd"
              :items="memberClasses"
              :label="$t('filters.groupMembers.class')"
            />
            <v-autocomplete
              v-model="codesModel"
              class="xrd"
              clearable
              multiple
              :items="codes"
              :label="$t('filters.groupMembers.code')"
            />
            <v-autocomplete
              v-model="subsystemsModel"
              class="xrd"
              clearable
              multiple
              :items="subsystems"
              :label="$t('filters.groupMembers.subsystem')"
            />
          </v-col>
        </v-row>
      </XrdFormBlock>
    </template>
    <template #prepend-save-button>
      <XrdBtn
        class="font-weight-medium"
        variant="outlined"
        text="filters.clearFields"
        @click="clearFields"
      />
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
/** Base component for simple dialogs */

import { defineComponent } from 'vue';

import { mapStores } from 'pinia';

import { XrdFormBlock } from '@niis/shared-ui';

import { useGlobalGroups } from '@/store/modules/global-groups';

import XrdBtn from '@niis/shared-ui/src/components/XrdBtn.vue';

const initialState = () => {
  return {
    typeMemberModel: false,
    typeSubsystemModel: false,
    instanceModel: '',
    memberClassModel: '',
    subsystemsModel: [],
    codesModel: [],
  };
};

export default defineComponent({
  components: { XrdBtn, XrdFormBlock },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
    // Is the content scrollable
    scrollable: {
      type: Boolean,
      default: false,
    },
    // Set save button loading spinner
    loading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'apply'],
  data() {
    return {
      opened: true,
      instances: [] as string[] | undefined,
      memberClasses: [] as string[] | undefined,
      subsystems: [] as string[] | undefined,
      codes: [] as string[] | undefined,
      ...initialState(),
    };
  },

  computed: {
    ...mapStores(useGlobalGroups),
  },
  created() {
    this.globalGroupStore.getMembersFilterModel(this.groupCode).then((resp) => {
      this.instances = resp.instances || undefined;
      this.memberClasses = resp.member_classes || undefined;
      this.subsystems = resp.subsystems || undefined;
      this.codes = resp.codes || undefined;
    });
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    clearFields(): void {
      Object.assign(this.$data, initialState());
    },
    apply(): void {
      const typeArray: string[] = [];
      if (this.typeMemberModel) {
        typeArray.push('MEMBER');
      }
      if (this.typeSubsystemModel) {
        typeArray.push('SUBSYSTEM');
      }
      this.$emit('apply', {
        member_class: this.memberClassModel,
        instance: this.instanceModel,
        codes: this.codesModel,
        subsystems: this.subsystemsModel,
        types: typeArray,
      });
    },
  },
});
</script>
