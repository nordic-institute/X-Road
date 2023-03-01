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
  <v-dialog v-if="dialog" :value="dialog" width="824" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <slot name="title">
          <span class="dialog-title-text">{{
            $t('systemSettings.selectSubsystem.title')
          }}</span>
        </slot>
        <v-spacer />
        <xrd-close-button id="dlg-close-x" @click="cancel()" />
      </v-card-title>

      <v-card-text style="height: 500px" class="elevation-0">
        <v-text-field
          v-model="search"
          :label="$t('systemSettings.selectSubsystem.search')"
          single-line
          hide-details
          class="search-input"
          autofocus
          append-icon="icon-Search"
        >
        </v-text-field>

        <!-- Table -->
        <v-radio-group v-model="selectedMember">
          <table class="xrd-table members-table fixed_header">
            <thead>
              <tr>
                <th class="checkbox-column"></th>
                <th>{{ $t('systemSettings.selectSubsystem.name') }}</th>
                <th>{{ $t('systemSettings.selectSubsystem.memberCode') }}</th>
                <th>{{ $t('systemSettings.selectSubsystem.memberClass') }}</th>
                <th>
                  {{ $t('systemSettings.selectSubsystem.subsystemCode') }}
                </th>
                <th>
                  {{ $t('systemSettings.selectSubsystem.xroadInstance') }}
                </th>
                <th>{{ $t('systemSettings.selectSubsystem.type') }}</th>
              </tr>
            </thead>
            <template v-if="selectableClients && selectableClients.length > 0">
              <tbody>
                <tr v-for="member in filteredMembers()" :key="member.id">
                  <td class="checkbox-column">
                    <div class="checkbox-wrap">
                      <v-radio :key="member.id" :value="member"></v-radio>
                    </div>
                  </td>
                  <td>{{ member.member_name }}</td>
                  <td>{{ member.xroad_id.member_code }}</td>
                  <td>{{ member.xroad_id.member_class }}</td>
                  <td>{{ member.xroad_id.subsystem_code }}</td>
                  <td>{{ member.xroad_id.instance_id }}</td>
                  <td>{{ member.xroad_id.type }}</td>
                </tr>
              </tbody>
            </template>

            <XrdEmptyPlaceholderRow
              :colspan="7"
              :data="filteredMembers()"
              :loading="loading"
              :no-items-text="$t('noData.noResults')"
            />
          </table>
        </v-radio-group>
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
          data-test="select-button"
          @click="select()"
          >{{ $t('action.select') }}</xrd-button
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { Client } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { clientStore } from '@/store/modules/clients';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  name: 'SelectSubsystemDialog',
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      loading: false,
      selectableClients: [] as Client[] | undefined,
      search: '',
      selectedMember: undefined,
    };
  },
  computed: {
    ...mapStores(clientStore),
  },
  created() {
    this.loading = true;
    this.clientStore
      .getByClientType('SUBSYSTEM')
      .then((resp) => {
        this.selectableClients = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loading = false;
      });
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    filteredMembers() {
      if (!this.search) {
        return this.selectableClients;
      }

      const tempSearch = this.search.toString().toLowerCase().trim();
      if (tempSearch === '') {
        return this.selectableClients;
      }

      return this.selectableClients?.filter((member) => {
        return (
          member.member_name?.toLowerCase().includes(tempSearch) ||
          member.xroad_id.member_code.toLowerCase().includes(tempSearch) ||
          member.xroad_id.member_class.toLowerCase().includes(tempSearch) ||
          member.xroad_id.subsystem_code?.toLowerCase().includes(tempSearch) ||
          member.xroad_id.instance_id.toLowerCase().includes(tempSearch) ||
          member.xroad_id.type.toLowerCase().includes(tempSearch)
        );
      });
    },
    cancel(): void {
      this.$emit('cancel');
      this.clearForm();
    },
    select(): void {
      this.$emit('select', this.selectedMember);
      this.clearForm();
    },
    clearForm(): void {
      this.selectedMember = undefined;
      this.search = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';

.checkbox-column {
  width: 50px;
}
.search-input {
  width: 300px;
}

.dialog-title-text {
  color: $XRoad-WarmGrey100;
  font-weight: bold;
  font-size: 24px;
  line-height: 32px;
}
</style>
