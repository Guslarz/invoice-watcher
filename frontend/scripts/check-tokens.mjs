import {readFileSync, readdirSync} from 'node:fs';
import {join, extname} from 'node:path';

function checkTokens(directory) {
  for (const entry of readdirSync(directory, {withFileTypes: true})) {
    if (
      ['dist', 'node_modules', 'tokens', 'build', '.gradle'].includes(
        entry.name,
      )
    )
      continue;
    const path = join(directory, entry.name);
    if (entry.isDirectory()) checkTokens(path);
    else if (['.ts', '.html', '.css', '.scss'].includes(extname(path)))
      checkFile(path);
  }
}
function checkFile(path) {
  if (/#(?:[0-9a-f]{3}){1,2}\b|rgba?\(/i.test(readFileSync(path, 'utf8')))
    throw Error(`${path}: use shared design tokens`);
}
checkTokens('apps');
checkTokens('libs');
