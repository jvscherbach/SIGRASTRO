// Testes do commit-bloco (A1, bloco 1). `node --test .claude/scripts/commit-bloco.test.mjs`
//
// Cada caso cria um repositório Git TEMPORÁRIO em os.tmpdir(), com identidade de teste e configuração global isolada
// (GIT_CONFIG_GLOBAL vazio, GIT_CONFIG_NOSYSTEM), roda o script como processo filho com essa pasta como diretório
// atual e apaga tudo no fim. Nenhum caso toca o repositório real nem fala com remoto.

import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

import {
  caminhoProibido,
  lerArgumentos,
  lerStatus,
  limparMensagem,
  linhasAcrescentadas,
  Recusa,
  validarMarca,
  validarMensagem,
} from './commit-bloco.mjs';

const executar = promisify(execFile);
const SCRIPT = fileURLToPath(new URL('./commit-bloco.mjs', import.meta.url));
const PALAVRA_PROIBIDA = ['pu', 'sh'].join(''); // montada para este arquivo também não conter a palavra
const NOME = 'Pessoa de Teste';
const EMAIL = 'pessoa.teste@exemplo.invalid';
const MSG = '.claude/tmp/commit-msg.txt';
const ARGS = ['--bloco', '3', '--mensagem', MSG];

const pastas = [];
let configGlobal;

after(async () => {
  for (const p of pastas) await rm(p, { recursive: true, force: true });
});

/** Ambiente isolado: sem a configuração global/sistema do Git da máquina e sem variáveis de autor. */
async function ambiente() {
  if (!configGlobal) {
    const pasta = await mkdtemp(path.join(os.tmpdir(), 'commit-bloco-cfg-'));
    pastas.splice(pastas.length, 0, pasta);
    configGlobal = path.join(pasta, 'gitconfig-vazio');
    await writeFile(configGlobal, '');
  }
  const env = {};
  for (const [k, v] of Object.entries(process.env)) {
    if (!k.toUpperCase().startsWith('GIT_')) env[k] = v;
  }
  return { ...env, GIT_CONFIG_GLOBAL: configGlobal, GIT_CONFIG_NOSYSTEM: '1' };
}

async function git(cwd, args) {
  assert.ok(!args.includes(PALAVRA_PROIBIDA), 'os testes nunca enviam ao remoto');
  const { stdout } = await executar('git', args, { cwd, env: await ambiente(), windowsHide: true, encoding: 'utf8' });
  return stdout;
}

/** Repositório temporário com um commit inicial e `.claude/tmp/` no .gitignore. */
async function criarRepo({ branch = 'auto/teste', identidade = true } = {}) {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'commit-bloco-repo-'));
  pastas.splice(pastas.length, 0, dir);
  await git(dir, ['init', '--quiet']);
  await git(dir, ['symbolic-ref', 'HEAD', `refs/heads/${branch}`]);
  await git(dir, ['config', 'commit.gpgsign', 'false']);
  await git(dir, ['config', 'core.autocrlf', 'false']);
  // O commit inicial do teste usa uma identidade só dele; o script lê a do repositório.
  await writeFile(path.join(dir, '.gitignore'), '.claude/tmp/\n');
  await writeFile(path.join(dir, 'LEIAME.md'), '# repositório de teste\n');
  await git(dir, ['add', '-A']);
  await git(dir, ['-c', 'user.name=Inicial', '-c', 'user.email=inicial@exemplo.invalid', 'commit', '--quiet', '-m', 'chore: inicial']);
  if (identidade) {
    await git(dir, ['config', 'user.name', NOME]);
    await git(dir, ['config', 'user.email', EMAIL]);
  }
  return dir;
}

async function gravar(dir, relativo, conteudo) {
  const destino = path.join(dir, relativo);
  await mkdir(path.dirname(destino), { recursive: true });
  await writeFile(destino, conteudo);
}

