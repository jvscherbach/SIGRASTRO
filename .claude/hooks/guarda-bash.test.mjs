// Testes da guarda 1 (node --test). Só entradas simuladas: nenhum comando é executado e nenhum segredo é lido.

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import { avaliar, avaliarTexto, palavras, programa, trechos } from './guarda-bash.mjs';

const HOOK = fileURLToPath(new URL('./guarda-bash.mjs', import.meta.url));

function rodar(entrada) {
  const texto = typeof entrada === 'string' ? entrada : JSON.stringify(entrada);
  return spawnSync(process.execPath, [HOOK], { input: texto, encoding: 'utf8' });
}

const bash = (command) => ({ hook_event_name: 'PreToolUse', tool_name: 'Bash', tool_input: { command } });
const ps = (command) => ({ hook_event_name: 'PreToolUse', tool_name: 'PowerShell', tool_input: { command } });

const PERMITIDOS = [
  'git diff',
  'git diff --stat',
  'git diff --name-only',
  'git diff --cached -- api/src docs/09-decisoes.md',
  'git -C D:/Projeto/siga-vistas diff --stat',
  'git --no-pager diff',
  'git diff --check && echo ok',
  'docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build',
  'docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml down',
  'docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml config --services',
  'docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c \'[ -z "$SIGA_DB_URL" ] && echo vazia || echo PREENCHIDA\'',
  'docker inspect --format "{{.State.Status}}" rastro-api-1',
  'npm test',
  'npm --prefix front test -- --watch=false',
  'npm --prefix front run e2e',
  'node --test .claude/hooks/',
  './mvnw -Dtest=ArchitectureTest test',
  'cd api && ./mvnw -q -o test',
  'cat .env.local.example',
  'grep -n DOSSIE .env.local.example',
  'ls -la docs',
  'echo "commit message: feat(x): y"',
  'grep -rn "git commit" docs/claude-code',
  'cat README.md | head -20',
  'printf "%s\\n" "$HOME"',
  'cat .envrc.md',
];

const BLOQUEADOS = {
  git: [
    'git commit -m "x"',
    'GIT COMMIT -m x',
    'git -C x commit -m y',
    'git -c user.name=x commit',
    'cd y && git push',
    'echo a | git add .',
    'npm test; git stash',
    'npm test || git reset --hard',
    'bash -c "git commit -m x"',
    'sh -c \'git push origin main\'',
    'echo $(git commit -m x)',
    'echo "`git push`"',
    '(git checkout -b nova)',
    'npm test\ngit branch nova',
    'env GIT_PAGER=cat git log',
    'sudo git push',
    'xargs git add',
    '"C:/Program Files/Git/bin/git.exe" status',
    'git',
    'git status',
    'git log --oneline',
    'git diff --output=saida.patch',
    'gh pr create',
    'gh api repos',
    'npm test && gh pr list',
  ],
  volumes: [
    'docker compose -f compose.yaml down -v',
    'docker compose down --volumes',
    'docker compose -f a -f b down --remove-orphans -v',
    'docker compose down -tv',
    'docker-compose down -v',
    'docker volume rm siga-vistas_dossies-tmp',
    'docker volume prune -f',
    'docker system prune -a --volumes',
  ],
  segredo: [
    'cat .env.local',
    'cat ./.env',
    'type .env.local',
    'more .env.local',
    'less .env.local',
    'head -5 .env.local',
    'tail .env.local',
    'grep SIGA .env.local',
    'cp .env.local /tmp/x',
    'ls -la .env.local',
    'cat .env*',
    'cat .env.producao',
    'sed -n 1,5p D:/Projeto/siga-vistas/.env.local',
    'while read l; do echo $l; done < .env.local',
    'docker compose --env-file .env.local config --services',
    'cat amostras-locais/mapa-local.csv',
    'bash -c "cat .env.local"',
  ],
  ambiente: [
    'printenv',
    'printenv SIGA_DB_URL',
    'env',
    'env -0',
    'set',
    'export -p',
    'declare -x',
    'docker compose exec api env',
    'docker compose -f compose.yaml exec -T api printenv',
    'docker exec rastro-api-1 env',
    'docker compose exec api sh -c "env"',
    'docker inspect rastro-api-1',
    'docker compose -f compose.yaml -f compose.local.yaml config',
    'echo $SIGA_DB_URL',
    'echo "${LICENCIAMENTO_API_TOKEN}"',
  ],
};

