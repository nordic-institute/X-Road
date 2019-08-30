<template>
  <div>
    <v-snackbar id="error-snackbar" v-model="error" color="error" :timeout="timeout">
      {{ message }}
      <v-btn id="close-snackbar" text @click="error = false">{{$t('action.close')}}</v-btn>
    </v-snackbar>

    <v-snackbar id="success-snackbar" v-model="success" color="success" :timeout="timeout">
      {{ message }}
      <v-btn id="close-snackbar" text @click="success = false">{{$t('action.close')}}</v-btn>
    </v-snackbar>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  // Wrapper for vuetify snackbar component
  data() {
    return {
      message: '',
      success: false,
      error: false,
      timeout: 2000,
    };
  },
  methods: {
    showError(message: string): void {
      this.message = message;
      this.error = true;
    },
    showSuccess(message: string): void {
      this.message = message;
      this.success = true;
    },
  },
});
</script>
