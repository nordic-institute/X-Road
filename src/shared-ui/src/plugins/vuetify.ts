import Vue from 'vue';
import Vuetify from 'vuetify';
Vue.use(Vuetify);

export default new Vuetify({
  theme: {
    options: {
      customProperties: true,
    },
    themes: {
      light: {
        primary: '#663cdc',
        secondary: '#00C9E7',
        accent: '#8c9eff',
      },
    },
  },
});
