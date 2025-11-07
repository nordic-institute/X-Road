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
    title="keys.token.details"
    save-button-text="action.confirm"
    save-button-icon="check"
    :loading="saving"
    :disable-save="isSaveDisabled"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock title="keys.token.info">
        <XrdFormBlockRow>
          <v-text-field
            class="xrd"
            variant="plain"
            readonly
            hide-details
            :model-value="token.id"
            :label="$t('keys.token.id')"
          />
          <template #description>
            <v-text-field
              class="xrd"
              variant="plain"
              readonly
              hide-details
              :model-value="token.type"
              :label="$t('keys.type')"
            />
          </template>
        </XrdFormBlockRow>
        <XrdFormBlockRow>
          <v-text-field
            v-model="friendlyName"
            v-bind="friendlyNameAttr"
            data-test="token-friendly-name"
            class="xrd"
            name="token.friendlyName"
            autofocus
            :label="$t('keys.friendlyName')"
            :maxlength="255"
            :disabled="!(hasEditPermission && canEditName)"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <XrdExpandable
        v-if="softwareToken && canUpdatePin"
        data-test="token-open-pin-change-button"
        class="mt-4"
        :disabled="!tokenLoggedIn"
        @open="onPinOpenClose"
      >
        <template #link="{ toggle, opened }">
          <span
            data-test="token-open-pin-change-link"
            class="font-weight-medium cursor-pointer"
            :class="{ 'on-surface': opened, 'on-surface-variant': !opened }"
            @click="toggle"
          >
            {{ $t('token.changePin') }}
          </span>
        </template>
        <template #content>
          <v-container class="pr-6 pb-0 pl-6" fluid>
            <v-alert
              v-if="isEnforceTokenPolicyEnabled"
              data-test="alert-token-policy-enabled"
              class="mr-2 mb-4 ml-2"
              variant="outlined"
              border="start"
              density="compact"
              type="info"
            >
              <p class="font-weight-bold">
                {{ $t('token.tokenPinPolicyHeader') }}
              </p>
              <p class="">{{ $t('token.tokenPinPolicy') }}</p>
            </v-alert>
            <v-row no-gutters>
              <v-col>
                <v-text-field
                  v-model="oldPin"
                  v-bind="oldPinAttr"
                  data-test="token-change-pin-old"
                  class="xrd"
                  name="token.oldPin"
                  :type="oldPinType"
                  :label="$t('fields.token.oldPin')"
                  :maxlength="255"
                >
                  <template #append-inner>
                    <v-icon
                      class="on-surface-variant"
                      :icon="appendOldPinIcon"
                      @click="oldPinType = toggleType(oldPinType)"
                    />
                  </template>
                </v-text-field>
              </v-col>
              <v-spacer class="ml-8" />
            </v-row>
            <v-row no-gutters>
              <v-col>
                <v-text-field
                  v-model="newPin"
                  v-bind="newPinAttr"
                  data-test="token-change-pin-new"
                  class="xrd"
                  name="token.newPin"
                  :type="newPinType"
                  :label="$t('fields.token.newPin')"
                  :maxlength="255"
                >
                  <template #append-inner>
                    <v-icon
                      class="on-surface-variant"
                      :icon="appendNewPinIcon"
                      @click="newPinType = toggleType(newPinType)"
                    />
                  </template>
                </v-text-field>
              </v-col>
              <v-spacer class="ml-8" />
            </v-row>
            <v-row no-gutters>
              <v-col>
                <v-text-field
                  v-model="newPinConfirm"
                  v-bind="newPinConfirmAttr"
                  data-test="token-change-pin-new-confirm"
                  class="xrd"
                  name="token.newPinConfirm"
                  :type="newPinType"
                  :label="$t('fields.token.newPinConfirm')"
                  :maxlength="255"
                >
                  <template #append-inner>
                    <v-icon
                      class="on-surface-variant"
                      :icon="appendNewPinIcon"
                      @click="newPinType = toggleType(newPinType)"
                    />
                  </template>
                </v-text-field>
              </v-col>
              <v-spacer class="ml-8" />
            </v-row>
          </v-container>
        </template>
      </XrdExpandable>
    </template>
    <template #prepend-save-button="{ dialogHandler }">
      <XrdBtn
        v-if="canDelete"
        data-test="token-delete-button"
        text="action.delete"
        prepend-icon="delete_forever"
        variant="outlined"
        :loading="deleting"
        @click="confirmDelete = true"
      />
      <!-- Confirm dialog delete token -->
      <XrdConfirmDialog
        v-if="confirmDelete"
        title="keys.token.deleteTitle"
        text="keys.token.deleteText"
        @cancel="confirmDelete = false"
        @accept="doDeleteToken(dialogHandler)"
      />
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
/***
 * Component for showing the details of a token.
 */
import { computed, defineComponent, ref } from 'vue';
import { Permissions } from '@/global';
import { PossibleAction, Token, TokenType } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useForm } from 'vee-validate';
import { useTokens } from '@/store/modules/tokens';
import {
  XrdSimpleDialog,
  XrdFormBlock,
  XrdFormBlockRow,
  useNotifications,
  XrdExpandable,
  XrdBtn,
  DialogSaveHandler,
  helper,
  XrdConfirmDialog,
} from '@niis/shared-ui';

type PinType = 'password' | 'text';

