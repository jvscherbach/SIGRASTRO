// Testes da guarda 2 (node --test). Só entradas simuladas: nenhum arquivo de segredo do projeto é lido.
// S6.1, bloco 2: caminhos normalizados, link simbólico, Grep/Glob sobre pasta (com um .gitignore de teste) e a
// configuração de `permissions.deny` do settings.json. A pasta de teste é temporária e tem uma isca fictícia.

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import os, { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, test } from 'node:test';

import {
  REGRA, REGRA_PASTA, avaliar, caminhoProibido, ignoradoPeloGit, normalizar, padraoAlcancaSegredo,
} from './guarda-arquivos.mjs';

const HOOK = fileURLToPath(new URL('./guarda-arquivos.mjs', import.meta.url));
const RAIZ_DO_PROJETO = path.resolve(path.dirname(HOOK), '..', '..');

// Pasta temporária no papel de "projeto": .env.local com isca (ignorado pelo .gitignore de teste), uma subpasta
// limpa e uma subpasta com segredo NÃO ignorado.
const PASTA = mkdtempSync(path.join(tmpdir(), 'guarda-arquivos-'));
const ISCA = `ISCA-${Math.random().toString(36).slice(2)}`;
writeFileSync(path.join(PASTA, '.gitignore'), '.env.local\n.env.*\n!.env.*.example\n');
writeFileSync(path.join(PASTA, '.env.local'), `TOKEN=${ISCA}\n`);
writeFileSync(path.join(PASTA, '.env.local.example'), 'TOKEN=\n');
mkdirSync(path.join(PASTA, 'src'), { recursive: true });
writeFileSync(path.join(PASTA, 'src', 'a.txt'), 'texto\n');
mkdirSync(path.join(PASTA, 'vazado'), { recursive: true });
writeFileSync(path.join(PASTA, 'vazado', 'mapa-local.csv'), `${ISCA}\n`); // segredo fora do .gitignore de teste

let antigoProjeto;
before(() => {
  antigoProjeto = process.env.CLAUDE_PROJECT_DIR;
  delete process.env.CLAUDE_PROJECT_DIR;
});
after(() => {
  if (antigoProjeto !== undefined) process.env.CLAUDE_PROJECT_DIR = antigoProjeto;
  rmSync(PASTA, { recursive: true, force: true });
});

const entrada = (tool_name, tool_input, cwd = PASTA) => ({ hook_event_name: 'PreToolUse', tool_name, tool_input, cwd });

test('exemplos e arquivos comuns passam', () => {
  for (const caminho of ['.env.local.example', '.env.example', 'D:\\Projeto\\siga-vistas\\.env.local.example',
    '/projeto/.env.local.example', 'README.md', 'docs/12-configuracoes.md', '.envrc', 'compose.local.yaml',
    '~/.bashrc', 'src/a.txt']) {
    assert.equal(avaliar(entrada('Read', { file_path: caminho })), null, caminho);
  }
});

test('segredos bloqueados em todas as ferramentas de arquivo', () => {
  const casos = [
    ['Read', { file_path: 'D:\\Projeto\\siga-vistas\\.env.local' }],
    ['Read', { file_path: '/projeto/.env' }],
    ['Read', { file_path: '.env.producao' }],
    ['Edit', { file_path: '.env.local', old_string: 'a', new_string: 'b' }],
    ['Write', { file_path: '.env.local', content: 'x' }],
    ['MultiEdit', { file_path: '.env.local', edits: [] }],
    ['NotebookEdit', { notebook_path: '.env.local', new_source: 'x' }],
    ['Grep', { pattern: 'SIGA', path: '.env.local' }],
    ['Grep', { pattern: 'SIGA', path: '.', glob: '.env*' }],
    ['Glob', { pattern: '**/.env.local' }],
    ['Glob', { pattern: '**/.env*' }],
    ['Glob', { pattern: '{README.md,.env.local}' }],
    ['Read', { file_path: 'amostras-locais/mapa-local.csv' }],
    ['Read', { file_path: '.secrets/token-arquivos' }],
    ['Read', { file_path: '/run/secrets-vistas/token-arquivos' }],
  ];
  for (const [ferramenta, dados] of casos) {
    const bloqueio = avaliar(entrada(ferramenta, dados));
    assert.ok(bloqueio, `${ferramenta} ${JSON.stringify(dados)}`);
    assert.match(bloqueio, /\.env\.local\.example/);
  }
});

