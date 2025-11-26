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
  <v-card variant="flat" class="xrd-card diagnostic-card">
    <v-card-title class="text-h5">
      {{ $t('diagnostics.connection.securityServer.title') }}
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <v-row class="my-2"></v-row>
      <v-row dense>
        <v-col cols="2">
          <xrd-form-label
            :label-text="$t('diagnostics.connection.securityServer.current')"
          />
        </v-col>
        <v-col cols="7">
          <v-combobox
            v-model="selectedClientId"
            :items="clients"
            item-title="id"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.client')"
            variant="outlined"
            data-test="other-security-server-client-id"
          ></v-combobox>
        </v-col>
        <v-col cols="2">
          <v-radio-group
            v-model="selectedProtocolType"
            name="protocolType"
            inline
            class="dlg-row-input"
          >
            <v-radio
              :label="$t('diagnostics.connection.securityServer.rest')"
              value="REST"
              data-test="other-security-server-rest-radio-button"
            ></v-radio>
            <v-radio
              :label="$t('diagnostics.connection.securityServer.soap')"
              value="SOAP"
              data-test="other-security-server-soap-radio-button"
            ></v-radio>
          </v-radio-group>
        </v-col>
        <v-col cols="1">
          <xrd-button
            large
            variant="text"
            :disabled="!selectedClientId || !selectedSecurityServerId || !selectedProtocolType || otherSecurityServerLoading"
            @click="testOtherSecurityServerStatus()"
            data-test="other-security-server-test-button"
          >
            {{ $t('diagnostics.connection.test') }}
          </xrd-button>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="2">
          <xrd-form-label
            :label-text="$t('diagnostics.connection.securityServer.target')"
          />
        </v-col>
        <v-col cols="2">
          <v-select
            v-model="selectedInstance"
            :items="xRoadInstanceIdentifiers"
            :label="$t('general.instance')"
            class="flex-input"
            variant="outlined"
            hide-details
            data-test="other-security-server-target-instance"
          ></v-select>
        </v-col>
        <v-col cols="5">
          <v-combobox
            v-model="selectedTargetSubsystemId"
            :items="allSubsystems"
            item-title="id"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.targetClient')"
            variant="outlined"
            data-test="other-security-server-target-client-id"
          ></v-combobox>
        </v-col>
        <v-col cols="2">
          <v-combobox
            v-model="selectedSecurityServerId"
            :items="securityServers"
            item-title="server_code"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.securityServer')"
            variant="outlined"
            data-test="other-security-server-id"
          ></v-combobox>
        </v-col>
      </v-row>
      <v-row dense>
        <v-col>
          <span v-if="!otherSecurityServerLoading">
            <xrd-status-icon :status="statusIconType(localOtherStatus?.status_class)"/>
          </span>
        </v-col>
        <v-col cols="11" data-test="other-security-server-status-message">
          <span v-if="otherSecurityServerLoading">
          </span>
          <span v-else-if="localOtherStatus && localOtherStatus?.status_class === 'OK'">
           {{ $t('diagnostics.connection.ok') }}
           </span>
          <span v-else>
            {{ otherSecurityServerErrorMessage }}
          </span>
        </v-col>
      </v-row>

      <XrdEmptyPlaceholder
        :data="xRoadInstances"
        :loading="otherSecurityServerLoading"
        :no-items-text="$t('noData.noData')"
        skeleton-type="list-item"
        :skeleton-count="1"
      />
    </v-card-text>
  </v-card>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from "pinia";
import { useNotifications } from "@/store/modules/notifications";
import { useGeneral } from "@/store/modules/general";
import { useClients } from "@/store/modules/clients";
import { useClient } from "@/store/modules/client";
import { useDiagnostics } from "@/store/modules/diagnostics";
import { formatErrorForUi, statusIconType } from "@/util/formatting";
import { ConnectionStatus } from "@/openapi-types";

const initialState = () => {
  return {
    selectedClientId: '',
    selectedProtocolType: '',
    selectedInstance: '',
    selectedTargetSubsystemId: '',
    selectedSecurityServerId: '',
    localOtherStatus: undefined as ConnectionStatus | undefined,
  };
};

export default defineComponent({
  name: 'ConnectionSecurityServerView',
  data() {
    return {
      otherSecurityServerLoading: false,
      ...initialState()
    };
  },

  created() {
    this.localOtherStatus = undefined;
  },

  computed: {
    ...mapState(useGeneral, ['xRoadInstances', 'xRoadInstanceIdentifiers']),
    ...mapState(useClients, ['clients', 'allSubsystems']),
    ...mapState(useClient, ['securityServers']),
    ...mapState(useDiagnostics, ['otherSecurityServerStatus']),

    localInstance(): string {
      const local = this.xRoadInstances.find(i => i.local);
      return local ? local.identifier : '';
    },
    otherSecurityServerErrorMessage() {
      const err = this.otherSecurityServerStatus?.error
      return formatErrorForUi(err)
    },
  },

  watch: {
    localInstance: {
      immediate: true,
      async handler(newInstance: string) {

        this.selectedInstance = newInstance
        this.selectedTargetSubsystemId = '';
        this.selectedSecurityServerId = '';

        if (newInstance) {
          await this.fetchAllSubsystems(newInstance);
        }
      },
    },
    async selectedTargetSubsystemId(newSubsystemId: string | null) {
      this.selectedSecurityServerId = '';
      if (newSubsystemId) {
        await this.fetchSecurityServers(newSubsystemId);
        if (this.securityServers.length === 1) {
          this.selectedSecurityServerId = this.securityServers[0].id;
        }
      }
    },
  },
  methods: {
    statusIconType,
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useClients, ['fetchAllSubsystems']),
    ...mapActions(useClient, ['fetchSecurityServers']),
    ...mapActions(useDiagnostics, ['fetchOtherSecurityServerStatus']),

    testOtherSecurityServerStatus() {
      this.otherSecurityServerLoading = true;
      this.localOtherStatus = undefined;
      this.fetchOtherSecurityServerStatus(this.selectedProtocolType, this.selectedClientId,
        this.selectedTargetSubsystemId, this.selectedSecurityServerId)
        .then(() => {
          this.localOtherStatus = this.otherSecurityServerStatus;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.otherSecurityServerLoading = false;
        });
    },
  },
});
</script>

<style scoped lang="scss">
.xrd-card-text {
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;
  margin-bottom: 30px;

  &:first-of-type {
    margin-top: 40px;
  }
}

</style>
