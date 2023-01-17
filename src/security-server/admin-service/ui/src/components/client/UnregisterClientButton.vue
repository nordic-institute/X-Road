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
    <xrd-button
      data-test="unregister-client-button"
      outlined
      @click="confirmUnregisterClient = true"
      >{{ $t('action.unregister') }}</xrd-button
    >

    <!-- Confirm dialog for unregister client -->
    <xrd-confirm-dialog
      :dialog="confirmUnregisterClient"
      :loading="unregisterLoading"
      title="client.action.unregister.confirmTitle"
      text="client.action.unregister.confirmText"
      @cancel="confirmUnregisterClient = false"
      @accept="unregisterClient()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default Vue.extend({
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

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    unregisterClient(): void {
      this.unregisterLoading = true;
      api
        .put(`/clients/${encodePathParameter(this.id)}/unregister`, {})
        .then(
          () => {
            this.showSuccess(this.$t('client.action.unregister.success'));
          },
          (error) => {
            this.showError(error);
          },
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmUnregisterClient = false;
          this.unregisterLoading = false;
        });
    },
  },
});
</script>
