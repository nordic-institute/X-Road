<template>
  <div>
      <v-snackbar
        v-model="error"
        color="error"
        :timeout="timeout"
      >
        {{ message }}
        <v-btn
           flat
          @click="error = false"
        >
          Close
        </v-btn>
      </v-snackbar>

      <v-snackbar
        v-model="success"
        color="success"
        :timeout="timeout"
      >
        {{ message }}
        <v-btn
          flat
          @click="success = false"
        >
          Close
        </v-btn>
      </v-snackbar>
  </div>
</template>
<script>
export default {
  // Wrapper for vuetify snackbar component
  data() {
    return {
      message: null,
      success: false,
      error: false,
      timeout: 2000
    }
  },
  methods: {
    showError(message) {
      this.message = message;
      this.error = true;
    },
    showSuccess(message) {
      this.message = message;
      this.success = true;
    }
  }
}
</script>
