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
    save-button-text="action.delete"
    title="members.member.details.deleteMember"
    :loading="loading"
    :disable-save="!meta.valid"
    @save="proceedDelete"
    @cancel="cancelDelete"
  >
    <template #text>
      <i18n-t scope="global" keypath="members.member.details.confirmDelete">
        <template #member>
          <b>{{ member.member_name }}</b>
        </template>
      </i18n-t>
    </template>
    <template #content>
      <v-text-field
        v-model="value"
        variant="outlined"
        :label="$t('members.member.details.enterCode')"
        autofocus
        data-test="member-code"
        :error-messages="errors"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Client } from '@/openapi-types';
import { useMember } from '@/store/modules/members';
import { toIdentifier } from '@/util/helpers';
import { useNotifications } from '@/store/modules/notifications';
import { useField } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';
import { RouteName } from '@/global';

export default defineComponent({
  props: {
    member: {
      type: Object as () => Client,
      required: true,
    },
  },
  emits: ['cancel'],
  setup(props) {
    const { value, errors, meta, resetField } = useField<string>('memberCode', {
      required: true,
      is: props.member.client_id.member_code,
    });
    return { resetField, value, errors, meta };
  },
  data() {
    return { loading: false, enteredCode: '' };
  },
  computed: {
    ...mapStores(useMember),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelDelete(): void {
      this.enteredCode = '';
      this.$emit('cancel');
    },
    proceedDelete(): void {
      this.loading = true;
      this.memberStore
        .deleteById(toIdentifier(this.member.client_id))
        .then(() => {
          this.showSuccess(
            this.$t('members.member.details.memberDeleted'),
            true,
          );
          this.$router.replace({ name: RouteName.Members });
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
