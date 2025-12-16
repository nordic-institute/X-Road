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
  <XrdCard title="diagnostics.connection.securityServer.title" class="overview-card">
    <v-card-text class="xrd-card-text">
      <v-row class="my-2"></v-row>
      <v-row dense>
        <v-col cols="2">
          <XrdFormLabel :label-text="$t('diagnostics.connection.securityServer.sourceClient')" />
        </v-col>
        <v-col cols="7">
          <v-combobox
            v-model="selectedClientId"
            class="xrd"
            :items="clients"
            item-title="id"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.client')"
            data-test="other-security-server-client-id"
          />
        </v-col>
        <v-col cols="2">
          <v-radio-group v-model="selectedProtocolType" name="protocolType" inline class="dlg-row-input">
            <v-radio
              class="xrd"
              :label="$t('diagnostics.connection.securityServer.rest')"
              value="REST"
              data-test="other-security-server-rest-radio-button"
            />
            <v-radio
              class="xrd"
              :label="$t('diagnostics.connection.securityServer.soap')"
              value="SOAP"
              data-test="other-security-server-soap-radio-button"
            />
          </v-radio-group>
        </v-col>
        <v-col cols="1">
          <XrdBtn
            variant="text"
            text="diagnostics.connection.test"
            :disabled="!selectedClientId || !selectedSecurityServerId || !selectedProtocolType || otherSecurityServerLoading"
            data-test="other-security-server-test-button"
            @click="testOtherSecurityServerStatus()"
          />
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="2">
          <XrdFormLabel :label-text="$t('diagnostics.connection.securityServer.target')" />
        </v-col>
        <v-col cols="2">
          <v-select
            v-model="selectedInstance"
            :items="xRoadInstanceIdentifiers"
            :label="$t('general.instance')"
            class="flex-input xrd"
            hide-details
            data-test="other-security-server-target-instance"
          />
        </v-col>
        <v-col cols="5">
          <v-combobox
            v-model="selectedTargetSubsystemId"
            class="xrd"
            :items="allSubsystems"
            item-title="id"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.targetClient')"
            data-test="other-security-server-target-client-id"
          />
        </v-col>
        <v-col cols="2">
          <v-combobox
            v-model="selectedSecurityServerId"
            class="xrd"
            :items="localSecurityServers"
            item-title="server_code"
            item-value="id"
            :return-object="false"
            :label="$t('diagnostics.connection.securityServer.securityServer')"
            data-test="other-security-server-id"
          />
        </v-col>
      </v-row>
      <v-row dense>
        <v-col cols="1">
          <XrdFormLabel :label-text="$t('diagnostics.status')" />
        </v-col>
        <v-col cols="1">
          <span v-if="!localOtherStatus">
            <StatusAvatar :status="statusIconType(undefined)" />
          </span>
          <span v-else>
            <StatusAvatar :status="statusIconType(localOtherStatus?.status_class)" />
          </span>
        </v-col>
        <v-col cols="10" data-test="other-security-server-status-message">
          <span v-if="otherSecurityServerLoading">
            <XrdEmptyPlaceholder
              :data="xRoadInstances"
              :loading="otherSecurityServerLoading"
              :no-items-text="$t('noData.noData')"
              skeleton-type="list-item"
              :skeleton-count="1"
            />
          </span>
          <span v-else-if="localOtherStatus && localOtherStatus?.status_class === 'OK'">
            {{ $t('diagnostics.connection.ok') }}
          </span>
          <span v-else>
            {{ otherSecurityServerErrorMessage }}
          </span>
        </v-col>
      </v-row>
    </v-card-text>
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useNotifications, XrdBtn, XrdCard, XrdFormLabel } from '@niis/shared-ui';
import { useGeneral } from '@/store/modules/general';
import { useClients } from '@/store/modules/clients';
import { useClient } from '@/store/modules/client';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { formatErrorForUi, statusIconType } from '@/util/formatting';
import { ConnectionStatus, SecurityServer } from '@/openapi-types';
import StatusAvatar from '@/views/Diagnostics/Overview/StatusAvatar.vue';

const initialState = () => {
  return {
    selectedClientId: '',
    selectedProtocolType: '',
    selectedInstance: '',
    selectedTargetSubsystemId: '',
    selectedSecurityServerId: '',
    localSecurityServers: [] as SecurityServer[],
    localOtherStatus: undefined as ConnectionStatus | undefined,
  };
};

export default defineComponent({
  name: 'ConnectionSecurityServerView',
  components: {
    XrdFormLabel,
    StatusAvatar,
    XrdBtn,
    XrdCard,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      otherSecurityServerLoading: false,
      ...initialState(),
    };
  },

  computed: {
    ...mapState(useGeneral, ['xRoadInstances', 'xRoadInstanceIdentifiers']),
    ...mapState(useClients, ['clients', 'allSubsystems']),
    ...mapState(useClient, ['securityServers']),
    ...mapState(useDiagnostics, ['otherSecurityServerStatus']),

    localInstance(): string {
      const local = this.xRoadInstances.find((i) => i.local);
      return local ? local.identifier : '';
    },
    otherSecurityServerErrorMessage() {
      const err = this.localOtherStatus?.error;
      return formatErrorForUi(err);
    },
  },

  watch: {
    localInstance: {
      immediate: true,
      async handler(newInstance: string) {
        this.selectedInstance = newInstance;
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
        this.localSecurityServers = this.securityServers.map((s: SecurityServer) => ({ ...s }));
        if (this.localSecurityServers.length === 1) {
          this.selectedSecurityServerId = this.localSecurityServers[0].id;
        }
      }
    },
  },

  created() {
    this.localOtherStatus = undefined;
  },

  methods: {
    statusIconType,
    ...mapActions(useClients, ['fetchAllSubsystems']),
    ...mapActions(useClient, ['fetchSecurityServers']),
    ...mapActions(useDiagnostics, ['fetchOtherSecurityServerStatus']),

    testOtherSecurityServerStatus() {
      this.otherSecurityServerLoading = true;
      this.localOtherStatus = undefined;
      this.fetchOtherSecurityServerStatus(
        this.selectedProtocolType,
        this.selectedClientId,
        this.selectedTargetSubsystemId,
        this.selectedSecurityServerId,
      )
        .then(() => {
          this.localOtherStatus = this.otherSecurityServerStatus;
        })
        .catch((error) => {
          this.addError(error);
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
</style>
