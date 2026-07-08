Marker: shared-ui components are migrated from 24f42d62b9f29a810840f5bae328be396c6ee563 (24f42d62 Eneli Reimets <eneli.reimets@nortal.com> on 2023-06-20 at 14:31) version

# X-Road Shared UI Components

This package contains shared UI components for X-Road Security Server and Central Server.

## Customize configuration

See [pnpm Install Reference](https://pnpm.io/installation).
See [Vite Configuration Reference](https://vitejs.dev/config/).

## Project Setup

Install dependencies:

```sh
pnpm install
```

### Building

This package is automatically built as part of pnpm workspace.

### Lint with [ESLint](https://eslint.org/)

```sh
pnpm run lint
```

## API call conventions

### Typing mutation request bodies

Always type JSON mutation request bodies via the operation's generated `*Data['body']` type from `src/openapi-types/types.gen.ts`:

```ts
// Good — body is typed against the OpenAPI contract
const body: RefreshServiceDescriptionData['body'] = { ignore_warnings: true };
api.put(`/service-descriptions/${id}/refresh`, body);

// Bad — inline object literal bypasses the type check
api.put(`/service-descriptions/${id}/refresh`, { ignore_warnings: true });
```

A shared ESLint rule (`local/no-inline-api-body`) enforces this: passing an inline object literal as the 2nd argument to `api.post`, `api.put`, or `api.patch` is a build error. Operations with no request body (`body?: never`) should pass `undefined`.

Multipart `formData` calls are exempt from this rule.
