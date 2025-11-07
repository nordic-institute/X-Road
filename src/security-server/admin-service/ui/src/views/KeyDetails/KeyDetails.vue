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
  <XrdElevatedViewFixedWidth
    :title="titleKey"
    :breadcrumbs="breadcrumbs"
    @close="close"
  >
    <XrdFormBlock>
      <XrdFormBlockRow>
        <v-text-field
          v-if="keyName"
          v-model="keyName"
          class="xrd"
          name="keys.name"
          autofocus
          :label="$t('fields.keys.name')"
          :maxlength="255"
          :disabled="!canEdit"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>

    <XrdCard class="mt-4" :loading="loading" title="keys.keyInfo">
      <XrdCardTable v-if="key">
        <XrdCardTableRow label="keys.keyId" :value="key.id" />
        <XrdCardTableRow label="keys.label" :value="key.label" />
        <XrdCardTableRow label="keys.keyAlgorithm" :value="key.key_algorithm" />
        <XrdCardTableRow
          label="keys.readOnly"
          :value="tokenForCurrentKey.read_only"
        />
      </XrdCardTable>
    </XrdCard>
    <template #footer>
      <v-spacer />
      <XrdBtn
        v-if="canDelete"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        :loading="deleting"
        @click="confirmDelete = true"
      />
      <XrdBtn
        class="ml-2"
        text="action.save"
        prepend-icon="check"
        :disabled="!meta.dirty || !meta.valid"
        :loading="saving"
        @click="save()"
      />
      <!-- Confirm dialog delete Key -->
      <XrdConfirmDialog
        v-if="confirmDelete"
        title="keys.deleteTitle"
        text="keys.deleteKeyText"
        @cancel="confirmDelete = false"
        @accept="doDeleteKey(false)"
      />

      <!-- Warning dialog when key is deleted -->
      <WarningDialog
        v-if="warningDialog"
        :warnings="warningInfo"
        localization-parent="keys"
        @cancel="cancelSubmit()"
        @accept="acceptWarnings()"
      />
    </template>
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
/***
 * Component for showing the details of a key
 */
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import {
  CodeWithDetails,
  Key,
  KeyUsageType,
  PossibleAction,
  Token,
} from '@/openapi-types';
import WarningDialog from '@/components/ui/WarningDialog.vue';

import { PossibleActions } from '@/openapi-types/models/PossibleActions';
import { isEmpty } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useTokens } from '@/store/modules/tokens';
import { useUser } from '@/store/modules/user';
import { useField } from 'vee-validate';
import {
  XrdElevatedViewFixedWidth,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdBtn,
  useNotifications,
  XrdCardTable,
  XrdCard,
  XrdCardTableRow,
  helper,
  XrdConfirmDialog,
} from '@niis/shared-ui';
import { useKeys } from '@/store/modules/keys';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';

export default defineComponent({
  components: {
    XrdCardTableRow,
    XrdCard,
    XrdCardTable,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdElevatedViewFixedWidth,
    XrdBtn,
    WarningDialog,
    XrdConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const { meta, value, resetField } = useField(
      'keys.name',
      {
        required: true,
      },
      {
        ...helper.veeDefaultFieldConfig(),
        initialValue: '',
      },
    );
    return {
      meta,
      keyName: value,
      resetField,
      addError,
      addSuccessMessage,
    };
  },
  data() {
    return {
      loading: false,
      confirmDelete: false,
      saving: false,
      key: {} as Key,
      possibleActions: [] as PossibleActions,
      deleting: false as boolean,
      warningInfo: [] as CodeWithDetails[],
      warningDialog: false as boolean,
      tokenForCurrentKey: {} as Token,
    };
  },
  computed: {
    ...mapState(useTokens, ['tokens']),
    ...mapState(useUser, ['hasPermission']),
    titleKey() {
      if (this.key.usage == KeyUsageType.SIGNING) {
        return 'keys.signDetailsTitle';
      } else if (this.key.usage == KeyUsageType.AUTHENTICATION) {
        return 'keys.authDetailsTitle';
      } else {
        return 'keys.detailsTitle';
      }
    },
    canEdit(): boolean {
      if (!this.possibleActions.includes(PossibleAction.EDIT_FRIENDLY_NAME)) {
        return false;
      }

      return this.hasPermission(Permissions.EDIT_KEY_FRIENDLY_NAME);
    },
    canDelete(): boolean {
      if (!this.possibleActions.includes(PossibleAction.DELETE)) {
        return false;
      }

      if (this.key.usage === KeyUsageType.SIGNING) {
        return this.hasPermission(Permissions.DELETE_SIGN_KEY);
      }

      if (this.key.usage === KeyUsageType.AUTHENTICATION) {
        return this.hasPermission(Permissions.DELETE_AUTH_KEY);
      }

      return this.hasPermission(Permissions.DELETE_KEY);
    },
    breadcrumbs() {
      const crumbs: BreadcrumbItem[] = [
        {
          title: this.$t('tab.keys.signAndAuthKeys'),
          to: {
            name: RouteName.SignAndAuthKeys,
          },
        },
      ];

      if (this.key) {
        crumbs.push({
          title: this.key.name ?? this.key.id,
        });
      }

      return crumbs;
    },
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.fetchData(this.id);
      },
    },
  },
  methods: {
    ...mapActions(useTokens, ['fetchTokens']),
    ...mapActions(useKeys, [
      'fetchKey',
      'fetchPossibleActions',
      'updateKeyName',
      'deleteKey',
    ]),
    close(): void {
      this.$router.back();
    },

    save(): void {
      this.saving = true;
      this.key.name = this.keyName;
      this.updateKeyName(this.id, { name: this.keyName })
        .then(() => {
          this.addSuccessMessage('keys.keySaved');
          this.fetchData(this.id);
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.saving = false));
    },

    async fetchData(id: string) {
      this.loading = true;
      this.fetchKey(id)
        .then((key) => (this.key = key))
        .then(() => this.setNameField())
        .then(() => this.doFetchPossibleActions(id))
        .catch((error) => this.addError(error, { navigate: true }))
        .finally(() => (this.loading = false));

      if (this.tokens?.length === 0) {
        await this.fetchTokens();
      }

      // Find the token that contains current key after token and key are fetched
      this.tokenForCurrentKey = this.tokens.find((token: Token) =>
        token.keys.find((key: Key) => key.id === this.id),
      ) as Token;
    },
    doFetchPossibleActions(id: string): void {
      this.fetchPossibleActions(id)
        .then((data) => (this.possibleActions = data))
        .catch((error) => this.addError(error));
    },
    cancelSubmit(): void {
      this.warningDialog = false;
    },
    acceptWarnings(): void {
      this.warningDialog = false;
      this.doDeleteKey(true);
    },
    doDeleteKey(ignoreWarnings: boolean): void {
      this.deleting = true;
      this.confirmDelete = false;

      this.deleteKey(this.id, ignoreWarnings)
        .then(() => {
          this.addSuccessMessage('keys.keyDeleted', {}, true);
          this.close();
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.addError(error);
          }
        })
        .finally(() => (this.deleting = false));
    },
    setNameField(): void {
      // If the key has no name, use key id instead
      this.resetField({
        value: isEmpty(this.key.name) ? this.key.id : this.key.name,
      });
    },
  },
});
</script>

<style lang="scss" scoped></style>
