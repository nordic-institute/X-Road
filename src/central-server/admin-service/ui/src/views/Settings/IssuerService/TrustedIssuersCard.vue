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
  <XrdCard data-test="trusted-issuers-card" title="issuerService.trustedIssuers.title">
    <v-data-table
      item-value="id"
      data-test="trusted-issuers-table"
      class="xrd border"
      hide-default-footer
      must-sort
      :loading="loading"
      :headers="headers"
      :header-props="{ showHeaderBorder: false }"
      :items="issuers"
      :items-per-page="-1"
    >
      <template #[`item.created_at`]="{ item }">
        <XrdDateTime :value="item.created_at" />
      </template>
      <template #[`item.button`]="{ item }">
        <XrdBtn
          v-if="canRemove"
          data-test="delete-trusted-issuer"
          variant="text"
          color="tertiary"
          text="action.remove"
          @click="openRemoveConfirm(item)"
        />
      </template>
    </v-data-table>

    <XrdConfirmDialog
      v-if="issuerToRemove"
      data-test="trusted-issuer-remove-confirm-dialog"
      title="issuerService.trustedIssuers.removeConfirm.title"
      :text="isLastIssuer ? 'issuerService.trustedIssuers.removeConfirm.lastText' : 'issuerService.trustedIssuers.removeConfirm.text'"
      focus-on-accept
      :loading="removing"
      :data="{ did: issuerToRemove.did }"
      @cancel="issuerToRemove = null"
      @accept="confirmRemove"
    />
  </XrdCard>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useNotifications, XrdBtn, XrdCard, XrdConfirmDialog, XrdDateTime } from '@niis/shared-ui';
import { Permissions } from '@/global';
import { TrustedIssuer } from '@/openapi-types';
import { useIssuerService } from '@/store/modules/issuer-service';
import { useUser } from '@/store/modules/user';

const emit = defineEmits<{ removed: [] }>();

const { t } = useI18n();
const { hasPermission } = useUser();
const { listTrustedIssuers, removeTrustedIssuer } = useIssuerService();
const { addError, addSuccessMessage } = useNotifications();

const issuers = ref<TrustedIssuer[]>([]);
const loading = ref(false);
const issuerToRemove = ref<TrustedIssuer | null>(null);
const removing = ref(false);

const canRemove = computed(() => hasPermission(Permissions.DELETE_TRUSTED_ISSUER));
const isLastIssuer = computed(() => issuers.value.length === 1);

const headers = computed(
  () =>
    [
      {
        title: t('issuerService.trustedIssuers.did'),
        align: 'start',
        key: 'did',
      },
      {
        title: t('issuerService.trustedIssuers.createdAt'),
        align: 'start',
        key: 'created_at',
      },
      {
        title: '',
        key: 'button',
        align: 'end',
        sortable: false,
      },
    ] as DataTableHeader[],
);

function fetchIssuers() {
  loading.value = true;
  return listTrustedIssuers()
    .then((result) => (issuers.value = result))
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
}

function openRemoveConfirm(issuer: TrustedIssuer): Promise<void> {
  return listTrustedIssuers()
    .then((result) => {
      issuers.value = result;
    })
    .catch((error) => addError(error))
    .finally(() => (issuerToRemove.value = issuer));
}

function confirmRemove(): void {
  if (!issuerToRemove.value) return;
  removing.value = true;
  removeTrustedIssuer(issuerToRemove.value.id)
    .then(() => {
      addSuccessMessage('issuerService.trustedIssuers.removeSuccess');
      emit('removed');
      return fetchIssuers();
    })
    .catch((error) => addError(error))
    .finally(() => {
      issuerToRemove.value = null;
      removing.value = false;
    });
}

fetchIssuers();
</script>

<style lang="scss" scoped></style>
