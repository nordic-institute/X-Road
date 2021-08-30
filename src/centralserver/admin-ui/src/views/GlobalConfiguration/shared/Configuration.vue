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
  <div>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">Signing Keys</div>
      </div>
    </div>

    <token-expandable
      v-for="token in tokens"
      :key="token.id"
      :token="token"
      @refresh-list="fetchData"
      @token-logout="logoutDialog = true"
      @token-login="loginDialog = true"
      @add-key="addKey"
    />

    <!-- Internal configuration -->
    <div class="header-row mt-7">
      <div class="xrd-view-title">{{ title }}</div>
    </div>

    <!-- Anchor -->
    <div id="anchor" class="mt-4">
      <v-card flat>
        <div class="card-top">
          <div class="card-main-title">Anchor</div>
          <div class="card-corner-button pr-4">
            <xrd-button outlined class="mr-4">
              <xrd-icon-base class="xrd-large-button-icon"
                ><XrdIconAdd
              /></xrd-icon-base>

              Re-create
            </xrd-button>
            <xrd-button outlined>
              <xrd-icon-base class="xrd-large-button-icon"
                ><XrdIconDownload
              /></xrd-icon-base>
              Download
            </xrd-button>
          </div>
        </div>
        <v-card-title class="card-title"
          >Certificate Hash (SHA-224)</v-card-title
        >
        <v-divider></v-divider>
        <v-card-text>
          <xrd-icon-base class="internal-conf-icon"
            ><XrdIconCertificate
          /></xrd-icon-base>
          42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F:42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F</v-card-text
        >
        <v-divider class="pb-4"></v-divider>
      </v-card>
    </div>

    <!-- Download URL -->
    <div id="download-url" class="mt-5">
      <v-card flat>
        <div class="card-top">
          <div class="card-main-title">Download URL</div>
        </div>
        <v-card-title class="card-title">URL Address</v-card-title>
        <v-divider></v-divider>
        <v-card-text>
          <v-icon class="internal-conf-icon">mdi-link</v-icon
          >http://dev-cs-i.x-road.rocks/internalconf</v-card-text
        >
        <v-divider class="pb-4"></v-divider>
      </v-card>
    </div>

    <!-- Configuration parts -->
    <div id="global-groups" class="mt-5">
      <v-card flat>
        <div class="card-top">
          <div class="card-main-title">Configuration parts</div>
        </div>

        <v-card-text class="px-0">
          <xrd-table id="global-groups-table">
            <thead>
              <tr>
                <th>File</th>
                <th>Content identifier</th>
                <th>Version</th>
                <th>Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <v-icon class="internal-conf-icon"
                    >mdi-file-document-outline</v-icon
                  >

                  fetchinterval-params.xml
                </td>
                <td>FETCHINTERVAL</td>
                <td>All versions</td>
                <td>2020-11-10 18:00</td>
                <td class="td-align-right">
                  <xrd-button :outlined="false" text
                    >{{ $t('action.download') }}
                  </xrd-button>
                </td>
              </tr>
              <tr>
                <td>
                  <v-icon class="internal-conf-icon"
                    >mdi-file-document-outline</v-icon
                  >
                  monitoring-params.xml
                </td>
                <td>MONITORING</td>
                <td>All versions</td>
                <td>2021-09-04 10:15</td>
                <td class="td-align-right">
                  <xrd-button :outlined="false" text
                    >{{ $t('action.upload') }}
                  </xrd-button>
                </td>
              </tr>
            </tbody>
          </xrd-table>
        </v-card-text>
      </v-card>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import TokenExpandable from './TokenExpandable.vue';
import { StoreTypes } from '@/global';
import { mapGetters } from 'vuex';

export default Vue.extend({
  components: {
    TokenExpandable,
  },
  props: {
    title: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '' as string,
      creatingBackup: false,
      uploadingBackup: false,
      needsConfirmation: false,
      uploadedFile: null as File | null,
    };
  },
  computed: {
    ...mapGetters({
      tokens: StoreTypes.getters.SORTED_TOKENS,
    }),
  },

  methods: {
    toggleChangePinOpen(): void {
      // TODO
    },

    isTokenLoggedIn(): boolean {
      // TODO
      return true;
    },

    fetchData(): void {
      // TODO
    },

    addKey(): void {
      // TODO
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

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

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}

.td-align-right {
  text-align: right;
}
</style>
