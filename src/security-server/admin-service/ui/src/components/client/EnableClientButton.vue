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
    <XrdBtn data-test="enable-client-button" variant="outlined" text="action.enable" @click="confirmEnableClient = true" />

    <!-- Confirm dialog for enable client -->
    <XrdConfirmDialog
      v-if="confirmEnableClient"
      title="client.action.enable.confirmTitle"
      text="client.action.enable.confirmText"
      :loading="isLoading"
      @cancel="confirmEnableClient = false"
      @accept="enableClient()"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { XrdBtn, useNotifications, XrdConfirmDialog } from '@niis/shared-ui';
import { useClient } from '@/store/modules/client';

export default defineComponent({
  components: { XrdBtn, XrdConfirmDialog },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  emits: ['done'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const { enableClient: apiEnableClient } = useClient();
    return { addError, addSuccessMessage, apiEnableClient };
  },
  data() {
    return {
      confirmEnableClient: false as boolean,
      isLoading: false as boolean,
    };
  },
  methods: {
    enableClient(): void {
      this.isLoading = true;
      this.apiEnableClient(this.id)
        .then(() => this.addSuccessMessage('client.action.enable.success'))
        .catch((error) => this.addError(error))
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmEnableClient = false;
          this.isLoading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
