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
  <XrdSimpleDialog title="endpoints.addEndpoint" submittable :loading="adding" :disable-save="!meta.valid" @save="save" @cancel="cancel">
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-select
            v-model="methodMdl"
            v-bind="methodAttr"
            data-test="endpoint-method"
            class="xrd"
            autofocus
            :label="$t('endpoints.httpRequestMethod')"
            :items="methods"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="pathMdl"
            v-bind="pathAttr"
            data-test="endpoint-path"
            name="path"
            class="xrd"
            :label="$t('endpoints.path')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <div class="font-weight-regular mt-6 on-surface">
        <div>
          <div>{{ $t('endpoints.endpointHelp1') }}</div>
          <div>{{ $t('endpoints.endpointHelp2') }}</div>
          <div>{{ $t('endpoints.endpointHelp3') }}</div>
          <div>{{ $t('endpoints.endpointHelp4') }}</div>
        </div>
      </div>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { useForm } from 'vee-validate';
import {
  XrdSimpleDialog,
  XrdFormBlock,
  XrdFormBlockRow,
  DialogSaveHandler,
  useNotifications,
  veeDefaultFieldConfig,
} from '@niis/shared-ui';
import { mapActions } from 'pinia';
import { useServices } from '@/store/modules/services';
import { Endpoint } from '@/openapi-types';

export default defineComponent({
  components: {
    XrdSimpleDialog,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {
    serviceId: {
      type: String,
      required: true,
    },
    serviceCode: {
      type: String,
      required: true,
    },
  },
  emits: ['save', 'cancel'],
  setup() {
    const { addSuccessMessage } = useNotifications();
    const { meta, resetForm, values, defineField } = useForm({
      validationSchema: {
        method: 'required',
        path: 'required|xrdEndpoint',
      },
      initialValues: {
        method: '*',
        path: '/',
      },
    });
    const componentConfig = veeDefaultFieldConfig();
    const [methodMdl, methodAttr] = defineField('method', componentConfig);
    const [pathMdl, pathAttr] = defineField('path', componentConfig);
    return {
      meta,
      resetForm,
      values,
      methodMdl,
      methodAttr,
      pathMdl,
      pathAttr,
      addSuccessMessage,
    };
  },
  data() {
    return {
      adding: false,
      methods: [
        { title: this.$t('endpoints.all'), value: '*' },
        { title: 'GET', value: 'GET' },
        { title: 'POST', value: 'POST' },
        { title: 'PUT', value: 'PUT' },
        { title: 'PATCH', value: 'PATCH' },
        { title: 'DELETE', value: 'DELETE' },
        { title: 'HEAD', value: 'HEAD' },
        { title: 'OPTIONS', value: 'OPTIONS' },
        { title: 'TRACE', value: 'TRACE' },
      ],
    };
  },
  methods: {
    ...mapActions(useServices, ['addEndpoint']),
    save(evt: Event, handler: DialogSaveHandler): void {
      this.adding = true;
      this.addEndpoint(this.serviceId, {
        method: this.values.method as Endpoint.method,
        path: this.values.path,
        service_code: this.serviceCode,
      })
        .then(() => {
          this.addSuccessMessage('endpoints.saveNewEndpointSuccess');
          this.$emit('save');
          this.clear();
        })
        .catch((error) => handler.addError(error))
        .finally(() => (this.adding = false));
    },
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    clear(): void {
      this.resetForm();
    },
  },
});
</script>

<style lang="scss" scoped></style>
