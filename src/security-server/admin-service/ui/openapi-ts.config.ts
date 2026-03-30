import {defineConfig} from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../../openapi-model/src/main/resources/META-INF/openapi-definition.yaml',
  output: './src/openapi-types',
  plugins: [
    {
      enums: true,
      name: '@hey-api/typescript',
    },
  ],
});
