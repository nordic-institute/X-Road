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
  <div class="xrd-sub-view-wrapper">
    <v-container fluid class="xrd-view-common mt-7">
      <v-row class="title-action mx-0">
        <div v-if="clientLoading" class="xrd-view-title mb-3">
          {{ $t('noData.loading') }}
        </div>

        <div v-else-if="client" class="xrd-view-title mb-3">
          {{ `${client.subsystem_code} (${$t('general.subsystem')})` }}
        </div>
        <div>
          <DisableClientButton v-if="showDisable" :id="id" @done="fetchData" />
          <EnableClientButton v-if="showEnable" :id="id" @done="fetchData" />
          <DeleteClientButton v-if="showDelete" :id="id" />
          <UnregisterClientButton
            v-if="showUnregister"
            :id="id"
            @done="fetchData"
          />
        </div>
      </v-row>

      <router-view />
    </v-container>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import DeleteClientButton from '@/components/client/DeleteClientButton.vue';
import UnregisterClientButton from '@/components/client/UnregisterClientButton.vue';
import DisableClientButton from '@/components/client/DisableClientButton.vue';
import EnableClientButton from '@/components/client/EnableClientButton.vue';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import { ClientStatus } from '@/openapi-types';

export default defineComponent({
  components: {
    EnableClientButton,
    DisableClientButton,
    UnregisterClientButton,
    DeleteClientButton,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmUnregisterClient: false as boolean,
      unregisterLoading: false as boolean,
    };
  },
  computed: {
    ...mapState(useClient, ['client', 'clientLoading']),
    ...mapState(useUser, ['hasPermission']),
    showUnregister(): boolean {
      if (!this.client) return false;
      return (
        this.client &&
        this.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) &&
        (this.client.status === ClientStatus.REGISTERED ||
          this.client.status === ClientStatus.REGISTRATION_IN_PROGRESS)
      );
    },

    showDelete(): boolean {
      if (
        !this.client ||
        this.client.status === ClientStatus.REGISTERED ||
        this.client.status === ClientStatus.REGISTRATION_IN_PROGRESS ||
        this.client.status === ClientStatus.ENABLING_IN_PROGRESS
      ) {
        return false;
      }

      return this.hasPermission(Permissions.DELETE_CLIENT);
    },

    showDisable(): boolean {
      return (
        !!this.client &&
        this.client.status === ClientStatus.REGISTERED &&
        this.hasPermission(Permissions.DISABLE_CLIENT)
      );
    },

    showEnable(): boolean {
      return (
        !!this.client &&
        this.client.status === ClientStatus.DISABLED &&
        this.hasPermission(Permissions.ENABLE_CLIENT)
      );
    },
  },
  created() {
    this.fetchData(this.id);
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useClient, ['fetchClient']),
    fetchData(id: string): void {
      this.fetchClient(id).catch((error) => {
        this.showError(error);
      });
    },
  },
});
</script>

<style lang="scss" scoped>
.title-action {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}
</style>
