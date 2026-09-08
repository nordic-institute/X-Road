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
  <XrdConfirmDialog
    :loading="loading"
    title="members.member.subsystems.deleteSubsystem"
    focus-on-accept
    @cancel="cancel"
    @accept="unregisterSubsystem"
  >
    <template #text>
      <span class="font-weight-regular body-regular">
        <i18n-t scope="global" keypath="members.member.subsystems.areYouSureUnregister">
          <template #subsystemCode>
            <span class="font-weight-bold">{{ subsystemCode }}</span>
          </template>
          <template #serverCode>
            <span class="font-weight-bold">{{ serverCode }}</span>
          </template>
        </i18n-t>
      </span>
    </template>
  </XrdConfirmDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapState, mapStores } from 'pinia';
import { useClient } from '@/store/modules/clients';
import { useSystem } from '@/store/modules/system';
import { useSubsystem } from '@/store/modules/subsystems';
import { useMember } from '@/store/modules/members';
import { toIdentifier } from '@/util/helpers';
import { ClientId } from '@/openapi-types';
import { useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

export default defineComponent({
  name: 'UnregisterMemberSubsystemDialog',
  components: { XrdConfirmDialog },
  props: {
    subsystemCode: {
      type: String,
      required: true,
    },
    serverCode: {
      type: String,
      required: true,
    },
    serverId: {
      type: String,
      required: true,
    },
    member: {
      type: Object as PropType<{ client_id: ClientId }>,
      required: true,
    },
  },
  emits: ['cancel', 'unregistered-subsystem'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useClient, useMember, useSubsystem),
    ...mapState(useSystem, ['getSystemStatus']),
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    unregisterSubsystem(): void {
      this.loading = true;
      this.subsystemStore
        .unregisterById(toIdentifier(this.member.client_id) + ':' + this.subsystemCode, this.serverId)
        .then(() => {
          this.addSuccessMessage('members.member.subsystems.subsystemSuccessfullyUnregistered', {
            subsystemCode: this.subsystemCode,
            serverCode: this.serverCode,
          });
          this.$emit('unregistered-subsystem');
        })
        .catch((error) => {
          this.addError(error);
          this.$emit('cancel');
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>
