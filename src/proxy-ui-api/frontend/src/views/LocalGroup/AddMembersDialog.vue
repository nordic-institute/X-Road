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
  <v-dialog :value="dialog" width="842" scrollable persistent>
    <v-card class="xrd-card px-0 mx-0">
      <v-card-title>
        <span class="headline">{{ $t(title) }}</span>
        <v-spacer />
        <i @click="cancel()" id="close-x"></i>
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0 px-0">
        <v-expansion-panels
          class="elevation-0 px-0"
          v-model="expandPanel"
          multiple
        >
          <v-expansion-panel class="elevation-0 px-0">
            <v-expansion-panel-header></v-expansion-panel-header>
            <v-expansion-panel-content class="elevation-0 px-0">
              <template v-slot:header>
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
                      :label="$t('name')"
                      outlined
                      autofocus
                      hide-details
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="instance"
                      :items="xroadInstances"
                      :label="$t('instance')"
                      class="flex-input"
                      clearable
                      outlined
                    ></v-select>
                  </div>

                  <div class="input-row">
                    <v-select
                      v-model="memberClass"
                      :items="memberClasses"
                      :label="$t('member_class')"
                      class="flex-input"
                      clearable
                      outlined
                    ></v-select>
                    <v-text-field
                      v-model="memberCode"
                      :label="$t('member_code')"
                      outlined
                      hide-details
                      class="flex-input"
                    ></v-text-field>
                  </div>
                  <v-text-field
                    v-model="subsystemCode"
                    :label="$t('subsystem_code')"
                    outlined
                    hide-details
                    class="flex-input"
                  ></v-text-field>
                </div>

                <div class="search-wrap">
                  <large-button @click="search()">{{
                    $t('action.search')
                  }}</large-button>
                </div>
              </div>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- Table -->

        <table class="xrd-table members-table fixed_header">
          <thead>
            <tr>
              <th></th>
              <th>{{ $t('name') }}</th>
              <th>{{ $t('localGroup.id') }}</th>
            </tr>
          </thead>
          <tbody v-if="members && members.length > 0">
            <tr v-for="member in members" v-bind:key="member.id">
              <td>
                <div class="checkbox-wrap">
                  <v-checkbox
                    @change="checkboxChange(member.id, $event)"
                    color="primary"
                  ></v-checkbox>
                </div>
              </td>

              <td>{{ member.member_name }}</td>
              <td>{{ member.id }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="members.length < 1 && !noResults" class="empty-row"></div>

        <div v-if="noResults" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <large-button class="button-margin" outlined @click="cancel()">{{
          $t('action.cancel')
        }}</large-button>

        <large-button :disabled="!canSave" @click="save()">{{
          $t('localGroup.addSelected')
        }}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';
import { Client } from '@/openapi-types';

const initialState = () => {
  return {
    name: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    expandPanel: [0],
    members: [] as Client[],
    selectedIds: [] as string[],
    noResults: false,
    checkbox1: true,
  };
};

export default Vue.extend({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    filtered: {
      type: Array as PropType<Client[]>,
    },
    title: {
      type: String,
      default: 'localGroup.addMembers',
    },
  },

  data() {
    return { ...initialState() };
  },
  computed: {
    ...mapGetters(['xroadInstances', 'memberClasses']),
    canSave(): boolean {
      return this.selectedIds.length > 0;
    },
  },

  methods: {
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

      api
        .get<Client[]>(query)
        .then((res) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out members that are already added
            this.members = res.data.filter((member) => {
              return !this.filtered.find((item) => {
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
          this.$store.dispatch('showError', error);
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
  created() {
    this.$store.dispatch('fetchXroadInstances');
    this.$store.dispatch('fetchMemberClasses');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';
@import '../../assets/add-dialogs';
</style>
