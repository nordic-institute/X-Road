// This file is the entry point for the library build
import { VueConstructor } from "vue";
import VuetifyButton from "./components/VuetifyButton.vue";
// Import vee-validate so it's configured on the library build
import "./plugins/vee-validate";

const SharedComponents = {
  install(Vue: VueConstructor, options?: any) {
    /* 
    // In theory a shared vuex store could be used like this:
    if (!options || !options.store) {
      throw new Error('Please initialise plugin with a Vuex store.')
    }
    options.store.registerModule('dummylib', store) 
    */

    Vue.component("vuetify-button", VuetifyButton);
  }
};

export default SharedComponents;