/** Bloco pronto: uma mudança, a marca de testes e a mensagem. */
async function prepararBloco(dir, { bloco = 3, testes = 'direcionados', mensagem = 'feat(exemplo): arquivo novo\n' } = {}) {
  await gravar(dir, 'src/novo.txt', 'conteúdo do bloco\n');
  await gravar(dir, `.claude/tmp/bloco-${bloco}.json`, JSON.stringify({ bloco, testes, resumo: 'teste' }));
  await gravar(dir, MSG, mensagem);
}

async function rodar(dir, args = ARGS) {
  try {
    const { stdout, stderr } = await executar(process.execPath, [SCRIPT, ...args], {
      cwd: dir,
      env: await ambiente(),
      windowsHide: true,
      encoding: 'utf8',
    });
    return { codigo: 0, stdout, stderr };
  } catch (erro) {
    return { codigo: erro.code, stdout: erro.stdout ?? '', stderr: erro.stderr ?? '' };
  }
}

async function contarCommits(dir) {
  return Number((await git(dir, ['rev-list', '--count', 'HEAD'])).trim());
}

/** Recusa sem efeito: código diferente de zero, um só commit (o inicial) e índice vazio. */
async function assertRecusado(dir, resultado, trecho) {
  assert.notEqual(resultado.codigo, 0, `deveria recusar; saída: ${resultado.stdout}`);
  if (trecho) assert.match(resultado.stderr, trecho);
  assert.equal(await contarCommits(dir), 1);
  assert.equal((await git(dir, ['diff', '--cached', '--name-only'])).trim(), '');
}

describe('commit feliz', () => {
  test('commita com a identidade configurada, apaga os temporários e imprime o hash', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    const r = await rodar(dir);
    assert.equal(r.codigo, 0, r.stderr);
    assert.equal(await contarCommits(dir), 2);
    const curto = (await git(dir, ['rev-parse', '--short', 'HEAD'])).trim();
    assert.match(r.stdout, new RegExp(curto));
    assert.match(r.stdout, /src\/novo\.txt/);
    const autor = (await git(dir, ['log', '-1', '--format=%an <%ae>|%cn <%ce>'])).trim();
    assert.equal(autor, `${NOME} <${EMAIL}>|${NOME} <${EMAIL}>`);
    assert.equal((await git(dir, ['log', '-1', '--format=%B'])).trim(), 'feat(exemplo): arquivo novo');
    assert.equal(existsSync(path.join(dir, MSG)), false);
    assert.equal(existsSync(path.join(dir, '.claude/tmp/bloco-3.json')), false);
    assert.equal((await git(dir, ['status', '--porcelain'])).trim(), '');
  });

  test('o autor é o do repositório mesmo com GIT_AUTHOR_* e GIT_COMMITTER_* no ambiente', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    const env = {
      ...(await ambiente()),
      GIT_AUTHOR_NAME: 'Intruso',
      GIT_AUTHOR_EMAIL: 'intruso@exemplo.invalid',
      GIT_COMMITTER_NAME: 'Intruso',
      GIT_COMMITTER_EMAIL: 'intruso@exemplo.invalid',
    };
    await executar(process.execPath, [SCRIPT, ...ARGS], { cwd: dir, env, windowsHide: true });
    const autor = (await git(dir, ['log', '-1', '--format=%an <%ae>|%cn <%ce>'])).trim();
    assert.equal(autor, `${NOME} <${EMAIL}>|${NOME} <${EMAIL}>`);
  });

  test('mensagem com coautoria e linhas de ferramenta sai sem elas', async () => {
    const dir = await criarRepo();
    const mensagem = [
      'fix(dossie): corrige a ordem',
      '',
      'Corpo que fica.',
      '',
      'Co-Authored-By: Claude Opus <noreply@anthropic.com>',
      'co-authored-by: Outro <outro@exemplo.invalid>',
      'Claude-Session: https://claude.ai/code/session_000',
      'Signed-off-by: Claude <noreply@anthropic.com>',
      '🤖 Generated with [Claude Code](https://claude.com/claude-code)',
      '',
    ].join('\r\n');
    await prepararBloco(dir, { mensagem });
    const r = await rodar(dir);
    assert.equal(r.codigo, 0, r.stderr);
    const corpo = await git(dir, ['log', '-1', '--format=%B']);
    assert.equal(corpo.trim(), 'fix(dossie): corrige a ordem\n\nCorpo que fica.');
    assert.doesNotMatch(corpo, /co-authored|claude|anthropic|generated/i);
  });

  for (const testes of ['direcionados', 'completa', 'nao-se-aplica']) {
    test(`marca de testes "${testes}" é aceita`, async () => {
      const dir = await criarRepo();
      await prepararBloco(dir, { testes });
      const r = await rodar(dir);
      assert.equal(r.codigo, 0, r.stderr);
      assert.equal(await contarCommits(dir), 2);
    });
  }

  test('arquivo alterado e apagado também entram', async () => {
    const dir = await criarRepo();
    await gravar(dir, 'LEIAME.md', '# mudou\n');
    await prepararBloco(dir);
    const r = await rodar(dir);
    assert.equal(r.codigo, 0, r.stderr);
    assert.match(await git(dir, ['show', '--name-only', '--format=', 'HEAD']), /LEIAME\.md/);
  });
});

