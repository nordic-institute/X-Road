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
    data-test="system-settings-member-class-delete-confirm-dialog"
    title="action.confirm"
    text="systemSettings.deleteMemberClass"
    focus-on-accept
    :loading="deleting"
    @cancel="cancelDelete"
    @accept="acceptDelete"
  />
</template>
<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { mapStores } from 'pinia';

import { useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

import { MemberClass } from '@/openapi-types';
import { useMemberClass } from '@/store/modules/member-class';

export default defineComponent({
  components: { XrdConfirmDialog },
  props: {
    memberClass: {
      type: Object as PropType<MemberClass>,
      required: true,
    },
  },
  emits: ['cancel', 'delete'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      deleting: false,
    };
  },
  computed: {
    ...mapStores(useMemberClass),
  },
  methods: {
    cancelDelete() {
      this.$emit('cancel');
    },
    async acceptDelete() {
      this.deleting = true;
      try {
        await this.memberClassStore.delete(this.memberClass);
        this.addSuccessMessage('systemSettings.memberClassDeleted');
        this.$emit('delete');
      } catch (error: unknown) {
        this.addError(error);
        this.$emit('cancel');
      }
      this.deleting = false;
    },
  },
});
</script>