const BLOQUEADOS_POWERSHELL = [
  'git commit -m x',
  'Set-Location api; git push',
  '& git commit -m x',
  '& "C:\\Program Files\\Git\\cmd\\git.exe" push',
  'Get-Content .env.local',
  'gc .env.local',
  'Get-Content D:\\Projeto\\siga-vistas\\.env.local',
  'type .env.local',
  'Select-String -Path .env.local -Pattern SIGA',
  'Copy-Item .env.local C:\\temp\\x',
  'copy .env.local x.txt',
  'Get-ChildItem env:',
  'gci env:',
  'dir env:',
  'Get-ChildItem -Path env:',
  '[Environment]::GetEnvironmentVariables()',
  'Write-Output $env:SIGA_DB_URL',
  'Invoke-Expression "git commit -m x"',
  'iex "git push"',
  'pwsh -Command "git commit -m x"',
  'powershell -c "Get-Content .env.local"',
  'Start-Process git -ArgumentList "commit"',
  'docker compose down -v',
  'gh pr list',
];

const PERMITIDOS_POWERSHELL = [
  'git diff --stat',
  'Get-Content .env.local.example',
  'Get-Content README.md -TotalCount 5',
  'npm --prefix front test -- --watch=false',
  'Get-ChildItem docs',
  '$env:VAR = "x"; npm test',
];

test('permitidos passam', () => {
  for (const comando of PERMITIDOS) {
    assert.equal(avaliarTexto(comando), null, `deveria passar: ${comando}`);
  }
});

for (const [regra, comandos] of Object.entries(BLOQUEADOS)) {
  test(`bloqueados (${regra})`, () => {
    for (const comando of comandos) {
      assert.ok(avaliarTexto(comando), `deveria bloquear: ${JSON.stringify(comando)}`);
    }
  });
}

test('PowerShell: bloqueados e permitidos', () => {
  for (const comando of BLOQUEADOS_POWERSHELL) {
    assert.ok(avaliar(ps(comando)), `deveria bloquear (PowerShell): ${comando}`);
  }
  for (const comando of PERMITIDOS_POWERSHELL) {
    assert.equal(avaliar(ps(comando)), null, `deveria passar (PowerShell): ${comando}`);
  }
});

test('mensagem diz a regra e o que fazer', () => {
  assert.match(avaliarTexto('git commit -m x'), /só `git diff` é permitido/);
  assert.match(avaliarTexto('git commit -m x'), /Use `git diff`/);
  assert.match(avaliarTexto('cat .env.local'), /\.env\.local\.example/);
  assert.match(avaliarTexto('printenv'), /\[ -z "\$VAR" \] && echo vazia \|\| echo preenchida/);
  assert.match(avaliarTexto('docker compose down -v'), /sem `-v`/);
});

test('outras ferramentas não são avaliadas por esta guarda', () => {
  assert.equal(avaliar({ tool_name: 'Read', tool_input: { file_path: '.env.local' } }), null);
});

test('peças da análise', () => {
  assert.deepEqual(trechos('a && b || c; d | e\nf'), ['a', 'b', 'c', 'd', 'e', 'f']);
  assert.deepEqual(palavras('git commit -m "uma mensagem" \'outra\''), ['git', 'commit', '-m', 'uma mensagem', 'outra']);
  assert.deepEqual(palavras('Get-Content D:\\p\\.env.local'), ['Get-Content', 'D:\\p\\.env.local']);
  assert.equal(programa('C:\\Git\\bin\\GIT.EXE'), 'git');
});

test('processo: bloqueio sai com código 2 e mensagem no stderr', () => {
  const resultado = rodar(bash('git push'));
  assert.equal(resultado.status, 2);
  assert.match(resultado.stderr, /Bloqueado pela guarda do projeto/);
  assert.equal(resultado.stdout, '');
});

test('processo: comando permitido sai com 0 e sem saída', () => {
  const resultado = rodar(bash('git diff --stat'));
  assert.equal(resultado.status, 0);
  assert.equal(resultado.stderr, '');
});

test('falha segura: JSON inválido não bloqueia (código 1, aviso curto)', () => {
  const resultado = rodar('{isto não é json');
  assert.equal(resultado.status, 1);
  assert.match(resultado.stderr, /\[guarda-bash\] aviso: entrada inválida/);
  assert.match(resultado.stderr, /a ação segue/);
});

test('falha segura: entrada sem comando não bloqueia', () => {
  const resultado = rodar({ tool_name: 'Bash', tool_input: {} });
  assert.equal(resultado.status, 1);
  assert.match(resultado.stderr, /erro interno/);
});

test('falha segura: entrada vazia não bloqueia', () => {
  const resultado = rodar('');
  assert.equal(resultado.status, 1);
});