describe('verificação 1: argumentos', () => {
  const invalidos = [
    [],
    ['--bloco', '3'],
    ['--bloco', '3', '--mensagem', MSG, '--extra'],
    ['--bloco', '3', '--mensagem', MSG, '--amend'],
    ['--mensagem', MSG, '--bloco', '3'],
    ['--bloco', '1234', '--mensagem', MSG],
    ['--bloco', '0', '--mensagem', MSG],
    ['--bloco', '03', '--mensagem', MSG],
    ['--bloco', '3a', '--mensagem', MSG],
    ['--bloco', '3', '--mensagem', './.claude/tmp/commit-msg.txt'],
    ['--bloco', '3', '--mensagem', '.claude\\tmp\\commit-msg.txt'],
    ['--bloco', '3', '--mensagem', '/etc/passwd'],
    ['--bloco', '3', '--mensagem', '.claude/tmp/../tmp/commit-msg.txt'],
    ['--bloco=3', '--mensagem', MSG],
  ];
  for (const args of invalidos) {
    test(`recusa ${JSON.stringify(args)}`, () => {
      assert.throws(() => lerArgumentos(args), (e) => e instanceof Recusa && e.etapa === 1);
    });
  }

  test('argumento extra é recusado de ponta a ponta, sem alterar nada', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir, [...ARGS, '--extra']), /verificação 1/);
  });

  test('aceita a forma exata', () => {
    assert.equal(lerArgumentos(['--bloco', '12', '--mensagem', MSG]), 12);
  });
});

describe('verificação 2: repositório e branch', () => {
  test('branch main é recusada', async () => {
    const dir = await criarRepo({ branch: 'main' });
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /auto\//);
  });

  test('branch master é recusada', async () => {
    const dir = await criarRepo({ branch: 'master' });
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /verificação 2/);
  });

  test('branch sem o prefixo auto/ é recusada', async () => {
    const dir = await criarRepo({ branch: 'feature/automatica' });
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /verificação 2/);
  });

  test('HEAD solto é recusado', async () => {
    const dir = await criarRepo();
    const hash = (await git(dir, ['rev-parse', 'HEAD'])).trim();
    await git(dir, ['checkout', '--quiet', '--detach', hash]);
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /HEAD solto/);
  });

  test('fora da raiz do repositório é recusado', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    const sub = path.join(dir, 'src');
    const r = await rodar(sub);
    await assertRecusado(dir, r, /raiz/);
  });
});

describe('verificação 3: identidade', () => {
  test('identidade vazia é recusada', async () => {
    const dir = await criarRepo({ identidade: false });
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /user\.name/);
  });
});

