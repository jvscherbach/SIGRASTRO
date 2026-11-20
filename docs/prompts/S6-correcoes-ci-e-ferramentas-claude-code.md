# S6: correções da homologação, CI mais leve e ferramentas do Claude Code no projeto

> **Forma de trabalho:** a sprint é dividida em **9 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

A S5 foi homologada. A S6 fecha o que a homologação encontrou, deixa o CI mais leve e **acrescenta ao repositório as ferramentas do próprio Claude Code** (hooks, servidor MCP e subagentes), que servem de **guarda de segurança** do projeto e serão mostradas à banca. O projeto passou a se chamar **Rastro**; o código ainda usa o nome antigo até a renomeação R1. **Nas coisas novas desta sprint use o nome Rastro** (nome do servidor MCP, textos, documentação). **Não** renomeie código existente aqui (é a R1).

**Fora desta sprint:** capa do dossiê (fica como está), material de apresentação, renomeação (R1), tramitação e financeiro (S7 a S9). Não antecipe nada disso.

**Pré-condição:** a S5.1 (data e hora no fuso de Cuiabá, utilidade única) precisa estar no código. Se não estiver, **pare** e avise.

Leia antes de começar: `README.md`, `CLAUDE.md`, `docs/02`, `docs/03`, `docs/05`, `docs/07`, `docs/08`, `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14`, `docs/progresso/s5.md` e o CI em `.github/workflows`.

## Decisões já tomadas (registre em `docs/09`)

