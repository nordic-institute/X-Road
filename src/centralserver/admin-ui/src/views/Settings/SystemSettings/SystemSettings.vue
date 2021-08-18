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
  <sub-view-container>
    <!-- Title  -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('tab.settings.systemSettings') }}
      </div>
    </div>

    <div>
      <!-- System Parameters -->

      <div id="anchor" class="mb-6">
        <v-card class="pb-4" flat>
          <div class="card-top">
            <div class="card-main-title">
              {{ $t('systemSettings.systemParameters') }}
            </div>
          </div>

          <table class="xrd-table mt-0 pb-3">
            <tbody>
              <tr>
                <td class="title-cell">
                  <div class="icon-column-wrap">
                    <div>
                      {{ $t('systemSettings.instanceIdentifier') }}
                    </div>
                  </div>
                </td>
                <td>DEV</td>
                <td></td>
              </tr>
              <tr>
                <td class="td-icon">
                  <div class="icon-column-wrap">
                    <div>
                      {{ $t('systemSettings.centralServerAddress') }}
                    </div>
                  </div>
                </td>
                <td>DEV</td>
                <td class="action-cell">
                  <xrd-button text :outlined="false">{{
                    $t('action.edit')
                  }}</xrd-button>
                </td>
              </tr>
            </tbody>
          </table>
        </v-card>
      </div>
    </div>

    <!-- Management Services -->

    <div id="anchor" class="mb-6">
      <v-card class="pb-4" flat>
        <div class="card-top">
          <div class="card-main-title">
            {{ $t('systemSettings.managementServices') }}
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <tbody>
            <!-- SOFTWARE token table body -->
            <tr>
              <td class="title-cell">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.serviceProviderIdentifier') }}
                  </div>
                </div>
              </td>
              <td>SUBSYSTEM:DEV/ORG/111/MANAGEMENT</td>
              <td class="action-cell">
                <xrd-button text :outlined="false">{{
                  $t('action.edit')
                }}</xrd-button>
              </td>
            </tr>

            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.serviceProviderName') }}
                  </div>
                </div>
              </td>
              <td>NIIS</td>
              <td></td>
            </tr>

            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.managementServiceSecurityServer') }}
                  </div>
                </div>
              </td>
              <td>SERVER:DEV/ORG/111/SS1</td>
              <td></td>
            </tr>

            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.wsdlAddress') }}
                  </div>
                </div>
              </td>
              <td>http://dev-cs.i.x-road.rocks/managementservices.wsdl</td>
              <td></td>
            </tr>

            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.centralServerAddress') }}
                  </div>
                </div>
              </td>
              <td>
                https://dev-cs.i.x-road.rocks:4002/managementservice/manage/
              </td>
              <td></td>
            </tr>

            <tr>
              <td class="td-icon">
                <div class="icon-column-wrap">
                  <div>
                    {{ $t('systemSettings.securityServerOwnerroupCode') }}
                  </div>
                </div>
              </td>
              <td>security-server-owners</td>
              <td></td>
            </tr>
          </tbody>
        </table>
      </v-card>
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="memberClasses"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #top>
        <div class="card-top">
          <div class="card-main-title">
            {{ $t('systemSettings.memberClasses') }}
          </div>
          <div class="card-corner-button">
            <xrd-button outlined class="mr-4">
              <xrd-icon-base class="xrd-large-button-icon"
                ><XrdIconAdd
              /></xrd-icon-base>
              {{ $t('action.add') }}
            </xrd-button>
          </div>
        </div>
      </template>

      <template #[`item.serverCode`]="{ item }">
        <div class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconSecurityServer /></xrd-icon-base>
          {{ item.serverCode }}
        </div>
      </template>

      <template #[`item.button`]>
        <div class="button-wrap">
          <xrd-button text :outlined="false">{{
            $t('action.edit')
          }}</xrd-button>

          <xrd-button text :outlined="false">{{
            $t('action.delete')
          }}</xrd-button>
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>
  </sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'trust services' tab
 */
import Vue from 'vue';
import SubViewContainer from '@/components/layout/SubViewContainer.vue';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  components: {
    SubViewContainer,
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      memberClasses: [
        {
          code: 'COM',
          description: 'Commercial',
        },
        {
          code: 'ORG',
          description: 'Organisation',
        },
        {
          code: 'TRE',
          description: 'Tamperial',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('systemSettings.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header member-classes-table-header-code',
        },
        {
          text: this.$t('systemSettings.description') as string,
          align: 'start',
          value: 'description',
          class: 'xrd-table-header member-classes-table-header-description',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header member-classes-table-header-buttons',
        },
      ];
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-top {
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}

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
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  max-width: 40%;
  width: 40%;
}

.action-cell {
  text-align: right;
  width: 100px;
  /*padding-right: 20px; */
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.cert-icon {
  margin-right: 10px;
  color: $XRoad-Purple100;
}

.icon-column-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
