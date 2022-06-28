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
  <div class="step-content-wrapper">
    <ValidationObserver ref="form1" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberName')"
            :help-text="$t('wizard.client.memberNameTooltip')"
          />
          <div
            v-if="memberName"
            class="readonly-info-field"
            data-test="selected-member-name"
          >
            {{ memberName }}
          </div>
          <div v-else class="readonly-info-field"></div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberClass')"
            :help-text="$t('wizard.client.memberClassTooltip')"
          />

          <ValidationProvider name="addClient.memberClass" rules="required">
            <v-select
              v-model="memberClass"
              :items="memberClassesCurrentInstance"
              :disabled="isServerOwnerInitialized"
              data-test="member-class-input"
              class="wizard-form-input"
            ></v-select>
          </ValidationProvider>
        </div>
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberCode')"
            :help-text="$t('wizard.client.memberCodeTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            ref="memberCodeVP"
            name="addClient.memberCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="memberCode"
              class="wizard-form-input"
              type="text"
              :error-messages="errors"
              :disabled="isServerOwnerInitialized"
              autofocus
              data-test="member-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.securityServerCode')"
            :help-text="$t('initialConfiguration.member.serverCodeHelp')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="securityServerCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="securityServerCode"
              class="wizard-form-input"
              type="text"
              :error-messages="errors"
              :disabled="isServerCodeInitialized"
              data-test="security-server-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </div>
      <div class="button-footer">
        <v-spacer></v-spacer>
        <div>
          <xrd-button
            v-if="showPreviousButton"
            outlined
            class="previous-button"
            data-test="previous-button"
            @click="previous"
            >{{ $t('action.previous') }}</xrd-button
          >
          <xrd-button
            :disabled="invalid"
            data-test="save-button"
            @click="done"
            >{{ $t(saveButtonText) }}</xrd-button
          >
        </div>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';

import { ValidationProvider, ValidationObserver } from 'vee-validate';

import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useGeneral } from '@/store/modules/general';
import { useUser } from '@/store/modules/user';
import { useInitializeServer } from '@/store/modules/initializeServer';

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        memberCodeVP: InstanceType<typeof ValidationProvider>;
      };
    }
  >
).extend({
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapState(useGeneral, ['memberClassesCurrentInstance', 'memberName']),

    ...mapState(useUser, [
      'currentSecurityServer',
      'isServerCodeInitialized',
      'isServerOwnerInitialized',
    ]),
    ...mapState(useInitializeServer, [
      'initServerMemberClass',
      'initServerMemberCode',
      'initServerSSCode',
    ]),

    memberClass: {
      get(): string | undefined {
        if (this.currentSecurityServer?.member_class) {
          return this.currentSecurityServer.member_class;
        }
        return this.initServerMemberClass;
      },
      set(value: string) {
        this.storeInitServerMemberClass(value);
      },
    },
    memberCode: {
      get(): string | undefined {
        if (this.currentSecurityServer?.member_code) {
          return this.currentSecurityServer.member_code;
        }
        return this.initServerMemberCode;
      },
      set(value: string) {
        this.storeInitServerMemberCode(value);
      },
    },
    securityServerCode: {
      get(): string | undefined {
        if (this.currentSecurityServer?.server_code) {
          return this.currentSecurityServer.server_code;
        }
        return this.initServerSSCode;
      },
      set(value: string) {
        this.storeInitServerSSCode(value);
      },
    },
  },

  watch: {
    memberClassesCurrentInstance(val: string[]) {
      // Set first member class selected if there is only one
      if (val?.length === 1) {
        this.storeInitServerMemberClass(val[0]);
      }
    },
    memberClass(val) {
      if (val) {
        // Update member name when info changes
        this.checkMember();
      }
    },
    async memberCode(val) {
      // Needs to be done here, because the watcher runs before the setter
      this.isMemberCodeValid = (await this.$refs.memberCodeVP.validate()).valid;
      if (val) {
        // Update member name when info changes
        this.checkMember();
      }
    },
  },
  beforeMount() {
    this.fetchMemberClassesForCurrentInstance().catch((error) => {
      if (error.response.status === 500) {
        // this can happen if anchor is not ready
        return;
      }
      this.showError(error);
    });

    this.checkMember();
  },
  mounted() {
    this.$refs.memberCodeVP;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useInitializeServer, [
      'storeInitServerSSCode',
      'storeInitServerMemberClass',
      'storeInitServerMemberCode',
    ]),

    ...mapActions(useGeneral, [
      'fetchMemberClassesForCurrentInstance',
      'fetchMemberName',
    ]),

    done(): void {
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },

    checkMember(): void {
      if (
        this.memberClass &&
        this.memberCode &&
        this.memberClass.length > 0 &&
        this.memberCode.length > 0 &&
        this.isMemberCodeValid
      ) {
        this.fetchMemberName(this.memberClass, this.memberCode).catch(
          (error) => {
            if (error.response.status === 404) {
              // no match found
              return;
            }
            this.showError(error);
          },
        );
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';

.readonly-info-field {
  max-width: 405px;
  height: 60px;
  padding-top: 12px;
}
</style>