- **Defeitos da homologação** (blocos 1 a 4) são corrigidos **na causa**, não mascarados. Cada bloco começa **descrevendo a causa encontrada** (no resumo e na mensagem de commit).
- **Armazenamento cheio:** gerar dossiê com disco cheio **falha com mensagem clara**, e a tela mostra o **erro real** e a ação certa. Nunca mensagem de sucesso quando o arquivo não existe ou está incompleto. A causa do relato original foi o disco da máquina do responsável lotado (o Docker grava no disco do host).
- **Teste de conexão:** diferencia **alcançável** de **funcional**. Resposta HTTP de um serviço alcançável **não** é "falha de rede".
- **CI sem custo:** o passo caro de instalar o LibreOffice (apt) roda **só na `main` ou manualmente**; PRs comuns e os do Dependabot **pulam** os testes que dependem do LibreOffice. Nada pago, nenhum recurso novo do GitHub.
- **Ferramentas do Claude Code no repositório** (blocos 6 a 8): tudo **gratuito**, em Node.js (o projeto já tem Node por causa do Angular), **sem build extra**, versionado em `.claude/`, `.mcp.json` e `tools/`. As guardas **reforçam as regras permanentes** (sem git além de `git diff`, sem abrir `.env.local`, sem `down -v`, sem dado pessoal real). As guardas **bloqueiam só o que casa com a regra**; erro interno do próprio hook **não trava a sessão** (avisa e deixa passar), e isso é testado e documentado.
- **O servidor MCP é somente leitura e só fala com o Rastro local em modo de demonstração (dados fictícios).** Nunca com o SIGA real.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou número de DAR real em teste, doc, exemplo, log ou repositório). CPF fictício é sempre **inválido de propósito** (ex.: `000.000.000-01`) e nomes são evidentes (`Servidor Exemplo 01`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Prefira o que o projeto já tem; para diagramas, **sem biblioteca**: HTML, CSS e SVG simples.
- **Somente leitura no banco.** O usuário do banco só tem `SELECT`. Todo SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados** (nunca concatenar valor), `FETCH FIRST n ROWS ONLY`, tempo limite de consulta e concentrado **num único componente de acesso** (risco R04 de `docs/10`). Nada é gravado no Oracle.
- **Dados pessoais:** `ASSIGNEE_`, vínculos de identidade e variáveis do Camunda guardam **CPF e nome**. O CPF **nunca** sai em resposta, log, erro, evento de auditoria, CSV ou tela, **nem parcialmente mascarado**. Variáveis do Camunda só podem ser lidas de uma **lista fixa** (lista branca) sem dado pessoal. A chave do arquivo, tokens e cookies também nunca saem (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Datas e horas sempre no fuso de **Cuiabá (`America/Cuiaba`)**, pela utilidade única já existente (S5.1). Data pura (sem hora) nunca muda de dia na conversão.
- **Ferramentas novas (blocos 6 a 8):** dependências npm só **gratuitas e de código aberto**, com **versão fixa**, licença registrada em `docs/13`. Prefira a biblioteca padrão do Node.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s6.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s6.md` (crie no bloco 1, com a tabela dos 9 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`. A mensagem **não** começa com espaço.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Lista de documentos: nome do arquivo × tipo, e cabeçalho da planilha | **SONNET** | Correção de mapeamento com testes fechados |
| 2 | Front: listagem de Usuários e perfis e coluna Detalhe da auditoria | **SONNET** | Layout |
| 3 | Armazenamento cheio: falha clara e erro real na tela | **OPUS** | Tratamento de erro de E/S em fluxo sensível |
| 4 | Teste de conexão: falso alarme (503, 400, tempo de 5 s) | **OPUS** | Diagnóstico de causa; fluxo com credencial |
| 5 | CI: testes de LibreOffice só na `main` ou manual | **SONNET** | Workflow |
| 6 | Hooks: guardas de git, segredos, volumes e dados pessoais | **OPUS** | Segurança; erro aqui trava ou deixa passar |
| 7 | Servidor MCP do Rastro (somente leitura, demonstração) | **OPUS** | Superfície de acesso e mascaramento |
| 8 | Subagentes e comando `/verificar-sprint` | **SONNET** | Definições em Markdown |
| 9 | Roteiro de homologação, documentação e fechamento | **SONNET** | Documentação |

---

## Bloco 1 (SONNET): nome do arquivo × tipo na lista de documentos e cabeçalho da planilha

**Commit sugerido:** `fix(documentos): nome do arquivo e tipo nos lugares certos e cabeçalho da planilha pelo nome do arquivo`

**O que a homologação viu:** na lista de documentos, o **nome do arquivo** aparece onde deveria aparecer o **tipo/descrição**, e o tipo onde deveria aparecer o nome (comparado com a lista do SIGA); e, na **conversão de planilha** para PDF, o cabeçalho da página mostra o **nome do tipo** do documento (ex.: "Teste de bombeamento.") em vez do **nome do arquivo**.

- **Antes de alterar**, localize de onde sai cada coluna (back-end: DTO e montagem; front: tabela; e o mapeamento da S2.1 em `docs/04`/`docs/08`) e **descreva o estado atual** no resumo. O critério é **o que o SIGA mostra**: a coluna do nome mostra o **nome do arquivo** e a de tipo/descrição mostra o **tipo**. Se o código contradisser a descrição acima (ex.: a lista já está certa e só o cabeçalho está errado), corrija **só o que estiver errado** e relate.
- Garanta a **mesma regra em todos os lugares** que exibem esses dois campos: lista, filtros e ordenação por coluna, título do modal de visualização, sumário do dossiê, páginas substitutas, **cabeçalho da planilha convertida**, e o retrato do documento na auditoria (título e arquivo), sem alterar o formato dos eventos já gravados.
- **Cabeçalho da planilha convertida:** o **nome do arquivo sem a extensão** (`Teste de bombeamento.xlsx` → `Teste de bombeamento`). Nome sem extensão, com ponto no meio ou só extensão: trate sem erro. Preserve o que a S3.1 definiu (centralização e posição), e mantenha o estilo da numeração da S3.2.
- Testes (back-end e front, Vitest e Playwright): um caso por lugar acima; nomes com acento, com ponto no meio, sem extensão; planilha de várias abas; o filtro por tipo continua funcionando.
- Docs: `docs/04` (campos de exibição), `docs/08`, `docs/progresso/s6.md` (crie com a tabela dos 9 blocos).

## Bloco 2 (SONNET): front, listagem de Usuários e perfis e coluna Detalhe

**Commit sugerido:** `fix(front): layout da lista de Usuários e perfis e alinhamento da coluna Detalhe da auditoria`

**O que a homologação viu:** a listagem de **Usuários e perfis** (Configurações) aparece quebrada, e a coluna **Detalhe** da auditoria de **Acessos** fica desalinhada.

- **Reproduza primeiro** (Playwright, 1280, 1024, 768 e 360 px, com muitos usuários e com textos longos) e descreva a causa (largura de colunas, `overflow`, quebra de linha, cabeçalho e células com alinhamentos diferentes, grid versus tabela). Corrija na causa; sem remendo por pixel.
- Usuários e perfis: colunas legíveis sem corte, ações (editar, ativar/desativar) alinhadas, perfil e situação com texto (não só cor), celular sem rolagem horizontal da página.
- Auditoria › Acessos: **cabeçalho e células da coluna Detalhe com o mesmo alinhamento e largura**, texto longo com quebra ou reticências com o texto completo acessível (tooltip e leitor de tela), mesmo comportamento na auditoria de **Documentos**.
- Testes (Playwright): asserção de **alinhamento por geometria** (posição horizontal do cabeçalho e das células da mesma coluna) em cada largura, e ausência de rolagem horizontal; não use comparação de imagem.
- Docs: `docs/08`.

## Bloco 3 (OPUS): armazenamento cheio, falha clara e erro real na tela

**Commit sugerido:** `fix(dossie): falha clara com disco cheio e erro real na tela de download`

**O que a homologação viu:** com o armazenamento cheio, ao baixar um dossiê gerado a tela mostra **mensagem de sucesso** e só oferece "gerar novamente" e "descartar". Depois de liberar espaço, o download funcionou: o defeito é de **tratamento de erro**.

- **Descreva a causa** (onde a falha de escrita é engolida, se o dossiê é marcado `CONCLUIDO` com arquivo ausente ou incompleto, e onde o front converte o erro em sucesso).
- **Back-end:**
  - detectar falta de espaço na **escrita** (`IOException` de "sem espaço", nas mensagens do Linux e do Windows, e `FileSystemException`/`UncheckedIOException` encadeadas), na **conversão** (arquivos temporários do LibreOffice) e na obtenção dos arquivos; o pedido vira `FALHA` com código `ARMAZENAMENTO_INSUFICIENTE` e mensagem "Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente."; **apague** os arquivos parciais;
  - **só marcar `CONCLUIDO`** depois de fechar o arquivo e verificar que existe e tem tamanho maior que zero;
  - **verificação prévia** de espaço livre no diretório temporário antes de iniciar (`DOSSIE_ESPACO_MINIMO_MB`, padrão 500; 0 desliga): sem espaço, o `POST /dossies` responde `507` (`ProblemDetail`, código `ARMAZENAMENTO_INSUFICIENTE`), sem criar pedido;
  - o endpoint de download de um pedido cujo arquivo sumiu ou é inválido responde com **erro real** (`410` ou `500` conforme o caso, com código), nunca `200` com corpo vazio;
  - o evento de auditoria da falha (S5) registra o código, **sem caminho de arquivo**.
- **Front:** na gaveta e em "Dossiês gerados": mostrar o **erro real** (a mensagem do `ProblemDetail`), **nunca** o estado de sucesso quando o download falha; ações **"Tentar novamente"** (repete o download), "Gerar novamente" e "Descartar", cada uma com explicação curta. Falha no `POST` por falta de espaço mostra a mesma mensagem e a orientação de liberar espaço.
- **Simulação segura, sem lotar o disco real:** use um diretório temporário de teste com **cota pequena** (ou um `FileStore` simulado / ponto de injeção da escrita que lança "sem espaço"); **nunca** encha o disco da máquina.
- Testes: falha na escrita, na conversão e na obtenção; arquivo parcial apagado; `CONCLUIDO` só com arquivo válido; verificação prévia (507); download de pedido com arquivo ausente; no front, cada ação e a **ausência de mensagem de sucesso** nos erros; limpeza de timers.
- Docs: `docs/07` (códigos e 507), `docs/12` (`DOSSIE_ESPACO_MINIMO_MB`), `docs/10` (risco de disco cheio), `docs/08`.

## Bloco 4 (OPUS): teste de conexão, falso alarme

**Commit sugerido:** `fix(config): teste de conexão distingue serviço alcançável de falha e usa o mesmo cliente do fluxo real`

**O que a homologação viu:** os testes de conexão da **API de arquivos** (HTTP 503) e do **SCORP** (HTTP 400) e/ou **tempos de cerca de 5 s** indicam falha, mas o consumo real dos documentos **funciona normalmente**: é **falso alarme**.

- **Descreva a causa** depois de ler o código: URL do teste montada diferente da do fluxo real (lembrete: a base do SCORP precisa terminar em `/api` e o host leva `.apps`; compare com o cliente real), cliente HTTP diferente (timeout, proxy, TLS, cabeçalhos, autenticação), caminho de teste que não existe no serviço, ou tempo limite do teste (5 s) menor do que o do fluxo real.
- **Mesma montagem do fluxo real:** o teste usa o **mesmo cliente, a mesma base de URL e a mesma autenticação** do consumo real, mudando só o recurso (uma chamada **inofensiva**, por exemplo uma chave fictícia que o serviço recusa de forma conhecida).
- **Classificação do resultado** (com texto na tela, não só cor): **OK** (resposta esperada de serviço funcional), **Alcançável com ressalva** (o serviço respondeu com 4xx esperado para a chamada de teste, ex.: 400/404 para chave fictícia; mostrar o código e a explicação), **Falha de autenticação** (401/403: orienta renovar o token), **Falha** (DNS, recusa, TLS, tempo esgotado, 5xx persistente: mostra o motivo). 503 isolado: **uma nova tentativa** antes de concluir.
- **Tempo limite do teste configurável** (`CONEXAO_TESTE_TIMEOUT_SEGUNDOS`, padrão 15) e mostrado na tela; o tempo medido aparece no resultado.
- **Sem vazamento:** a resposta do teste **não** inclui URL completa com parâmetro, token, cookie ou corpo do serviço; a auditoria registra tipo e resultado.
- **Nada contra serviços reais:** teste com **servidores simulados** (MockWebServer/WireMock ou o que o projeto já usa): 200, 400 e 404 esperados, 401, 403, 503 isolado, 503 persistente, tempo esgotado, DNS inexistente, TLS inválido.
- Testes: cada classificação; que o teste usa o **mesmo** componente de montagem de URL do fluxo real (teste de regressão: se alguém mudar a URL do fluxo, o teste muda junto); o tempo limite; ausência de segredo no resultado.
- Docs: `docs/12` (conexões, classificações e variável), `docs/10`, `docs/05` (como diagnosticar cada resultado).

## Bloco 5 (SONNET): CI, testes de LibreOffice só na `main` ou manual

**Commit sugerido:** `ci: instala o LibreOffice e roda os testes de conversão só na main ou manualmente`

**Problema:** o passo "Instalar LibreOffice (testes de conversão)" via `apt` é demorado e consome minutos do plano gratuito do Actions em **todo** PR, inclusive nos do Dependabot.

- Marque os testes que dependem do LibreOffice com uma **tag** (JUnit 5, por exemplo `conversao-libreoffice`) e configure o build para **excluí-los** por padrão no CI de PR e **incluí-los** quando uma propriedade os habilitar. Localmente, o comando para rodar tudo continua documentado.
- **Workflow:** o passo de instalação e a execução dos testes marcados rodam **somente** em `push` na `main` e em `workflow_dispatch` (execução manual, com opção de incluí-los). Em PR comum e em PR do Dependabot (`github.actor == 'dependabot[bot]'`), o passo é **pulado** e o resumo do job diz, de forma visível, "testes de conversão com LibreOffice pulados neste PR".
- **Cache** do que já for gratuito e disponível (dependências), sem recurso novo do GitHub; **nada pago**; **não** remova nenhum outro teste nem verificação (ArchUnit, front, e2e).
- Verifique localmente o que for possível (build com e sem a tag) e **valide o YAML** (sintaxe) sem executar o Actions; descreva no resumo como confirmar no primeiro PR.
- Docs: `docs/13` (CI: o que roda onde, como rodar os testes de conversão localmente e manualmente no Actions, e o **risco** de uma regressão de conversão só ser pega na `main`, com a mitigação: rodar localmente antes do PR quando mexer em `conversao`).

## Bloco 6 (OPUS): hooks, guardas do projeto

**Commit sugerido:** `feat(claude): hooks de guarda para git, segredos, volumes e dados pessoais`

Objetivo: **transformar as regras permanentes em guardas automáticas** da sessão do Claude Code. Os hooks do Claude Code rodam um comando em eventos de ferramenta; um comando que sai com **código 2** **bloqueia** a ação e devolve a mensagem de `stderr` ao Claude. **Confira o formato atual na documentação do próprio Claude Code** (nomes de eventos, `matcher`, entrada em JSON pela entrada padrão, variável `CLAUDE_PROJECT_DIR`) antes de escrever; não suponha.

- **Estrutura:** `.claude/settings.json` (**versionado**) com os hooks; `.claude/hooks/*.mjs` (Node ESM, sem dependências; comando sempre com `node "$CLAUDE_PROJECT_DIR/.claude/hooks/<arquivo>.mjs"` para funcionar de qualquer diretório, **inclusive no Windows**); testes `.claude/hooks/*.test.mjs` com `node --test`. `.claude/settings.local.json` continua **fora** do versionamento (confira o `.gitignore`).
- **Hook 1: `guarda-bash` (PreToolUse, Bash).** Bloqueia, com mensagem em português que diz **qual regra** e **o que fazer**:
  - **qualquer `git` que não seja `git diff`** (aceitos: `git diff`, com `--stat`, `--name-only`, `--cached`, `--` e caminhos), inclusive com `-C`, em comando composto (`&&`, `||`, `;`, `|`, quebra de linha, subshell e `bash -c`/`sh -c`), e **qualquer `gh`**;
  - `docker compose ... down` com `-v`/`--volumes`, `docker volume rm|prune`, `docker system prune` com `--volumes`;
  - **leitura de arquivo de segredo** por comando (`cat`, `type`, `Get-Content`, `more`, `less`, `head`, `tail`, `grep`, `Select-String`, `cp`, `copy`, `ls`/`dir` de conteúdo e qualquer comando com o caminho): `.env`, `.env.local`, `.env.*` **exceto** `*.example`;
  - **dump de ambiente:** `printenv`, `env` sem argumentos, `set` sem argumentos, `docker ... exec ... env|printenv`; a mensagem indica o **jeito permitido** ("só vazia ou preenchida": `[ -z "$VAR" ] && echo vazia || echo preenchida`).
- **Hook 2: `guarda-arquivos` (PreToolUse, Read, Edit, Write, MultiEdit, NotebookEdit, Grep, Glob).** Bloqueia caminho (`file_path`, `path`, `pattern`) que seja `.env`, `.env.local` ou `.env.*` que **não** termine em `.example`, e `mapa-local.csv`.
- **Hook 3: `varrer-dados` (PostToolUse, Write, Edit, MultiEdit).** Depois de gravar um arquivo de texto (até 2 MB; ignore `node_modules`, `dist`, binários e a própria pasta `.claude/hooks/`), procura: **CPF e CNPJ com dígito verificador válido** (com e sem máscara), exceto os fictícios documentados (`12.345.678/0001-90` e CPF inválidos de propósito), **token** (padrão de JWT `eyJ...`, `Bearer <longo>`), **chave privada**. Achado: **código 2**, com a **lista de arquivo e linha, sem repetir o valor**, orientando trocar por `<CPF_DE_TESTE>`/`<CNPJ_DE_TESTE>` ou por dado evidentemente fictício.
- **Reforço em `permissions.deny`** do `settings.json` onde **não** bloquear os arquivos de exemplo (ex.: negar `Bash(git commit:*)` e `Bash(git push:*)`); não duplique o que o hook já cobre se o formato de regra não permitir a exceção.
- **Falha segura e sem travar:** erro **interno** do hook (JSON inválido, exceção) **não bloqueia**: escreve um aviso curto em `stderr`, sai com código **não bloqueante** e deixa a ação seguir. **Só** o que casa com uma regra bloqueia. Teste isso.
- **Testes** (`node --test`, entradas JSON simuladas): para o hook 1, **permitidos** (`git diff`, `git diff --stat`, `docker compose up`, `npm test`, `node --test`, `mvn`/`gradle` do projeto, `docker compose ... exec ... sh -c '[ -z "$X" ] ...'`) e **bloqueados** (cada regra, com variações: maiúsculas, `git -C x commit`, `cd y && git push`, `echo a | git add`, `bash -c "git commit"`, `cat .env.local`, `Get-Content .env.local`, `type .env.local`); para o hook 2, `.env.local.example` e `.env.example` **permitidos** e `.env.local` bloqueado; para o hook 3, **CPF e CNPJ válidos gerados no teste por cálculo** (nunca literais no arquivo), os fictícios aceitos, JWT de exemplo gerado no teste, e o aviso sem eco do valor; e o caso de entrada inválida (não bloqueia).
- **Não** tente executar `git commit` ou ler `.env.local` para "provar" o bloqueio: a prova é o teste automático com entradas simuladas.
- Docs: `docs/claude-code/README.md` (visão geral das ferramentas e **como desligar** um hook temporariamente: remover a entrada de `settings.json`), `docs/claude-code/hooks.md` (cada guarda, a regra que aplica, a mensagem, a falha segura, como rodar os testes), `CLAUDE.md` (seção "Ferramentas do Claude Code"), `docs/13` (testes dos hooks no CI **só se for barato**: `node --test` sem instalação).

## Bloco 7 (OPUS): servidor MCP do Rastro (somente leitura, demonstração)

**Commit sugerido:** `feat(claude): servidor MCP do Rastro com consultas somente leitura em modo de demonstração`

O **MCP** (Model Context Protocol) permite que o Claude Code chame **ferramentas** de um servidor local. O servidor do Rastro expõe consultas **somente leitura** ao próprio serviço **em modo de demonstração** (dados fictícios), para mostrar a integração sem tocar nada real.

- **Local:** `tools/mcp-rastro/` (Node ESM, **sem build**), `package.json` com **versão fixa** de `@modelcontextprotocol/sdk` (MIT) e do que ele exigir; licenças em `docs/13`. Registro em `.mcp.json` (raiz, versionado) com o servidor `rastro` por `stdio`. **Confira o formato e o SDK na documentação atual** antes de escrever.
- **Ferramentas** (todas `GET`, só leitura, saída em texto curto estruturado):
  - `buscar_requerimento` (`tipo`: REQUERIMENTO ou PROCESSO; `numero` `N/AAAA`): dados de exibição (número, situação, tipo, datas), **sem** CPF/CNPJ do interessado;
  - `listar_documentos` (`idRequerimento`): título, nome do arquivo, tipo, data, situação (vigente, substituído, indisponível), sigiloso (sim/não); **sem** chave do arquivo e **sem** conteúdo;
  - `estado_do_servico`: saúde do Rastro local e o modo (`demonstracao` ou outro).
- **Recurso** `rastro://docs/indice`: o índice de `docs/` (títulos e caminhos), só leitura.
- **Acesso ao Rastro:** `RASTRO_MCP_URL` (padrão `http://localhost:<porta do front>`, descubra em `compose.local.yaml`), usuário e senha de **demonstração** por `RASTRO_MCP_USUARIO`/`RASTRO_MCP_SENHA` (o login simulado do protótipo; **sem valor padrão no repositório**, só nome da variável em `.env.example`; **nunca** lidos de `.env.local` pelo servidor: vêm do ambiente do processo). O servidor faz login, guarda o token **só em memória** e renova ao expirar.
- **Segurança (testada):**
  - **só local:** recusa `RASTRO_MCP_URL` cujo host não seja `localhost`, `127.0.0.1` ou `host.docker.internal`; sem exceção por variável;
  - **só demonstração:** consulta o estado do serviço e **recusa operar** se ele não se declarar em modo de demonstração (`compose.verificacao.yaml`); mensagem clara orientando subir esse ambiente;
  - **só `GET`**; nenhuma ferramenta cria, altera, apaga, gera dossiê nem baixa documento;
  - **mascaramento de saída**: CPF/CNPJ (com e sem máscara), e-mail, token, cookie e chave de arquivo nunca saem, mesmo que a API os devolva por engano (varredura na saída com substituição por `***` e contagem no final);
  - erros sem URL com parâmetro, sem token e sem corpo bruto; tempo limite e tamanho máximo de resposta;
  - `stdout` só com o protocolo MCP; logs em `stderr`, sem dado sensível.
- **Testes** (`node --test`, com servidor HTTP **simulado** na própria suíte): login e renovação, cada ferramenta, recusa de host remoto, recusa fora do modo de demonstração, mascaramento (CPF/CNPJ/token injetados na resposta simulada), erro e tempo esgotado, e que **nenhuma** ferramenta usa método diferente de `GET` (teste inspeciona as chamadas).
- Docs: `docs/claude-code/mcp.md` (o que expõe, como subir o ambiente de demonstração, como o Claude Code pede aprovação ao usar o servidor do projeto, como testar, o que **não** faz), `docs/12` (variáveis `RASTRO_MCP_*`), `.env.example`, `CLAUDE.md`.

## Bloco 8 (SONNET): subagentes e comando `/verificar-sprint`

**Commit sugerido:** `feat(claude): três subagentes e comando /verificar-sprint de orquestração mínima`

Orquestração **mínima**: um comando que dispara **três subagentes em paralelo** e **consolida** o resultado. **Confira o formato atual** dos arquivos de subagente (`.claude/agents/*.md`, com `name`, `description`, `tools` e `model` no cabeçalho) e de comando (`.claude/commands/*.md`) na documentação do Claude Code.

- **`.claude/agents/revisor-seguranca.md`** (somente leitura: `Read`, `Grep`, `Glob`; modelo mais forte): revisa o **`git diff`** do trabalho em andamento e o código novo contra as **regras permanentes**: SQL só `SELECT` e parametrizado; credencial, token, chave de arquivo e CPF fora de resposta, log, erro e auditoria; sigilo e matriz de acesso (`docs/07`, RN07, RN08); falha de auditoria sem efeito na operação; sem dado pessoal real em testes e docs; sem dependência paga. Devolve **achados** com arquivo e linha, gravidade e correção sugerida, **sem repetir valores sensíveis**.
- **`.claude/agents/executor-de-testes.md`** (`Bash`, `Read`, `Grep`, `Glob`): descobre em `CLAUDE.md` e `docs/13` **como rodar** os testes do back-end, do front e o e2e **do jeito documentado** (com Docker e os três arquivos de composição quando for o caso), roda, e devolve **só** o resumo: o que passou, o que falhou (nome do teste e a causa provável), o que foi pulado e por quê. Não corrige código.
- **`.claude/agents/atualizador-de-docs.md`** (`Read`, `Grep`, `Glob`, `Edit`; **só em `docs/`**): confere se a documentação (`docs/02`, `03`, `07`, `08`, `09`, `10`, `12`, `13`, `14` e o `docs/progresso` da sprint) **reflete** o que o código e o `git diff` mostram (rotas, variáveis, decisões, riscos, progresso) e **corrige** divergências com edições mínimas em `docs/`; lista o que mudou. **Nunca** edita código nem arquivos fora de `docs/`.
- **`.claude/commands/verificar-sprint.md`** (`argument-hint: <número da sprint>`): o orquestrador (a sessão principal): 1) lê `docs/progresso/s<N>.md`; 2) dispara os **três subagentes em paralelo**, cada um com o contexto da sprint; 3) **consolida** em `docs/progresso/verificacao-s<N>.md` com três seções (Segurança, Testes, Documentação), cada uma com **status** (`OK`, `ATENÇÃO`, `FALHA`), achados e **próximo passo**; 4) responde com a tabela resumo. O comando **não** corrige código, **não** faz commit e **não** roda nada fora das guardas dos hooks.
- Os agentes **seguem as regras** (nenhum git além de `git diff`, nada de `.env.local`); os hooks do bloco 6 valem para eles.
- **Teste:** rode `/verificar-sprint 6` **uma vez**, no estado atual do repositório, e **guarde o relatório gerado** (`docs/progresso/verificacao-s6.md`) como evidência; relate no resumo o tempo e o que cada agente achou. Se algum subagente falhar por formato, corrija a definição e rode de novo.
- Docs: `docs/claude-code/agentes.md` (papel, ferramentas e limites de cada agente; o fluxo do comando; como estender), `CLAUDE.md`.

