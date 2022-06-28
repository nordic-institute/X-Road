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
  <v-dialog v-if="dialog" :value="dialog" width="750" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{ title }}</span>
        <v-spacer />
        <i data-test="x-close-button" @click="cancel()"></i>
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0">
        <v-text-field
          v-model="search"
          :label="searchLabel"
          single-line
          hide-details
          class="search-input"
          data-test="client-search-input"
          autofocus
        >
          <v-icon slot="append">mdi-magnify</v-icon>
        </v-text-field>

        <!-- Table -->
        <v-radio-group v-model="selectedMember">
          <table class="xrd-table members-table fixed_header">
            <thead>
              <tr>
                <th class="checkbox-column"></th>
                <th>{{ $t('general.name') }}</th>
                <th>{{ $t('localGroup.id') }}</th>
              </tr>
            </thead>
            <tbody v-if="selectableClients && selectableClients.length > 0">
              <tr v-for="member in filteredMembers()" :key="member.id">
                <td class="checkbox-column">
                  <div class="checkbox-wrap">
                    <v-radio :key="member.id" :value="member"></v-radio>
                  </div>
                </td>

                <td>{{ member.member_name }}</td>
                <td>{{ member.id }}</td>
              </tr>
            </tbody>
          </table>
        </v-radio-group>

        <div v-if="filteredMembers().length < 1" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <xrd-button
          class="button-margin"
          outlined
          data-test="cancel-button"
          @click="cancel()"
          >{{ $t('action.cancel') }}</xrd-button
        >

        <xrd-button
          :disabled="!selectedMember"
          data-test="save-button"
          @click="save()"
          >{{ $t('localGroup.addSelected') }}</xrd-button
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { Client } from '@/openapi-types';

export default Vue.extend({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    searchLabel: {
      type: String,
      required: true,
    },
    selectableClients: {
      type: Array as PropType<Client[]>,
      default() {
        return [];
      },
    },
  },

  data() {
    return {
      search: '',
      selectedMember: undefined,
    };
  },
  methods: {
    filteredMembers() {
      if (!this.search) {
        return this.selectableClients;
      }

      const tempSearch = this.search.toString().toLowerCase().trim();
      if (tempSearch === '') {
        return this.selectableClients;
      }

      return this.selectableClients.filter((member) => {
        if (member.member_name?.toLowerCase().includes(tempSearch)) {
          return true;
        } else if (member.id?.toLowerCase().includes(tempSearch)) {
          return true;
        }

        return false;
      });
    },
    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('save', this.selectedMember);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      this.selectedMember = undefined;
      this.search = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';
@import '../../assets/add-dialogs';

.checkbox-column {
  width: 50px;
}

.search-input {
  width: 300px;
}
</style>
