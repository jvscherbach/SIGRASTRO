// Testes da guarda 3 (node --test). Os CPFs e CNPJs válidos são GERADOS por cálculo aqui, nunca escritos como
// literais; o token JWT também é montado no teste. Os arquivos ficam numa pasta temporária do sistema.

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, test } from 'node:test';

import { cnpjValido, cpfValido, ignorado, varrerTexto } from './varrer-dados.mjs';

const HOOK = fileURLToPath(new URL('./varrer-dados.mjs', import.meta.url));
const PASTA = mkdtempSync(path.join(tmpdir(), 'varrer-dados-'));
after(() => rmSync(PASTA, { recursive: true, force: true }));

/** Completa os dígitos verificadores de uma base de 9 dígitos (CPF). */
function gerarCpf(base) {
  const d = base.split('').map(Number);
  for (const n of [9, 10]) {
    let soma = 0;
    for (let i = 0; i < n; i++) soma += d[i] * (n + 1 - i);
    const resto = (soma * 10) % 11;
    d.push(resto === 10 ? 0 : resto);
  }
  return d.join('');
}

/** Completa os dígitos verificadores de uma base de 12 dígitos (CNPJ). */
function gerarCnpj(base) {
  const d = base.split('').map(Number);
  for (const n of [12, 13]) {
    let soma = 0;
    let peso = n - 7;
    for (let i = 0; i < n; i++) {
      soma += d[i] * peso--;
      if (peso < 2) peso = 9;
    }
    const resto = soma % 11;
    d.push(resto < 2 ? 0 : 11 - resto);
  }
  return d.join('');
}

const mascararCpf = (c) => `${c.slice(0, 3)}.${c.slice(3, 6)}.${c.slice(6, 9)}-${c.slice(9)}`;
const mascararCnpj = (c) => `${c.slice(0, 2)}.${c.slice(2, 5)}.${c.slice(5, 8)}/${c.slice(8, 12)}-${c.slice(12)}`;
const base64url = (texto) => Buffer.from(texto).toString('base64url');

const CPF = gerarCpf('529982247');
const CNPJ = gerarCnpj('481234560001');
const JWT = `${base64url('{"alg":"HS256","typ":"JWT"}')}.${base64url('{"sub":"teste","exp":1}')}.${'a'.repeat(20)}`;

function gravar(nome, conteudo) {
  const caminho = path.join(PASTA, nome);
  mkdirSync(path.dirname(caminho), { recursive: true });
  writeFileSync(caminho, conteudo);
  return caminho;
}

function rodar(entrada) {
  const texto = typeof entrada === 'string' ? entrada : JSON.stringify(entrada);
  return spawnSync(process.execPath, [HOOK], { input: texto, encoding: 'utf8', env: { ...process.env, CLAUDE_PROJECT_DIR: PASTA } });
}

const gravacao = (arquivo, ferramenta = 'Write') => ({
  hook_event_name: 'PostToolUse', tool_name: ferramenta, cwd: PASTA, tool_input: { file_path: arquivo }, tool_response: {},
});

test('o gerador do teste produz documentos válidos', () => {
  assert.ok(cpfValido(CPF));
  assert.ok(cnpjValido(CNPJ));
});

test('CPF e CNPJ válidos, com e sem máscara, são achados na linha certa', () => {
  const texto = ['linha limpa', `cpf ${mascararCpf(CPF)}`, `cnpj ${mascararCnpj(CNPJ)}`, `cru ${CPF} e ${CNPJ}`,
    `arquivo contrato-${CPF}.pdf`].join('\n');
  assert.deepEqual(varrerTexto(texto), [
    { linha: 2, tipo: 'CPF válido' },
    { linha: 3, tipo: 'CNPJ válido' },
    { linha: 4, tipo: 'CPF válido' },
    { linha: 4, tipo: 'CNPJ válido' },
    { linha: 5, tipo: 'CPF válido' },
  ]);
});

