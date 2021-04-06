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
      <v-card class="details-card" data-test="member-name-card">
        <v-card-title class="card-title">{{
          $t('global.memberName')
        }}</v-card-title>
        <v-divider></v-divider>
        <v-card-text data-test="member-name-value">Netum</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>

      <v-card class="details-card" data-test="member-class-card">
        <v-card-title class="card-title">{{
          $t('global.memberClass')
        }}</v-card-title>
        <v-divider></v-divider>
        <v-card-text data-test="member-class-value">COM</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>

      <v-card class="details-card" data-test="member-code-card">
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
        <xrd-search data-test="search-owned-servers" />
      </div>

      <v-card>
        <div class="card-corner-button pt-4 pr-4">
          <xrd-button outlined>
            <v-icon class="xrd-large-button-icon">mdi-plus-circle</v-icon
            >{{ $t('members.member.details.addServer') }}
          </xrd-button>
        </div>
        <v-card-title class="card-title">Server</v-card-title>
        <v-divider></v-divider>
        <v-card-text>SS1</v-card-text>
        <v-divider class="pb-4"></v-divider>
      </v-card>
    </div>

    <!-- Global Groups -->
    <div id="global-groups">
      <div class="xrd-title-search mt-8 mb-8">
        <div class="xrd-view-title">
          {{ $t('members.member.details.globalGroups') }}
        </div>
        <xrd-search data-test="search-global-groups" />
      </div>

      <v-card>
        <div class="card-corner-button pt-4 pr-4">
          <xrd-button outlined data-test="add-member-to-group">
            <v-icon class="xrd-large-button-icon">mdi-plus-circle</v-icon
            >{{ $t('members.member.details.addMemberToGroup') }}
          </xrd-button>
        </div>

        <v-card-text>
          <xrd-table id="global-groups-table">
            <thead>
              <tr>
                <th>{{ $t('members.member.details.group') }}</th>
                <th>{{ $t('global.subsystem') }}</th>
                <th>{{ $t('members.member.details.addedToGroup') }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>security-server-owners</td>
                <td></td>
                <td>2020-11-10 18:00</td>
                <td></td>
              </tr>
              <tr>
                <td>Test data</td>
                <td>Subsystem</td>
                <td>2021-09-04 10:15</td>
                <td class="xrd-clickable">{{$t('global.delete')}}</td>
              </tr>
            </tbody>
          </xrd-table>
        </v-card-text>
      </v-card>
    </div>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';

/**
 * Component for a Members details view
 */
export default Vue.extend({
  name: 'MemberDetails',
});
</script>

<style scoped lang="scss">
@import '../../../../assets/colors';

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
