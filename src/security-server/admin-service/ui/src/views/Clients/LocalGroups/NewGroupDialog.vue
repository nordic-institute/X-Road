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
    v-if="dialog"
    title="localGroup.addLocalGroup"
    :disable-save="!meta.valid"
    width="620"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="code"
          v-bind="codeAttrs"
          variant="outlined"
          :label="$t('localGroup.code')"
          autofocus
          data-test="add-local-group-code-input"
        ></v-text-field>
      </div>

      <div class="dlg-input-width">
        <v-text-field
          v-model="description"
          v-bind="descriptionAttrs"
          :label="$t('localGroup.description')"
          variant="outlined"
          data-test="add-local-group-description-input"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { PublicPathState, useForm } from "vee-validate";

export default defineComponent({
  setup() {
    const { meta, defineField, resetForm } = useForm({
      validationSchema: {
        code: 'required|max:255',
        description: 'required|max:255',
      },
      initialValues: {
        code: '',
        description: '',
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const [code, codeAttrs] = defineField('code', componentConfig);
    const [description, descriptionAttrs] = defineField('description', componentConfig);
    return { meta, code, codeAttrs, description, descriptionAttrs, resetForm };
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['cancel', 'group-added'],
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancel(): void {
      this.resetForm();
      this.$emit('cancel');
    },
    save(): void {
      api
        .post(`/clients/${encodePathParameter(this.id)}/local-groups`, {
          code: this.code,
          description: this.description,
        })
        .then(() => {
          this.showSuccess(this.$t('localGroup.localGroupAdded'));
          this.$emit('group-added');
        })
        .catch((error) => {
          this.showError(error);
          this.$emit('cancel');
        })
        .finally(() => this.resetForm());
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
