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
        <v-card-text data-test="member-code-value">12121212</v-card-text>
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
              <xrd-button text :outlined="false">{{
                $t('action.delete')
              }}</xrd-button>
            </div>
          </template>

          <template #footer>
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>
    </div>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';

/**
 * Component for a Members details view
 */
export default Vue.extend({
  name: 'MemberDetails',
  data() {
    return {
      searchServers: '',
      searchGroups: '',
      loading: false,
      loadingGroups: false,
      showOnlyPending: false,
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
