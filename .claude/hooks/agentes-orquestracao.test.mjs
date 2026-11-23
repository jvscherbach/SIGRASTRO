// Confere as definições dos quatro agentes do modo automático (A1, bloco 3).
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const pasta = join(dirname(fileURLToPath(import.meta.url)), '..', 'agents');

function ler(nome) {
  const texto = readFileSync(join(pasta, `${nome}.md`), 'utf8').replace(/\r\n/g, '\n');
  const m = /^---\n([\s\S]*?)\n---\n([\s\S]*)$/.exec(texto);
  assert.ok(m, `${nome}: cabeçalho ausente`);
  const cab = {};
  for (const linha of m[1].split('\n')) {
    const i = linha.indexOf(':');
    cab[linha.slice(0, i).trim()] = linha.slice(i + 1).trim();
  }
  return { texto, cab, corpo: m[2] };
}

const executores = [
  ['executor-haiku', 'haiku'],
  ['executor-sonnet', 'sonnet'],
  ['executor-opus', 'opus'],
];
const FERRAMENTAS = 'Read, Write, Edit, Glob, Grep, Bash';

for (const [nome, modelo] of executores) {
  test(`${nome}: nome, modelo, ferramentas e descrição`, () => {
    const { cab } = ler(nome);
    assert.equal(cab.name, nome);
    assert.equal(cab.model, modelo);
    assert.equal(cab.tools, FERRAMENTAS);
    assert.match(cab.description, /Executa UM bloco/);
    assert.match(cab.description, /Não commita/);
  });
}

test('o corpo dos três executores é idêntico', () => {
  const corpos = executores.map(([n]) => ler(n).corpo);
  assert.equal(corpos[0], corpos[1]);
  assert.equal(corpos[1], corpos[2]);
});

test('commitador: haiku, só Read e Bash, sem ferramenta de escrita', () => {
  const { cab, corpo } = ler('commitador');
  assert.equal(cab.name, 'commitador');
  assert.equal(cab.model, 'haiku');
  assert.equal(cab.tools, 'Read, Bash');
  for (const f of ['Write', 'Edit', 'NotebookEdit']) {
    assert.ok(!cab.tools.includes(f), `commitador não pode ter ${f}`);
  }
  assert.match(corpo, /node \.claude\/scripts\/commit-bloco\.mjs --bloco <N> --mensagem \.claude\/tmp\/commit-msg\.txt/);
  assert.match(corpo, /FALHA DO COMMIT:/);
});

test('nenhum dos quatro agentes cita push', () => {
  for (const nome of ['executor-haiku', 'executor-sonnet', 'executor-opus', 'commitador']) {
    assert.doesNotMatch(ler(nome).texto, /push/i, `${nome} cita push`);
  }
});

test('nenhum dos quatro agentes manda coautoria nem ferramenta na mensagem', () => {
  for (const nome of ['executor-haiku', 'executor-sonnet', 'executor-opus', 'commitador']) {
    assert.doesNotMatch(ler(nome).texto, /Generated with|noreply@anthropic/i, nome);
  }
});
