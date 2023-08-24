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
    :loading="loading"
    title="members.member.details.editMemberName"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    :disable-save="!meta.valid || !meta.dirty"
    @cancel="cancelEdit"
    @save="saveNewMemberName"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-bind="memberName"
          variant="outlined"
          data-test="edit-member-name"
          :error-messages="errors.memberName"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { mapActions, mapStores } from 'pinia';
import { useMember } from '@/store/modules/members';
import { Client } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { toIdentifier } from '@/util/helpers';
import { defineComponent, PropType } from 'vue';
import { useForm } from 'vee-validate';

export default defineComponent({
  props: {
    member: {
      type: Object as PropType<Client>,
      required: true,
    },
  },
  emits: ['cancel', 'name-changed'],
  setup(props) {
    const { defineComponentBinds, values, errors, meta, setFieldError } =
      useForm({
        validationSchema: { memberName: 'required' },
        initialValues: { memberName: props.member.member_name },
      });
    const memberName = defineComponentBinds('memberName');
    return { values, errors, setFieldError, meta, memberName };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useMember),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelEdit(): void {
      this.$emit('cancel');
    },
    saveNewMemberName(): void {
      this.loading = true;
      this.memberStore
        .editMemberName(toIdentifier(this.member.client_id), {
          member_name: this.values.memberName,
        })
        .then(() => {
          this.showSuccess(this.$t('members.member.details.memberNameSaved'));
          this.$emit('name-changed');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