test('fictícios documentados e inválidos de propósito passam', () => {
  const texto = [
    '12.345.678/0001-90', '000.000.000-01', '000.000.000-00', '00.000.000/0000-00', '111.111.111-11',
    '<CPF_DE_TESTE>', '<CNPJ_DE_TESTE>', `${CPF.slice(0, 10)}${(Number(CPF[10]) + 1) % 10}`,
    // exemplos didáticos que os testes de validação já usam (lista FICTICIOS)
    ['111', '444', '777', '35'].join(''), ['11', '222', '333', '0001', '81'].join(''),
  ].join('\n');
  assert.deepEqual(varrerTexto(texto), []);
});

test('números longos que não são documento não disparam (hash, timestamp, id)', () => {
  const texto = ['a1b2c3d4e5f60718293a4b5c6d7e8f9', 'timestamp 1730000000000', 'id 1234567890123456789',
    `hash ab${CPF}cd`].join('\n');
  assert.deepEqual(varrerTexto(texto), []);
});

test('token JWT, Bearer longo e chave privada são achados', () => {
  const texto = [`token=${JWT}`, `Authorization: Bearer ${'x9'.repeat(16)}`, `-----BEGIN ${'PRIVATE'} KEY-----`,
    'Authorization: Bearer token-de-teste', 'Authorization: Bearer token-que-nao-existe-de-jeito-nenhum'].join('\n');
  assert.deepEqual(varrerTexto(texto), [
    { linha: 1, tipo: 'token JWT' },
    { linha: 2, tipo: 'token Bearer' },
    { linha: 3, tipo: 'chave privada' },
  ]);
});

test('processo: achado sai com código 2, arquivo e linha, sem repetir o valor', () => {
  const arquivo = gravar('docs/exemplo.md', `# Exemplo\n\nInteressado: ${mascararCpf(CPF)}\n${JWT}\n`);
  const resultado = rodar(gravacao(arquivo));
  assert.equal(resultado.status, 2);
  assert.match(resultado.stderr, /docs\/exemplo\.md:3 \(CPF válido\)/);
  assert.match(resultado.stderr, /docs\/exemplo\.md:4 \(token JWT\)/);
  assert.match(resultado.stderr, /<CPF_DE_TESTE>/);
  assert.ok(!resultado.stderr.includes(CPF), 'não pode repetir o CPF');
  assert.ok(!resultado.stderr.includes(mascararCpf(CPF)), 'não pode repetir o CPF mascarado');
  assert.ok(!resultado.stderr.includes(JWT), 'não pode repetir o token');
});

test('processo: arquivo limpo passa; Edit e MultiEdit também são varridos', () => {
  const limpo = gravar('limpo.md', 'CNPJ fictício 12.345.678/0001-90\n');
  assert.equal(rodar(gravacao(limpo)).status, 0);
  const sujo = gravar('sujo.ts', `const cnpj = '${CNPJ}';\n`);
  assert.equal(rodar(gravacao(sujo, 'Edit')).status, 2);
  assert.equal(rodar(gravacao(sujo, 'MultiEdit')).status, 2);
  assert.equal(rodar(gravacao(sujo, 'Read')).status, 0, 'Read não é varrido');
});

test('processo: ignora node_modules, dist, .claude/hooks, binário e arquivo grande', () => {
  const conteudo = `cpf ${CPF}\n`;
  for (const nome of ['front/node_modules/x/a.js', 'front/dist/a.js', '.claude/hooks/a.mjs', 'imagem.png']) {
    assert.equal(rodar(gravacao(gravar(nome, conteudo))).status, 0, nome);
  }
  const binario = gravar('dados.dat', Buffer.concat([Buffer.from([0, 1, 2]), Buffer.from(conteudo)]));
  assert.equal(rodar(gravacao(binario)).status, 0);
  const grande = gravar('grande.txt', 'x'.repeat(2 * 1024 * 1024 + 10) + `\n${CPF}\n`);
  assert.equal(rodar(gravacao(grande)).status, 0);
  assert.ok(ignorado(path.join('a', '.claude', 'hooks', 'b.mjs')));
});

test('falha segura: entrada inválida, sem caminho ou arquivo inexistente não bloqueiam', () => {
  for (const entrada of ['{', { tool_name: 'Write', tool_input: {} }, gravacao(path.join(PASTA, 'nao-existe.md'))]) {
    const resultado = rodar(entrada);
    assert.equal(resultado.status, 1, JSON.stringify(entrada));
    assert.match(resultado.stderr, /\[varrer-dados\] aviso/);
  }
});