## Bloco 9 (SONNET): roteiro de homologação, documentação e fechamento

**Commit sugerido:** `docs(s6): roteiro de homologação, documentação das ferramentas do Claude Code e fechamento`

- `docs/roteiros/s6-homologacao.md` (modelo dos roteiros anteriores), com:
  - **Defeitos corrigidos:** nome do arquivo × tipo (lista, modal, sumário, cabeçalho da planilha, com um arquivo de nome com ponto e outro sem extensão); Usuários e perfis e a coluna Detalhe (1280, 768 e 360 px); armazenamento cheio (simulação **segura** por `DOSSIE_ESPACO_MINIMO_MB` alto, **sem lotar o disco**) e o download com erro real; teste de conexão em cada classificação.
  - **CI:** abrir um PR comum e ver o passo pulado com a mensagem; disparar manualmente com a inclusão; conferir na `main`.
  - **Ferramentas do Claude Code:** como **demonstrar** cada uma: (1) pedir ao Claude para fazer commit e ver o **bloqueio**; (2) pedir para ler o `.env.local` e ver o bloqueio, e ler o `.env.local.example` e ver que passa; (3) pedir para gravar um arquivo com CPF válido (gerado na hora) e ver o aviso sem eco do valor; (4) chamar `buscar_requerimento` e `listar_documentos` pelo MCP com o ambiente de demonstração e ver a saída mascarada; (5) rodar `/verificar-sprint 6` e abrir o relatório.
  - **Manuais do responsável (fora do repositório):** rodar todos os casos de teste; comparar o **segundo processo de 2022** (a diferença 29 × 35 de documentos); **marca de versão** `vistas-1.0` no estado apresentado (ele cria, **você não**).
