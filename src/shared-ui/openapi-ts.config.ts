import {defineConfig} from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../common/common-admin-api/src/main/resources/common-openapi-definition.yaml',
  output: './src/openapi-types',
  plugins: [
    {
      enums: true,
      name: '@hey-api/typescript',
    },
  ],
});
