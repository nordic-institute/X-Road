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
  <XrdWizardStep>
    <XrdFormBlock>
      <XrdFormBlockRow v-for="item in csrForm" :key="item.id">
        <v-text-field
          v-bind="componentRef(item.id)"
          class="xrd"
          :data-test="`dynamic-csr-input_${item.id}`"
          :name="item.id"
          :disabled="item.read_only"
          :autofocus="autofocusField === item.id"
          :label="$t(`certificateProfile.${item.label_key}`)"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow
        v-if="acmeCapable"
        description="csr.orderAcmeCertificate"
      >
        <v-checkbox
          v-model="acmeOrders"
          class="xrd"
          data-test="order-acme-certificate-checkbox"
          persistent-hint
          :disabled="externalAccountBindingRequiredButMissing"
          :hint="externalAccountBindingRequiredButMissingHint"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="outlined"
        text="action.cancel"
        @click="cancel"
      />
      <v-spacer />

      <XrdBtn
        data-test="previous-button"
        variant="outlined"
        class="mr-2"
        text="action.previous"
        @click="previous"
      />
      <XrdBtn
        data-test="save-button"
        :disabled="!meta.valid"
        :text="saveButtonText"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent, Ref } from 'vue';
import { useCsr } from '@/store/modules/certificateSignRequest';
import { PublicPathState, useForm } from 'vee-validate';
import { CsrSubjectFieldDescription } from '@/openapi-types';
import { mapState, mapWritableState } from 'pinia';
import {
  XrdWizardStep,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdBtn,
} from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdWizardStep,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdBtn,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.done',
    },
    showGenerateButton: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['cancel', 'previous', 'done'],
  setup() {
    const {
      csrForm,
      setCsrForm,
    }: {
      csrForm: CsrSubjectFieldDescription[];
      setCsrForm: (form: CsrSubjectFieldDescription[]) => void;
    } = useCsr();
    const validationSchema: Record<string, string> = csrForm.reduce(
      (acc, cur) => ({ ...acc, [cur.id]: cur.required && 'required' }),
      {},
    );
    const initialValues: Record<string, string> = csrForm.reduce(
      (acc, cur) => ({ ...acc, [cur.id]: cur.default_value }),
      {},
    );
    const { meta, values, defineComponentBinds } = useForm({
      validationSchema,
      initialValues,
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const componentBinds: Record<string, Ref> = csrForm.reduce(
      (acc, cur) => ({
        ...acc,
        [cur.id]: defineComponentBinds(cur.id, componentConfig),
      }),
      {},
    );
    return { meta, values, ...componentBinds, csrForm, setCsrForm };
  },
  data() {
    return { acmeOrders: false };
  },
  computed: {
    ...mapState(useCsr, [
      'acmeCapable',
      'eabRequired',
      'acmeEabCredentialsStatus',
    ]),
    ...mapWritableState(useCsr, ['acmeOrder']),
    externalAccountBindingRequiredButMissing(): boolean {
      return (
        !!this.eabRequired &&
        !this.acmeEabCredentialsStatus?.has_acme_external_account_credentials
      );
    },
    externalAccountBindingRequiredButMissingHint(): string | undefined {
      return this.externalAccountBindingRequiredButMissing
        ? this.$t('csr.eabCredRequired')
        : undefined;
    },
    autofocusField(): string | undefined {
      return this.csrForm
        .filter((field) => !field.read_only)
        .map((field) => field.id)
        .shift();
    },
  },
  created() {
    this.acmeOrder = false;
  },
  methods: {
    componentRef(id: string): Ref {
      return (this as never)[id];
    },
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.setCsrForm(
        this.csrForm.map((field: CsrSubjectFieldDescription) => ({
          ...field,
          default_value: this.values[field.id],
        })),
      );
      this.$emit('done');
    },
  },
});
</script>

<style lang="scss" scoped></style>
