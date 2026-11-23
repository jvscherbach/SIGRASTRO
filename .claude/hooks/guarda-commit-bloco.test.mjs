// Testes da A1, bloco 2 (node --test): a exceção estreita do commit-bloco na guarda-bash, a proteção do `.claude/`
// nas duas guardas (Bash/PowerShell e ferramentas de arquivo) e o settings.json (allow único, deny intacto, sem
// coautoria). Só entradas simuladas: nada é executado, nada é gravado no projeto. O marcador de liberação é um arquivo
// temporário do teste (nunca o `~/.rastro/` de verdade).

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { after, describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { caminhoClaudeProtegido, construcaoLiberada, curingaAlcancaClaude } from './comum.mjs';
import { REGRA_CLAUDE_ARQUIVOS, avaliar as avaliarArquivo } from './guarda-arquivos.mjs';
import { COMANDO_COMMIT_BLOCO, REGRA_CLAUDE, REGRA_COMMIT_BLOCO, REGRA_GIT, avaliar as avaliarBash } from './guarda-bash.mjs';

const HOOK_BASH = fileURLToPath(new URL('./guarda-bash.mjs', import.meta.url));
const RAIZ = path.resolve(path.dirname(HOOK_BASH), '..', '..');
const PASTA = mkdtempSync(path.join(tmpdir(), 'guarda-a1-'));
const MARCADOR = path.join(PASTA, 'liberar-construcao-claude');
const SEM_MARCADOR = path.join(PASTA, 'nao-existe');
writeFileSync(MARCADOR, '');
after(() => rmSync(PASTA, { recursive: true, force: true }));

const E = 'node .claude/scripts/commit-bloco.mjs --bloco 3 --mensagem .claude/tmp/commit-msg.txt';
const SUBAGENTE = { agent_id: 'agente-de-teste', agent_type: 'commitador' };

function bash(command, { ferramenta = 'Bash', liberado = false, extra = {} } = {}) {
  const entrada = { hook_event_name: 'PreToolUse', tool_name: ferramenta, tool_input: { command }, cwd: RAIZ, ...extra };
  return avaliarBash(entrada, { marcador: liberado ? MARCADOR : SEM_MARCADOR });
}
function arquivo(tool_name, tool_input, { liberado = false, extra = {} } = {}) {
  const entrada = { hook_event_name: 'PreToolUse', tool_name, tool_input, cwd: RAIZ, ...extra };
  return avaliarArquivo(entrada, { marcador: liberado ? MARCADOR : SEM_MARCADOR });
}

describe('exceção estreita: o comando exato do commit-bloco', () => {
  for (const bloco of ['1', '3', '12', '999']) {
    test(`permitido: bloco ${bloco}`, () => {
      assert.equal(bash(E.replace('--bloco 3', `--bloco ${bloco}`)), null);
    });
  }
  test('permitido com espaços nas pontas (o comando é comparado depois de trim)', () => {
    assert.equal(bash(`  ${E}\n`), null);
  });
  test('permitido no PowerShell', () => {
    assert.equal(bash(E, { ferramenta: 'PowerShell' }), null);
  });
  test('permitido para o subagente commitador', () => {
    assert.equal(bash(E, { extra: SUBAGENTE }), null);
  });
  test('a expressão do comando exato não aceita nada além do formato', () => {
    assert.equal(COMANDO_COMMIT_BLOCO.test(E), true);
    assert.equal(COMANDO_COMMIT_BLOCO.test(`${E} `), false);
    assert.equal(COMANDO_COMMIT_BLOCO.test(E.replace('3', '0003')), false);
  });

  const PERMITIDOS_SEM_EXECUTAR = [
    'node --test .claude/scripts/commit-bloco.test.mjs',
    'node --test .claude/scripts/commit-bloco.test.mjs 2>&1 | tail -20',
    'node --test .claude/scripts/commit-bloco.test.mjs .claude/hooks/guarda-commit-bloco.test.mjs',
    'cat .claude/scripts/commit-bloco.mjs',
    "sed -n '1,40p' .claude/scripts/commit-bloco.mjs",
    'grep -n Recusa .claude/scripts/commit-bloco.mjs',
    'wc -l .claude/scripts/commit-bloco.mjs .claude/scripts/commit-bloco.test.mjs',
  ];
  for (const comando of PERMITIDOS_SEM_EXECUTAR) {
    test(`permitido (não executa o script): ${comando}`, () => {
      assert.equal(bash(comando), null);
    });
  }
});

// [o que tenta, comando, regra esperada (opcional)]
const VARIACOES = [
  ['argumento extra', `${E} --extra`, REGRA_COMMIT_BLOCO],
  ['--amend no fim', `${E} --amend`, REGRA_COMMIT_BLOCO],
  ['--mensagem /etc/passwd', E.replace('.claude/tmp/commit-msg.txt', '/etc/passwd'), REGRA_COMMIT_BLOCO],
  ['mensagem com ./', E.replace('.claude/tmp/', './.claude/tmp/'), REGRA_COMMIT_BLOCO],
  ['mensagem com ..', E.replace('.claude/tmp/', '.claude/tmp/../tmp/'), REGRA_COMMIT_BLOCO],
  ['mensagem em outro arquivo', E.replace('commit-msg.txt', 'outra.txt'), REGRA_COMMIT_BLOCO],
  ['script com ./', E.replace('node .claude', 'node ./.claude'), REGRA_COMMIT_BLOCO],
  ['script com ..', E.replace('.claude/scripts/', '.claude/scripts/../scripts/'), REGRA_COMMIT_BLOCO],
  ['script com barras invertidas', E.replace('.claude/scripts/commit-bloco.mjs', '.claude\\scripts\\commit-bloco.mjs'), REGRA_COMMIT_BLOCO],
  ['bloco com 4 dígitos', E.replace('--bloco 3', '--bloco 1234'), REGRA_COMMIT_BLOCO],
  ['bloco com letra', E.replace('--bloco 3', '--bloco abc'), REGRA_COMMIT_BLOCO],
  ['bloco com =', E.replace('--bloco 3', '--bloco=3'), REGRA_COMMIT_BLOCO],
  ['bloco repetido', E.replace('--bloco 3', '--bloco 3 --bloco 4'), REGRA_COMMIT_BLOCO],
  ['ordem trocada', 'node .claude/scripts/commit-bloco.mjs --mensagem .claude/tmp/commit-msg.txt --bloco 3', REGRA_COMMIT_BLOCO],
  ['dois espaços', E.replace('node ', 'node  '), REGRA_COMMIT_BLOCO],
  ['tabulação', E.replace('node ', 'node\t'), REGRA_COMMIT_BLOCO],
  ['comentário no fim', `${E} #`, REGRA_COMMIT_BLOCO],
  ['&& git push', `${E} && git push`],
  ['; git push', `${E}; git push`],
  ['| cat', `${E} | cat`],
  ['& em segundo plano', `${E} & echo x`],
  ['|| true', `${E} || true`],
  ['outro comando antes', `echo x && ${E}`],
  ['redirecionamento de saída', `${E} > saida.txt`],
  ['redirecionamento de erro', `${E} 2> erro.txt`],
  ['redirecionamento de entrada', `${E} < entrada.txt`],
  ['crase', `${E} \`echo x\``],
  ['$(...) no bloco', E.replace('--bloco 3', '--bloco $(echo 3)')],
  ['variável no bloco', E.replace('--bloco 3', '--bloco $N')],
  ['quebra de linha e git push', `${E}\ngit push`],
  ['CRLF no meio', `${E}\r\necho x`],
  ['programa entre aspas', E.replace('node', '"node"'), REGRA_COMMIT_BLOCO],
  ['script entre aspas', E.replace('.claude/scripts/commit-bloco.mjs', "'.claude/scripts/commit-bloco.mjs'"), REGRA_COMMIT_BLOCO],
  ['node.exe', E.replace('node', 'node.exe'), REGRA_COMMIT_BLOCO],
  ['variável de ambiente antes', `NODE_OPTIONS=--require=./x.cjs ${E}`, REGRA_COMMIT_BLOCO],
  ['env antes', `env X=1 ${E}`, REGRA_COMMIT_BLOCO],
  ['timeout antes', `timeout 60 ${E}`, REGRA_COMMIT_BLOCO],
  ['npx antes', `npx ${E}`, REGRA_COMMIT_BLOCO],
  ['dentro de bash -c', `bash -c "${E}"`, REGRA_COMMIT_BLOCO],
  ['dentro de sh -c', `sh -c '${E}'`, REGRA_COMMIT_BLOCO],
  ['dentro de pwsh -c', `pwsh -c "${E}"`, REGRA_COMMIT_BLOCO],
  ['node -e com require', "node -e \"require('./.claude/scripts/commit-bloco.mjs')\"", REGRA_COMMIT_BLOCO],
  ['node --import antes', E.replace('node ', 'node --import ./x.mjs '), REGRA_COMMIT_BLOCO],
  ['nome montado com aspas', E.replace('commit-bloco', "commit-bl'o'co"), REGRA_COMMIT_BLOCO],
  ['nome com curinga', E.replace('commit-bloco', 'commit-b*'), REGRA_COMMIT_BLOCO],
  ['caminho por $(echo)', E.replace('.claude/scripts/commit-bloco.mjs', '"$(echo .claude/scripts/commit-bloco.mjs)"'), REGRA_COMMIT_BLOCO],
  ['cópia do script para outro lugar e execução', `cp .claude/scripts/commit-bloco.mjs x.mjs && node x.mjs --bloco 3 --mensagem .claude/tmp/commit-msg.txt`, REGRA_COMMIT_BLOCO],
  ['outro script em .claude/scripts', E.replace('commit-bloco.mjs', 'outro.mjs'), REGRA_COMMIT_BLOCO],
  ['script de .claude/scripts por curinga', 'node .claude/scripts/*.mjs --bloco 3', REGRA_COMMIT_BLOCO],
  ['git commit -m x', 'git commit -m x', REGRA_GIT],
  ['git commit -F com a mensagem do bloco', 'git commit -F .claude/tmp/commit-msg.txt', REGRA_GIT],
  ['git push', 'git push', REGRA_GIT],
  ['git push --force', 'git push --force origin auto/x', REGRA_GIT],
  ['git add -A', 'git add -A', REGRA_GIT],
  ['git -c user.name=x commit', 'git -c user.name=x commit -m y', REGRA_GIT],
  ['git reset --hard', 'git reset --hard HEAD~1', REGRA_GIT],
  ['git commit --no-verify', 'git commit --no-verify -m x', REGRA_GIT],
];

describe('variações do commit continuam bloqueadas', () => {
  test(`pelo menos 40 variações (${VARIACOES.length})`, () => {
    assert.ok(VARIACOES.length >= 40);
  });
  for (const [nome, comando, regra] of VARIACOES) {
    test(`bloqueia: ${nome}`, () => {
      const r = bash(comando);
      assert.ok(r, `deveria bloquear: ${JSON.stringify(comando)}`);
      if (regra) assert.equal(r, regra);
    });
  }
  test('PowerShell: & node e Start-Process', () => {
    assert.ok(bash(`& ${E}`, { ferramenta: 'PowerShell' }));
    assert.ok(bash(`Start-Process node -ArgumentList '${E.slice(5)}'`, { ferramenta: 'PowerShell' }));
  });
  test('a liberação de construção não abre o commit', () => {
    assert.equal(bash(`${E} && git push`, { liberado: true }), REGRA_COMMIT_BLOCO);
    assert.equal(bash('git commit -m x', { liberado: true }), REGRA_GIT);
  });
});

describe('proteção do .claude/ na guarda-bash', () => {
  const ESCRITAS = [
    'echo x > .claude/hooks/guarda-bash.mjs',
    'echo x >> .claude/settings.json',
    'echo x>.claude/hooks/a.mjs',
    'sed -i s/a/b/ .claude/hooks/guarda-bash.mjs',
    "sed -n '1,5p' .claude/hooks/comum.mjs > .claude/hooks/b.mjs",
    'perl -pi -e s/a/b/ .claude/hooks/comum.mjs',
    'cp x.mjs .claude/scripts/commit-bloco.mjs',
    'mv .claude/hooks/a.mjs .claude/hooks/b.mjs',
    'rm -rf .claude/hooks',
    'rm -rf .claude',
    'rm .c*/hooks/*',
    'rm -rf */hooks',
    'tee .claude/agents/executor-opus.md',
    'echo x | tee -a .claude/commands/orquestrar.md',
    'touch .claude/commands/novo.md',
    'mkdir .claude/agents/novo',
    'chmod +x .claude/scripts/commit-bloco.mjs',
    'ln -s /tmp/x .claude/hooks/y.mjs',
    'cd .claude/hooks && echo x > a.mjs',
    'echo x > .CLAUDE/HOOKS/a.mjs',
    'echo x > .claude\\hooks\\a.mjs',
    'echo x > ./.claude/tmp/../hooks/a.mjs',
    'echo x > D:/Projeto/siga-vistas/.claude/settings.json',
    'echo {} > .claude/settings.local.json',
    'echo {} > ~/.claude/settings.json',
    'echo x > CLAUDE~1/hooks/a.mjs',
    'node tools/gravar.mjs .claude/hooks/a.mjs',
    'npx prettier --write .claude/hooks',
    'X=.claude/hooks',
    'git apply x.patch --directory=.claude/hooks',
  ];
  for (const comando of ESCRITAS) {
    test(`bloqueia escrita: ${comando}`, () => {
      assert.ok(bash(comando), comando);
    });
  }
  const ESCRITAS_PS = [
    'Set-Content -Path .claude/hooks/a.mjs -Value x',
    'Out-File .claude/agents/a.md',
    'Remove-Item .claude/hooks -Recurse',
    'Copy-Item x .claude/scripts/y.mjs',
    'New-Item .claude/commands/x.md',
    "'x' | Out-File -FilePath .claude\\settings.json",
    'Add-Content .claude\\agents\\commitador.md "linha"',
  ];
  for (const comando of ESCRITAS_PS) {
    test(`bloqueia escrita (PowerShell): ${comando}`, () => {
      assert.ok(bash(comando, { ferramenta: 'PowerShell' }), comando);
    });
  }
  test('a mensagem diz a regra e não repete o comando', () => {
    assert.equal(bash('echo isca-a1 > .claude/hooks/a.mjs'), REGRA_CLAUDE);
    assert.doesNotMatch(REGRA_CLAUDE, /isca-a1/);
  });

  const LEITURAS = [
    'cat .claude/hooks/guarda-bash.mjs',
    "sed -n '1,20p' .claude/hooks/comum.mjs",
    'grep -n REGRA .claude/hooks/guarda-bash.mjs',
    'ls -la .claude',
    'ls .claude/hooks',
    'wc -l .claude/hooks/*.mjs',
    'git diff -- .claude/',
    'git diff --stat .claude/settings.json',
    'node --test .claude/hooks/guarda-bash.test.mjs .claude/hooks/vazamento.test.mjs',
    'node --test .claude/hooks/guarda-bash.test.mjs 2>&1 | grep -E "^# (pass|fail)"',
    'node --check .claude/hooks/comum.mjs',
    'node --test --test-reporter=spec .claude/scripts/commit-bloco.test.mjs',
    'echo "mensagem" > .claude/tmp/commit-msg.txt',
    'mkdir -p .claude/tmp',
    'cat .claude/skills/vistas-banco/SKILL.md',
    'cat .claude/hooks/comum.mjs > /dev/null',
  ];
  for (const comando of LEITURAS) {
    test(`permite: ${comando}`, () => {
      assert.equal(bash(comando), null);
    });
  }
  test('permite (PowerShell): Get-Content e Select-String', () => {
    assert.equal(bash('Get-Content .claude/settings.json', { ferramenta: 'PowerShell' }), null);
    assert.equal(bash('Select-String -Path .claude\\hooks\\comum.mjs -Pattern REGRA', { ferramenta: 'PowerShell' }), null);
  });

  test('sessão principal liberada pelo marcador pode escrever', () => {
    assert.equal(bash('echo x > .claude/hooks/a.mjs', { liberado: true }), null);
  });
  test('subagente nunca é liberado, mesmo com o marcador', () => {
    assert.equal(bash('echo x > .claude/hooks/a.mjs', { liberado: true, extra: SUBAGENTE }), REGRA_CLAUDE);
    assert.equal(bash('echo x > .claude/hooks/a.mjs', { liberado: true, extra: { agent_type: 'executor-opus' } }), REGRA_CLAUDE);
  });
});

describe('proteção do .claude/ na guarda-arquivos', () => {
  const PROTEGIDOS = [
    '.claude/hooks/guarda-bash.mjs',
    '.claude/scripts/commit-bloco.mjs',
    '.claude/agents/commitador.md',
    '.claude/commands/orquestrar.md',
    '.claude/settings.json',
    '.claude/settings.local.json',
    '.claude/hooks/novo.mjs',
    './.claude/hooks/a.mjs',
    '.claude/tmp/../hooks/a.mjs',
    '.claude\\hooks\\a.mjs',
    '.CLAUDE/Hooks/a.mjs',
    path.join(RAIZ, '.claude', 'settings.json'),
    '~/.claude/settings.json',
  ];
  for (const ferramenta of ['Write', 'Edit', 'MultiEdit']) {
    for (const caminho of PROTEGIDOS) {
      test(`${ferramenta} bloqueado: ${caminho}`, () => {
        assert.equal(arquivo(ferramenta, { file_path: caminho, content: 'x' }), REGRA_CLAUDE_ARQUIVOS);
      });
    }
  }
  test('NotebookEdit bloqueado', () => {
    assert.equal(arquivo('NotebookEdit', { notebook_path: '.claude/hooks/a.ipynb' }), REGRA_CLAUDE_ARQUIVOS);
  });
  test('gravar em .claude/tmp/ é permitido', () => {
    assert.equal(arquivo('Write', { file_path: '.claude/tmp/commit-msg.txt', content: 'docs: x' }), null);
    assert.equal(arquivo('Write', { file_path: '.claude/tmp/bloco-3.json', content: '{}' }), null);
  });
  test('ler o protegido é permitido', () => {
    assert.equal(arquivo('Read', { file_path: '.claude/hooks/guarda-bash.mjs' }), null);
    assert.equal(arquivo('Grep', { pattern: 'REGRA', path: '.claude/hooks', glob: '*.mjs' }), null);
  });
  test('outras pastas seguem livres', () => {
    assert.equal(arquivo('Write', { file_path: 'docs/claude-code/hooks.md', content: 'x' }), null);
    assert.equal(arquivo('Edit', { file_path: '.claude/skills/vistas-banco/SKILL.md' }), null);
  });
  test('sessão principal liberada pode escrever; subagente nunca', () => {
    assert.equal(arquivo('Edit', { file_path: '.claude/hooks/comum.mjs' }, { liberado: true }), null);
    assert.equal(arquivo('Edit', { file_path: '.claude/hooks/comum.mjs' }, { liberado: true, extra: SUBAGENTE }), REGRA_CLAUDE_ARQUIVOS);
  });
  test('segredo continua bloqueado com a regra antiga', () => {
    assert.notEqual(arquivo('Write', { file_path: '.env.local', content: 'x' }), null);
  });
});

describe('funções comuns', () => {
  test('caminhoClaudeProtegido', () => {
    for (const c of ['.claude', '.claude/', '.claude/hooks', 'x=.claude/hooks/a', '>.claude/settings.json']) {
      assert.equal(caminhoClaudeProtegido(c), true, c);
    }
    for (const c of ['.claude/tmp/a', '.claude/skills/x', '.claudette', 'docs/.claude.md', '.claude/settings.json.bak']) {
      assert.equal(caminhoClaudeProtegido(c), false, c);
    }
  });
  test('curingaAlcancaClaude', () => {
    for (const c of ['*', '.c*', '*/hooks/*', '**/*.mjs', '.claude/*', '.claude/s*']) assert.equal(curingaAlcancaClaude(c), true, c);
    for (const c of ['docs/*.md', 'api/src/**/*.java', 'front/*.json']) assert.equal(curingaAlcancaClaude(c), false, c);
  });
  test('construcaoLiberada: só com marcador e fora de subagente', () => {
    assert.equal(construcaoLiberada({}, MARCADOR), true);
    assert.equal(construcaoLiberada({}, SEM_MARCADOR), false);
    assert.equal(construcaoLiberada({ agent_id: 'x' }, MARCADOR), false);
    assert.equal(construcaoLiberada({ agent_type: 'x' }, MARCADOR), false);
  });
});

describe('settings.json', () => {
  const settings = JSON.parse(readFileSync(path.join(RAIZ, '.claude', 'settings.json'), 'utf8'));
  test('allow tem só a regra do comando exato', () => {
    assert.deepEqual(settings.permissions.allow, [
      'Bash(node .claude/scripts/commit-bloco.mjs --bloco * --mensagem .claude/tmp/commit-msg.txt)',
    ]);
  });
  test('deny de git commit, git push e gh segue intacto', () => {
    for (const regra of ['Bash(git commit:*)', 'Bash(git push:*)', 'Bash(gh:*)', 'PowerShell(git commit:*)',
      'PowerShell(git push:*)', 'PowerShell(gh:*)', 'Read(.env.local)', 'Edit(.env.local)']) {
      assert.ok(settings.permissions.deny.includes(regra), regra);
    }
  });
  test('sem coautoria: attribution.commit e .pr vazios e sem URL da sessão', () => {
    assert.equal(settings.attribution.commit, '');
    assert.equal(settings.attribution.pr, '');
    assert.equal(settings.attribution.sessionUrl, false);
  });
});

describe('o hook de verdade, em processo separado', () => {
  const rodar = (entrada) => spawnSync(process.execPath, [HOOK_BASH], { input: JSON.stringify(entrada), encoding: 'utf8' });
  test('comando exato: código 0', () => {
    const r = rodar({ tool_name: 'Bash', tool_input: { command: E }, cwd: RAIZ, ...SUBAGENTE });
    assert.equal(r.status, 0, r.stderr);
  });
  test('variação: código 2 e só a regra', () => {
    const r = rodar({ tool_name: 'Bash', tool_input: { command: `${E} && git push` }, cwd: RAIZ, ...SUBAGENTE });
    assert.equal(r.status, 2);
    assert.match(r.stderr, /commit automático só existe pelo comando exato/);
  });
  test('escrita de subagente no .claude/hooks: código 2', () => {
    const r = rodar({ tool_name: 'Bash', tool_input: { command: 'echo x > .claude/hooks/a.mjs' }, cwd: RAIZ, ...SUBAGENTE });
    assert.equal(r.status, 2);
  });
});
