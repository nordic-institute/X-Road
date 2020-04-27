
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
          <div data-test="selected-member-name">{{memberName}}</div>
        </div>

        <div class="row-wrap">
          <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />
          <div data-test="selected-member-class">{{memberClass}}</div>
        </div>
        <div class="row-wrap">
          <FormLabel labelText="wizard.memberCode" helpText="wizard.client.memberCodeTooltip" />
          <div data-test="selected-member-code">{{memberCode}}</div>
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
        <div v-if="duplicateClient" class="duplicate-warning">{{$t('wizard.client.memberExists')}}</div>

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
            >{{$t('action.cancel')}}</large-button>
          </div>
          <large-button
            @click="done"
            :disabled="invalid || duplicateClient"
            data-test="submit-add-subsystem-button"
          >{{$t('action.addSubsystem')}}</large-button>
        </div>
      </ValidationObserver>

      <SelectClientDialog
        :dialog="showSelectClient"
        :selectableClients="selectableSubsystems"
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
      disableDone: false as boolean,
      showSelectClient: false as boolean,
      registerChecked: true as boolean,
      existingSubsystems: [] as Client[],
      selectableSubsystems: [] as Client[],
      subsystemCode: undefined as undefined | string,
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
      this.$store
        .dispatch('addSubsystem', {
          memberName: this.memberName,
          memberClass: this.memberClass,
          memberCode: this.memberCode,
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
          instanceId: this.instanceId,
          memberClass: this.memberClass,
          memberCode: this.memberCode,
          subsystemCode: this.subsystemCode,
        })
        .then(
          () => {
            this.disableDone = false;
            this.exitView();
          },
          (error) => {
            this.$store.dispatch('showError', error);
            this.exitView();
          },
        );
    },

    exitView(): void {
      this.$router.replace({ name: RouteName.Clients });
    },
    saveSelectedClient(selectedMember: Client): void {
      this.subsystemCode = selectedMember.subsystem_code;
      this.showSelectClient = false;
    },
    fetchData(): void {
      // Fetch selectable subsystems
      api
        .get(
          `/clients?instance=${this.instanceId}&member_class=${this.memberClass}&member_code=${this.memberCode}&show_members=false&exclude_local=true`,
        )
        .then((res) => {
          this.selectableSubsystems = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });

      // Fetch existing subsystems
      api
        .get(
          `/clients?instance=${this.instanceId}&member_class=${this.memberClass}&member_code=${this.memberCode}&internal_search=true`,
        )
        .then((res) => {
          console.log(res.data);
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