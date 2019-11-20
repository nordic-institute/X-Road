<script lang="ts">
/**
 * Mixin to centralize snackbar messages. Adds event bus for the appliccation.
 * Snackbar component needs to be added in template where it's used.
 */

import Snackbar from './Snackbar.vue';

import Vue from 'vue';
import { VueConstructor } from 'vue';

interface ISnackbar {
  showSuccess(message: string): void;
  showError(message: string): void;
}

// Telling typescript that there is snackbar in refs
export default (Vue as VueConstructor<
  Vue & {
    $refs: {
      snackbar: ISnackbar;
    };
  }
>).extend({
  components: {
    Snackbar,
  },
  methods: {
    showSuccessSnackbar(messageKey: string): void {
      // Translate localisation key and pass it forward to snackbar component
      this.$refs.snackbar.showSuccess(this.$t(messageKey) as string);
    },
    showErrorSnackbar(message: string): void {
      this.$refs.snackbar.showError(message);
    },
  },
  created() {
    this.$bus.$on('show-success', this.showSuccessSnackbar);
    this.$bus.$on('show-error', this.showErrorSnackbar);
  },
});
</script>
