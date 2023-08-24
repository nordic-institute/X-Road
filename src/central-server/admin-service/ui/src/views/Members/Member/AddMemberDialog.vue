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
    :loading="loading"
    cancel-button-text="action.cancel"
    title="members.addMember"
    z-index="1999"
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <v-text-field
        v-model="memberName"
        :label="$t('global.memberName')"
        variant="outlined"
        autofocus
        data-test="add-member-name-input"
      />
      <v-select
        v-model="memberClass"
        :items="memberClasses"
        :label="$t('global.memberClass')"
        item-title="code"
        item-value="code"
        variant="outlined"
        data-test="add-member-class-input"
        z-index="2410"
      />
      <v-text-field
        v-bind="memberCode"
        :label="$t('global.memberCode')"
        :error-messages="errors.memberCode"
        variant="outlined"
        data-test="add-member-code-input"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { ErrorInfo, MemberClass } from '@/openapi-types';
import { useClient } from '@/store/modules/clients';
import { useMember } from '@/store/modules/members';
import { useSystem } from '@/store/modules/system';
import { useNotifications } from '@/store/modules/notifications';
import { useMemberClass } from '@/store/modules/member-class';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';
import { useForm } from 'vee-validate';

export default defineComponent({
  emits: ['save', 'cancel'],
  setup() {
    const {
      defineComponentBinds,
      values,
      meta,
      errors,
      setFieldError,
      resetForm,
    } = useForm({ validationSchema: { memberCode: 'required' } });
    const memberCode = defineComponentBinds('memberCode');
    return {
      values,
      meta,
      errors,
      setFieldError,
      memberCode,
      resetForm,
    };
  },
  data() {
    return {
      loading: false,
      memberName: '',
      memberClass: '',
    };
  },
  computed: {
    ...mapStores(useClient, useMember, useMemberClass),
    ...mapState(useSystem, ['getSystemStatus']),
    memberClasses(): MemberClass[] {
      return this.memberClassStore.memberClasses;
    },
    formReady(): boolean {
      return !!(this.memberName && this.memberClass && this.meta.valid);
    },
  },
  created() {
    this.memberClassStore.fetchAll();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
      this.clearForm();
    },
    clearForm(): void {
      this.memberName = '';
      this.memberClass = '';
      this.resetForm();
    },
    add(): void {
      this.loading = true;
      this.memberStore
        .add({
          member_name: this.memberName,
          member_id: {
            member_class: this.memberClass,
            member_code: this.values.memberCode,
          },
        })
        .then(() => {
          this.showSuccess(
            this.$t('members.memberSuccessfullyAdded', {
              memberName: this.memberName,
            }),
          );
          this.$emit('save');
          this.clearForm();
        })
        .catch((error) => {
          const errorInfo: ErrorInfo = getErrorInfo(error as AxiosError);
          if (isFieldError(errorInfo)) {
            let fieldErrors = errorInfo.error?.validation_errors;
            if (fieldErrors) {
              this.setFieldError(
                'memberCode',
                getTranslatedFieldErrors(
                  'memberAddDto.memberId.memberCode',
                  fieldErrors,
                ),
              );
            }
          } else {
            this.showError(error);
          }
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>