export default defineComponent({
  components: {
    XrdExpandable,
    XrdSimpleDialog,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdBtn,
    XrdConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  emits: ['cancel', 'update', 'delete'],
  setup(props) {
    const { addSuccessMessage, addTranslatedSuccessMessage, addError } =
      useNotifications();
    const { tokens } = useTokens();
    const isChangePinOpen = ref(false);
    const token: Token = tokens.find((token) => token.id === props.id)!;
    const validationSchema = computed(() => {
      if (isChangePinOpen.value) {
        return {
          'token.friendlyName': 'required|max:255',
          'token.oldPin': 'required',
          'token.newPin': 'required|confirmed:@token.newPinConfirm',
          'token.newPinConfirm': 'required',
        };
      } else {
        return {
          'token.friendlyName': 'required|max:255',
        };
      }
    });
    const {
      meta,
      values,
      setFieldValue,
      isFieldValid,
      isFieldDirty,
      resetField,
      defineField,
    } = useForm({
      validationSchema,
      initialValues: {
        token: {
          friendlyName: token.name,
          oldPin: '',
          newPin: '',
          newPinConfirm: '',
        },
      },
    });

    const [friendlyName, friendlyNameAttr] = defineField(
      'token.friendlyName',
      helper.veeDefaultFieldConfig(),
    );
    const [oldPin, oldPinAttr] = defineField(
      'token.oldPin',
      helper.veeDefaultFieldConfig(),
    );
    const [newPin, newPinAttr] = defineField(
      'token.newPin',
      helper.veeDefaultFieldConfig(),
    );
    const [newPinConfirm, newPinConfirmAttr] = defineField(
      'token.newPinConfirm',
      helper.veeDefaultFieldConfig(),
    );
    return {
      isChangePinOpen,
      token,
      meta,
      values,
      setFieldValue,
      isFieldValid,
      isFieldDirty,
      resetField,
      friendlyName,
      friendlyNameAttr,
      oldPin,
      oldPinAttr,
      newPin,
      newPinAttr,
      newPinConfirm,
      newPinConfirmAttr,
      addSuccessMessage,
      addTranslatedSuccessMessage,
      addError,
    };
  },
  data() {
    return {
      saving: false,
      confirmDelete: false,
      deleting: false as boolean,
      oldPinType: 'password' as PinType,
      newPinType: 'password' as PinType,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'isEnforceTokenPolicyEnabled']),
    hasEditPermission(): boolean {
      return this.hasPermission(Permissions.EDIT_TOKEN_FRIENDLY_NAME);
    },
    hasDeletePermission(): boolean {
      return this.hasPermission(Permissions.DELETE_TOKEN);
    },
    canUpdatePin(): boolean {
      return this.hasPermission(Permissions.UPDATE_TOKEN_PIN);
    },
    isSaveDisabled(): boolean {
      if (this.isChangePinOpen) {
        return !this.meta.dirty || !this.meta.valid;
      } else {
        return (
          !this.isFieldDirty('token.friendlyName') ||
          !this.isFieldValid('token.friendlyName')
        );
      }
    },
    canEditName(): boolean {
      return (
        this.token?.possible_actions?.includes(
          PossibleAction.EDIT_FRIENDLY_NAME,
        ) ?? false
      );
    },
    canDelete(): boolean {
      if (
        !this.token?.possible_actions?.includes(PossibleAction.TOKEN_DELETE)
      ) {
        return false;
      }

      return this.hasDeletePermission;
    },
    tokenLoggedIn(): boolean {
      return (
        this.token.possible_actions?.includes(
          PossibleAction.TOKEN_CHANGE_PIN,
        ) ?? false
      );
    },
    softwareToken(): boolean {
      return this.token.type === TokenType.SOFTWARE;
    },
    appendOldPinIcon() {
      return this.oldPinType === 'password' ? 'visibility_off' : 'visibility';
    },
    appendNewPinIcon() {
      return this.newPinType === 'password' ? 'visibility_off' : 'visibility';
    },
  },
  created() {
    this.fetchInitializationStatus().catch((error) => {
      this.addError(error);
    });
  },
  methods: {
    ...mapActions(useUser, ['fetchInitializationStatus']),
    ...mapActions(useTokens, ['updatePin', 'updateToken', 'deleteToken']),
    cancel(): void {
      this.$emit('cancel');
    },
    toggleType(type: PinType): PinType {
      return type === 'password' ? 'text' : 'password';
    },
    async save(handler: DialogSaveHandler): Promise<void> {
      this.saving = true;

      try {
        let successMsg = this.$t('keys.token.saved') as string;
        if (this.isChangePinOpen) {
          await this.updatePin(
            this.id,
            this.values.token.oldPin,
            this.values.token.newPin,
          );
          successMsg = this.$t('token.pinChanged') as string;
        }
        if (this.isFieldDirty('token.friendlyName')) {
          await this.updateToken({
            ...this.token,
            name: this.values.token.friendlyName,
          });
        }
        this.addTranslatedSuccessMessage(successMsg);
        this.$emit('update');
      } catch (error) {
        // Error comes from axios, so it most probably is AxiosError
        handler.addError(error);
      } finally {
        this.saving = false;
      }
    },

    doDeleteToken(handler: DialogSaveHandler): void {
      this.deleting = true;
      this.confirmDelete = false;
      this.deleteToken(this.id)
        .then(() => {
          this.addSuccessMessage('keys.token.deleteSuccess');
          this.$emit('delete');
        })
        .catch((error) => handler.addError(error))
        .finally(() => (this.deleting = false));
    },

    onPinOpenClose(state: boolean): void {
      if (!this.tokenLoggedIn) {
        return;
      }
      this.isChangePinOpen = state;
      this.resetField('token.oldPin');
      this.resetField('token.newPin');
      this.resetField('token.newPinConfirm');
    },
  },
});
</script>

<style lang="scss" scoped></style>
