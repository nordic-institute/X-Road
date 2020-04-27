
<template>
  <div class="view-wrap">
    <subViewTitle class="view-title" :title="$t('wizard.subsystem.title')" :showClose="false" />

    <div class="content">
      <div class="info-block">
        <div>
          {{$t('wizard.subsystem.info1')}}
          <br />
          <br />
          {{$t('wizard.subsystem.info2')}}
        </div>
        <div class="action-block">
          <large-button
            @click="showSelectClient = true"
            outlined
            data-test="select-subsystem-button"
          >{{$t('wizard.subsystem.selectSubsystem')}}</large-button>
        </div>
      </div>

      <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
        <div class="row-wrap">
          <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
          <div v-if="client" data-test="selected-member-name">{{client.member_name}}</div>
        </div>

        <div class="row-wrap">
          <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />
          <div v-if="client" data-test="selected-member-class">{{client.member_class}}</div>
        </div>
        <div class="row-wrap">
          <FormLabel labelText="wizard.memberCode" helpText="wizard.client.memberCodeTooltip" />
          <div v-if="client" data-test="selected-member-code">{{client.member_code}}</div>
        </div>

        <div class="row-wrap">
          <FormLabel
            labelText="wizard.subsystemCode"
            helpText="wizard.client.subsystemCodeTooltip"
          />

          <ValidationProvider name="addClient.subsystemCode" rules="required" v-slot="{ errors }">
            <v-text-field
              class="form-input"
              type="text"
              :error-messages="errors"
              v-model="subsystemCode"
              data-test="subsystem-code-input"
            ></v-text-field>
          </ValidationProvider>
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
        <div v-if="duplicateClient" class="duplicate-warning">{{$t('wizard.client.memberExists')}}</div>
        <div class="button-footer">
          <div class="button-group">
            <large-button outlined @click="cancel" data-test="cancel-button">{{$t('action.cancel')}}</large-button>
          </div>
          <large-button
            @click="done"
            :disabled="invalid"
            data-test="submit-add-subsystem-button"
          >{{$t('action.addSubsystem')}}</large-button>
        </div>
      </ValidationObserver>

      <SelectClientDialog
        :dialog="showSelectClient"
        :selectableClients="selectableClients"
        @cancel="showSelectClient = false"
        @save="saveSelectedClient"
      />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import SubsystemDetailsPage from './SubsystemDetailsPage.vue';
import SelectClientDialog from '@/views/AddClient/SelectClientDialog.vue';
import FormLabel from '@/components/ui/FormLabel.vue';
import { Key, Token } from '@/types';
import { RouteName, UsageTypes } from '@/global';
import { containsClient } from '@/util/helpers';
import { Client } from '@/types';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

import * as api from '@/util/api';

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
    SubViewTitle,
  },
  props: {
    clientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      disableDone: false as boolean,
      showSelectClient: false as boolean,
      registerChecked: true as boolean,
    };
  },
  computed: {
    ...mapGetters(['client', 'selectableClients', 'reservedClients']),
    memberClass: {
      get(): string {
        return this.$store.getters.memberClass;
      },
      set(value: string) {
        this.$store.commit('setMemberClass', value);
      },
    },

    memberCode: {
      get(): string {
        return this.$store.getters.memberCode;
      },
      set(value: string) {
        this.$store.commit('setMemberCode', value);
      },
    },

    subsystemCode: {
      get(): string {
        return this.$store.getters.subsystemCode;
      },
      set(value: string) {
        this.$store.commit('setSubsystemCode', value);
      },
    },

    selectedMember: {
      get(): Client {
        return this.$store.getters.selectedMember;
      },
      set(value: Client) {
        this.$store.commit('setMember', value);
      },
    },

    duplicateClient(): boolean {
      return containsClient(
        this.reservedClients,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
    },
  },
  methods: {
    cancel(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },
    done(): void {
      this.$store
        .dispatch('addSubsystem', {
          memberName: this.client.member_name,
          memberClass: this.client.member_class,
          memberCode: this.client.member_code,
          subsystemCode: this.subsystemCode,
        })
        .then(
          () => {
            this.disableDone = false;

            if (this.registerChecked) {
              this.registerSubsystem();
            } else {
              this.exitView();
            }
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },

    registerSubsystem(): void {
      this.$store
        .dispatch('registerClient', {
          instanceId: this.client.instance_id,
          memberClass: this.client.member_class,
          memberCode: this.client.member_code,
          subsystemCode: this.subsystemCode,
        })
        .then(
          () => {
            this.disableDone = false;
            this.exitView();
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        );
    },

    exitView(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },
    saveSelectedClient(selectedMember: Client): void {
      this.$store.dispatch('setSelectedMember', selectedMember).then(
        () => {
          this.$store.dispatch('fetchReservedSubsystems', selectedMember);
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
      this.showSelectClient = false;
    },
    fetchData(): void {
      // Fetch "parent" client from backend
      this.$store.dispatch('fetchClient', this.clientId).then(
        () => {
          this.$store.dispatch('fetchSelectableForSubsystem', this.client);
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
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