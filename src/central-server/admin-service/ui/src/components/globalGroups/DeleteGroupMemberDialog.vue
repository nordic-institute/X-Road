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
    title="globalGroup.dialog.deleteMember.title"
    save-button-text="action.delete"
    cancel-button-text="action.cancel"
    :loading="deleting"
    :disable-save="!meta.valid"
    @cancel="$emit('cancel')"
    @save="deleteGroupMember"
  >
    <template #text>
      <i18n-t
        scope="global"
        keypath="globalGroup.dialog.deleteMember.confirmation"
      >
        <template #identifier>
          <b class="no-break">{{ identifier }}</b>
        </template>
      </i18n-t>
    </template>
    <template #content>
      <v-text-field
        v-bind="memberCode"
        data-test="verify-member-code"
        variant="outlined"
        autofocus
        :placeholder="$t('globalGroup.dialog.deleteMember.placeholder')"
        :label="$t('fields.memberCode')"
        :error-messages="errors.memberCode"
      >
      </v-text-field>
    </template>
  </xrd-simple-dialog>
</template>
<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { GroupMemberListView } from '@/openapi-types';
import { toIdentifier } from '@/util/helpers';
import { useForm } from 'vee-validate';
import { Event } from '@/ui-types';

export default defineComponent({
  props: {
    groupCode: {
      type: String,
      required: true,
    },
    groupMember: {
      type: Object as PropType<GroupMemberListView>,
      required: true,
    },
  },
  emits: [Event.Cancel, Event.Delete],
  setup(props) {
    const { meta, errors, defineComponentBinds } = useForm({
      validationSchema: {
        memberCode: `required|is:${props.groupMember.client_id.member_code}`,
      },
    });
    const memberCode = defineComponentBinds('memberCode');
    return { meta, errors, memberCode };
  },
  data() {
    return {
      deleting: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
    identifier(): string {
      return toIdentifier(this.groupMember.client_id);
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    deleteGroupMember() {
      this.deleting = true;
      this.globalGroupStore
        .deleteGroupMember(
          this.groupCode,
          this.groupMember.client_id.encoded_id || '',
        )
        .then(() => this.$emit(Event.Delete))
        .then(() =>
          this.showSuccess(
            this.$t('globalGroup.dialog.deleteMember.success', {
              identifier: this.identifier,
            }),
          ),
        )
        .catch((error) => this.showError(error))
        .finally(() => (this.deleting = false));
    },
  },
});
</script>
<style lang="scss" scoped></style>
