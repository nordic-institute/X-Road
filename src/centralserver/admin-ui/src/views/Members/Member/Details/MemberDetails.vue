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
<!--
  Member details view
-->
<template>
  <main id="member-details-content">
    <!-- Member Details -->
    <div id="member-details">
      <v-card class="details-card" data-test="member-name-card" flat>
        <v-card-title class="card-title">{{
          $t('global.memberName')
        }}</v-card-title>
        <v-divider></v-divider>
        <v-card-text data-test="member-name-value">Netum</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>

      <v-card class="details-card" data-test="member-class-card" flat>
        <v-card-title class="card-title">{{
          $t('global.memberClass')
        }}</v-card-title>
        <v-divider></v-divider>
        <v-card-text data-test="member-class-value">COM</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>

      <v-card class="details-card" data-test="member-code-card" flat>
        <v-card-title class="card-title">{{
          $t('global.memberCode')
        }}</v-card-title>
        <v-divider></v-divider>
        <v-card-text data-test="member-code-value">{{
          memberCode
        }}</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>
    </div>

    <!-- Owned Servers -->
    <div id="owned-servers">
      <div class="xrd-title-search mb-8">
        <div class="xrd-view-title">
          {{ $t('members.member.details.ownedServers') }}
        </div>
        <xrd-search v-model="searchServers" data-test="search-owned-servers" />
      </div>

      <v-card flat>
        <div class="card-corner-button pt-4 pr-4">
          <xrd-button outlined>
            <xrd-icon-base class="xrd-large-button-icon"
              ><xrd-icon-add /></xrd-icon-base
            >{{ $t('members.member.details.addServer') }}
          </xrd-button>
        </div>

        <!-- Table -->
        <v-data-table
          :loading="loading"
          :headers="serversHeaders"
          :items="servers"
          :search="searchServers"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
          hide-default-footer
        >
          <template #footer>
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>
    </div>

    <!-- Global Groups -->
    <div id="global-groups">
      <div class="xrd-title-search mt-8 mb-8">
        <div class="xrd-view-title">
          {{ $t('members.member.details.globalGroups') }}
        </div>
        <xrd-search v-model="searchGroups" data-test="search-global-groups" />
      </div>

      <v-card flat>
        <div class="card-corner-button pt-4 pr-4">
          <xrd-button outlined data-test="add-member-to-group">
            <xrd-icon-base class="xrd-large-button-icon"
              ><xrd-icon-add /></xrd-icon-base
            >{{ $t('members.member.details.addMemberToGroup') }}
          </xrd-button>
        </div>

        <!-- Table -->
        <v-data-table
          :loading="loadingGroups"
          :headers="groupsHeaders"
          :items="globalGroups"
          :search="searchGroups"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
          hide-default-footer
        >
          <template #[`item.button`]>
            <div class="cs-table-actions-wrap">
              <xrd-button
                text
                :outlined="false"
                @click="showDeleteFromGroupDialog = true"
                >{{ $t('action.delete') }}</xrd-button
              >
            </div>
          </template>

          <template #footer>
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>

      <div class="delete-action" @click="showDialog = true">
        <div>
          <v-icon class="xrd-large-button-icon" :color="colors.Purple100"
            >mdi-close-circle</v-icon
          >
        </div>
        <div class="action-text">
          {{ $t('members.member.details.deleteMember') }} "{{ memberName }}"
        </div>
      </div>
    </div>

    <!-- Delete member - Check member code dialog -->

    <v-dialog v-if="showDialog" v-model="showDialog" width="500" persistent>
      <ValidationObserver ref="initializationForm" v-slot="{ invalid }">
        <v-card class="xrd-card">
          <v-card-title>
            <span class="headline">{{
              $t('members.member.details.deleteMember')
            }}</span>
          </v-card-title>
          <v-card-text class="pt-4">
            {{
              $t('members.member.details.areYouSure1', {
                member: 'NIIS',
              })
            }}
            <div class="dlg-input-width pt-4">
              <ValidationProvider
                v-slot="{ errors }"
                ref="initializationParamsVP"
                name="init.identifier"
                :rules="{ required: true, is: memberCode }"
                data-test="instance-identifier--validation"
              >
                <v-text-field
                  v-model="offeredCode"
                  outlined
                  :label="$t('members.member.details.enterCode')"
                  autofocus
                  data-test="add-local-group-code-input"
                  :error-messages="errors"
                ></v-text-field>
              </ValidationProvider>
            </div>
          </v-card-text>
          <v-card-actions class="xrd-card-actions">
            <v-spacer></v-spacer>
            <xrd-button outlined @click="cancelDelete()">{{
              $t('action.cancel')
            }}</xrd-button>
            <xrd-button :disabled="invalid" @click="deleteMember()">{{
              $t('action.delete')
            }}</xrd-button>
          </v-card-actions>
        </v-card>
      </ValidationObserver>
    </v-dialog>

    <!-- Delete member from a group -->
    <v-dialog
      v-if="showDeleteFromGroupDialog"
      v-model="showDeleteFromGroupDialog"
      width="500"
      persistent
    >
      <v-card class="xrd-card">
        <v-card-title>
          <span class="headline">{{
            $t('members.member.details.deleteMember')
          }}</span>
        </v-card-title>
        <v-card-text class="pt-4">
          {{
            $t('members.member.details.areYouSure2', {
              member: memberName,
              group: 'opendata-providers',
            })
          }}
        </v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <xrd-button outlined @click="cancelDelete()">{{
            $t('action.cancel')
          }}</xrd-button>
          <xrd-button @click="deleteMemberFromGroup()">{{
            $t('action.delete')
          }}</xrd-button>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { Colors } from '@/global';
