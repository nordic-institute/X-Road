# Security Server Admin Service UI

UI application for Security Server Admin Service. 

Tools and libraries used in this project:
- [Vue.js](https://vuejs.org/)
- [Vuetify](https://vuetifyjs.com/)
- [Vue i18n](https://kazupon.github.io/vue-i18n/)
- [VeeValidate](https://vee-validate.logaretm.com/v4/)
- [Axios](https://axios-http.com/)
- [Pinia](https://pinia.esm.dev/)
- [Vite](https://vitejs.dev/)
- [pnpm](https://pnpm.io/)

## Building




## Development

Individual `pnpm` scripts can be used for development.

### Install dependencies
```
pnpm install
```

### Compiles and hot-reloads for development

Starts development server on port `8080`
```
pnpm run dev
```

### Compiles and minifies for production
```
pnpm run build
```

### Run tests
```
pnpm run test
```

### Lints and fixes files
```
pnpm run lint
```

## Customize configuration

[Vite's Env variables](https://vitejs.dev/guide/env-and-mode#env-files) can be used to customize build environment.
To use custom Env variables, create `.env.local` file in the root of the project. 
Apart from variables in [.env](.env) file, `PROXY_ADDRESS` can be used to set target running admin service API. 
`PROXY_ADDRESS` default value is `https://localhost:4200`, which corresponds to `ss0` Admin Service API running in 
[docker compose environment](../../../../Docker/xrd-dev-stack/README.md).

