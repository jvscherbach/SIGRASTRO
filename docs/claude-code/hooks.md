# Hooks: as guardas do projeto (S6, bloco 6)

Os hooks do Claude Code rodam um comando antes (`PreToolUse`) ou depois (`PostToolUse`) de cada uso de ferramenta. O comando recebe o JSON do evento pela entrada padrão. **Código 2 bloqueia** (no `PreToolUse` a ação não acontece; no `PostToolUse` a mensagem volta ao Claude, porque a gravação já aconteceu) e devolve o `stderr` ao Claude; **outro código diferente de zero é erro não bloqueante** (a ação segue e um aviso aparece). Formato conferido na documentação oficial (`code.claude.com/docs/en/hooks`) para o Claude Code 2.1.

## Configuração

Em [`.claude/settings.json`](../../.claude/settings.json), versionado:

| Evento | `matcher` | Guarda |
|---|---|---|
| `PreToolUse` | `Bash\|PowerShell` | `guarda-bash.mjs` |
| `PreToolUse` | `Read\|Edit\|Write\|MultiEdit\|NotebookEdit\|Grep\|Glob` | `guarda-arquivos.mjs` |
| `PostToolUse` | `Write\|Edit\|MultiEdit` | `varrer-dados.mjs` |

O comando é sempre `node "$CLAUDE_PROJECT_DIR/.claude/hooks/<arquivo>.mjs"`, que funciona de qualquer diretório. **Windows:** os hooks rodam pelo Git Bash quando ele está instalado (o caso desta máquina). Sem Git Bash o Claude Code usa o PowerShell, onde `$CLAUDE_PROJECT_DIR` não é a variável de ambiente: a guarda não acharia o arquivo, sairia com erro **não bloqueante** e ficaria desligada (o aviso aparece). O `matcher` inclui `PowerShell` porque no Windows o Claude Code tem as duas ferramentas de shell.

**Reforço em `permissions.deny`** (vale mesmo com os hooks desligados): `Bash(git commit:*)`, `Bash(git push:*)`, `Bash(gh:*)`, as mesmas três para `PowerShell(...)`, e `Read`/`Edit` de `./.env` e `./.env.local`. Não há regra para `.env.*`: o formato não permite a exceção dos `*.example` (quem cobre é a guarda). A documentação avisa que regra de `Bash` casa só o texto do comando (`git -C . push` escapa dela), por isso as guardas fazem a análise de verdade.

## Guarda 1: `guarda-bash` (Bash e PowerShell)

Divide o comando em trechos (`&&`, `||`, `;`, `|`, `&`, quebra de linha, parênteses, chaves, `$( )` e crases, inclusive dentro de aspas duplas) e analisa cada um; o script de `bash -c`/`sh -c`/`pwsh -Command`/`Invoke-Expression` é analisado também, em qualquer posição (ex.: `docker ... exec api sh -c '...'`). Pula atribuições (`VAR=x`) e invólucros (`sudo`, `env VAR=x`, `nohup`, `time`, `timeout`, `xargs`, `&` do PowerShell). Nome de programa sem caminho, sem `.exe` e sem diferenciar maiúsculas.

| Regra | Bloqueia | Passa |
|---|---|---|
| Só `git diff` | qualquer `git` que não seja `git diff` (inclusive `git -C x commit`, `git -c k=v push`, `GIT COMMIT`, em comando composto, subshell, `bash -c`); `git diff --output=...` (grava arquivo); qualquer `gh`; `Start-Process git` | `git diff`, `git diff --stat`, `--name-only`, `--cached`, `--check`, `-- caminhos`, `git -C pasta diff`, `git --no-pager diff` |
| Volumes | `docker compose ... down` com `-v`/`--volumes` (também combinado, `-tv`), `docker volume rm/remove/prune`, `docker system prune --volumes` | `docker compose ... down`, `up`, `exec` |
| Arquivo de segredo | qualquer comando que cite `.env`, `.env.local`, `.env.<x>` (menos `*.example`), um curinga `.env*`/`.env.*` ou `mapa-local.csv`: `cat`, `type`, `Get-Content`, `more`, `less`, `head`, `tail`, `grep`, `Select-String`, `cp`, `copy`, `ls`/`dir`, redirecionamento `< .env.local`, `--env-file .env.local`, caminho completo do Windows ou Unix | `.env.local.example`, `.env.example`, `.envrc` |
| Dump de ambiente | `printenv` (com ou sem nome), `env` sem comando, `set`/`export -p`/`declare -x` sem nomes, `Get-ChildItem env:` (e `gci`/`dir`/`ls`), `[Environment]::GetEnvironmentVariables()`, `docker ... exec\|run ... env\|printenv`, `docker inspect` sem `--format`, `docker compose config` sem `--services`/`--volumes`/`--profiles`/`--images`/`--networks`/`-q` (resolve o `.env.local` e imprime os valores), `echo`/`printf`/`Write-Output` de `$SIGA_DB_*`, `$LICENCIAMENTO_API_*`, `$SCORP_API_*`, `$VISTAS_DB_*`, `$RASTRO_MCP_SENHA` | `docker compose ... exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia \|\| echo PREENCHIDA'`, `docker inspect --format ...`, `config --services` |

**Mensagens** (o Claude recebe uma só, a da primeira regra que casar):

