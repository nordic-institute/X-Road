<template>
  <div>
    <div class="info-block">
      <div>
        {{$t('wizard.clientInfo1')}}
        <br />
        <br />
        {{$t('wizard.clientInfo2')}}
      </div>
      <div class="action-block">
        <large-button
          @click="showSelectClient = true"
          outlined
          data-test="select-client-button"
        >{{$t('wizard.selectClient')}}</large-button>
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberName" helpText="wizard.client.memberNameTooltip" />
        <div data-test="selected-member-name">{{selectedMemberName}}</div>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.memberClass" helpText="wizard.client.memberClassTooltip" />

        <ValidationProvider name="addClient.memberClass" rules="required" v-slot="{ errors }">
          <v-select
            :items="memberClasses"
            :error-messages="errors"
            class="form-input"
            v-model="memberClass"
            data-test="member-class-input"
          ></v-select>
        </ValidationProvider>
      </div>
      <div class="row-wrap">
        <FormLabel labelText="wizard.memberCode" helpText="wizard.client.memberCodeTooltip" />

        <ValidationProvider name="addClient.memberCode" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="memberCode"
            data-test="member-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <FormLabel labelText="wizard.subsystemCode" helpText="wizard.client.subsystemCodeTooltip" />

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
      <div class="button-footer">
        <div class="button-group">
          <large-button outlined @click="cancel" data-test="cancel-button">{{$t('action.cancel')}}</large-button>
        </div>
        <large-button
          @click="done"
          :disabled="invalid || duplicateClient"
          data-test="next-button"
        >{{$t('action.next')}}</large-button>
      </div>
    </ValidationObserver>

    <SelectClientDialog
      :dialog="showSelectClient"
      :selectableClients="selectableClients"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import FormLabel from '@/components/ui/FormLabel.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { Client } from '@/openapi-types';
import { containsClient } from '@/util/helpers';

import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  computed: {
    ...mapGetters([
      'reservedClients',
      'selectableClients',
      'memberClasses',
      'selectedMemberName',
    ]),

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

    duplicateClient(): boolean {
      return containsClient(
        this.reservedClients,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
    },
  },
  data() {
    return {
      disableDone: false,
      certificationService: undefined,
      filteredServiceList: [],
      showSelectClient: false as boolean,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.$store.dispatch('setSelectedMember', selectedMember).then(
        (response) => {
          this.$store.dispatch('fetchReservedClients', selectedMember);
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
      this.showSelectClient = false;
    },
  },
  created() {
    this.$store.dispatch('fetchSelectableClients');
    this.$store.dispatch('fetchMemberClasses');
  },

  watch: {
    memberClasses(val) {
      // Set first member class selected as default when the list is updated
      if (val?.length === 1) {
        this.memberClass = val[0];
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

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
</style>