test('caminho normalizado: .., ./, barra invertida, maiúsculas, ~ e caminho POSIX do Windows', () => {
  const formas = [
    'src/../.env.local', './src/./../.env.local', 'src\\..\\.env.local', '.\\.ENV.LOCAL', 'SRC/../.Env.Local',
    path.join(PASTA, 'src', '..', '.env.local'), `${PASTA}${path.sep}.env.local`,
    '~/.rastro/env.local', '~\\.rastro\\env.local', '~/.ssh/id_ed25519', '~/.aws/credentials',
    '~/.git-credentials', '~/.npmrc', '~/.claude/.credentials.json', '~/.docker/config.json',
  ];
  if (process.platform === 'win32') {
    const unidade = PASTA[0].toLowerCase();
    formas.push(`/${unidade}${PASTA.slice(2).replace(/\\/g, '/')}/.env.local`);
    formas.push(`//${unidade}${PASTA.slice(2).replace(/\\/g, '/')}/.env.local`);
  }
  for (const forma of formas) {
    assert.ok(avaliar(entrada('Read', { file_path: forma })), `deveria bloquear: ${forma}`);
  }
  assert.equal(normalizar('~', PASTA), os.homedir());
  assert.ok(caminhoProibido('src/../.env.local', PASTA));
});

test('link simbólico com nome inocente que aponta para o segredo é bloqueado', (t) => {
  const link = path.join(PASTA, 'inocente.txt');
  try {
    symlinkSync(path.join(PASTA, '.env.local'), link, 'file');
  } catch (erro) {
    t.skip(`este sistema não deixa criar link simbólico sem privilégio (${erro.code}); a regra usa realpath`);
    return;
  }
  assert.equal(avaliar(entrada('Read', { file_path: 'inocente.txt' })), REGRA);
  assert.equal(avaliar(entrada('Edit', { file_path: link, old_string: 'a', new_string: 'b' })), REGRA);
});

test('Grep sobre pasta: segredo ignorado pelo .gitignore não é alcançado; fora dele, bloqueia', () => {
  // Raiz de teste: .env.local é ignorado, mas vazado/mapa-local.csv NÃO: bloqueia.
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: '.' })), REGRA_PASTA);
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN' })), REGRA_PASTA, 'sem path = pasta atual');
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: 'vazado' })), REGRA_PASTA);
  // Subpasta limpa passa; glob restrito passa mesmo na raiz.
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: 'src' })), null);
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: '.', glob: '**/*.txt' })), null);
  // glob que alcança segredo bloqueia.
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: 'src', glob: '**/.*' })), REGRA);
  assert.equal(avaliar(entrada('Grep', { pattern: 'TOKEN', path: '.', glob: '*' })), REGRA);
});

test('Grep sobre a raiz com todos os segredos ignorados passa (é o caso do projeto)', () => {
  const outra = mkdtempSync(path.join(tmpdir(), 'guarda-arquivos-raiz-'));
  try {
    writeFileSync(path.join(outra, '.gitignore'), '.env.local\n.secrets/\namostras-locais/\n');
    writeFileSync(path.join(outra, '.env.local'), `X=${ISCA}\n`);
    mkdirSync(path.join(outra, '.secrets'));
    writeFileSync(path.join(outra, '.secrets', 'token'), `${ISCA}\n`);
    mkdirSync(path.join(outra, 'amostras-locais'));
    writeFileSync(path.join(outra, 'amostras-locais', 'mapa-local.csv'), `${ISCA}\n`);
    assert.equal(avaliar(entrada('Grep', { pattern: 'X', path: '.' }, outra)), null);
    // Se alguém tirar o .env.local do .gitignore, a mesma busca passa a bloquear.
    writeFileSync(path.join(outra, '.gitignore'), '.secrets/\namostras-locais/\n');
    const nova = mkdtempSync(path.join(tmpdir(), 'guarda-arquivos-raiz2-'));
    writeFileSync(path.join(nova, '.gitignore'), '.secrets/\n');
    writeFileSync(path.join(nova, '.env.local'), `X=${ISCA}\n`);
    assert.equal(avaliar(entrada('Grep', { pattern: 'X', path: '.' }, nova)), REGRA_PASTA);
    rmSync(nova, { recursive: true, force: true });
  } finally {
    rmSync(outra, { recursive: true, force: true });
  }
});

test('Glob sobre pasta: padrão que não alcança segredo passa; o que alcança bloqueia', () => {
  assert.equal(avaliar(entrada('Glob', { pattern: '**/*.java' })), null);
  assert.equal(avaliar(entrada('Glob', { pattern: 'docs/**/*.md' })), null);
  assert.equal(avaliar(entrada('Glob', { pattern: 'src/*.txt', path: '.' })), null);
  for (const padrao of ['**/*', '**/.*', '*', '.e*', '**/.?nv*', '**/[.]env.local', '**/mapa-*.csv']) {
    assert.ok(avaliar(entrada('Glob', { pattern: padrao })), `deveria bloquear: ${padrao}`);
  }
});

