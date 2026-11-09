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
    title="members.member.details.editDid.title"
    data-test="member-did-edit-dialog"
    save-button-text="action.save"
    submittable
    :scrollable="false"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="save"
    @cancel="close"
  >
    <template #content="{ dialogHandler }">
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="memberDidInput"
            v-bind="memberDidInputAttrs"
            data-test="member-did-edit-field"
            name="memberDid"
            class="xrd"
            autofocus
            :label="$t('members.member.details.memberDid')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow full-length>
          <DidCheckRow
            button-text="members.member.details.editDid.checkDid"
            button-test-id="member-did-check-button"
            result-test-id="member-did-check-result"
            :disabled="!memberDidInput"
            :checking="checking"
            :outcome="outcome"
            @check="check(dialogHandler)"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import type { PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { useForm } from 'vee-validate';
import { XrdSimpleDialog, useBasicForm, XrdFormBlock, XrdFormBlockRow } from '@niis/shared-ui';
import type { DialogSaveHandler } from '@niis/shared-ui';

import DidCheckRow from '@/components/DidCheckRow.vue';
import { useDidCheck } from '@/composables/use-did-check';
import type { Client, DidResolutionReason, MemberDidRefusalReason, MemberServer } from '@/openapi-types';
import { useMember } from '@/store/modules/members';

const props = defineProps({
  member: {
    type: Object as PropType<Client>,
    required: true,
  },
  server: {
    type: Object as PropType<MemberServer>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const REFUSAL_TEXT: Record<MemberDidRefusalReason, string> = {
  NO_STORED_VALUE: 'error_code.member_did_not_stored',
  WRONG_SCHEME: 'error_code.member_did_wrong_scheme',
  DID_TOO_LONG: 'error_code.member_did_too_long',
  NON_ASCII_HOST: 'error_code.member_did_non_ascii_host',
  UNDECODABLE_DID: 'error_code.member_did_undecodable',
  NOT_CANONICAL: 'error_code.member_did_not_canonical',
  HOST_DIFFERS: 'error_code.member_did_host_differs',
  DID_OF_OTHER_MEMBER: 'error_code.member_did_of_other_member',
  MEMBER_NOT_SERVED: 'error_code.member_did_member_not_served',
  UNPARSEABLE_REGISTERED_ADDRESS: 'error_code.member_did_unparseable_address',
  DID_HELD_BY_ANOTHER_SERVER: 'error_code.member_did_held_by_another_server',
};

const RESOLUTION_TEXT: Record<DidResolutionReason, string> = {
  MALFORMED_DID: 'members.member.details.editDid.check.not_resolved_malformed_did',
  NO_DOCUMENT: 'members.member.details.editDid.check.not_resolved_no_document',
  ID_MISMATCH: 'members.member.details.editDid.check.not_resolved_id_mismatch',
  FETCH_FAILURE: 'members.member.details.editDid.check.not_resolved_fetch_failure',
};

const { meta, resetForm, defineField, handleSubmit } = useForm({
  validationSchema: { memberDid: 'required' },
  initialValues: { memberDid: props.server.member_did },
});
const fieldConfig = { props: (state: { errors: string[] }) => ({ 'error-messages': state.errors }) };
const [memberDidInput, memberDidInputAttrs] = defineField('memberDid', fieldConfig);

const { t } = useI18n();
const { editMemberDid, checkMemberDid } = useMember();
const { addSuccessMessage, addError, loading } = useBasicForm();

const { checking, outcome, check } = useDidCheck({
  request: () => {
    const { memberId, serverId } = ids();
    return checkMemberDid(memberId, serverId, memberDidInput.value as string);
  },
  resetOn: [memberDidInput],
  texts: {
    resolved: 'members.member.details.editDid.check.resolved',
    resolverUnavailable: 'members.member.details.editDid.check.resolver_unavailable',
    resolution: RESOLUTION_TEXT,
    refusal: REFUSAL_TEXT,
  },
});

function ids() {
  return { memberId: props.member.client_id.encoded_id as string, serverId: props.server.server_id.encoded_id as string };
}

function close() {
  resetForm();
  emits('cancel');
}

function save(evt: Event, handler: DialogSaveHandler) {
  return handleSubmit(() => {
    const { memberId, serverId } = ids();
    loading.value = true;
    return editMemberDid(memberId, serverId, memberDidInput.value as string)
      .then(() => {
        addSuccessMessage('members.member.details.editDid.success');
        addError(t('securityServers.dialogs.editAddress.notify'), { warning: true });
        emits('save');
      })
      .catch((error) => handler.addError(error))
      .finally(() => (loading.value = false));
  })(evt);
}
</script>
