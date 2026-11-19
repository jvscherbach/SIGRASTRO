# Verificação da S6 (`/verificar-sprint 6`)

- **Sprint:** 6 (correções da homologação, CI mais leve e ferramentas do Claude Code), estado do bloco 8.
- **Data:** 19/11/2026 (fuso de Cuiabá).
- **Base:** a árvore estava limpa e `git diff main...HEAD` trouxe 671 arquivos (a branch inteira). O escopo foi limitado ao que `docs/progresso/s6.md` dá como entregue (blocos 1 a 7 e os arquivos do bloco 8).
- **Duração:** cerca de 8 min 30 s do disparo até o último relatório (o executor de testes levou a maior parte: ~8 min; segurança ~2 min 45 s; documentação ~1 min).
- **Como foi executada:** esta é a execução única pedida pelo bloco 8. Os agentes novos ainda não estavam carregados na sessão que os criou (o Claude Code lê `.claude/agents/` e `.claude/commands/` ao iniciar), então o orquestrador disparou **três agentes gerais em paralelo que leram as definições** e seguiram as mesmas instruções e o mesmo modelo (`opus` para segurança, `sonnet` para testes e documentação). A restrição de ferramentas valeu só por instrução. Numa sessão nova, o comando usa os agentes definidos.

| Área | Status | Resumo |
|---|---|---|
| Segurança | **ATENÇÃO** | Nenhum achado alto; 1 média e 2 baixas |
| Testes | **ATENÇÃO** | Tudo que rodou passou; 56 testes do back-end pulados |
| Documentação | **ATENÇÃO** | 4 correções feitas em `docs/`; 4 divergências deixadas |

## Segurança

**Status: ATENÇÃO.** Revisados 34 arquivos: hooks e `settings.json`, servidor MCP e `.mcp.json`, agentes e comando, `ci.yml`, e o código de produção dos blocos 1 a 4 (`configuracao`, `integration.*`, `dossie`, `documento`, front).

**Achados**

- **[MÉDIA]** `.claude/hooks/guarda-bash.mjs:283`: a guarda só bloqueia o arquivo de segredo quando o comando cita o nome dele. Uma busca recursiva a partir da raiz (por exemplo `grep -rn <NOME_DE_VARIAVEL> .`, `rg --hidden`, `Select-String` ou `findstr` com recursão) passa e pode imprimir o conteúdo do arquivo de segredo da raiz. O próprio teste `guarda-bash.test.mjs:43` confirma que `grep -rn` passa. Sugestão do revisor: bloquear busca recursiva cujo alvo inclua a raiz sem exclusão explícita dos arquivos de segredo, ou quando o padrão procurado for nome de variável sensível; acrescentar testes e registrar em `docs/claude-code/hooks.md`.
- **[BAIXA]** `guarda-bash.mjs:342`: o despejo de variável sensível só é barrado por `echo`/`printf`/`Write-Output`. Escapam a variável sozinha como comando no PowerShell, `node -e` com `process.env` e `python -c` com `os.environ`. Corrigir ou registrar como limite conhecido.
- **[BAIXA]** `.github/workflows/ci.yml:73`: nenhum job roda `npm --prefix tools/mcp-rastro test` nem `npm audit` do servidor MCP. As garantias do MCP (só GET, só local, só demonstração, saída mascarada) e os alertas de dependência ficam sem fiscalização automática. Sugestão: um passo barato condicionado a `tools/`, com `npm ci`, os testes e `npm audit --omit=dev --audit-level=high`.

**Conferido sem achado:** erro interno dos hooks sai com código não bloqueante e sem eco da entrada; `git` só aceita `diff`; `gh`, `down -v` e variações de volume cobertos; MCP recusa host remoto, só faz `GET`, exige modo de demonstração, mascara a saída, guarda o token só em memória; teste de conexão sem URL, token ou corpo nas mensagens e sem risco de SSRF (o destino vem só da configuração); armazenamento cheio com mensagem fixa, sem caminho; nome do arquivo sem novo dado de identificação; front com token fora da URL; CI com valores do contexto por `env`, permissões mínimas e actions pinadas.

**Próximo passo (responsável):** decidir se as duas lacunas dos hooks viram um ajuste do bloco 6 (sessão OPUS, com testes) ou ficam registradas como limite conhecido em `docs/claude-code/hooks.md`; decidir se o teste do MCP entra no CI. Nada disso foi alterado neste bloco.

