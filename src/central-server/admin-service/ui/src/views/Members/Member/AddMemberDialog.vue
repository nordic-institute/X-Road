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
    :dialog="showDialog"
    :loading="loading"
    cancel-button-text="action.cancel"
    title="members.addMember"
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="memberName"
          :label="$t('global.memberName')"
          outlined
          autofocus
          data-test="add-member-name-input"
        ></v-text-field>
      </div>

      <v-select
        v-model="memberClass"
        :items="memberClasses"
        :label="$t('global.memberClass')"
        item-text="code"
        outlined
        data-test="add-member-class-input"
      ></v-select>

      <div class="dlg-input-width">
        <ValidationProvider
          ref="memberCodeVP"
          v-slot="{ errors }"
          rules="required"
          name="memberCode"
          class="validation-provider"
        >
          <v-text-field
            v-model="memberCode"
            :label="$t('global.memberCode')"
            outlined
            data-test="add-member-code-input"
            :error-messages="errors"
          ></v-text-field>
        </ValidationProvider>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue, {VueConstructor} from 'vue';
import {mapActions, mapState, mapStores} from 'pinia';
import {ErrorInfo, MemberClass} from '@/openapi-types';
import {clientStore} from '@/store/modules/clients';
import {memberStore} from '@/store/modules/members';
import {systemStore} from '@/store/modules/system';
import {notificationsStore} from '@/store/modules/notifications';
import {useMemberClassStore} from '@/store/modules/member-class';
import {getErrorInfo, getTranslatedFieldErrors, isFieldError,} from '@/util/helpers';
import {AxiosError} from 'axios';
import {ValidationProvider} from 'vee-validate';

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        memberCodeVP: InstanceType<typeof ValidationProvider>;
      };
    }
  >
).extend({
  name: 'AddMemberDialog',
  components: { ValidationProvider },
  props: {
    showDialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      loading: false,
      memberName: '',
      memberClass: '',
      memberCode: '',
    };
  },
  computed: {
    ...mapStores(clientStore, memberStore, useMemberClassStore),
    ...mapState(systemStore, ['getSystemStatus']),
    memberClasses(): MemberClass[] {
      return this.memberClassStore.memberClasses;
    },
    formReady(): boolean {
      return !!(this.memberName && this.memberClass && this.memberCode);
    },
  },
  created() {
    this.memberClassStore.fetchAll();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
      this.clearForm();
    },
    clearForm(): void {
      this.memberName = '';
      this.memberClass = '';
      this.memberCode = '';
    },
    add(): void {
      this.loading = true;
      const instanceId: string = this.getSystemStatus?.initialization_status
        ?.instance_identifier as string;
      this.memberStore
        .add({
          member_name: this.memberName,
          member_id: {
            member_class: this.memberClass,
            member_code: this.memberCode,
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
            if (fieldErrors && this.$refs?.memberCodeVP) {
              this.$refs.memberCodeVP.setErrors(
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
