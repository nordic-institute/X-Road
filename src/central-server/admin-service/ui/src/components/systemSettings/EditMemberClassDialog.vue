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
    :title="title"
    data-test="system-settings-member-class-edit-dialog"
    :scrollable="false"
    :show-close="true"
    :loading="saving"
    save-button-text="action.save"
    :disable-save="!meta.valid || !meta.dirty"
    @save="onSaveMemberClass"
    @cancel="onCancel"
  >
    <template #content>
      <v-text-field
        v-bind="classCode"
        data-test="system-settings-member-class-code-edit-field"
        :disabled="!modeAdd"
        :label="$t('systemSettings.code')"
        :autofocus="modeAdd"
        variant="outlined"
        class="dlg-row-input"
        name="code"
        :error-messages="errors.code"
      />
      <v-text-field
        v-bind="classDescription"
        data-test="system-settings-member-class-description-edit-field"
        :label="$t('systemSettings.description')"
        :autofocus="!modeAdd"
        variant="outlined"
        class="dlg-row-input"
        name="memberClass"
        :error-messages="errors.description"
      />
    </template>
  </xrd-simple-dialog>
</template>
<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { useForm } from 'vee-validate';
import { Event } from '@/ui-types';
import { ErrorInfo, MemberClass } from '@/openapi-types';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';
import { mapStores } from 'pinia';
import { useMemberClass } from '@/store/modules/member-class';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  props: {
    memberClass: {
      type: Object as PropType<MemberClass>,
      default: undefined,
    },
  },
  emits: [Event.Cancel, Event.Edit],
  setup(props) {
    const { meta, values, errors, setFieldError, defineComponentBinds } =
      useForm({
        validationSchema: {
          code: 'required|min:1|max:255',
          description: 'required|min:1',
        },
        initialValues: {
          code: props.memberClass?.code || '',
          description: props.memberClass?.description || '',
        },
      });
    const classCode = defineComponentBinds('code');
    const classDescription = defineComponentBinds('description');
    return { meta, values, errors, setFieldError, classCode, classDescription };
  },
  data() {
    return {
      saving: false,
    };
  },
  computed: {
    ...mapStores(useMemberClass, useNotifications),
    modeAdd(): boolean {
      return !this.memberClass;
    },
    title(): string {
      return this.modeAdd
        ? 'systemSettings.addMemberClassTitle'
        : 'systemSettings.editMemberClassTitle';
    },
  },
  methods: {
    onCancel() {
      this.$emit(Event.Cancel);
    },
    async onSaveMemberClass() {
      this.saving = true;
      try {
        await (this.modeAdd
          ? this.memberClassStore.add({
              code: this.values.code,
              description: this.values.description,
            })
          : this.memberClassStore.update(
              this.values.code,
              this.values.description,
            ));
        this.notificationsStore.showSuccess(
          this.$t('systemSettings.memberClassSaved'),
        );
        this.$emit(Event.Edit);
      } catch (error: unknown) {
        const errorInfo: ErrorInfo = getErrorInfo(error as AxiosError);
        if (isFieldError(errorInfo)) {
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors) {
            this.setFieldError(
              'code',
              getTranslatedFieldErrors('memberClassDto.code', fieldErrors),
            );
            this.setFieldError(
              'description',
              getTranslatedFieldErrors(
                'memberClassDto.description',
                fieldErrors,
              ),
            );
            return;
          }
        } else {
          this.notificationsStore.showError(error);
        }
      } finally {
        this.saving = false;
      }
    },
  },
});
</script>