describe('verificação 4: marca de testes', () => {
  test('marca ausente é recusada', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await rm(path.join(dir, '.claude/tmp/bloco-3.json'));
    await assertRecusado(dir, await rodar(dir), /verificação 4/);
  });

  test('marca de outro bloco é recusada', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await gravar(dir, '.claude/tmp/bloco-3.json', JSON.stringify({ bloco: 2, testes: 'direcionados' }));
    await assertRecusado(dir, await rodar(dir), /outro bloco/);
  });

  for (const testes of ['falharam', 'ok', '', 'DIRECIONADOS', null, true]) {
    test(`"testes": ${JSON.stringify(testes)} é recusado`, async () => {
      const dir = await criarRepo();
      await prepararBloco(dir, { testes });
      await assertRecusado(dir, await rodar(dir), /verificação 4/);
    });
  }

  test('bloco como texto e JSON inválido são recusados', () => {
    assert.throws(() => validarMarca('{"bloco":"3","testes":"completa"}', 3), Recusa);
    assert.throws(() => validarMarca('não é json', 3), Recusa);
    assert.throws(() => validarMarca('[3]', 3), Recusa);
  });
});

describe('verificação 5: mensagem', () => {
  const ruins = [
    '',
    '\n\n',
    'Co-Authored-By: Claude <noreply@anthropic.com>\n',
    ' feat: começa com espaço\n',
    'feature: tipo inválido\n',
    'feat(Escopo): escopo com maiúscula\n',
    'feat:sem espaço\n',
    'feat: \n',
    'atualiza coisas\n',
    `feat: ${'x'.repeat(100)}\n`,
    'feat: resumo\ncorpo colado sem linha em branco\n',
  ];
  for (const mensagem of ruins) {
    test(`recusa ${JSON.stringify(mensagem.slice(0, 40))}`, () => {
      assert.throws(() => validarMensagem(limparMensagem(mensagem)), (e) => e instanceof Recusa && e.etapa === 5);
    });
  }

  test('mensagem fora do padrão é recusada de ponta a ponta', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir, { mensagem: 'atualiza tudo\n' });
    await assertRecusado(dir, await rodar(dir), /Conventional Commits/);
  });

  test('aceita os oito tipos, escopo opcional e até 100 caracteres', () => {
    for (const tipo of ['feat', 'fix', 'refactor', 'test', 'docs', 'perf', 'chore', 'ci']) {
      assert.equal(validarMensagem(`${tipo}: resumo`), `${tipo}: resumo\n`);
      assert.equal(validarMensagem(`${tipo}(claude-code): resumo`), `${tipo}(claude-code): resumo\n`);
    }
    const cem = `feat: ${'x'.repeat(94)}`;
    assert.equal(cem.length, 100);
    assert.equal(validarMensagem(cem), `${cem}\n`);
  });

  test('limpeza sem diferenciar caixa', () => {
    const limpa = limparMensagem('docs: x\n\nCO-AUTHORED-BY: A <a@exemplo.invalid>\nveja CLAUDE.AI/CODE/x\nfim\n');
    assert.equal(limpa, 'docs: x\n\nfim');
  });
});

