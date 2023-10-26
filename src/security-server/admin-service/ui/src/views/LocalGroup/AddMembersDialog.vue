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
  <v-dialog
    v-if="dialog"
    :model-value="dialog"
    width="842"
    scrollable
    persistent
  >
    <v-card class="xrd-card px-0 mx-0" height="90vh">
      <v-card-title class="d-flex pt-4">
        <span class="text-h5" data-test="add-members-dialog-title">{{
          $t(title)
        }}</span>
        <v-spacer />
        <i id="close-x" @click="cancel()"></i>
      </v-card-title>

      <v-card-text style="height: 500px" class="pa-0">
        <xrd-expandable :is-open="true" class="px-4">
          <template #link="{ toggle }">
            <div class="exp-title cursor-pointer" @click="toggle">
              {{ $t('localGroup.searchOptions') }}
            </div>
          </template>
          <template #content>
            <div class="flex-wrap">
              <div class="input-row px-2 pb-4">
                <v-text-field
                  v-model="name"
                  :label="$t('general.name')"
                  variant="outlined"
                  autofocus
                  clearable
                  hide-details
                  class="flex-input"
                ></v-text-field>

                <v-select
                  v-model="instance"
                  :items="xroadInstances"
                  :label="$t('general.instance')"
                  class="flex-input"
                  clearable
                  variant="outlined"
                  hide-details
                ></v-select>
              </div>

              <div class="input-row px-2 pb-4">
                <v-select
                  v-model="memberClass"
                  :items="memberClasses"
                  :label="$t('general.memberClass')"
                  class="flex-input"
                  clearable
                  variant="outlined"
                  hide-details
                ></v-select>
                <v-text-field
                  v-model="memberCode"
                  :label="$t('general.memberCode')"
                  variant="outlined"
                  clearable
                  hide-details
                  class="flex-input"
                ></v-text-field>
              </div>

              <div class="input-row px-2 pb-4">
                <v-text-field
                  v-model="subsystemCode"
                  :label="$t('general.subsystemCode')"
                  variant="outlined"
                  clearable
                  hide-details
                  class="flex-input"
                ></v-text-field>
              </div>
            </div>

            <div class="search-wrap">
              <xrd-button :loading="loading" @click="search()"
                >{{ $t('action.search') }}
              </xrd-button>
            </div>
          </template>
        </xrd-expandable>

        <!-- Table -->

        <table class="xrd-table members-table fixed_header">
          <thead>
            <tr>
              <th></th>
              <th>{{ $t('general.name') }}</th>
              <th>{{ $t('localGroup.id') }}</th>
            </tr>
          </thead>
          <tbody v-if="members && members.length > 0">
            <tr v-for="member in members" :key="member.id">
              <td>
                <div class="checkbox-wrap">
                  <v-checkbox
                    data-test="add-local-group-member-checkbox"
                    @update:model-value="
                      checkboxChange(member.id as string, $event)
                    "
                  ></v-checkbox>
                </div>
              </td>

              <td>{{ member.member_name }}</td>
              <td>{{ member.id }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="empty-row">
          <p>{{ $t('action.searching') }}</p>
        </div>
        <div
          v-else-if="members.length < 1 && !noResults"
          class="empty-row"
        ></div>

        <div v-if="noResults" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <xrd-button class="button-margin" outlined @click="cancel()"
          >{{ $t('action.cancel') }}
        </xrd-button>

        <xrd-button :disabled="!canSave" @click="save()"
          >{{ $t('localGroup.addSelected') }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import * as api from '@/util/api';
import { Client, GroupMember } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { Colors } from '@/global';
import { XrdExpandable } from '@niis/shared-ui';

const initialState = () => {
  return {
    name: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    members: [] as Client[],
    selectedIds: [] as string[],
    noResults: false,
    checkbox1: true,
    loading: false,
    colors: Colors,
  };
};

export default defineComponent({
  components: { XrdExpandable },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    filtered: {
      type: Array as PropType<GroupMember[]>,
      default: undefined,
    },
    title: {
      type: String,
      default: 'localGroup.addMembers',
    },
  },
  emits: ['cancel', 'members-added'],
  data() {
    return { ...initialState() };
  },
  computed: {
    ...mapState(useGeneral, ['xroadInstances', 'memberClasses']),
    canSave(): boolean {
      return this.selectedIds.length > 0;
    },
  },
  created() {
    this.fetchXroadInstances();
    this.fetchMemberClasses();
  },

  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useGeneral, ['fetchMemberClasses', 'fetchXroadInstances']),
    checkboxChange(id: string, event: boolean): void {
      if (event) {
        this.selectedIds.push(id);
      } else {
        const index = this.selectedIds.indexOf(id);
        if (index > -1) {
          this.selectedIds.splice(index, 1);
        }
      }
    },
    search(): void {
      this.noResults = false;
      let query = `/clients?name=${this.name}&member_code=${this.memberCode}&subsystem_code=${this.subsystemCode}&show_members=false&internal_search=false`;

      // These checks are needed because instance and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        query = query + `&instance=${this.instance}`;
      }

      if (this.memberClass) {
        query = query + `&member_class=${this.memberClass}`;
      }

      this.loading = true;
      this.members = [];
      this.selectedIds = [];
      api
        .get<Client[]>(query)
        .then((res) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out members that are already added
            this.members = res.data.filter((member) => {
              return !this.filtered?.find((item) => {
                return item.id === member.id;
              });
            });
          } else {
            // Show results straight if there is nothing to filter
            this.members = res.data;
          }

          if (this.members.length < 1) {
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
      this.$emit('members-added', this.selectedIds);
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

.cursor-pointer {
  cursor: pointer;
}
</style>
