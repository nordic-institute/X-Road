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
    title="members.member.subsystems.deleteSubsystem"
    accept-button-text="action.delete"
    focus-on-accept
    @cancel="cancel"
    @accept="deleteSubsystem"
  >
    <template #text>
      <span
        data-test="delete-subsystem"
        class="font-weight-regular body-regular"
      >
        <i18n-t
          scope="global"
          keypath="members.member.subsystems.areYouSureDelete"
        >
          <template #subsystemCode>
            <span class="font-weight-bold">{{ subsystemCode }}</span>
          </template>
          <template #memberId>
            <span class="font-weight-bold">{{ shortMemberId }}</span>
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
import { useMember } from '@/store/modules/members';
import { useSystem } from '@/store/modules/system';
import { useSubsystem } from '@/store/modules/subsystems';
import { ClientId } from '@/openapi-types';
import { toIdentifier, toShortMemberId } from '@/util/helpers';
import { useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdConfirmDialog },
  props: {
    subsystemCode: {
      type: String,
      required: true,
    },
    member: {
      type: Object as PropType<{ client_id: ClientId }>,
      required: true,
    },
  },
  emits: ['delete', 'cancel'],
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
    shortMemberId() {
      return toShortMemberId(this.member.client_id);
    },
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    deleteSubsystem(): void {
      this.loading = true;
      this.subsystemStore
        .deleteById(
          toIdentifier(this.member.client_id) + ':' + this.subsystemCode,
        )
        .then(() => {
          this.addSuccessMessage(
            'members.member.subsystems.subsystemSuccessfullyDeleted',
            {
              subsystemCode: this.subsystemCode,
            },
          );
          this.$emit('delete');
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
