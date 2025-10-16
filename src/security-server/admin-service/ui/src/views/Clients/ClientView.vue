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
  <XrdView translated-title :breadcrumbs="breadcrumbs">
    <template #title>
      <div class="title-view font-weight-bold">
        {{ client?.member_name }}
        <span v-if="client?.owner" class="opacity-60 font-weight-regular">
          ({{ $t(client?.owner ? 'client.owner' : 'client.member') }})
        </span>
      </div>
    </template>
    <template #append-header>
      <v-spacer />
      <MakeOwnerButton v-if="showMakeOwner" :id="id" @done="fetchData" />
      <DeleteClientButton v-if="showDelete" :id="id" class="ml-2" />
      <UnregisterClientButton
        v-if="showUnregister"
        :id="id"
        class="ml-2"
        @done="fetchData"
      />
    </template>
    <template #tabs>
      <ClientTabs :id="id" />
    </template>
    <router-view />
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Permissions, RouteName } from '@/global';
import DeleteClientButton from '@/components/client/DeleteClientButton.vue';
import UnregisterClientButton from '@/components/client/UnregisterClientButton.vue';
import MakeOwnerButton from '@/components/client/MakeOwnerButton.vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import { XrdView, useNotifications, Breadcrumb } from '@niis/shared-ui';
import ClientTabs from '@/views/Clients/ClientTabs.vue';
import { clientTitle } from '@/util/ClientUtil';

export default defineComponent({
  components: {
    ClientTabs,
    UnregisterClientButton,
    DeleteClientButton,
    MakeOwnerButton,
    XrdView,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  computed: {
    ...mapState(useClient, ['client', 'clientLoading']),
    ...mapState(useUser, ['hasPermission']),
    title(): string {
      return clientTitle(this.client, this.clientLoading);
    },
    showMakeOwner(): boolean {
      return (
        !!this.client &&
        this.hasPermission(Permissions.SEND_OWNER_CHANGE_REQ) &&
        this.client.status === 'REGISTERED' &&
        !this.client.owner
      );
    },
    showUnregister(): boolean {
      return (
        !!this.client &&
        this.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) &&
        (this.client.status === 'REGISTERED' ||
          this.client.status === 'REGISTRATION_IN_PROGRESS')
      );
    },
    showDelete(): boolean {
      if (
        !this.client ||
        this.client.status === 'REGISTERED' ||
        this.client.status === 'REGISTRATION_IN_PROGRESS'
      ) {
        return false;
      }

      return this.hasPermission(Permissions.DELETE_CLIENT);
    },
    breadcrumbs() {
      return [
        {
          title: 'tab.main.clients',
          to: {
            name: RouteName.Clients,
          },
        },
      ] as Breadcrumb[];
    },
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.fetchData(this.id);
      },
    },
  },
  methods: {
    ...mapActions(useClient, ['fetchClient']),
    fetchData(id: string): void {
      this.fetchClient(id).catch((error) => this.addError(error, { navigate: true }));
    },
  },
});
</script>

<style lang="scss" scoped></style>
