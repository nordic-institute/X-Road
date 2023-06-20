// Vuetify
import 'vuetify/styles'
import { createVuetify } from 'vuetify'

export default createVuetify({

  theme: {
    //TODO check customProperties in https://vuetifyjs.com/en/getting-started/upgrade-guide/
    themes: {
      light: {
        colors: {
          primary: '#663cdc',
          secondary: '#00C9E7',
          //accent: '#8c9eff',
        },
      },
    },
  },
});
