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
  <XrdSimpleDialog
    :disable-save="!meta.valid || !meta.dirty"
    :loading="loading"
    cancel-button-text="action.cancel"
    save-button-text="action.save"
    title="systemParameters.configurableProperties.editDialog.title"
    data-test="edit-configurable-property-dialog"
    submittable
    @cancel="close"
    @save="save"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="propertyValueMdl"
            v-bind="propertyValueRef"
            data-test="configurable-property-value-field"
            class="xrd"
            autofocus
            :label="property.property_name"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { useForm } from 'vee-validate';
import { mapActions } from 'pinia';
import { XrdFormBlock, XrdFormBlockRow, useNotifications, XrdSimpleDialog } from '@niis/shared-ui';
import { SecurityServerConfigurableProperty } from '@/openapi-types';
import { useSystem } from '@/store/modules/system';

export default defineComponent({
  components: {
    XrdSimpleDialog,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {
    property: {
      type: Object as PropType<SecurityServerConfigurableProperty>,
      required: true,
    },
  },
  emits: ['cancel', 'saved'],
  // saved emits the scope string so the parent can show a targeted restart warning
  setup(props) {
    const { addError, addSuccessMessage } = useNotifications();

    const { values, meta, resetForm, defineField } = useForm({
      validationSchema: {
        propertyValue: 'required|max:4096',
      },
      initialValues: {
        propertyValue: props.property.current_value ?? props.property.default_value ?? '',
      },
    });

    const [propertyValueMdl, propertyValueRef] = defineField('propertyValue');

    return {
      values,
      meta,
      resetForm,
      propertyValueMdl,
      propertyValueRef,
      addError,
      addSuccessMessage,
    };
  },
  data() {
    return {
      loading: false,
    };
  },
  methods: {
    ...mapActions(useSystem, ['updateConfigurableProperty']),
    close(): void {
      this.resetForm();
      this.$emit('cancel');
    },
    save() {
      this.loading = true;
      return this.updateConfigurableProperty({
        property_name: this.property.property_name!,
        property_value: this.values.propertyValue,
        scope: this.property.scope,
      })
        .then(() => {
          this.addSuccessMessage('systemParameters.configurableProperties.updateSuccess');
          this.$emit('saved', this.property.scope || 'common');
        })
        .catch((error) => {
          this.addError(error);
          this.close();
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
