<template>
  <div>
    <!-- Error: raw text  -->
    <v-snackbar
      data-test="error-snackbar"
      v-model="showErrorRaw"
      color="error"
      :timeout="timeout"
    >
      {{ errorMessageRaw }}
      <v-btn
        icon
        color="white"
        data-test="close-snackbar"
        @click="closeError()"
      >
        <v-icon dark>mdi-close-circle</v-icon>
      </v-btn>
    </v-snackbar>

    <!-- Error: localization code. Doesn't close automatically  -->
    <v-snackbar
      data-test="error-snackbar"
      v-model="showErrorCode"
      color="error"
      :timeout="forever"
    >
      {{ $t(errorMessageCode) }}
      <v-btn
        icon
        color="white"
        data-test="close-snackbar"
        @click="closeError()"
      >
        <v-icon dark>mdi-close-circle</v-icon>
      </v-btn>
    </v-snackbar>

    <!-- Success: localization code -->
    <v-snackbar
      data-test="success-snackbar"
      v-model="showSuccessCode"
      color="success"
      :timeout="timeout"
    >
      {{ $t(successMessageCode) }}
      <v-btn data-test="close-snackbar" text @click="closeSuccess()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Success: raw text -->
    <v-snackbar
      data-test="success-snackbar"
      v-model="showSuccessRaw"
      color="success"
      :timeout="timeout"
    >
      {{ successMessageRaw }}
      <v-btn data-test="close-snackbar" text @click="closeSuccess()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Error: Object. Doesn't close automatically -->
    <v-snackbar
      data-test="indefinite-snackbar"
      v-if="errorObject"
      v-model="showError"
      :timeout="forever"
      color="error"
      multi-line
    >
      <div class="row-wrapper scrollable">
        <div v-if="errorCode">
          {{ $t('error_code.' + errorCode) }}
        </div>
        <div v-else="">
          {{ errorObject }}
        </div>

        <!-- Show the error metadata if it exists -->
        <div v-for="meta in errorMetadata" :key="meta">
          {{ meta }}
        </div>

        <!-- Error ID -->
        <div v-if="errorId">
          {{ $t('id') }}:
          {{ errorId }}
        </div>
      </div>

      <template v-if="errorId">
        <v-btn
          outlined
          color="white"
          data-test="copy-id-button"
          @click.prevent="copyId"
          >{{ $t('action.copyId') }}
        </v-btn>
      </template>

      <v-btn
        icon
        color="white"
        data-test="close-snackbar"
        @click="closeError()"
      >
        <v-icon dark>mdi-close-circle</v-icon>
      </v-btn>
    </v-snackbar>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { toClipboard } from '@/util/helpers';

export default Vue.extend({
  // Component for snackbar notifications
  computed: {
    ...mapGetters([
      'successMessageCode',
      'successMessageRaw',
      'errorMessageRaw',
      'errorMessageCode',
      'errorObject',
    ]),

    showSuccessCode: {
      get(): string {
        return this.$store.getters.showSuccessCode;
      },
      set(value: string) {
        this.$store.commit('setSuccessCodeVisible', value);
      },
    },
    showSuccessRaw: {
      get(): string {
        return this.$store.getters.showSuccessRaw;
      },
      set(value: string) {
        this.$store.commit('setSuccessRawVisible', value);
      },
    },
    showError: {
      get(): string {
        return this.$store.getters.showErrorObject;
      },
      set(value: string) {
        this.$store.commit('setErrorObjectVisible', value);
      },
    },
    showErrorRaw: {
      get(): string {
        return this.$store.getters.showErrorRaw;
      },
      set(value: string) {
        this.$store.commit('setErrorRawVisible', value);
      },
    },
    showErrorCode: {
      get(): string {
        return this.$store.getters.showErrorCode;
      },
      set(value: string) {
        this.$store.commit('setErrorCodeVisible', value);
      },
    },
    errorCode(): string | undefined {
      if (this.errorObject?.response?.data?.error?.code) {
        return this.errorObject.response.data.error.code;
      }

      return undefined;
    },
    errorId(): string | undefined {
      if (this.errorObject?.response?.headers['x-road-ui-correlation-id']) {
        return this.errorObject.response.headers['x-road-ui-correlation-id'];
      }

      return undefined;
    },
    errorMetadata(): string[] {
      if (this.errorObject?.response?.data?.error?.metadata) {
        return this.errorObject.response.data.error.metadata;
      }

      return [];
    },
  },

  data() {
    return {
      timeout: 2000,
      forever: 0,
    };
  },
  methods: {
    closeSuccess(): void {
      this.$store.commit('setSuccessRawVisible', false);
      this.$store.commit('setSuccessCodeVisible', false);
    },
    closeError(): void {
      this.$store.commit('setErrorRawVisible', false);
      this.$store.commit('setErrorCodeVisible', false);
      this.$store.commit('setErrorObjectVisible', false);
    },
    copyId(): void {
      if (this.errorId) {
        toClipboard(this.errorId);
      }
    },
  },
});
</script>

<style lang="scss" scoped>
.row-wrapper {
  display: flex;
  flex-direction: column;
  overflow: auto;
  overflow-wrap: break-word;
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}

</style>