- **`docs/05`:** registre a pendência **P06** como **confirmada na prática**: o SCORP exige a base terminando em `/api` e o host com `.apps`, e a chave funciona com o token usado nos documentos pessoais (use o que já estiver em `docs/05`; **não invente** o que não souber). **P30:** leia o que ela é nos documentos, registre o estado atual e deixe escrito **"decisão do responsável"**; **não decida por ele**.
- Atualize `docs/14` (plano: R1, S7 a S11, conforme `docs/prompts/INDICE.md`, se existir) e `docs/progresso/s6.md`; `docs/10` revisado; `docs/claude-code/README.md` completo.
- Varredura final: nenhum CPF, CNPJ, nome ou número real no repositório (use o próprio hook como conferência); `git diff --stat` e resumo da sprint.

---

## Critérios de aceite (ao final do bloco 9)

1. Nome do arquivo e tipo **nos lugares certos** em todas as telas e no PDF; cabeçalho da planilha convertida pelo **nome do arquivo sem extensão**.
2. Usuários e perfis e a coluna Detalhe **alinhados** em 1280, 768 e 360 px.
3. Disco cheio: a geração **falha com mensagem clara**, sem arquivo parcial, e a tela mostra o **erro real** com "Tentar novamente"; **nunca** sucesso falso.
4. Teste de conexão **sem falso alarme** nos cenários simulados, com o mesmo cliente do fluxo real.
5. CI: LibreOffice só na `main` ou manual; PR e Dependabot pulam com aviso visível; nada pago.
6. **Hooks** ativos e testados: git (só `git diff`), `.env.local`, `down -v`, dump de ambiente e dado pessoal/token; erro interno não trava a sessão.
7. **MCP** `rastro` somente leitura, só local e só em demonstração, com saída mascarada, testado com servidor simulado.
8. **Três subagentes** e `/verificar-sprint` funcionando, com o relatório `docs/progresso/verificacao-s6.md` como evidência.
9. Testes verdes (back, front, e2e, ArchUnit, `node --test` das ferramentas), `docs/` e `docs/progresso/s6.md` atualizados, `docs/claude-code/` completo.
