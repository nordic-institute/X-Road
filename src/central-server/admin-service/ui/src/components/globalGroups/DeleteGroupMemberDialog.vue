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
  <ValidationObserver v-slot="{ invalid }">
    <xrd-simple-dialog
      v-if="dialog"
      title="globalGroup.dialog.deleteMember.title"
      save-button-text="action.delete"
      cancel-button-text="action.cancel"
      :dialog="dialog"
      :loading="deleting"
      :disable-save="invalid"
      @cancel="close"
      @save="deleteGroupMember"
    >
      <template #content>
        <p>
          <i18n path="globalGroup.dialog.deleteMember.confirmation">
            <template #identifier>
              <b class="no-break">{{ identifier }}</b>
            </template>
          </i18n>
        </p>
        <ValidationProvider
          v-slot="{ errors }"
          :rules="`required|is:${member.code}`"
          name="memberCode"
        >
          <v-text-field
            v-model="memberCode"
            data-test="verify-server-code"
            outlined
            autofocus
            :placeholder="$t('globalGroup.dialog.deleteMember.placeholder')"
            :label="$t('fields.memberCode')"
            :error-messages="errors"
          >
          </v-text-field>
        </ValidationProvider>
      </template>
    </xrd-simple-dialog>
  </ValidationObserver>
</template>
<script lang="ts">
import Vue from 'vue';
import { mapActions, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { GroupMember } from '@/openapi-types';
import { ValidationObserver, ValidationProvider } from 'vee-validate';

export default Vue.extend({
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    groupId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      memberCode: '',
      deleting: false,
      member: null as GroupMember | null,
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore),
    memberName(): string | undefined {
      return this.member?.name;
    },
    dialog(): boolean {
      return this.member != null;
    },
    identifier(): string {
      if (this.member == null) {
        return '';
      }
      let parts = [this.member.instance, this.member.class, this.member.code];
      if (this.member.subsystem) {
        parts.push(this.member.subsystem);
      }
      return parts.join(':');
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    open(member: GroupMember) {
      this.member = member;
    },
    close() {
      this.member = null;
      this.memberCode = '';
    },
    deleteGroupMember() {
      if (this.member == null) {
        return;
      }
      this.deleting = true;
      this.globalGroupStore
        .deleteGroupMember(this.groupId, this.member.id)
        .then(() => this.$emit('deleted'))
        .then(() =>
          this.showSuccess(
            this.$t('globalGroup.dialog.deleteMember.success', {
              identifier: this.identifier,
            }),
          ),
        )
        .then(() => this.close())
        .catch((error) => this.showError(error))
        .finally(() => (this.deleting = false));
    },
  },
});
</script>
<style lang="scss" scoped></style>