## Testes

**Status: ATENÇÃO** (nenhuma falha; parte ficou pulada).

| Comando | Resultado |
|---|---|
| `./mvnw -B -Dtest=ArchitectureTest test` | passou 23 |
| `./mvnw -B clean verify` | BUILD SUCCESS: 921 passaram, 0 falhas, 56 pulados (7 min 27 s) |
| `npm --prefix front run build` | sem erro |
| `npm --prefix front test -- --watch=false` | 438 passaram |
| `npm --prefix front run e2e` | 143 passaram |
| `node --test` dos hooks | 29 passaram |
| `npm --prefix tools/mcp-rastro test` | 20 passaram |
| `git diff --check` | sem problemas |

**Pulados (56, todos em `integration`):** 50 de conversão com LibreOffice (`soffice` ausente nesta máquina) e 6 de fumaça (`ArquivosFumacaTest`, `ScorpFumacaTest`, `SigaFumacaTest`: variáveis de fumaça ausentes, o esperado). O agente não confirmou o motivo exato de cada um na saída do Maven, nem se o Docker estava disponível para os testes de Testcontainers (eles não aparecem entre os pulados).

**Próximo passo (responsável):** rodar a conversão real com o LibreOffice instalado, ou disparar o CI manual com `incluir_conversao` (bloco 5), antes de fechar a sprint.

## Documentação

**Status: ATENÇÃO.**

**Corrigido em `docs/` pelo agente (para revisar no `git diff`)**

- `docs/10`: o risco "Regressão de conversão só aparece na `main`" estava como R65, número já usado pelo risco das guardas do Claude Code; passou a **R67** e foi movido para dentro da tabela de riscos. Referências ajustadas em `docs/13` e em `docs/progresso/s6.md` (bloco 5).
- `docs/14`: a visão geral não tinha a S6 vigente; foi acrescentada a linha "S6 (correções)" com o prompt e o estado, e registrado como NÃO CONFIRMADO em qual sprint entra a antiga "Validação e apresentação".

**Divergências que o agente não corrigiu**

- `docs/14`, seção "S6 — Validação e apresentação" (~linha 364): descreve outro escopo, que o prompt atual da S6 exclui. Decisão do responsável.
- `docs/10`: linhas de risco fora de ordem (por exemplo R64 antes de R56); sem repetição depois da correção.
- O roteiro de homologação da S6 (bloco 9) ainda não existe; o prompt manda `docs/roteiros/s6-homologacao.md`, enquanto os roteiros antigos ficam em `docs/homologacao/`. O bloco 9 decide o caminho.
- `docs/claude-code/agentes.md` não existia durante a verificação; foi criado em seguida, neste mesmo bloco.

Conferidos e batendo com o código: `docs/07` (507, códigos de arquivo ausente e inválido, `GET /estado`), `docs/09` (D76 a D80), `docs/12` (variáveis `DOSSIE_ESPACO_MINIMO_MB`, `CONEXAO_TESTE_TIMEOUT_SEGUNDOS`, `RASTRO_MODO`, `RASTRO_MCP_*`), `docs/13` (CI, tag, licenças). `docs/05` e `docs/08` só foram checados por contagem de menções, não a fundo.

**Próximo passo (responsável):** revisar as edições do agente em `docs/10` e `docs/14` e decidir o que fazer com o escopo antigo da "S6" em `docs/14`.

## Limites desta verificação

- Nada foi conferido contra dados reais: a homologação com arquivos, SIGA, SCORP e Camunda reais é do responsável (bloco 9).
- O CI no GitHub (inclusive o passo condicional de LibreOffice do bloco 5) não foi executado.
- O revisor não executou nada e não revisou os testes Java e Vitest dos blocos 1 a 4 nem o restante dos 671 arquivos da branch. Não confirmou se a ferramenta Grep do Claude Code alcança arquivos ocultos ou ignorados quando recebe um diretório, nem se o Dependabot cobre `tools/mcp-rastro/`.
- Os agentes foram executados como agentes gerais lendo as definições (ver o início do relatório); o comportamento dos agentes carregados de `.claude/agents/` numa sessão nova não foi exercitado.
