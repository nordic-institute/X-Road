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
  <xrd-simple-dialog
    :disable-save="!formReady"
    :dialog="dialog"
    :loading="loading"
    cancel-button-text="action.cancel"
    title="globalResources.addGlobalGroup"
    @cancel="cancel"
    @save="save"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="code"
          outlined
          :label="$t('globalResources.code')"
          autofocus
          data-test="add-global-group-code-input"
        ></v-text-field>
      </div>

      <div class="dlg-input-width">
        <v-text-field
          v-model="description"
          :label="$t('globalResources.description')"
          outlined
          data-test="add-global-group-description-input"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  name: 'AddGroupDialog',
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      loading: false,
      code: '',
      description: '',
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore, notificationsStore),
    formReady(): boolean {
      return !!(
        this.code &&
        this.code.length > 0 &&
        this.description.length > 0
      );
    },
  },

  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.loading = true;
      this.globalGroupStore
        .add({ code: this.code, description: this.description })
        .then(() => {
          this.notificationsStoreStore.showSuccess(
            this.$t('globalResources.globalGroupSuccessfullyAdded'),
          );
          this.clearForm();
          this.$emit('group-added');
        })
        .catch((error) => {
          this.notificationsStoreStore.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    clearForm(): void {
      this.code = '';
      this.description = '';
    },
  },
});
</script>
