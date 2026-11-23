// V1, bloco 3: fotografa as cenas da demonstração (1920x1080) com a API simulada, sem back-end, SIGA ou Docker.
// Saída: front/test-results/video/capturas/NN-cena.png (fora do Git). Uso: node tools/video-demo/capturar.mjs
import { spawnSync } from 'node:child_process';
import { readdirSync, rmSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const front = join(raiz, 'front');
const pasta = join(front, 'test-results', 'video', 'capturas');

rmSync(pasta, { recursive: true, force: true });
mkdirSync(pasta, { recursive: true });

const r = spawnSync('npx', ['playwright', 'test', '--config', 'playwright.video.config.ts'], { cwd: front, stdio: 'inherit', shell: true });
if (r.status !== 0) {
  console.error('A captura falhou; veja a saída acima.');
  process.exit(r.status ?? 1);
}
console.log('\nCapturas geradas:');
for (const arquivo of readdirSync(pasta).sort()) console.log(join(pasta, arquivo));
