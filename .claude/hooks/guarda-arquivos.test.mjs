// Testes da guarda 2 (node --test). Só entradas simuladas: nenhum arquivo de segredo é lido.

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import { avaliar } from './guarda-arquivos.mjs';

const HOOK = fileURLToPath(new URL('./guarda-arquivos.mjs', import.meta.url));

const entrada = (tool_name, tool_input) => ({ hook_event_name: 'PreToolUse', tool_name, tool_input });

test('exemplos passam', () => {
  for (const caminho of ['.env.local.example', '.env.example', 'D:\\Projeto\\siga-vistas\\.env.local.example',
    '/projeto/.env.local.example', 'README.md', 'docs/12-configuracoes.md', '.envrc', 'compose.local.yaml']) {
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
  ];
  for (const [ferramenta, dados] of casos) {
    const bloqueio = avaliar(entrada(ferramenta, dados));
    assert.ok(bloqueio, `${ferramenta} ${JSON.stringify(dados)}`);
    assert.match(bloqueio, /\.env\.local\.example/);
  }
});

test('no Grep, o padrão de busca é texto, não caminho', () => {
  assert.equal(avaliar(entrada('Grep', { pattern: '.env.local', path: 'docs' })), null);
});

test('outras ferramentas não são avaliadas', () => {
  assert.equal(avaliar(entrada('Bash', { command: 'cat .env.local' })), null);
});

test('processo: bloqueio com código 2; exemplo com 0', () => {
  const bloqueado = spawnSync(process.execPath, [HOOK], {
    input: JSON.stringify(entrada('Read', { file_path: '.env.local' })), encoding: 'utf8',
  });
  assert.equal(bloqueado.status, 2);
  assert.match(bloqueado.stderr, /Bloqueado pela guarda do projeto/);
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
