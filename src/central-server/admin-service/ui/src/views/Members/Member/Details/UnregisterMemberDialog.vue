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
  <xrd-confirm-dialog
    :loading="loading"
    title="members.member.details.unregisterMember"
    focus-on-accept
    @cancel="emit('cancel')"
    @accept="unregister"
  >
    <template #text>
      <i18n-t
        scope="global"
        keypath="members.member.details.areYouSureUnregister"
      >
        <template #memberCode>
          <b>{{ member.client_id.member_code }}</b>
        </template>
        <template #serverCode>
          <b>{{ server.server_id.server_code }}</b>
        </template>
      </i18n-t>
    </template>
  </xrd-confirm-dialog>
</template>

<script setup lang="ts">
import { PropType, ref } from 'vue';
import { Client, SecurityServer } from '@/openapi-types';
import { useMember } from '@/store/modules/members';
import { useI18n } from 'vue-i18n';
import { useNotifications } from '@/store/modules/notifications';


const props = defineProps({
  server: {
    type: Object as PropType<SecurityServer>,
    required: true,
  },
  member: {
    type: Object as PropType<Client>,
    required: true,
  },
});

const { t } = useI18n<{ message: MessageSchema }>({ useScope: 'global' });
const { showSuccess, showError } = useNotifications();

const memberStore = useMember();

const loading = ref(false);

const emit = defineEmits(['cancel', 'unregister']);

function unregister() {
  loading.value = true;
  memberStore.unregister(props.member.client_id.encoded_id, props.server.server_id.encoded_id)
    .then(() => {
      showSuccess(
        t(
          'members.member.details.memberSuccessfullyUnregistered',
          {
            memberCode: props.member.client_id.member_code,
            serverCode: props.server.server_id.server_code,
          },
        ),
      );
      emit('unregister');
    })
    .catch((error) => showError(error))
    .finally(() => loading.value = false);
}
</script>
