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
    <XrdBtn
      data-test="disable-client-button"
      variant="outlined"
      text="action.disable"
      :disabled="disabled"
      @click="confirmDisableClient = true"
    />

    <!-- Confirm dialog for disable client -->
    <XrdConfirmDialog
      v-if="confirmDisableClient"
      title="client.action.disable.confirmTitle"
      text="client.action.disable.confirmText"
      :loading="isLoading"
      @cancel="confirmDisableClient = false"
      @accept="disableClient()"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { XrdBtn, useNotifications } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdBtn },
  props: {
    id: {
      type: String,
      required: true,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['done'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      confirmDisableClient: false as boolean,
      isLoading: false as boolean,
    };
  },
  methods: {
    disableClient(): void {
      this.isLoading = true;
      api
        .put(`/clients/${encodePathParameter(this.id)}/disable`, {})
        .then(
          () => {
            this.addSuccessMessage('client.action.disable.success');
          },
          (error) => this.addError(error),
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmDisableClient = false;
          this.isLoading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