- git: "Bloqueado pela guarda do projeto (regra: só `git diff` é permitido; nada de commit, push, branch, stash, add, checkout, reset, gh etc.). Quem commita é o responsável. Use `git diff` ou `git diff --stat` para mostrar a mudança."
- volumes: "... nunca apagar volumes ... Use `docker compose ... down` sem `-v`."
- segredo: "... nunca abrir, imprimir ou copiar `.env`, `.env.local`, `.env.*` nem `mapa-local.csv`. Use o `.env.local.example` para ver os nomes das variáveis; para saber se uma variável está preenchida, confira dentro do container sem imprimir o valor: `[ -z "$VAR" ] && echo vazia || echo preenchida`"
- ambiente: "... não despejar variáveis de ambiente ... O jeito permitido é só 'vazia ou preenchida', sem o valor: `[ -z "$VAR" ] && echo vazia || echo preenchida`"

**Efeito colateral conhecido:** citar o caminho do arquivo de segredo mesmo para excluí-lo (`find . -not -name ".env.local"`) ou buscar o texto `.env.local` com `grep` é bloqueado; escreva o comando sem o nome. Comandos `git` só de leitura (`git status`, `git log`, `git ls-files`) também são bloqueados, como pede a regra do `CLAUDE.md`.

## Guarda 2: `guarda-arquivos` (Read, Edit, Write, MultiEdit, NotebookEdit, Grep, Glob)

Bloqueia quando `file_path`, `notebook_path`, `path`, `pattern` (no Glob) ou `glob` (no Grep) apontam para `.env`, `.env.local`, `.env.<x>` que não termine em `.example`, um curinga `.env*`, ou `mapa-local.csv`, com caminho Windows ou Unix e alternativas de glob (`{a,.env.local}`). No Grep o `pattern` é o texto procurado, não um caminho: buscar a palavra ".env.local" nos docs passa. `.env.local.example` e `.env.example` passam. Mensagem: a mesma da regra de segredo.

## Guarda 3: `varrer-dados` (depois de Write, Edit, MultiEdit)

Lê o arquivo gravado (até 2 MB; ignora binários, `node_modules`, `dist`, `target`, `.git`, `.angular` e a própria `.claude/hooks/`) e procura, linha a linha:

- **CPF e CNPJ com dígito verificador válido**, com e sem máscara (11 ou 14 dígitos seguidos, também dentro de nome de arquivo, mas não no meio de um hash);
- **token**: JWT (`eyJ...` em três partes) e `Bearer` seguido de valor com cara de aleatório (24+ caracteres, com letra e dígito: "Bearer token-que-nao-existe" passa);
- **chave privada** (`-----BEGIN ... PRIVATE KEY-----`).

Achado: código 2 com a lista `arquivo:linha (tipo)`, **sem repetir o valor**, e a orientação: trocar por `<CPF_DE_TESTE>`/`<CNPJ_DE_TESTE>` ou por dado evidentemente fictício, e, em teste, gerar o valor por cálculo na hora.

**Fictícios aceitos** (lista `FICTICIOS` em `varrer-dados.mjs`): o CNPJ de exemplo do projeto `12.345.678/0001-90`, todo CPF/CNPJ inválido de propósito (como `000.000.000-01`, que nem passa no dígito verificador) e três números **didáticos clássicos, válidos por cálculo**, que os testes de validação e máscara já usavam antes desta guarda: `111.444.777-35`, `11.222.333/0001-81` e `123.456.789-09`. Para acrescentar outro, edite a lista e registre aqui; prefira sempre gerar o número no teste.

Na criação desta guarda a varredura de todos os arquivos do projeto achou só um JWT de exemplo escrito como literal num teste (`EventoDeAuditoriaTest`), que passou a ser montado na hora.

## Falha segura

Erro **interno** de qualquer guarda (JSON inválido, entrada sem o campo esperado, arquivo que sumiu, exceção) não bloqueia: escreve em `stderr` `[<guarda>] aviso: <motivo>; a ação segue sem esta guarda.` e sai com código **1** (não bloqueante). O motivo é só o tipo do erro, nunca o conteúdo da entrada. Só o que casa com uma regra sai com código 2. Cada guarda tem `timeout` de 10 a 15 s no `settings.json`; estourar o tempo também não bloqueia.

## Testes

`node --test` com entradas simuladas (29 testes, `.claude/hooks/*.test.mjs`):

- `guarda-bash.test.mjs`: permitidos (`git diff` e variações, `docker compose up/down/exec` com a verificação "vazia ou preenchida", `npm test`, `node --test`, `./mvnw`, `.env.local.example`) e bloqueados por regra, com variações (maiúsculas, `git -C x commit`, `cd y && git push`, `echo a | git add`, `bash -c "git commit"`, `$( )`, crase, `cat`/`type`/`Get-Content .env.local`, caminho do Windows, PowerShell), a mensagem e o processo (código 2, código 0, falha segura com código 1).
- `guarda-arquivos.test.mjs`: exemplos passam, segredos bloqueados em cada ferramenta, `pattern` do Grep não é caminho, falha segura.
- `varrer-dados.test.mjs`: CPF e CNPJ válidos **gerados por cálculo no teste** (nunca literais), com e sem máscara; fictícios e inválidos aceitos; números longos que não são documento; JWT montado no teste; a mensagem sem eco do valor; pastas ignoradas, binário e arquivo grande; falha segura.

**Não** se prova o bloqueio tentando `git commit` ou lendo o `.env.local` de verdade: a prova é o teste automático. Para demonstrar à banca, o roteiro da S6 (bloco 9) pede ao Claude essas ações e mostra a recusa.