import { ValidationObserver, ValidationProvider } from 'vee-validate';

/**
 * Component for a Members details view
 */
export default Vue.extend({
  name: 'MemberDetails',
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  data() {
    return {
      searchServers: '',
      searchGroups: '',
      memberName: 'Netum',
      loading: false,
      loadingGroups: false,
      showOnlyPending: false,
      colors: Colors,
      showDialog: false,
      offeredCode: '',
      memberCode: '12345', // Mock. This will come from a backend with member data
      wrongCode: false,
      showDeleteFromGroupDialog: false,
      servers: [
        {
          name: 'SS1',
        },
        {
          name: 'SS2',
        },
      ],
      globalGroups: [
        {
          group: 'security-server-owners',
          subsystem: 'test',
          added: '2020-11-10 18:00',
        },
        {
          group: 'test data',
          subsystem: 'subsystem',
          added: '2021-02-05 17:00',
        },
      ],
    };
  },
  computed: {
    serversHeaders(): DataTableHeader[] {
      return [
        {
          text: this.$t('global.server') as string,
          align: 'start',
          value: 'name',
          class: 'xrd-table-header servers-table-header-server',
        },
      ];
    },
    groupsHeaders(): DataTableHeader[] {
      return [
        {
          value: 'group',
          text: this.$t('members.member.details.group') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-group',
        },
        {
          value: 'subsystem',
          text: this.$t('global.subsystem') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-subsystem',
        },
        {
          value: 'added',
          text: this.$t('members.member.details.addedToGroup') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-added',
        },
        {
          value: 'button',
          text: '',
          sortable: false,
          class: 'xrd-table-header groups-table-header-buttons',
        },
      ];
    },
  },
  methods: {
    deleteMember() {
      // Delete action
      this.showDialog = false;
      this.offeredCode = '';
    },

    deleteMemberFromGroup() {
      this.showDeleteFromGroupDialog = false;
    },
    cancelDelete() {
      this.showDialog = false;
      this.showDeleteFromGroupDialog = false;
      this.offeredCode = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
  justify-content: flex-end;
}

.delete-action {
  margin-top: 34px;
  color: $XRoad-Link;
  cursor: pointer;
  display: flex;
  flex-direction: row;
  .action-text {
    margin-top: 2px;
  }
}

#member-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}

#global-groups-table {
  tbody tr td:last-child {
    width: 50px;
  }
}
</style>
