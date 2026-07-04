/*
The MIT License

Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
Copyright (c) 2018 Estonian Information System Authority (RIA),
Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
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