test('peças: padrão e .gitignore', () => {
  assert.ok(padraoAlcancaSegredo('**/.e*'));
  assert.ok(padraoAlcancaSegredo('{a,.env.local}'));
  assert.ok(!padraoAlcancaSegredo('**/*.java'));
  assert.ok(ignoradoPeloGit(path.join(PASTA, '.env.local'), PASTA));
  assert.ok(!ignoradoPeloGit(path.join(PASTA, '.env.local.example'), PASTA), '!.env.*.example vence');
  assert.ok(!ignoradoPeloGit(path.join(PASTA, 'vazado', 'mapa-local.csv'), PASTA));
  assert.ok(ignoradoPeloGit(path.join(RAIZ_DO_PROJETO, '.env.local'), RAIZ_DO_PROJETO), 'o .gitignore do projeto ignora o .env.local');
  assert.ok(ignoradoPeloGit(path.join(RAIZ_DO_PROJETO, '.secrets', 'x'), RAIZ_DO_PROJETO));
  assert.ok(ignoradoPeloGit(path.join(RAIZ_DO_PROJETO, 'amostras-locais', 'mapa-local.csv'), RAIZ_DO_PROJETO));
  assert.ok(ignoradoPeloGit(path.join(RAIZ_DO_PROJETO, 'x', '.rastro', 'env.local'), RAIZ_DO_PROJETO));
});

test('no Grep, o padrão de busca é texto, não caminho', () => {
  assert.equal(avaliar(entrada('Grep', { pattern: '.env.local', path: 'src' })), null);
});

test('outras ferramentas não são avaliadas', () => {
  assert.equal(avaliar(entrada('Bash', { command: 'cat .env.local' })), null);
});

test('settings.json: JSON válido, hooks ligados e as regras deny esperadas presentes', () => {
  const settings = JSON.parse(readFileSync(path.join(RAIZ_DO_PROJETO, '.claude', 'settings.json'), 'utf8'));
  const deny = settings.permissions.deny;
  const esperadas = ['Bash(git commit:*)', 'Bash(git push:*)', 'Bash(gh:*)', 'PowerShell(git commit:*)',
    'PowerShell(git push:*)', 'PowerShell(gh:*)'];
  for (const ferramenta of ['Read', 'Edit']) {
    for (const alvo of ['.env', '.env.local', '.env.*', '!.env.example', '!.env.*.example', 'mapa-local.csv',
      '.secrets/**', '.rastro/**', '~/.rastro/**', '~/.ssh/**', '~/.aws/**', '~/.git-credentials', '~/.npmrc',
      '~/.claude/.credentials.json']) {
      esperadas.push(`${ferramenta}(${alvo})`);
    }
  }
  for (const regra of esperadas) assert.ok(deny.includes(regra), `falta a regra deny: ${regra}`);
  // A negação do exemplo vem depois da regra que ela corrige (mesma fonte, ordem importa).
  assert.ok(deny.indexOf('Read(!.env.*.example)') > deny.indexOf('Read(.env.*)'));
  assert.ok(deny.indexOf('Edit(!.env.*.example)') > deny.indexOf('Edit(.env.*)'));
  assert.ok(!deny.some((r) => /^(Read|Edit)\(.*\.example\)$/.test(r) && !r.includes('(!')), 'nenhum exemplo negado');
  const comandos = JSON.stringify(settings.hooks);
  for (const guarda of ['guarda-bash.mjs', 'guarda-arquivos.mjs', 'varrer-dados.mjs']) assert.ok(comandos.includes(guarda));
  assert.equal(settings.sandbox, undefined, 'sandbox não configurado: indisponível no Windows nativo (hooks.md)');
});

test('.gitignore do projeto ignora a pasta .rastro', () => {
  const gitignore = readFileSync(path.join(RAIZ_DO_PROJETO, '.gitignore'), 'utf8');
  assert.match(gitignore, /^\*\*\/\.rastro\/\*\*$/m);
});

test('processo: bloqueio com código 2; exemplo com 0', () => {
  const bloqueado = spawnSync(process.execPath, [HOOK], {
    input: JSON.stringify(entrada('Read', { file_path: '.env.local' })), encoding: 'utf8',
  });
  assert.equal(bloqueado.status, 2);
  assert.match(bloqueado.stderr, /Bloqueado pela guarda do projeto/);
  assert.ok(!bloqueado.stderr.includes(ISCA));
  const permitido = spawnSync(process.execPath, [HOOK], {
    input: JSON.stringify(entrada('Read', { file_path: '.env.local.example' })), encoding: 'utf8',
  });
  assert.equal(permitido.status, 0);
});

test('falha segura: entrada inválida ou sem tool_input não bloqueia', () => {
  for (const texto of ['nada', JSON.stringify({ tool_name: 'Read' }), '[]']) {
    const resultado = spawnSync(process.execPath, [HOOK], { input: texto, encoding: 'utf8' });
    assert.equal(resultado.status, 1, texto);
    assert.match(resultado.stderr, /\[guarda-arquivos\] aviso/);
  }
});
