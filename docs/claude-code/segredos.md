# Segredos: camadas de proteção e o segredo fora da vista do agente (S6.1, bloco 2)

**Exigência do responsável: nada pode vazar.** Nenhuma camada sozinha garante isso (D81), então são quatro, independentes. Este documento diz o que cada uma cobre, o que **não** cobre e o passo a passo **manual** para tirar os segredos da pasta do projeto.

## As camadas

| Camada | O que cobre | O que não cobre |
|---|---|---|
| 1. Guardas (hooks) | `guarda-bash`: qualquer comando de Bash ou PowerShell que possa ler, copiar, enviar ou imprimir segredo ou ambiente, com a postura "na dúvida, bloqueia" ([hooks.md](hooks.md)). `guarda-arquivos`: Read, Edit, Write, MultiEdit, NotebookEdit, Grep e Glob, com caminho normalizado (`..`, `./`, `\`, maiúsculas, `~`, `/c/...`, link simbólico) e Grep/Glob sobre pasta conferidos no disco | Script em arquivo que o comando roda; Windows sem Git Bash (hooks desligados); erro interno da guarda (a ação segue) |
| 2. `permissions.deny` (`.claude/settings.json`) | `Read` e `Edit` (o `Edit` vale para Write e NotebookEdit; o `Read` deny também vale para Grep e Glob, e para `cat`/`head`/`tail`/`sed`/`tee` no Bash, segundo a documentação) de `.env`, `.env.local`, `.env.*` (com `!.env.example` e `!.env.*.example` liberando os exemplos), `mapa-local.csv`, `.secrets/**`, `.rastro/**`, `~/.rastro/**`, `~/.ssh/**`, `~/.aws/**`, `~/.gnupg/**`, `~/.kube/**`, `~/.docker/config.json`, `~/.git-credentials`, `~/.netrc`, `~/.npmrc`, `~/.claude/.credentials.json`; `git commit`, `git push` e `gh` no Bash e no PowerShell. Vale mesmo com os hooks desligados | A documentação diz que regra de Bash casa só o texto (não impede `grep -r . ` nem script que abre o arquivo) e que a aplicação a Grep/Glob é "best-effort" |
| 3. Isolamento do sistema (sandbox) | **Indisponível nesta máquina.** A documentação oficial: "The sandbox runs on macOS, Linux, and WSL2. On native Windows, Claude Code runs commands unsandboxed." Também: o sandbox cobre só comandos de shell ("Claude's file tools, MCP servers, and hooks run outside it") | Não foi configurado (`sandbox` ausente do `settings.json`, conferido por teste). Para ter: rodar o Claude Code dentro de uma distribuição **WSL2** e ativar com `/sandbox` ou `"sandbox": {"enabled": true, "filesystem": {"denyRead": ["~/.rastro", "<pasta do projeto>/.env.local"]}, "network": {"allowedDomains": [...]}}` no `settings.json` (caminhos do sandbox: `/abs` e `~/x`, diferentes das regras de `Read`) |
| 4. Segredo fora da vista | O arquivo de segredos sai da pasta do projeto (abaixo). Mesmo que uma camada falhe, o que está na pasta do projeto não tem mais segredo | Depende do responsável fazer a mudança à mão |

**O que nenhuma camada impede:** o responsável colar um segredo no chat; um script do projeto (executado por `npm run`, `node arquivo.mjs`, `bash x.sh`) que leia o arquivo de segredos se ele estiver ao alcance; um programa desconhecido que receba o caminho sem curinga. Por isso a camada 4.

### Grep e Glob: o que alcançam (prova)

Documentação oficial: "Grep respects `.gitignore`, so gitignored files are skipped" e "Glob doesn't respect `.gitignore` by default, so it finds gitignored files alongside tracked ones" (só nomes). Conferido nesta sessão (S6.1, bloco 2) com duas iscas fictícias, apagadas em seguida: o Grep a partir da raiz **achou** um arquivo oculto não ignorado (`docs/.isca-s61-oculta`) e **não achou** o ignorado (`api/target/isca-s61-ignorada.txt`); o Glob `**/isca-s61*` **listou** o ignorado. Logo: o Grep alcança arquivos ocultos, mas não os ignorados; todos os segredos do projeto são ignorados (`.env.local`, `.env.*`, `.secrets/`, `amostras-locais/`, `.rastro/`). A `guarda-arquivos` não confia só nisso: num Grep sobre pasta sem `glob`, confere no disco que **todo** segredo da pasta está ignorado pelo `.gitignore` (se alguém tirar um padrão do `.gitignore`, a busca passa a ser bloqueada); no Glob, bloqueia padrão que alcance nome de segredo (`**/*`, `**/.*`, `.e*`).

### Permissões: como ficou e o que foi conferido

A documentação diz que a avaliação é "deny, then ask, then allow" e que "An allow rule can't carve an exception out of a deny rule", mas aceita **negação** dentro do próprio `deny` (padrão que começa com `!`, como no `.gitignore`, na mesma fonte). Por isso `.env.*` é negado e `!.env.*.example` vem logo depois (o teste confere a ordem). Conferido na sessão: depois da mudança, a leitura do `.env.local.example` continua permitida. **Não isolado na sessão:** se o `deny` sozinho barra o `.env.local`, porque o hook `guarda-arquivos` barra antes; o formato segue a documentação.

## Variáveis do ambiente do Claude Code (só nomes)

Conferido nesta sessão por um script que lista **só os nomes** (nunca o valor) das variáveis do processo cujo nome parece segredo: nenhuma do projeto (`SIGA_*`, `LICENCIAMENTO_*`, `SCORP_*`, `VISTAS_DB_*`, `RASTRO_MCP_SENHA`). Apareceram 9, todas do próprio app do Claude: `CLAUDE_CODE_MESSAGING_TOKEN` (injetada pelo app desktop; o eco de qualquer variável com `TOKEN` no nome é barrado pela `guarda-bash`), `CLAUDE_CODE_SESSION_ID`, `CLAUDE_CODE_HOST_SESSION_ID`, `CLAUDE_CODE_CHILD_SESSION`, `CLAUDE_CODE_OAUTH_SCOPES`, `CLAUDE_CODE_SDK_HAS_HOST_AUTH_REFRESH`, `CLAUDE_CODE_SESSION_ATTENDED`, `USE_LOCAL_OAUTH`, `USE_STAGING_OAUTH`. Não há o que remover.

**Se um dia aparecer uma variável de segredo do projeto no ambiente** (por exemplo, `export SIGA_DB_PASSWORD=...` no perfil do shell): tire-a de onde ela é definida e abra o Claude Code num terminal novo.

- Git Bash: procure em `~/.bashrc`, `~/.bash_profile`, `~/.profile` (`grep -n SIGA_ ~/.bashrc` feito por você, não pelo Claude) e apague a linha.
- PowerShell: `notepad $PROFILE`, apague a linha; e nas variáveis do usuário do Windows: **Configurações → Sistema → Sobre → Configurações avançadas do sistema → Variáveis de Ambiente**, remova da lista do usuário.
- Credencial do servidor MCP (`RASTRO_MCP_SENHA`) é de **demonstração** e fica só no terminal da demonstração; defina sem eco (`read -s RASTRO_MCP_SENHA; export RASTRO_MCP_SENHA`) e feche o terminal depois.

## Passo a passo manual: segredos fora da pasta do projeto

**Quem faz:** o responsável, à mão. O Claude não abre, não move e não apaga o `.env.local`.

**Por que não basta `--env-file`:** no Compose do projeto as variáveis chegam ao container pelo `env_file` do `compose.local.yaml` (o `compose.yaml` não usa `${...}`). O `--env-file` da linha de comando só serve para interpolar o próprio arquivo do Compose; sozinho, ele **não** entrega as variáveis à `api`. Por isso o caminho do arquivo vira uma variável no `env_file`.

1. **Crie a pasta fora do projeto** e copie o arquivo (copiar, não mover, até conferir):
   - PowerShell: `New-Item -ItemType Directory -Force "$HOME\.rastro"; Copy-Item .env.local "$HOME\.rastro\env.local"`
   - Git Bash: `mkdir -p ~/.rastro && cp .env.local ~/.rastro/env.local && chmod 600 ~/.rastro/env.local`
   (Se o token da API de arquivos estiver em `.secrets/`, mova também: `~/.rastro/secrets/`, e ajuste o volume comentado no `compose.local.yaml` para esse caminho.)
2. **Aponte o Compose para o arquivo novo**, editando `compose.local.yaml` (serviço `api`, `env_file`):

   ```yaml
       env_file:
         - path: ${RASTRO_ENV_FILE:-.env.local}
           required: false
   ```

   Com isso, sem a variável nada muda (continua o `.env.local` do projeto); com ela, vale o arquivo de fora.
3. **Defina a variável no terminal onde você sobe o ambiente** (não no `.env.local`):
   - PowerShell: `$env:RASTRO_ENV_FILE = "$HOME\.rastro\env.local"`
   - Git Bash: `export RASTRO_ENV_FILE="$HOME/.rastro/env.local"`
   (Para ficar permanente, coloque essa linha no `$PROFILE` ou no `~/.bashrc`: ela tem só o caminho, não o segredo.)
4. **O comando de subir muda só para o ambiente com os sistemas reais** (o da homologação, sem o `compose.verificacao.yaml`); a verificação de sempre continua com os três arquivos e esvazia os externos de qualquer jeito:

   ```bash
   docker compose -f compose.yaml -f compose.local.yaml up -d --build      # homologação, com RASTRO_ENV_FILE definido
   ```
5. **Confira que as 15 variáveis externas chegam ao container**, só "vazia ou preenchida" (rode você; a guarda não deixa o Claude rodar laço com `eval`):

   ```bash
   docker compose -f compose.yaml -f compose.local.yaml exec -T api sh -c '
   for v in SIGA_DB_URL SIGA_DB_USER SIGA_DB_PASSWORD LICENCIAMENTO_API_URL LICENCIAMENTO_API_TOKEN \
            LICENCIAMENTO_API_TOKEN_FILE LICENCIAMENTO_API_AUTH_MODE LICENCIAMENTO_API_COOKIE_NAME SCORP_API_URL \
            SCORP_API_TOKEN_FILE SCORP_API_AUTH_MODE SCORP_API_COOKIE_NAME VISTAS_DB_URL VISTAS_DB_USER VISTAS_DB_PASSWORD; do
     eval "[ -z \"\${$v}\" ]" && echo "$v vazia" || echo "$v preenchida"
   done'
   ```

   Compare com o que você esperava: as que você preencheu antes devem continuar "preenchida". As vazias por escolha (por exemplo, `VISTAS_DB_*` no protótipo) continuam "vazia".
6. **Só depois da conferência, apague o `.env.local` do projeto** (`Remove-Item .env.local` ou `rm .env.local`). Se o token estava em `.secrets/`, apague a pasta também.
7. **Negação para o agente:** o caminho novo já é negado por `permissions.deny` (`Read(~/.rastro/**)`, `Edit(~/.rastro/**)`) e pelas duas guardas (qualquer caminho com a pasta `.rastro`); o `.gitignore` tem `**/.rastro/**` para o caso de alguém copiar a pasta para dentro do projeto.

**Como voltar atrás:** copie `~/.rastro/env.local` de volta para `.env.local` na raiz do projeto, remova `RASTRO_ENV_FILE` do terminal (`Remove-Item Env:RASTRO_ENV_FILE` ou `unset RASTRO_ENV_FILE`) e, se quiser, desfaça a linha do `compose.local.yaml` (o padrão `${RASTRO_ENV_FILE:-.env.local}` já cai no `.env.local` sem a variável).
