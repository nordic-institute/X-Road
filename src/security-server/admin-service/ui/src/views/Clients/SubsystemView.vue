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
  <XrdTitledView :title="title">
    <template #header-buttons>
      <DisableClientButton v-if="showDisable" class="ml-5" :id="id" @done="fetchData" />
      <EnableClientButton v-if="showEnable" class="ml-5" :id="id" @done="fetchData" />
      <DeleteClientButton v-if="showDelete" class="ml-5" :id="id" />
      <UnregisterClientButton v-if="showUnregister" class="ml-5" :id="id" @done="fetchData" />
      <RenameClientButton v-if="showRename" class="pl-5" :id="id" :subsystem-name="subSystemName" @done="fetchData" />
    </template>

    <router-view />
  </XrdTitledView>
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
import { XrdTitledView } from '@niis/shared-ui';
import RenameClientButton from '@/components/client/RenameClientButton.vue';

export default defineComponent({
  components: {
    RenameClientButton,
    XrdTitledView,
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
    title(): string {
      if (this.clientLoading) {
        return this.$t('noData.loading');
      } else if (this.client) {
        return `${this.client.subsystem_name || this.client.subsystem_code} (${this.$t('general.subsystem')})`;
      }
      return '';
    },
    subSystemName(): string {
      return this.client ? this.client.subsystem_name : '';
    },
    showUnregister(): boolean {
      if (!this.client) return false;
      return (
        this.client &&
        this.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) && [
          ClientStatus.REGISTERED,
          ClientStatus.REGISTRATION_IN_PROGRESS,
          ClientStatus.DISABLED,
        ].includes(this.client.status)
      );
    },
    showRename(): boolean {
      if (!this.client) return false;
      return (
        this.client && this.client.subsystem_code &&
        (ClientStatus.SAVED == this.client.status ? true : !this.client.rename_in_progress) &&
        this.hasPermission(Permissions.RENAME_SUBSYSTEM)
      );
    },

    showDelete(): boolean {
      if (
        !this.client ||
        this.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) && [
          ClientStatus.REGISTERED,
          ClientStatus.REGISTRATION_IN_PROGRESS,
          ClientStatus.ENABLING_IN_PROGRESS,
          ClientStatus.DISABLING_IN_PROGRESS,
          ClientStatus.DISABLED,
        ].includes(this.client.status)
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

</style>
