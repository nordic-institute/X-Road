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
    <subViewTitle
      class="view-title"
      :title="$t('wizard.subsystem.title')"
      :showClose="false"
    />

    <div class="content">
      <div class="info-block">
        <div>
          {{ $t('wizard.subsystem.info1') }}
          <br />
          <br />
          {{ $t('wizard.subsystem.info2') }}
        </div>
        <div class="action-block">
          <large-button
            @click="showSelectClient = true"
            outlined
            data-test="select-subsystem-button"
            >{{ $t('wizard.subsystem.selectSubsystem') }}</large-button
          >
        </div>
      </div>

      <ValidationObserver ref="form2" v-slot="{ invalid }">
        <div class="row-wrap">
          <FormLabel
            labelText="wizard.memberName"
            helpText="wizard.client.memberNameTooltip"
          />
          <div data-test="selected-member-name" class="identifier-wrap">
            {{ memberName }}
          </div>
        </div>

        <div class="row-wrap">
          <FormLabel
            labelText="wizard.memberClass"
            helpText="wizard.client.memberClassTooltip"
          />
          <div data-test="selected-member-class" class="identifier-wrap">
            {{ memberClass }}
          </div>
        </div>
        <div class="row-wrap">
          <FormLabel
            labelText="wizard.memberCode"
            helpText="wizard.client.memberCodeTooltip"
          />
          <div data-test="selected-member-code" class="identifier-wrap">
            {{ memberCode }}
          </div>
        </div>

        <div class="row-wrap">
          <FormLabel
            labelText="wizard.subsystemCode"
            helpText="wizard.client.subsystemCodeTooltip"
          />

          <ValidationProvider
            name="addClient.subsystemCode"
            rules="required|xrdIdentifier"
            v-slot="{ errors }"
          >
            <v-text-field
              class="form-input"
              type="text"
              :error-messages="errors"
              v-model="subsystemCode"
              autofocus
              data-test="subsystem-code-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
        <div v-if="duplicateClient" class="duplicate-warning">
          {{ $t('wizard.client.memberExists') }}
        </div>

        <div class="row-wrap">
          <FormLabel labelText="wizard.subsystem.registerSubsystem" />
          <v-checkbox
            v-model="registerChecked"
            color="primary"
            class="register-checkbox"
            data-test="register-subsystem-checkbox"
          ></v-checkbox>
        </div>
        <div class="button-footer">
          <div class="button-group">
            <large-button
              outlined
              @click="exitView"
              data-test="cancel-button"
              >{{ $t('action.cancel') }}</large-button
            >
          </div>
          <large-button
            @click="done"
            :disabled="invalid || duplicateClient"
            data-test="submit-add-subsystem-button"
            :loading="submitLoading"
            >{{ $t('action.addSubsystem') }}</large-button
          >
        </div>
      </ValidationObserver>

      <SelectClientDialog
        :dialog="showSelectClient"
        :selectableClients="selectableSubsystems"
        @cancel="showSelectClient = false"
        @save="saveSelectedClient"
      />

      <ConfirmDialog
        :dialog="confirmRegisterClient"
        title="clients.action.register.confirm.title"
        text="clients.action.register.confirm.text"
        @cancel="exitView"
        @accept="registerSubsystem"
        :loading="registerClientLoading"
      />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import FormLabel from '@/components/ui/FormLabel.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import { RouteName } from '@/global';
import { containsClient, createClientId } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
    SubViewTitle,
    ConfirmDialog,
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
  methods: {
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
          this.$store.dispatch(
            'showSuccess',
            'wizard.subsystem.subsystemAdded',
          );
          if (this.registerChecked) {
            this.confirmRegisterClient = true;
          } else {
            this.exitView();
          }
        },
        (error) => {
          this.submitLoading = false;
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch(
            'showSuccess',
            'wizard.subsystem.subsystemAdded',
          );
          this.exitView();
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.exitView();
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
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch('showError', error);
        });
    },
  },

  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';
@import '../../assets/wizards';

.view-wrap {
  width: 100%;
  max-width: 1000px;
  margin: 10px;

  .view-title {
    width: 100%;
    max-width: 100%;
    margin-bottom: 30px;
  }

  .info-block {
    display: flex;
    flex-direction: row;
    margin-bottom: 40px;

    .action-block {
      margin-top: 30px;
      margin-left: auto;
      margin-right: 0px;
    }
  }

  .duplicate-warning {
    margin-left: 230px;
    margin-top: 10px;
    color: #ff5252;
    font-size: 12px;
  }
}
</style>
