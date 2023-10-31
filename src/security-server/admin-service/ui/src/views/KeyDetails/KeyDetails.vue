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
  <div class="xrd-tab-max-width detail-view-outer">
    <div class="detail-view-content">
      <div>
        <xrd-sub-view-title
          v-if="key.usage == 'SIGNING'"
          :title="$t('keys.signDetailsTitle')"
          @close="close"
        />
        <xrd-sub-view-title
          v-else-if="key.usage == 'AUTHENTICATION'"
          :title="$t('keys.authDetailsTitle')"
          @close="close"
        />
        <xrd-sub-view-title
          v-else
          :title="$t('keys.detailsTitle')"
          @close="close"
        />
        <div class="detail-view-tools">
          <xrd-button
            v-if="canDelete"
            :loading="deleting"
            outlined
            @click="confirmDelete = true"
            >{{ $t('action.delete') }}
          </xrd-button>
        </div>
      </div>

      <div>
        <v-text-field
          v-model="keyName"
          class="code-input key-name"
          name="keys.name"
          type="text"
          :label="$t('fields.keys.name')"
          variant="outlined"
          :maxlength="255"
          :error-messages="errorMessage"
          :disabled="!canEdit"
          @blur="handleBlur"
        ></v-text-field>
      </div>

      <div>
        <h3 class="info-title">{{ $t('keys.keyInfo') }}</h3>
        <div class="info-row">
          <div class="row-title">{{ $t('keys.keyId') }}</div>
          <div class="row-data">{{ key.id }}</div>
        </div>
        <div class="info-row">
          <div class="row-title">{{ $t('keys.label') }}</div>
          <div class="row-data">{{ key.label }}</div>
        </div>
        <div class="info-row">
          <div class="row-title">{{ $t('keys.readOnly') }}</div>
          <div class="row-data">{{ tokenForCurrentKey.read_only }}</div>
        </div>
      </div>
    </div>
    <div class="detail-view-actions-footer">
      <xrd-button outlined @click="close()"
        >{{ $t('action.cancel') }}
      </xrd-button>
      <xrd-button
        :loading="saving"
        :disabled="!meta.touched || !meta.valid"
        @click="save()"
        >{{ $t('action.save') }}
      </xrd-button>
    </div>

    <!-- Confirm dialog delete Key -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      title="keys.deleteTitle"
      text="keys.deleteKeyText"
      @cancel="confirmDelete = false"
      @accept="deleteKey(false)"
    />

    <!-- Warning dialog when key is deleted -->
    <WarningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localization-parent="keys"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of a key
 */
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import {
  CodeWithDetails,
  Key,
  KeyUsageType,
  PossibleAction,
  PossibleActions as PossibleActionsList,
  Token,
} from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import WarningDialog from '@/components/ui/WarningDialog.vue';

import { PossibleActions } from '@/openapi-types/models/PossibleActions';
import { isEmpty } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useTokens } from '@/store/modules/tokens';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useField } from 'vee-validate';

export default defineComponent({
  components: {
    WarningDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { meta, errorMessage, value, setValue, handleBlur } = useField(
      'keys.name',
      {
        required: true,
      },
      { initialValue: '' },
    );
    return {
      meta,
      errorMessage,
      keyName: value,
      setKeyNameField: setValue,
      handleBlur,
    };
  },
  data() {
    return {
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
  },
  created() {
    this.fetchData(this.id);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useTokens, ['fetchTokens']),
    close(): void {
      this.$router.back();
    },

    save(): void {
      this.saving = true;
      this.key.name = this.keyName;
      api
        .patch(`/keys/${encodePathParameter(this.id)}`, this.key)
        .then(() => {
          this.saving = false;
          this.showSuccess(this.$t('keys.keySaved'));
          this.close();
        })
        .catch((error) => {
          this.saving = false;
          this.showError(error);
        });
    },

    async fetchData(id: string) {
      const keyResponse = await api.get<Key>(
        `/keys/${encodePathParameter(id)}`,
      );

      this.key = keyResponse.data;
      this.fetchPossibleActions(id);
      this.setNameField();

      if (this.tokens?.length === 0) {
        await this.fetchTokens();
      }

      // Find the token that contains current key after token and key are fetched
      this.tokenForCurrentKey = this.tokens.find((token: Token) =>
        token.keys.find((key: Key) => key.id === this.id),
      ) as Token;
    },
    fetchPossibleActions(id: string): void {
      api
        .get<PossibleActionsList>(
          `/keys/${encodePathParameter(id)}/possible-actions`,
        )
        .then((res) => {
          this.possibleActions = res.data;
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    cancelSubmit(): void {
      this.warningDialog = false;
    },
    acceptWarnings(): void {
      this.warningDialog = false;
      this.deleteKey(true);
    },
    deleteKey(ignoreWarnings: boolean): void {
      this.deleting = true;
      this.confirmDelete = false;

      api
        .remove(
          `/keys/${encodePathParameter(
            this.id,
          )}?ignore_warnings=${ignoreWarnings}`,
        )
        .then(() => {
          this.showSuccess(this.$t('keys.keyDeleted'));
          this.close();
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.showError(error);
          }
        })
        .finally(() => (this.deleting = false));
    },
    setNameField(): void {
      // If the key has no name, use key id instead
      this.setKeyNameField(
        isEmpty(this.key.name) ? this.key.id : this.key.name,
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/detail-views';
@import '@/assets/wizards';

.info-title {
  margin-top: 30px;
  margin-bottom: 10px;
}

.info-row {
  display: flex;
  flex-direction: row;
}

.key-name {
  width: 405px;
}
</style>
