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
  <div class="view-wrap">
    <xrd-sub-view-title
      class="wizard-view-title"
      :title="$t('wizard.addSubsystemTitle')"
      :show-close="false"
    />
    <ValidationObserver ref="form2" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div class="wizard-info-block">
          <div>
            {{ $t('wizard.subsystem.info1') }}
            <br />
            <br />
            {{ $t('wizard.subsystem.info2') }}
          </div>
          <div class="action-block">
            <xrd-button
              outlined
              data-test="select-subsystem-button"
              @click="showSelectClient = true"
              >{{ $t('wizard.subsystem.selectSubsystem') }}</xrd-button
            >
          </div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberName')"
            :help-text="$t('wizard.client.memberNameTooltip')"
          />
          <div data-test="selected-member-name" class="identifier-wrap">
            {{ memberName }}
          </div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberClass')"
            :help-text="$t('wizard.client.memberClassTooltip')"
          />
          <div data-test="selected-member-class" class="identifier-wrap">
            {{ memberClass }}
          </div>
        </div>
        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.memberCode')"
            :help-text="$t('wizard.client.memberCodeTooltip')"
          />
          <div data-test="selected-member-code" class="identifier-wrap">
            {{ memberCode }}
          </div>
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.subsystemCode')"
            :help-text="$t('wizard.client.subsystemCodeTooltip')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="addClient.subsystemCode"
            rules="required|xrdIdentifier"
          >
            <v-text-field
              v-model="subsystemCode"
              class="wizard-form-input"
              type="text"
              :error-messages="errors"
              autofocus
              :placeholder="$t('wizard.subsystemCode')"
              outlined
              data-test="subsystem-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
        <div v-if="duplicateClient" class="wizard-duplicate-warning">
          {{ $t('wizard.subsystem.subsystemExists') }}
        </div>

        <div class="wizard-row-wrap">
          <xrd-form-label
            :label-text="$t('wizard.subsystem.registerSubsystem')"
          />
          <v-checkbox
            v-model="registerChecked"
            color="primary"
            class="register-checkbox"
            data-test="register-subsystem-checkbox"
          ></v-checkbox>
        </div>
      </div>

      <div class="button-footer">
        <div class="button-group">
          <xrd-button outlined data-test="cancel-button" @click="exitView">{{
            $t('action.cancel')
          }}</xrd-button>
        </div>
        <xrd-button
          :disabled="invalid || duplicateClient"
          data-test="submit-add-subsystem-button"
          :loading="submitLoading"
          @click="done"
          >{{ $t('action.addSubsystem') }}</xrd-button
        >
      </div>
    </ValidationObserver>

    <SelectClientDialog
      :title="$t('wizard.addSubsystemTitle')"
      :search-label="$t('wizard.subsystem.searchLabel')"
      :dialog="showSelectClient"
      :selectable-clients="selectableSubsystems"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />

    <xrd-confirm-dialog
      :dialog="confirmRegisterClient"
      title="clients.action.register.confirm.title"
      text="clients.action.register.confirm.text"
      :loading="registerClientLoading"
      @cancel="exitView"
      @accept="registerSubsystem"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { RouteName } from '@/global';
import { containsClient, createClientId } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default Vue.extend({
  components: {
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  props: {
    instanceId: {
      type: String,
      required: true,
    },
    memberClass: {
      type: String,
      required: true,
    },
    memberCode: {
      type: String,
      required: true,
    },
    memberName: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      showSelectClient: false as boolean,
      registerChecked: true as boolean,
      existingSubsystems: [] as Client[],
      selectableSubsystems: [] as Client[],
      subsystemCode: undefined as undefined | string,
      submitLoading: false as boolean,
      confirmRegisterClient: false as boolean,
      registerClientLoading: false as boolean,
    };
  },
  computed: {
    duplicateClient(): boolean {
      if (!this.subsystemCode) {
        return false;
      }

      return containsClient(
        this.existingSubsystems,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
    },
  },

  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    done(): void {
      this.submitLoading = true;
      const body = {
        client: {
          member_name: this.memberName,
          member_class: this.memberClass,
          member_code: this.memberCode,
          subsystem_code: this.subsystemCode,
        },
        ignore_warnings: false,
      };

      api.post('/clients', body).then(
        () => {
          this.submitLoading = false;
          this.showSuccess(this.$t('wizard.subsystem.subsystemAdded'));
          if (this.registerChecked) {
            this.confirmRegisterClient = true;
          } else {
            this.exitView();
          }
        },
        (error) => {
          this.submitLoading = false;
          this.showError(error);
        },
      );
    },

    registerSubsystem(): void {
      this.registerClientLoading = true;

      const clientId = createClientId(
        this.instanceId,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
      api.put(`/clients/${encodePathParameter(clientId)}/register`, {}).then(
        () => {
          this.exitView();
          this.showSuccess(this.$t('wizard.subsystem.subsystemAdded'));
        },
        (error) => {

          this.exitView();
          this.showError(error);
        },
      );
    },

    exitView(): void {
      this.registerClientLoading = false;
      this.confirmRegisterClient = false;
      this.submitLoading = false;
      this.$router.replace({ name: RouteName.Clients });
    },
    saveSelectedClient(selectedMember: Client): void {
      this.subsystemCode = selectedMember.subsystem_code;
      this.showSelectClient = false;
    },
    fetchData(): void {
      // Fetch selectable subsystems
      api
        .get<Client[]>(
          `/clients?instance=${this.instanceId}&member_class=${this.memberClass}&member_code=${this.memberCode}&show_members=false&exclude_local=true&internal_search=false`,
        )
        .then((res) => {
          this.selectableSubsystems = res.data;
        })
        .catch((error) => {
          this.showError(error);
        });

      // Fetch existing subsystems
      api
        .get<Client[]>(
          `/clients?instance=${this.instanceId}&member_class=${this.memberClass}&member_code=${this.memberCode}&internal_search=true`,
        )
        .then((res) => {
          this.existingSubsystems = res.data;
        })
        .catch((error) => {
          this.showError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/wizards';
</style>
