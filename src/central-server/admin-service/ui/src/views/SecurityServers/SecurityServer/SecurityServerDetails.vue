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
  <main data-test="security-server-details-view" class="mt-8">
    <!-- Security Server Details -->
    <div id="security-server-details">
      <info-card
        :title-text="$t('securityServers.ownerName')"
        :info-text="securityServer.ownerName"
        data-test="security-server-owner-name"
        :action-text="$t('action.edit')"
        @actionClicked="editOwnerName"
      />

      <info-card
        :title-text="$t('securityServers.ownerClass')"
        :info-text="securityServer.ownerClass"
        data-test="security-server-owner-class"
      />

      <info-card
        :title-text="$t('securityServers.ownerCode')"
        :info-text="securityServer.ownerCode"
        data-test="security-server-owner-code"
      />
    </div>

    <info-card
      class="mb-6"
      :title-text="$t('securityServers.serverCode')"
      :info-text="securityServer.serverCode"
      data-test="security-server-server-code"
    />

    <info-card
      class="mb-6"
      :title-text="$t('securityServers.address')"
      :info-text="securityServer.address"
      data-test="security-server-address"
      :action-text="$t('action.edit')"
      @actionClicked="editAddress"
    />

    <info-card
      :title-text="$t('securityServers.registered')"
      :info-text="securityServer.registered"
      data-test="security-server-registered"
    />

    <div class="delete-action" @click="showVerifyCodeDialog = true">
      <div>
        <v-icon class="xrd-large-button-icon" :color="colors.Purple100"
          >mdi-close-circle</v-icon
        >
      </div>
      <div class="action-text">
        {{ $t('securityServers.securityServer.deleteSecurityServer') }}
        "{{ securityServer.serverCode }}"
      </div>
    </div>

    <!-- Delete Security Server - Check code dialog -->

    <v-dialog
      v-if="showVerifyCodeDialog"
      v-model="showVerifyCodeDialog"
      width="500"
      persistent
    >
      <ValidationObserver ref="deleteDialog" v-slot="{ invalid }">
        <v-card class="xrd-card">
          <v-card-title>
            <span class="headline">{{
              $t('securityServers.securityServer.deleteSecurityServer')
            }}</span>
          </v-card-title>
          <v-card-text class="pt-4">
            {{
              $t('securityServers.securityServer.areYouSure', {
                serverCode: securityServer.serverCode,
              })
            }}
            <div class="dlg-input-width pt-4">
              <ValidationProvider
                v-slot="{ errors }"
                ref="serverCodeInput"
                name="init.identifier"
                :rules="{ required: true, is: securityServer.serverCode }"
                data-test="instance-identifier--validation"
              >
                <v-text-field
                  v-model="offeredCode"
                  outlined
                  :label="$t('securityServers.securityServer.enterCode')"
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
            <xrd-button :disabled="invalid" @click="deleteServer()">{{
              $t('action.delete')
            }}</xrd-button>
          </v-card-actions>
        </v-card>
      </ValidationObserver>
    </v-dialog>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { Colors } from '@/global';
import { extend, ValidationObserver, ValidationProvider } from 'vee-validate';

/**
 * Component for a Security server details view
 */
export default Vue.extend({
  name: 'SecurityServerDetails',
  components: {
    InfoCard,
    ValidationObserver,
    ValidationProvider,
  },
  data() {
    return {
      searchServers: '',
      searchGroups: '',
      loading: false,
      loadingGroups: false,
      showOnlyPending: false,
      colors: Colors,
      showVerifyCodeDialog: false,
      offeredCode: '',
      securityServer: {
        ownerName: 'NIIS',
        ownerClass: 'ORG',
        ownerCode: '555',
        serverCode: 'NIIS-SS1',
        address: 'xroad-lxd-ss1.net',
        registered: '2020-11-10 16:55:01',
      },
    };
  },

  methods: {
    editOwnerName(): void {
      // do something
    },
    editAddress(): void {
      // do something
    },

    deleteServer() {
      // Delete action
      this.showVerifyCodeDialog = false;
      this.offeredCode = '';
    },
    cancelDelete() {
      this.showVerifyCodeDialog = false;
      this.offeredCode = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

#security-server-details {
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
</style>
