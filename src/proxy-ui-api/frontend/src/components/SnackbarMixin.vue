<script>
/**
 * Mixin to centralize snackbar messages. Adds event bus for the appliccation.
 * Snackbar component needs to be added in template where it's used.
 */
import Snackbar from "./Snackbar";

export default {
  components: {
    Snackbar
  },
  methods: {
    showSuccessSnackbar(message) {
      this.$refs.snackbar.showSuccess(message);
      console.log("mixin");
    },
    showErrorSnackbar(message) {
      this.$refs.snackbar.showError(message);
      console.log("mixin");
    }
  },
  created() {
    this.$bus.$on("show-success", this.showSuccessSnackbar);
    this.$bus.$on("show-error", this.showErrorSnackbar);
  }
};
</script>
