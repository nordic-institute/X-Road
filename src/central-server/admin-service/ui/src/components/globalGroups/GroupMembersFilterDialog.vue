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
  <xrd-simple-dialog
    title="filters.chooseFilters"
    data-test="group-members-filter-dialog"
    save-button-text="filters.apply"
    width="824"
    z-index="1999"
    @save="apply"
    @cancel="cancel"
  >
    <template #content>
      <v-container fluid class="ma-0 pa-0 mb-9">
        <!-- By type -->
        <div class="filter-title-row field-title">
          {{ $t('filters.groupMembers.byType') }}
        </div>
        <v-row class="filter-dlg-row">
          <v-col class="d-flex" cols="4" sm="4" md="4">
            <v-checkbox
              v-model="typeMemberModel"
              :label="$t('filters.groupMembers.member')"
            />
          </v-col>
          <v-col class="d-flex" cols="4" sm="4" md="4">
            <v-checkbox
              v-model="typeSubsystemModel"
              :label="$t('filters.groupMembers.subsystem')"
            />
          </v-col>
        </v-row>
        <v-divider class="custom-divider"></v-divider>

        <v-row align="center" class="filter-dlg-row">
          <v-col class="d-flex flex-column" cols="12" sm="6">
            <div class="field-title mt-6 mb-6">
              {{ $t('filters.groupMembers.byInstance') }}
            </div>
            <v-select
              v-model="instanceModel"
              :items="instances"
              :label="$t('filters.groupMembers.instance')"
              variant="outlined"
            />
          </v-col>

          <v-col class="d-flex flex-column" cols="12" sm="6">
            <div class="field-title mt-6 mb-6">
              {{ $t('filters.groupMembers.byClass') }}
            </div>
            <v-select
              v-model="memberClassModel"
              :items="memberClasses"
              variant="outlined"
              :label="$t('filters.groupMembers.class')"
            />
          </v-col>
        </v-row>

        <!-- By code -->
        <v-row align="center" class="filter-dlg-row">
          <v-col class="d-flex flex-column" cols="12" sm="6">
            <div class="field-title mt-0 mb-6">
              {{ $t('filters.groupMembers.byCode') }}
            </div>
            <v-autocomplete
              v-model="codesModel"
              clearable
              multiple
              :items="codes"
              variant="underlined"
            />
          </v-col>

          <!-- By subsystem -->
          <v-col class="d-flex flex-column" cols="12" sm="6">
            <div class="field-title mt-0 mb-6">
              {{ $t('filters.groupMembers.bySubsystem') }}
            </div>
            <v-autocomplete
              v-model="subsystemsModel"
              clearable
              multiple
              :items="subsystems"
              variant="underlined"
            />
          </v-col>
        </v-row>
      </v-container>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
/** Base component for simple dialogs */

import { defineComponent } from 'vue';
import { mapStores } from 'pinia';
import { useGlobalGroups } from '@/store/modules/global-groups';

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
  name: 'GroupMembersFilterDialog',
  components: { },
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
      instances: [] as string[] | null | undefined,
      memberClasses: [] as string[] | null | undefined,
      subsystems: [] as string[] | null | undefined,
      codes: [] as string[] | null | undefined,
      ...initialState(),
    };
  },

  computed: {
    ...mapStores(useGlobalGroups),
  },
  created() {
    this.globalGroupStore.getMembersFilterModel(this.groupCode).then((resp) => {
      this.instances = resp.instances;
      this.memberClasses = resp.member_classes;
      this.subsystems = resp.subsystems;
      this.codes = resp.codes;
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
      if (this.typeMemberModel === true) {
        typeArray.push('MEMBER');
      }
      if (this.typeSubsystemModel === true) {
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

<style lang="scss">
/* Override default vuetify paddings so the horizontal line doesn't cut off  */
.v-dialog > .v-card > .filters-content-wrapper {
  margin-left: 0;
  margin-right: 0;
  padding-left: 0;
  padding-right: 0;
}
</style>

<style lang="scss" scoped>
@import '@/assets/colors';

.xrd-card {
  .xrd-card-actions {
    background-color: $XRoad-WarmGrey10;
    height: 72px;
    padding-right: 24px;
  }
}

.filter-title-row {
  margin: 20px;
}

.filter-dlg-row {
  margin-left: 10px;
  margin-right: 10px;
}

.dlg-button-margin {
  margin-right: 14px;
}

.close-button {
  margin-left: auto;
  margin-right: 0;
}

.alert-slot {
  margin-left: 20px;
  margin-right: 20px;
}

.custom-divider {
  width: 100%;
}

.field-title {
  color: $XRoad-WarmGrey100;
  font-weight: 600;
  font-size: 14px;
}

.dialog-title-text {
  color: $XRoad-WarmGrey100;
  font-weight: bold;
  font-size: 24px;
  line-height: 32px;
}
</style>