describe('verificação 6: caminhos proibidos e árvore limpa', () => {
  test('.env.local na árvore é recusado', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await gravar(dir, '.env.local', 'NOME_FICTICIO=valor-falso\n');
    await assertRecusado(dir, await rodar(dir), /\.env\.local/);
  });

  test('node_modules/ na árvore é recusado', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await gravar(dir, 'front/node_modules/pacote/index.js', 'module.exports = 1;\n');
    await assertRecusado(dir, await rodar(dir), /node_modules/);
  });

  test('.claude/tmp/ fora do .gitignore é recusado', async () => {
    const dir = await criarRepo();
    await gravar(dir, '.gitignore', '\n');
    await prepararBloco(dir);
    await assertRecusado(dir, await rodar(dir), /\.claude\/tmp/);
  });

  test('árvore limpa é recusada (nada para commitar)', async () => {
    const dir = await criarRepo();
    await gravar(dir, '.claude/tmp/bloco-3.json', JSON.stringify({ bloco: 3, testes: 'direcionados' }));
    await gravar(dir, MSG, 'docs: nada\n');
    await assertRecusado(dir, await rodar(dir), /nada para commitar/);
  });

  test('lista de caminhos', () => {
    const proibidos = [
      '.env', '.env.local', 'api/.env.prod', 'mapa-local.csv', 'amostras-locais/a.pdf', 'certs/servidor.pem',
      'chave.key', 'front/node_modules/x/y.js', 'front/dist/main.js', 'front/test-results/a.txt',
      'front/playwright-report/index.html', '.claude/tmp/commit-msg.txt', '.secrets/token', '.rastro/segredo',
    ];
    const permitidos = ['.env.local.example', 'docs/README.md', 'api/src/Main.java', '.claude/scripts/x.mjs', 'distribuicao.md'];
    for (const c of proibidos) assert.equal(caminhoProibido(c), true, c);
    for (const c of permitidos) assert.equal(caminhoProibido(c), false, c);
  });

  test('status -z com renomeação', () => {
    const saida = ['R  novo.txt', 'velho.txt', '?? a b.txt', ' M x.md', ''].join('\0');
    assert.deepEqual(lerStatus(saida), [
      { estado: 'R ', caminho: 'novo.txt', origem: 'velho.txt' },
      { estado: '??', caminho: 'a b.txt', origem: undefined },
      { estado: ' M', caminho: 'x.md', origem: undefined },
    ]);
  });
});

describe('verificação 7: varredura de dados', () => {
  // Valor claramente inventado, montado em tempo de execução (o arquivo de teste não contém o padrão).
  const SEGREDO_FALSO = ['Bearer', 'chave-falsa-0000-0000-0000-0000'].join(' ');

  test('segredo falso em arquivo novo é recusado, sem repetir o valor', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await gravar(dir, 'src/config.txt', `Authorization: ${SEGREDO_FALSO}\n`);
    const r = await rodar(dir);
    await assertRecusado(dir, r, /src\/config\.txt:1 \(token Bearer\)/);
    assert.doesNotMatch(r.stderr, /chave-falsa/);
  });

  test('segredo falso em arquivo alterado é recusado', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir);
    await gravar(dir, 'LEIAME.md', `# repositório de teste\n${SEGREDO_FALSO}\n`);
    await assertRecusado(dir, await rodar(dir), /LEIAME\.md:2/);
  });

  test('segredo falso na mensagem é recusado', async () => {
    const dir = await criarRepo();
    await prepararBloco(dir, { mensagem: `feat: x\n\n${SEGREDO_FALSO}\n` });
    await assertRecusado(dir, await rodar(dir), /mensagem de commit/);
  });

  test('só as linhas acrescentadas contam, com o número da linha no arquivo novo', () => {
    const diff = '--- a/x\n+++ b/x\n@@ -1 +1 @@\n-removida\n+nova\n@@ -10,0 +11,2 @@\n+onze\n+++doze\n';
    assert.deepEqual(linhasAcrescentadas(diff), { texto: 'nova\nonze\n++doze', numeros: [1, 11, 12] });
  });
});

describe('o script', () => {
  test(`não contém a palavra "${PALAVRA_PROIBIDA}" (nunca envia ao remoto)`, async () => {
    const fonte = await readFile(SCRIPT, 'utf8');
    assert.equal(fonte.toLowerCase().includes(PALAVRA_PROIBIDA), false);
  });

  test('não usa shell, --no-verify, --amend, --author nem -m', async () => {
    const fonte = await readFile(SCRIPT, 'utf8');
    assert.doesNotMatch(fonte, /shell\s*:\s*true|\bexecSync\b|\bspawnSync\b|['"]--no-verify['"]|['"]--amend['"]|['"]--author|['"]-m['"]/);
  });

  test('reaproveita o detector da guarda varrer-dados (sem duplicar expressões)', async () => {
    const fonte = await readFile(SCRIPT, 'utf8');
    assert.match(fonte, /from '\.\.\/hooks\/varrer-dados\.mjs'/);
    assert.doesNotMatch(fonte, /eyJ|BEGIN .*PRIVATE KEY/);
  });
});
