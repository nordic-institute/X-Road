import vuetifyConfig from '../src/plugins/vuetify';
import '../src/plugins/vee-validate';
import 'roboto-fontface/css/roboto/roboto-fontface.css';
import "@mdi/font/css/materialdesignicons.css";
import i18n from '../src/i18n';

export const parameters = {
  actions: { argTypesRegex: "^on[A-Z].*" },
}

const appDecorator = () => {
  return {
    vuetify: vuetifyConfig,
    i18n,
    template: `
      <v-app>
          <v-main>
            <story/>
          </v-main>
      </v-app>
    `
  };
};

export const decorators = [appDecorator];
