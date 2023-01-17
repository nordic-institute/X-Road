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
      data-test="make-owner-button"
      outlined
      @click="confirmMakeOwner = true"
      >{{ $t('client.action.makeOwner.button') }}</xrd-button
    >

    <!-- Confirm dialog for make owner -->
    <xrd-simple-dialog
      :dialog="confirmMakeOwner"
      :loading="makeOwnerLoading"
      save-button-text="client.action.makeOwner.button"
      title="client.action.makeOwner.confirmTitle"
      @cancel="confirmMakeOwner = false"
      @save="makeOwner()"
    >
      <div slot="content">
        {{ $t('client.action.makeOwner.confirmText1') }}
        <br />
        <br />
        <b>{{ id }}</b>
        <br />
        <br />
        {{ $t('client.action.makeOwner.confirmText2') }}
      </div>
    </xrd-simple-dialog>
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
      confirmMakeOwner: false as boolean,
      makeOwnerLoading: false as boolean,
    };
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    makeOwner(): void {
      this.makeOwnerLoading = true;

      api
        .put(`/clients/${encodePathParameter(this.id)}/make-owner`, {})
        .then(
          () => {
            this.showSuccess(this.$t('client.action.makeOwner.success'));
          },
          (error) => {
            this.showError(error);
          },
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmMakeOwner = false;
          this.makeOwnerLoading = false;
        });
    },
  },
});
</script>
