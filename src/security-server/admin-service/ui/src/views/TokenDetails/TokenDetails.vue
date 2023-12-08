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
      <xrd-sub-view-title :title="$t('keys.tokenDetails')" @close="close" />
      <v-row>
        <v-col>
          <h3>{{ $t('keys.tokenInfo') }}</h3>
          <div class="d-flex">
            <div class="row-title">{{ $t('keys.tokenId') }}</div>
            <div class="row-data text-break">{{ token.id }}</div>
          </div>
          <div class="d-flex">
            <div class="row-title">{{ $t('keys.type') }}</div>
            <div class="row-data">{{ token.type }}</div>
          </div>
        </v-col>

        <v-col>
          <v-row no-gutters>
            <v-text-field
              v-bind="friendlyNameRef"
              class="code-input"
              name="token.friendlyName"
              type="text"
              variant="outlined"
              :label="$t('keys.friendlyName')"
              :maxlength="255"
              :disabled="!(hasEditPermission && canEditName())"
              data-test="token-friendly-name"
              autofocus
            ></v-text-field>
          </v-row>
          <v-row v-if="isSoftwareToken() && canUpdatePin" no-gutters>
            <xrd-expandable
              class="expandable"
              :is-open="isChangePinOpen"
              :is-disabled="!isTokenLoggedIn()"
              data-test="token-open-pin-change-button"
              @open="toggleChangePinOpen"
              @close="toggleChangePinOpen"
            >
              <template #link>
                <div
                  :class="isTokenLoggedIn() && 'pointer'"
                  data-test="token-open-pin-change-link"
                  @click="toggleChangePinOpen"
                >
                  <span class="font-weight-black">
                    {{ $t('token.changePin') }}
                  </span>
                </div>
              </template>
              <template #content>
                <v-row no-gutters v-if="isEnforceTokenPolicyEnabled">
                  <v-alert
                    data-test="alert-token-policy-enabled"
                    class="mb-6"
                    variant="outlined"
                    border="start"
                    density="compact"
                    type="info"
                  >
                    <h4>{{ $t('token.tokenPinPolicyHeader') }}</h4>
                    <div>{{ $t('token.tokenPinPolicy') }}</div>
                  </v-alert>
                </v-row>
                <v-row no-gutters>
                  <v-text-field
                    v-bind="oldPinRef"
                    class="code-input"
                    name="token.oldPin"
                    type="password"
                    variant="outlined"
                    :label="$t('fields.token.oldPin')"
                    :maxlength="255"
                    data-test="token-change-pin-old"
                  ></v-text-field>
                </v-row>
                <v-row no-gutters>
                  <v-text-field
                    v-bind="newPinRef"
                    class="code-input"
                    name="token.newPin"
                    type="password"
                    variant="outlined"
                    :label="$t('fields.token.newPin')"
                    :maxlength="255"
                    data-test="token-change-pin-new"
                  ></v-text-field>
                </v-row>
                <v-row no-gutters>
                  <v-text-field
                    v-bind="newPinConfirmRef"
                    class="code-input"
                    name="token.newPinConfirm"
                    type="password"
                    variant="outlined"
                    :label="$t('fields.token.newPinConfirm')"
                    :maxlength="255"
                    data-test="token-change-pin-new-confirm"
                  ></v-text-field>
                </v-row>
              </template>
            </xrd-expandable>
          </v-row>
        </v-col>
      </v-row>
    </div>
    <div class="detail-view-actions-footer">
      <xrd-button outlined data-test="token-details-cancel" @click="close()">
        {{ $t('action.cancel') }}
      </xrd-button>
      <xrd-button
        :loading="saving"
        :disabled="isSaveDisabled"
        data-test="token-details-save"
        @click="save()"
        >{{ $t('action.save') }}
      </xrd-button>
    </div>
  </div>
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
import { useNotifications } from '@/store/modules/notifications';
import { AxiosError } from 'axios';
import { PublicPathState, useForm } from 'vee-validate';
import { useTokens } from '@/store/modules/tokens';

export default defineComponent({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup(props) {
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
      defineComponentBinds,
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
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const friendlyNameRef = defineComponentBinds(
      'token.friendlyName',
      componentConfig,
    );
    const oldPinRef = defineComponentBinds('token.oldPin', componentConfig);
    const newPinRef = defineComponentBinds('token.newPin', componentConfig);
    const newPinConfirmRef = defineComponentBinds(
      'token.newPinConfirm',
      componentConfig,
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
      friendlyNameRef,
      oldPinRef,
      newPinRef,
      newPinConfirmRef,
    };
  },
  data() {
    return {
      saving: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'isEnforceTokenPolicyEnabled']),
    hasEditPermission(): boolean {
      return this.hasPermission(Permissions.EDIT_TOKEN_FRIENDLY_NAME);
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
  },
  created() {
    this.fetchInitializationStatus().catch((error) => {
      this.showError(error);
    });
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useUser, ['fetchInitializationStatus']),
    ...mapActions(useTokens, ['updatePin', 'updateToken']),
    close(): void {
      this.$router.back();
    },

    async save(): Promise<void> {
      this.saving = true;

      try {
        let successMsg = this.$t('keys.tokenSaved') as string;
        if (this.isChangePinOpen) {
          this.updatePin(
            this.id,
            this.values.token.oldPin,
            this.values.token.newPin,
          );
          successMsg = this.$t('token.pinChanged') as string;
        }
        if (this.isFieldDirty('token.friendlyName')) {
          this.updateToken({
            ...this.token,
            name: this.values.token.friendlyName,
          });
        }
        this.showSuccess(successMsg);
        this.$router.back();
      } catch (error) {
        // Error comes from axios, so it most probably is AxiosError
        this.showError(error as AxiosError);
      } finally {
        this.saving = false;
      }
    },

    canEditName(): boolean {
      return (
        this.token?.possible_actions?.includes(
          PossibleAction.EDIT_FRIENDLY_NAME,
        ) ?? false
      );
    },

    isTokenLoggedIn(): boolean {
      return (
        this.token.possible_actions?.includes(
          PossibleAction.TOKEN_CHANGE_PIN,
        ) ?? false
      );
    },

    isSoftwareToken(): boolean {
      return this.token.type === TokenType.SOFTWARE;
    },

    toggleChangePinOpen(): void {
      if (!this.isTokenLoggedIn()) {
        return;
      }
      this.isChangePinOpen = !this.isChangePinOpen;
      this.resetField('token.oldPin');
      this.resetField('token.newPin');
      this.resetField('token.newPinConfirm');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/detail-views';

.code-input {
  width: 450px;
}

.expandable::v-deep(.exp-header) {
  padding: 0;
  margin-left: -12px;
}

.pointer {
  cursor: pointer;
}
</style>
