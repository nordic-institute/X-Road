<template>
  <div class="wrapper">
    <div>
      Vuetify component -
      <br />
      {{ hello }}
  <!--   Localised: {{$t('locals_demo')}} -->
    </div>
    <v-btn
      @click="increment"
      :disabled="disabled"
      rounded
      color="primary"
      class="large-button"
      >{{ text }}</v-btn
    >
    <!-- Localised: {{$t('warning')}} -->
    <ValidationProvider name="username" :rules="'required'" v-slot="{ errors }">
      <v-text-field
        name="username"
        type="text"
        v-model="textf"
        :error-messages="errors"
      ></v-text-field>
    </ValidationProvider>
  </div>
</template>
<script lang="ts">
import Vue from "vue";
import { ValidationProvider } from "vee-validate";
export default Vue.extend({
  components: {
    ValidationProvider
  },
  props: {
    disabled: {
      type: Boolean,
      default: false
    },
    hello: {
      type: String,
      required: false,
      default: "wow!"
    }
  },
  data() {
    return {
      count: 0,
      textf: ""
    };
  },
  computed: {
    times(): string {
      return this.count > 1 ? "times" : "time";
    },
    text(): string {
      return `Clicked ${this.count} ${this.times}`;
    }
  },
  methods: {
    increment(): void {
      this.count += 1;
    }
  },
  created() {
    console.log("library $t: ");
    console.log(this.$t);
   // console.log(this.$t('locals_demo'));
  }
});
</script>

<style lang="scss" scoped>
$large-button-width: 300px;

.wrapper {
  border: solid grey 3px;
  width: 320px;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin: 10px;
}

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
