
<template>
  <div class="view-wrap">
    <subViewTitle
      class="view-title"
      :title="$t('initialConfiguration.title')"
      :showClose="false"
      data-test="wizard-title"
    />
    <v-stepper :alt-labels="true" v-model="currentStep" class="stepper noshadow">
      <!-- Headers without anchor page -->
      <v-stepper-header v-if="isAnchorImported" class="noshadow">
        <v-stepper-step
          :complete="currentStep > 1"
          step="1"
        >{{$t('initialConfiguration.member.title')}}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step
          :complete="currentStep > 2"
          step="2"
        >{{$t('initialConfiguration.pin.title')}}</v-stepper-step>
      </v-stepper-header>
      <!-- Headers with anchor page -->
      <v-stepper-header v-else class="noshadow">
        <v-stepper-step
          :complete="currentStep > 1"
          step="1"
        >{{$t('initialConfiguration.anchor.title')}}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step
          :complete="currentStep > 2"
          step="2"
        >{{$t('initialConfiguration.member.title')}}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step
          :complete="currentStep > 3"
          step="3"
        >{{$t('initialConfiguration.pin.title')}}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items v-if="isAnchorImported" class="stepper-content">
        <!-- Member step -->
        <v-stepper-content step="1">
          <OwnerMemberStep @done="ownerMemberReady" :showPreviousButton="false" />
        </v-stepper-content>
        <!-- PIN step -->
        <v-stepper-content step="2">
          <TokenPinStep @previous="currentStep = 1" @done="tokenPinReady" />
        </v-stepper-content>
      </v-stepper-items>

      <v-stepper-items v-else class="stepper-content">
        <!-- Anchor step -->
        <v-stepper-content step="1">
          <ConfigurationAnchorStep @done="configAnchorReady" />
        </v-stepper-content>
        <!-- Member step -->
        <v-stepper-content step="2">
          <OwnerMemberStep @previous="currentStep = 1" @done="ownerMemberReady" />
        </v-stepper-content>
        <!-- PIN step -->
        <v-stepper-content step="3">
          <TokenPinStep @previous="currentStep = 2" @done="tokenPinReady" :saveBusy="pinSaveBusy" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>

    <!-- Confirm dialog for warnings when initializing server -->
    <warningDialog
      :dialog="confirmInitWarning"
      :warnings="warningInfo"
      localizationParent="initialConfiguration.warning"
      @cancel="confirmInitWarning = false"
      @accept="acceptInitWarning()"
    ></warningDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import TokenPinStep from './TokenPinStep.vue';
import ConfigurationAnchorStep from './ConfigurationAnchorStep.vue';
import WarningDialog from './WarningDialog.vue';
import OwnerMemberStep from './OwnerMemberStep.vue';
import { Key, Token } from '@/openapi-types';
import { RouteName, UsageTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    SubViewTitle,
    TokenPinStep,
    ConfigurationAnchorStep,
    OwnerMemberStep,
    WarningDialog,
  },
  props: {},
  computed: {
    ...mapGetters([
      'isAnchorImported',
      'isServerOwnerInitialized',
      'isServerCodeInitialized',
    ]),
  },
  data() {
    return {
      currentStep: 1,
      pinSaveBusy: false,
      warningInfo: [],
      confirmInitWarning: false,
      requestPayload: undefined as any,
    };
  },
  methods: {
    configAnchorReady(): void {
      this.currentStep++;
    },

    ownerMemberReady(): void {
      this.currentStep++;
    },

    tokenPinReady(pin: string): void {
      this.pinSaveBusy = true;

      this.requestPayload = {
        software_token_pin: pin,
        ignore_warnings: false,
      };

      // If owner member is not already set up add it
      if (!this.isServerOwnerInitialized) {
        this.requestPayload.owner_member_class = this.$store.getters.initServerMemberClass;
        this.requestPayload.owner_member_code = this.$store.getters.initServerMemberCode;
      }

      // Add security code if it's not already set up
      if (!this.isServerCodeInitialized) {
        this.requestPayload.security_server_code = this.$store.getters.initServerSSCode;
      }

      this.initServer(this.requestPayload);
    },

    acceptInitWarning(): void {
      this.requestPayload.ignore_warnings = true;
      this.confirmInitWarning = false;
      this.initServer(this.requestPayload);
    },

    initServer(payload: any): void {
      api
        .post('/initialization', payload)
        .then((res) => {
          this.$store.dispatch('showSuccess', 'initialConfiguration.success');
          this.pinSaveBusy = false;
          this.fetchCurrentSecurityServer();
          this.$router.replace({ name: RouteName.Clients });
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmInitWarning = true;
          } else {
            this.$store.dispatch('showError', error);
            this.pinSaveBusy = false;
          }
        });
    },

    fetchInitStatus(): void {
      this.$store.dispatch('fetchInitializationStatus').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    fetchCurrentSecurityServer() {
      this.$store.dispatch('fetchCurrentSecurityServer').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
  created() {
    this.fetchInitStatus();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.view-wrap {
  width: 100%;
  max-width: 850px;
  margin: 10px;
}

.view-title {
  width: 100%;
  max-width: 100%;
  margin-bottom: 30px;
}

.stepper-content {
  width: 100%;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.stepper {
  width: 100%;
}

.noshadow {
  -webkit-box-shadow: none;
  -moz-box-shadow: none;
  box-shadow: none;
}
</style>