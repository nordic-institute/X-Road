/**
 * Generates openapi-msw-paths.d.ts from the CS OpenAPI spec.
 *
 * The CS spec has two non-standard issues that prevent `openapi-typescript` from
 * processing it directly:
 *
 * 1. Discriminator mapping values use bare schema names (e.g. "ClientId") instead
 *    of $ref format ("#/components/schemas/ClientId"). This fails Redocly bundling.
 *
 * 2. The spec references an external file via relative paths
 *    ("../../../../../common/common-admin-api/.../common-openapi-definition.yaml").
 *    `openapi-typescript` must be invoked from the spec's own directory so that
 *    relative refs resolve correctly.
 *
 * This script pre-processes the spec in memory (fixes discriminator mappings),
 * writes a temporary patched copy next to the original, generates the types,
 * then removes the temporary file.
 */

import { execSync } from 'node:child_process';
import { readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const uiDir = resolve(__dirname, '..');
const specDir = resolve(uiDir, '../../openapi-model/src/main/resources');
const specPath = resolve(specDir, 'openapi-definition.yaml');
const patchedSpecPath = resolve(specDir, 'openapi-definition-msw-patched.yaml');
const outputPath = resolve(uiDir, 'src/openapi-msw-paths.d.ts');

function fixDiscriminatorMappings(content) {
  const lines = content.split('\n');
  let inMapping = false;
  let mappingIndent = 0;
  const result = [];

  for (const line of lines) {
    const stripped = line.trimStart();
    if (stripped.startsWith('mapping:')) {
      inMapping = true;
      mappingIndent = line.length - stripped.length;
      result.push(line);
    } else if (inMapping) {
      const curIndent = line.length - line.trimStart().length;
      if (line.trim() === '' || (line.trim() && curIndent <= mappingIndent)) {
        inMapping = false;
        result.push(line);
      } else {
        const m = line.match(/^(\s+[\w_A-Z]+): (.+)$/);
        if (m) {
          const keyPart = m[1];
          const val = m[2].trim().replace(/^["']|["']$/g, '');
          if (!val.startsWith('#/')) {
            result.push(`${keyPart}: "#/components/schemas/${val}"`);
          } else {
            result.push(line);
          }
        } else {
          result.push(line);
        }
      }
    } else {
      result.push(line);
    }
  }

  return result.join('\n');
}

const original = readFileSync(specPath, 'utf-8');
const patched = fixDiscriminatorMappings(original);
writeFileSync(patchedSpecPath, patched, 'utf-8');

try {
  const cmd = [
    'node',
    resolve(uiDir, '../../../node_modules/.pnpm/openapi-typescript@7.13.0_typescript@5.9.3/node_modules/openapi-typescript/bin/cli.js'),
    'openapi-definition-msw-patched.yaml',
    '-o',
    outputPath,
  ].join(' ');

  execSync(cmd, { cwd: specDir, stdio: 'inherit' });
} finally {
  try {
    unlinkSync(patchedSpecPath);
  } catch {
    // ignore cleanup errors
  }
}
