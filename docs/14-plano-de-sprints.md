# 14 — Plano de sprints (protótipo)

Objetivo do protótipo: comprovar, com processos reais, que (1) os processos que hoje não geram vista passam a gerar, (2) planilhas e outros formatos entram convertidos no PDF e (3) todos os documentos relacionados são listados. Com isso, apresentar a proposta e pedir acesso ao Pandora e ao ambiente do SIGA.

## Premissas

- Sprints de **1 semana**. Total: 7 sprints (S0 a S6).
- A implementação é feita com o Claude Code a partir de um prompt `.md` por sprint, seguindo [13 — Padrões técnicos](13-padroes-tecnicos.md). A revisão é feita por `git diff`; commit e push ficam com o responsável pelo projeto.
- Escopo do protótipo: sem schema próprio (dados de usuários, auditoria, configurações e fila em memória), execução em Docker, pools de conexão pequenos.
- O caminho que prova a proposta (consulta → documentos → arquivo → PDF → conversão) vem **antes** das telas de gestão.

## Pré-requisitos (antes da S0)

| # | Item | Pendência | Necessário a partir de |
|---|---|---|---|
| 1 | Usuário `VISTAS_LEITURA_SIGA` com `SELECT` nas tabelas do [04](04-modelo-de-dados.md) | P10 | S1 |
| 2 | Rede da máquina do Docker até o Oracle do SIGA e a API de arquivos | P11 | S1 |
| 3 | Token de sessão capturado e forma de envio confirmada (Bearer ou cookie) | P08 | S2 |
| 4 | Lista de processos de teste em `casos-de-teste.md` (que falham hoje e com planilhas) | P09 | S2 (uso contínuo), obrigatório na S6 |
| 5 | Mapeamento das tabelas de objetivo/finalidade/atividade e da prioridade de atendimento | P01 | S1 |

## Visão geral

| Sprint | Tema | Resultado demonstrável |
|---|---|---|
| S0 | Fundação | `api` e `front` sobem no Docker; ArchUnit, erros e correlação funcionando |
| S1 | Consulta | Buscar por requerimento/processo e ver dados reais e tarefa atual |
| S2 | Documentos e dossiê PDF | Listar todos os documentos e gerar o dossiê de **PDFs** de um processo real |
| S2.1 / S2.2 | Ajustes da S2 | Documentos da Vista do SIGA (S2.1); seleção total, ordem garantida, falhas coletadas com continuação, PDF com os não incluídos e dossiê grande por ticket (S2.2) |
| S3 | Conversão e visualização | Planilhas, textos e imagens convertidos dentro do dossiê; modal e pré-visualização |
| S4 | Regras de acesso | Perfis, sigilo, interessado, geometrias e tela "Dossiês gerados" |
| S5 | Gestão | Auditoria (documentos e acessos) e configurações, em memória |
| S6 | Validação e apresentação | Casos de teste executados, evidências e material da apresentação |
| S6 (correções) | Correções da homologação, CI mais leve e ferramentas do Claude Code | Prompt [S6-correcoes-ci-e-ferramentas-claude-code.md](prompts/S6-correcoes-ci-e-ferramentas-claude-code.md); **blocos 1 a 9 concluídos** (em [progresso/s6.md](progresso/s6.md); verificação em [progresso/verificacao-s6.md](progresso/verificacao-s6.md); roteiro de homologação em [roteiros/s6-homologacao.md](roteiros/s6-homologacao.md), a executar pelo responsável). Entrega: nome do arquivo × tipo, layout de Usuários e Detalhe, disco cheio, teste de conexão, CI sem LibreOffice em PR, hooks, MCP e subagentes. O prompt é o da S6 vigente; o tema "Validação e apresentação" da linha acima ainda não tem prompt (NÃO CONFIRMADO: em qual sprint entra) |
| R1 | Renomeação para Rastro (concluída, blocos 1 a 7) | Nome visível e documentação com "Rastro" (Fase 1, blocos 1 a 4); nomes técnicos só depois da apresentação e da marca `vistas-1.0` (Fase 2, blocos 5 a 7). Ver [R1](#r1--renomeação-para-rastro) |
| S7, S8, S9 | Tramitação e financeiro (prompts já escritos) | [S7](prompts/S7-tramitacao-onde-esta-e-com-quem.md) (onde está e com quem), [S8](prompts/S8-tramitacao-historico-e-fluxograma.md) (histórico e fluxograma), [S9](prompts/S9-financeiro-taxas-dar-e-pagamentos.md) (taxas, DAR e pagamentos). **S7 com os 11 blocos feitos** (catálogo de etapas com validador, Camunda fictício para testes, leitura da posição atual, responsável sem CPF, avisos do confronto com a situação, API e auditoria, varredura de vazamento e resiliência, "Onde está agora" e processo com vários requerimentos, parâmetros do Rastro nas Configurações, roteiro de homologação; [progresso/s7.md](progresso/s7.md), [15](15-tramitacao.md), roteiro em [roteiros/s7-homologacao.md](roteiros/s7-homologacao.md)). **Falta a homologação pelo responsável** (com dados reais e no ambiente com SIGA fictício, que ainda não existe; R66, R69) e as decisões P38 e P39. Resultado demonstrável na tela com a API simulada (Playwright); com dados reais, só depois da homologação |
| S8 | Tramitação: histórico, com quem esteve e fluxograma | [prompt S8](prompts/S8-tramitacao-historico-e-fluxograma.md). Plano B da situação do Licenciamento para o `SEM_INSTANCIA` (ver docs/15). Inclui as correções herdadas da S7 (status "Concluído" na verificação da revisão e "Camunda" fora das telas de consulta; P38, P39, D83). Escopo conforme o prompt. **S8 com os 12 blocos feitos** (histórico do caminho percorrido, reconstrução de etapas, ciclos e voltas, finais, plano B, API, desempenho, linha do tempo, fluxograma, estados de erro, acesso e varredura; [progresso/s8.md](progresso/s8.md), [15](15-tramitacao.md)). Roteiro de homologação em [roteiros/s8-homologacao.md](roteiros/s8-homologacao.md). **Falta a homologação pelo responsável** (conferência com dados reais, fora do repositório, e tela com SIGA fictício, que ainda não existe; R66, R69) |
| S9 | Financeiro: taxas, DAR e pagamentos | [prompt S9](prompts/S9-financeiro-taxas-dar-e-pagamentos.md), **11 blocos** (3 sessões: OPUS, SONNET e HAIKU). Inclui taxa vencida (aviso "Taxa vencida" por data, que a S7 deixou para cá), "Isento de Taxa", taxas e pagamentos no detalhe, marcas na tramitação e parâmetros nas Configurações. Blocos 1 a 10 feitos ([progresso/s9.md](progresso/s9.md)); bloco 11 = roteiro de homologação ([roteiros/s9-homologacao.md](roteiros/s9-homologacao.md)), com o passo `/verificar-sprint 9` numa sessão nova. **Falta a homologação pelo responsável** (conferência com dados reais, fora do repositório, e tela com SIGA fictício, que ainda não existe; R66). Regras provisórias e pontos abertos: P43 a P45 e R75, R76 em [10](10-pendencias-e-riscos.md) |
| S10 | Painel do Gestor | [prompt S10](prompts/S10-painel-do-gestor.md), **9 blocos** (sessões OPUS, SONNET e HAIKU). Painel em `/painel` (só Gestor e Administrador) com três gráficos: protocolados × concluídos por mês, fila por etapa e carga por responsável, no ano considerado (`rastro.painelAno`, 0 = ano corrente). Lê um **instantâneo em memória**, refeito por leitura periódica e limitada do SIGA e do Camunda (P42 resolvida: instantâneo no lugar da tabela de apoio; D92, D93). **S10 concluída com os 9 blocos** ([progresso/s10.md](progresso/s10.md); roteiro em [roteiros/s10-homologacao.md](roteiros/s10-homologacao.md), a executar pelo responsável, com o passo `/verificar-sprint 10` numa sessão nova). **Falta a homologação pelo responsável** (conferência com dados reais, fora do repositório, e tela com SIGA fictício, que ainda não existe; R66). Pontos provisórios: P46 (coluna do protocolo, data de conclusão aproximada, instância raiz) e riscos R82 e R83 em [10](10-pendencias-e-riscos.md) |
| S11 | Caixa de entrada do analista e lista por analista no painel | [prompt S11](prompts/S11-caixa-do-analista-e-lista-por-analista.md), **11 blocos** (sessões SONNET e HAIKU; sem Opus, decisão do responsável até a apresentação). Perfil **Analista** com a caixa "Minha caixa" (posse na análise e na revisão, correção com o interessado, aviso de recebimento com a origem do retorno e visto em memória), nome do interessado nas listagens, aba lateral "Processos por analista" no painel do Gestor e pizza na fila por etapa (correção da S10). **S11 concluída com os 11 blocos** ([progresso/s11.md](progresso/s11.md); roteiro em [roteiros/s11-homologacao.md](roteiros/s11-homologacao.md), a executar pelo responsável). Os números reais da suíte completa entram no fechamento (ver relatório final). **Falta a homologação com dados reais** (posse na correção, origens do retorno, nome de empresa e etapas do catálogo; P50 e R89). Pontos provisórios: D96 a D103, P47 a P50 e riscos R87 a R89 em [10](10-pendencias-e-riscos.md). O antigo tema "pago que virou isento" saiu da numeração e está na lista pós-apresentação abaixo |

| A1 | Commit automático e orquestração com quatro agentes (ferramentas do Claude Code) | [prompt A1](prompts/A1-commit-automatico-orquestracao.md), **6 blocos** (OPUS, SONNET e HAIKU). Constrói o modo automático: script `commit-bloco`, exceção estreita nas guardas, agentes `executor-*` e `commitador` e o comando `/orquestrar`. Blocos 1 a 5 feitos, bloco 6 (prompt de exemplo e roteiro) com o HAIKU ([progresso/a1.md](progresso/a1.md), [claude-code/orquestracao.md](claude-code/orquestracao.md)). Decisão D89, riscos R77 e R78. **Falta o teste de ponta a ponta pelo responsável** (roteiro do bloco 6). Os prompts seguintes, como a R2, só rodam sem parar entre os blocos depois disso |

> **Lista de sprints:** o índice dos prompts está em [prompts/INDICE.md](prompts/INDICE.md); a ordem acima vem dos prompts existentes em `docs/prompts/` (R1, S6, S7, S8, S9, S10, S11, A1). A lista do que fica para depois da apresentação está na seção [Primeira sprint pós-apresentação](#primeira-sprint-pós-apresentação), no fim desta seção.

## Primeira sprint pós-apresentação

Lista registrada no fechamento da S11 (bloco 11). **Sem prompt escrito**: cada item tem o motivo, e o prompt sai quando o responsável decidir a ordem. Os números abaixo são itens, não blocos.

1. **H1: identidade e persistência.** Inclui a persistência do "visto" da caixa, hoje em memória atrás da porta `VistoDoAnalista` (R88). *Motivo:* usuários, auditoria, configurações e o visto vivem em memória e somem ao reiniciar (R50, R57); a chave do analista muda a cada subida (P48c).
2. **Revisão com Opus das partes sensíveis da S10 e da S11.** Concorrência do instantâneo (painel e caixa), vazamento de CPF e chave opaca, limites de proteção do SIGA e do Camunda, e leitura em lote da posse. *Motivo:* são as partes de maior risco; a S11 não usou Opus por decisão do responsável até a apresentação.
3. **Validação com dados reais** da posse na correção (histórico do Camunda, P47a) e das origens "Voltou da ..." (P48a). *Motivo:* são NÃO CONFIRMADOS; só a conferência com dados reais os fecha (roteiro da S11, seção 8).
4. **Tarefas de grupo ainda não assumidas** na caixa. *Motivo:* ficaram fora desta sprint; o analista só vê o que tem na posse.
5. **Aviso de saída da posse** (decisão atual: não avisar) e **histórico das mudanças vistas**. *Motivo:* a saída foi decidida sem aviso (foi o próprio analista que enviou); o histórico depende da persistência do item 1.
6. **"Pago que virou isento"** (destaque e explicação), o antigo escopo da S11. *Motivo:* saiu do plano desta sprint pelo responsável; usa o cenário FIN12 de [15](15-tramitacao.md) e os dados do financeiro da S9.
7. **Documentos sigilosos divergentes** entre SIGA e Rastro (4 casos encontrados na verificação com dados reais). *Motivo:* a divergência é consciente hoje (D43), mas os quatro casos pedem decisão sobre a regra; os dados não entram no repositório.
8. **Conversão de planilhas sem tipo no dossiê**, se ainda não entregue (prompt V6, D94 e D95). *Motivo:* **NÃO CONFIRMADO**: não há registro de conclusão dessa parte em `docs/14` nem em `docs/progresso/`; confirmar antes de planejar.

**Revisão de segurança com dados reais:** o risco de segurança da conversão de XLSX sem `DisableActiveContent` (R84) segue para a revisão do item 2.

## R2 — Refatoração do front para o SIGRASTRO

Prompt: [R2-refatorar-front-sigrastro.md](prompts/R2-refatorar-front-sigrastro.md), **18 blocos** (OPUS, SONNET e HAIKU). Andamento em [progresso/r2.md](progresso/r2.md). Só `front/` e documentação: nome SIGRASTRO, marca (escudo árvore-vida), tema único Cerrado, barra superior com navegação horizontal, fontes locais, login novo, trilha no detalhe, testes e acessibilidade. Decisões D90 e D91, riscos R79 e R80. Guia visual em [design/sigrastro/LEIA-ME.md](design/sigrastro/LEIA-ME.md); roteiro do responsável em [roteiros/r2-homologacao.md](roteiros/r2-homologacao.md). **Situação:** blocos 1 a 17 feitos; falta o bloco 18 (nome visível nos documentos vivos) e a homologação pelo responsável.

## R1 — Renomeação para Rastro

Prompt: [R1-renomeacao-rastro.md](prompts/R1-renomeacao-rastro.md). Andamento por bloco em [progresso/r1.md](progresso/r1.md). Decisões D73 (nome e fases), D74 (pacote e propriedades com alias) e D75 (chaves do navegador com migração). Roteiro de conferência: [roteiros/r1-homologacao.md](roteiros/r1-homologacao.md).

**Situação:** blocos 1 a 7 concluídos (Fase 1 e Fase 2). Commits, testes e verificação com Docker estão em `docs/progresso/r1.md`. Ficam para o responsável: renomear o repositório e a pasta, migrar o volume e remover os aliases quando o prazo passar (lista em `docs/13` e R62 em [10](10-pendencias-e-riscos.md)).

- **Fase 1 (blocos 1 a 4, antes da apresentação):** o nome visível do produto passa a ser **Rastro** (front, PDF, OpenAPI e documentação). "Vistas" continua sendo o nome do módulo do dossiê. Não muda comportamento nem nome técnico.
- **Parada:** a Fase 2 só começa depois da apresentação e da marca de versão `vistas-1.0`, criada pelo responsável.
- **Fase 2 (blocos 5 a 7, depois da marca):** pacote Java, artefatos e containers, chaves do navegador e propriedades `vistas.*` (com alias), CI e varredura final. Rotas `/api`, SQL, schemas `SIGA2_*`, variáveis de ambiente e regras de negócio não mudam.

---

## S0 — Fundação

**Objetivo:** esqueleto do projeto pronto para receber funcionalidades sem retrabalho.

Entregas:
- Estrutura de pastas conforme [`padroes/estrutura-de-pastas.md`](padroes/estrutura-de-pastas.md) (só o que esta sprint usa).
- `api`: Spring Boot 3 / Java 21, `VistasApplication`, `shared` (ProblemDetail, `errorCode`, filtro de correlação, `PageResponse`), OpenAPI só no perfil local, `ArchitectureTest` com as regras de dependência e de `integration.*`.
- `front`: Angular com layout (barra lateral, topo, tema e tokens da paleta), login simulado com os três perfis e interceptors (token, erro por `errorCode`, correlação).
- `compose.yaml`, `compose.local.yaml`, Dockerfiles (`api` com LibreOffice já instalado), `.env.local.example`, `.gitignore`, `README.md`.
- Endpoint de saúde e uma tela inicial vazia navegável.

Aceite:
- `docker compose ... up` sobe `api` e `front`; `config --services` lista só os dois.
- `./mvnw clean verify` e o build do Angular passam; `ArchitectureTest` verde.

## S1 — Consulta

**Status:** implementada, testada e **homologada com dados reais** pelo responsável em 20 e 21/10/2026 (ver "Validação da S1" abaixo). Os campos do P01 apareciam como "—" e foram ligados na S1.1.

**Objetivo:** ler o SIGA de verdade.

Entregas:
- Datasource `siga` somente leitura (pool 2, `ReadTimeout`).
- `integration.siga`: consultas de requerimento, processo, interessado e tarefa atual (objeto e prioridade entraram na S1.1; o número de processo passou a 1:N na S1.1).
- Módulo `consulta`: busca por número de requerimento, de processo, CPF/CNPJ e nome; número do processo com prioridade na identificação (RN04).
- Front: telas de consulta e de detalhe (quadros com dados, datas e tarefa atual), sem a lista de documentos ainda.
- Tradução das situações do SIGA (Apêndice A do [04](04-modelo-de-dados.md)).
- **Antecipado da S4:** vínculo do Operador com o interessado (RN07) aplicado na consulta, no SQL (D24). A S4 mantém sigilo, geometrias, SCORP, "Dossiês gerados" e a validação completa da matriz de perfis.
- A `api` sobe e responde 503 `SIGA_INDISPONIVEL` sem o SIGA configurado (D26); roteiro de homologação em [`homologacao/S1-roteiro-homologacao.md`](homologacao/S1-roteiro-homologacao.md) (D27).

Aceite:
- Busca por requerimento, processo e interessado funcionando contra o SIGA, com os dados batendo com a tela atual do SIGA para 3 processos.
- Erros 400/404/503 no formato ProblemDetail.

### Validação da S1

**Executada pelo responsável em 20 e 21/10/2026**, com o roteiro [`homologacao/S1-roteiro-homologacao.md`](homologacao/S1-roteiro-homologacao.md). O desenvolvimento não se conectou ao SIGA real.

| Processo | Situação no SIGA | Resultado no SIGA Vistas | Bate? |
|---|---|---|---|
| 3331/2025 | Concluído, sem tarefa ativa | Identificação, situação, datas, interessado e tipo corretos; "Sem tarefa ativa" | Sim |
| 10468/2026 | Tarefa "Realizar Análise do Processo" desde 20/10/2026 12:14, com responsável | Tarefa, data e hora corretas (sem deslocamento de fuso); o responsável aparecia como CPF, corrigido na S1.1 | Sim, com ajuste |
| 18933/2026 | Não protocolado; tarefa "Conferir documentos apresentados", sem responsável | Identificação por número de requerimento, "não protocolado", tarefa correta, "Não atribuída" | Sim |

Observações: os campos do P01 apareciam como "—" (ligados na S1.1). A conexão real, o teste de fumaça e a configuração do Docker foram validados. Problemas de ambiente encontrados e resolvidos: nome curto do host do banco não resolvido dentro do container (usar FQDN ou IP no `.env.local`) e containers sem portas quando o `compose.local.yaml` não é passado (registrados nos problemas comuns do `README.md`).

## S1.1 — Ajustes da consulta com o mapeamento P01

**Status:** implementada, testada com portas falsas, `MockMvc` e Oracle 23 Free local (Testcontainers) e **homologada pelo responsável em 21/10/2026, em base de desenvolvimento** (ver "Validação da S1.1"). Dois achados da homologação foram corrigidos na S2: o quadro do topo que ainda mudava de tamanho (A1) e a listagem confusa de objetivos, finalidades e atividades (A2). O prompt da S1.1 foi recebido pela conversa e não está em `docs/prompts/`.

Entregas:
- Mapeamento P01 registrado em [04](04-modelo-de-dados.md#mapeamento-de-objeto-e-prioridade-p01) e ligado no SQL: `objetivos-outorga.sql`, `finalidades-atividades-outorga.sql`, `objeto-licenciamento.sql`, `prioridade.sql` e tipo do requerimento nas consultas por id e por número (porta `ObjetoEPrioridadeDoRequerimento`, adapter `ObjetoEPrioridadeDoSigaJdbc`).
- Domínio: `ObjetoDoRequerimento` (listas independentes), `TipoOutorga`, `PrioridadeAtendimento` e `CriterioPrioridade` (D30), `ResponsavelTarefa`.
- Responsável pela tarefa: nome resolvido em `tarefa-atual.sql` (`SERVIDOR`, depois `PESSOA`), situação `ATRIBUIDO`/`NAO_ATRIBUIDA`/`NAO_IDENTIFICADO`, CPF nunca sai do SQL; visível só para Gestor e Administrador (`Perfil.podeVerResponsavelTarefa()`, `permissoes.verResponsavelTarefa`, RN12, D32).
- Busca por CPF/CNPJ por comparação direta (`p.CPF_CNPJ = :digitos`), inclusive na restrição do interessado.
- Número de processo 1:N (D31): a busca devolve todos os requerimentos do número, com RN07 no SQL (403 se nenhum é do Operador); `requerimento-por-processo.sql` removido; nenhuma consulta lê `PROCESSO`.
- API: `objeto`, `prioridade`, `tipoRequerimento` e `tarefaAtual.responsavel` condicional ([07](07-api.md)).
- Front: chip de prioridade com critérios, tipo do requerimento, cartões do objeto, linha "Responsável" só quando vem na resposta, lista e aviso para processo repetido, quadro do topo com altura estável ([08](08-telas.md)).
- Roteiro [`homologacao/S1.1-roteiro-homologacao.md`](homologacao/S1.1-roteiro-homologacao.md).

Aceite:
- Processo 6023/2025 (outorga) com objeto igual ao do SIGA; um de licenciamento e um com critério de prioridade conferidos; 10468/2026 com o nome do responsável para Gestor e Admin e sem ele para o Operador; 445/2022 listando 2 requerimentos.
- Busca por CPF/CNPJ com os mesmos totais da S1.

### Validação da S1.1

**Executada pelo responsável em 21/10/2026, em base de desenvolvimento**, com o roteiro [`homologacao/S1.1-roteiro-homologacao.md`](homologacao/S1.1-roteiro-homologacao.md). O desenvolvimento não se conectou ao SIGA real.

| Item | Resultado |
|---|---|
| Grants | `SELECT` confirmado nas 19 tabelas lidas (`SIGA2_LICENCIAMENTO`, `SIGA2_OUTORGA`, `SIGA2_ARRECADACAO.ATIVIDADE` e 3 do motor de fluxo) |
| Colunas de ligação | Confirmadas em `ALL_TAB_COLUMNS`: `FINALIDADE_ATIVIDADE.ID_FINALIDADE`, `OBJETIVO_REQUERIMENTO_OUTORGA.ID_OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_FINALIDADE_ATIVIDADE` |
| Tipos | `ACT_RU_TASK.ASSIGNEE_` é `NVARCHAR2(510)`; `PESSOA.CPF_CNPJ` é `VARCHAR2(20)`; `SERVIDOR.CPF` é `VARCHAR2(11)` |
| Outorga (6023/2025) | Tipo do requerimento, objetivo, tipo, finalidade e atividade conferem com o SIGA; prioridade "Atendimento normal" confere; responsável aparece **só com o nome** |
| Outorga com prioridade (4162/2026) | 2 objetivos, finalidade/atividade e o critério "Pessoa com idade igual ou superior a 60 anos" conferem com o SIGA |
| Licenciamento (3040/2022, LAC) | Atividade confere; **diverge no Objetivo:** o SIGA mostra "LAC" e o SIGRASTRO mostra "—" (ver P20); o grupo da atividade aparecia junto da atividade (corrigido na S2, A2) |
| Número repetido (445/2022) | Lista os 2 requerimentos com o aviso |
| Busca por CPF/CNPJ | Totais da tela iguais ao `SELECT COUNT(*)` na base de desenvolvimento (8 e 2) |
| Quadro do topo | **Ainda mudava de tamanho** (corrigido na S2, A1, com medição automática) |

**Índices** (passo 2 do roteiro): existem `IDX_PRIORIDADE_PROC_REQ` (`PRIORIDADE_PROCESSO.ID_REQUERIMENTO`) e as chaves únicas de `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` e `REQUERIMENTO_OUTORGA.ID_REQUERIMENTO`. **Não retornaram índice:** `PESSOA.CPF_CNPJ`, `SERVIDOR.CPF` e `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_REQUERIMENTO` (R17).

Pendências abertas: P19 (só as descrições oficiais dos critérios e as tarefas atribuídas a grupo), P20 (objetivo "LAC") e R17 em [10](10-pendencias-e-riscos.md).

## S2 — Documentos e dossiê em PDF (com as correções da S1.1)

**Status:** implementada e testada sem nenhum sistema externo real (portas falsas, servidor HTTP local de teste, `MockMvc`, Oracle 23 Free por Testcontainers, Playwright com API simulada). **A homologação com arquivos reais é do responsável** (ver "Validação da S2"). O prompt da S2 foi recebido pela conversa e não está em `docs/prompts/`.

**Objetivo:** provar a geração do dossiê dos processos que hoje falham (somente PDFs nesta sprint).

Entregas da **Parte A** (correções da S1.1):
- **A1.** Quadro do topo com altura estável em todos os estados: a causa era `.search-feedback{min-height}` (a caixa do alerta passava da reserva e o quadro crescia 14 px a 1280×720, escalando a ilustração); a reserva agora é de altura fixa, com o alerta cortado em 2 linhas. Medido por teste e2e (24 casos, diferença de 0 px).
- **A2.** Objeto do requerimento em cartões "Objetivos" (unindo "Objetivos" e "Tipo | Objetivo"), "Finalidade e atividade" (agrupado por finalidade) e "Atividade" (grupo só como legenda quando difere); só cartões com dados.
- **A3.** Atalhos recentes desempatados ("Processo 445/2022 · Req. 3391/2022" e a situação).
- **A4.** Dados pessoais reais removidos do roteiro da S1.1; roteiros comparam a tela com `SELECT COUNT(*)`.
- **A5.** Homologação da S1.1 registrada; P19 reduzida, P20 e R17 abertos.

Entregas da **Parte B**:
- `integration.siga`: **uma consulta por origem** (seis origens; ampliadas por suposição na S2 e reduzidas na S2.1), sem filtrar vigência; vigência (RN01), sigilo tolerante (RN06), deduplicação por chave (RN05), data cronológica (RN03); compactados fora (RN11); geometrias classificadas e fora da lista até a S4.
- `integration.licenciamento`: download por streaming para disco temporário, com timeout de conexão e de leitura, modos `bearer` e `cookie` e tradução de erros (401/403 interrompem tudo, 404/503/timeout falham só o documento).
- Módulo `documento`: lista com totais e bloqueios, seleção validada no servidor; chave nunca exposta (RNF09); `id` em texto.
- Módulo `dossie`: pedido assíncrono, fila em memória (`DOSSIE_THREADS`), montagem com PDFBox em disco (capa, sumário determinístico, marcadores, numeração pela caixa e rotação, tamanho e rotação preservados), regra "completo ou nenhum" (RN09) com "tentar novamente" e "remover e gerar", expiração e limpeza agendada (D22).
- **Antecipado da S4:** RN07 e sigilo (RF20) já na lista e no `POST /dossies` (D36).
- Front: seção Documentos (busca, filtro de origem, ordenação, seleção em massa, bloqueios), barra de seleção, gaveta de geração com polling, pré-visualização e download por Blob.
- `compose.verificacao.yaml` e as regras de verificação sem sistemas externos (D34).

Aceite:
- Dossiê gerado com sucesso para **todos** os processos da lista de testes que hoje falham na Vista do SIGA (considerando só os PDFs). **Pendente de homologação.**
- Falha simulada (timeout) interrompe a geração sem entregar arquivo; nova tentativa funciona. **Provado em teste** (HTTP local) e a conferir com `DOWNLOAD_TIMEOUT_SEGUNDOS=1` na homologação.
- Nenhuma conexão do SIGA aberta durante download e montagem. **Provado em teste** (`ConexaoDoSigaLiberadaTest`: `getActiveConnections() == 0` no download, na validação e na montagem).

### Validação da S2

**PENDENTE: homologação manual pelo responsável**, com o token dele e arquivos reais. Roteiro, script de grants, consultas de dicionário e tabelas de comparação: [`homologacao/S2-roteiro-homologacao.md`](homologacao/S2-roteiro-homologacao.md). Casos de teste: [`casos-de-teste.md`](casos-de-teste.md). Registrar o resultado aqui e abrir pendência em [10](10-pendencias-e-riscos.md) para o que não bater.

| Requerimento / processo | Falha hoje na Vista do SIGA? | Documentos (SIGA × Vistas) | Dossiê gerado? | Páginas | Tempo | Bate? |
|---|---|---|---|---|---|---|
| _a preencher_ | | | | | | |

## S2.1 — Documentos reproduzem a Vista do SIGA

**Motivação:** o teste de fumaça da S2 contra o SIGA real falhou com `ORA-00904: "A"."NOME_ARQUIVO"` em `COMPLEMENTACAO_ITEM_ARQUIVO` (colunas assumidas por padrão de nome; origens ampliadas por suposição).

**Entregas:**
- Origens reduzidas às **seis da Vista** (`DOCUMENTO_PROCESSO`, `DOCUMENTO_REQUERIMENTO`, `COMPLEMENTACAO`, `IMOVEL`, `REPRESENTANTE_LEGAL`, `RESPONSAVEL_TECNICO`), com SQL e colunas conforme o banco; removidas `PARECER_ANEXO`, `JUNTADA`, `TITULACAO`, `PRORROGACAO`, `EXTRATO_PUBLICACAO` e a tabela de assinaturas (D41, D42).
- Chave única `(origem, id)`; título em três níveis; data da própria origem (nula vai ao final); sigilo real em RL e RT (D43); deduplicação e pré-seleção como divergências conscientes (D45, D46).
- Tipo `DESCONHECIDO` (D44): selecionável, fora da pré-seleção, etiqueta "tipo não informado", `tipoNaoInformado` nos totais; ao baixar, não-PDF falha o documento (RN09).
- `SigaFumacaTest` por origem (`-1`) e por requerimento real (`SIGA_FUMACA_REQUERIMENTO`); roteiro de homologação reescrito.
- Riscos do SIGA para levar à equipe: R22 a R25.

**Estado:** implementada e testada sem sistema externo real; **a homologação com o SIGA real é do responsável** (`homologacao/S2-roteiro-homologacao.md`).


## S2.2 — Seleção total, ordem garantida e geração que continua de onde parou

**Motivação:** homologação da S2/S2.1 com três processos reais. Documentos de tipo não informado ficavam fora da primeira geração; "Tentar novamente" e "Remover documento e gerar" reiniciavam o dossiê inteiro; arquivos de ~50 MB davam tempo limite; o PDF não registrava o que ficou de fora. O dossiê é usado em processos jurídicos, então a ordem por data e a transparência são obrigatórias.

**Forma de trabalho (nova):** 10 blocos, cada um com testes, documentação e ponto de commit próprios, revisados e commitados um a um pelo responsável.

| Bloco | Entrega | Commit sugerido |
|---|---|---|
| 1 | Seleção total dos selecionáveis (RN13, D47); totais `vigentes · sem efeito · indisponíveis` que somam o total | `feat(documentos)` |
| 2 | Ordem cronológica sempre do servidor (`DATA_DOC` completo, sem data ao final, empate por origem e id numérico); datas lidas sem passar pelo fuso da JVM nem do navegador; "sem data" e nota no sumário; aviso na gaveta | `fix(dossie)` |
| 3 | Download robusto: conexão 10 s, inatividade (`DOWNLOAD_TIMEOUT_SEGUNDOS`, 120), teto total (`DOWNLOAD_TOTAL_MAXIMO_SEGUNDOS`, 900), até 2 novas tentativas (tempo limite, 503, queda de conexão), tentativas no item, checagem de disco (`DOSSIE_ESPACO_MINIMO_MB`) | `feat(licenciamento)` |
| 4 | Token por arquivo (`LICENCIAMENTO_API_TOKEN_FILE`), relido a cada pedido e tentativa, com precedência; montagem de `./.secrets/` documentada; variável esvaziada na verificação | `feat(licenciamento)` |
| 5 | Falhas coletadas, `PENDENTE_DECISAO` e `POST /dossies/{id}/continuacao` (`TENTAR_NOVAMENTE`, `REMOVER_E_CONTINUAR`) sem baixar de novo o que já foi obtido; TTL desde a última atividade; RN09 reescrita (D48) | `feat(dossie)` |
| 6 | Seção "Documentos não incluídos" no PDF (motivo de cada um) e capa "N documentos incluídos de M listados" | `feat(dossie)` |
| 7 | Gaveta para o pedido pendente: lista de falhas, "Tentar novamente / Remover os N com problema", Cancelar, progresso preservado, mensagem do token por arquivo | `feat(front)` |
| 8 | Montagem medida com 20 PDFs de ~48 MB: 4,7 s, pico de heap de ~130 MB (13% de 1 GB; 54% de 256 MB), sem lotes (R26) | `perf(dossie)` |
| 9 | Dossiê grande (acima de `DOSSIE_BLOB_MAX_MB`, 150): download direto por ticket de uso único (D49), sem pré-visualização; nginx sem log nem buffer nessa rota | `feat(dossie)` |
| 10 | Roteiro de homologação da S2.2 (passos 7 e 10), casos de teste, P21 (diferença esperada pelo SCORP), coerência dos docs e verificação final | `docs(homologacao)` |

**Estado:** implementada e testada **sem sistema externo real** (portas falsas, servidor HTTP local, Oracle 23 Free por Testcontainers, Playwright com API simulada). Verificação final: `./mvnw -B clean verify`, front (build, Vitest, e2e) e Compose com os três arquivos, com as variáveis externas vazias dentro do container. **A homologação com o SIGA, o token e os arquivos reais é do responsável** (passo 10 de `homologacao/S2-roteiro-homologacao.md`; resultados em `casos-de-teste.md`).

### Validação da S2.2

**PENDENTE: homologação manual pelo responsável.**

| Verificação (roteiro, passo 10) | Resultado |
|---|---|
| 10.1 Tudo selecionado; totais somam | |
| 10.2 Só o documento com falha é refeito; progresso não volta a zero | |
| 10.3 "Remover e gerar": seção e capa | |
| 10.4 Ordem do PDF contra a tabela | |
| 10.5 Token trocado pelo arquivo, sem recriar o container | |
| 10.6 Dossiê grande: tempo, tamanho e disco | |

## S3 — Conversão e visualização

**Objetivo:** provar que planilhas e outros formatos entram no PDF legíveis, **sem cortar colunas nem linhas**, e identificados como convertidos.

**Forma de trabalho:** 10 blocos, cada um terminando em um ponto de commit, executados por duas sessões do Claude Code (OPUS e SONNET) que recebem o mesmo prompt; o estado de cada bloco está em [`progresso/s3.md`](progresso/s3.md).

| Bloco | Entrega | Sessão | Commit sugerido |
|---|---|---|---|
| 1 | Tipo real do arquivo pelo conteúdo (`DetectorDeTipoReal`); seleção dos formatos convertíveis (RN13); chave `CONVERSAO_HABILITADA`; porta `ParametrosDaConversao` | SONNET | `feat(documentos)` |
| 2 | Conversor LibreOffice isolado (`integration.libreoffice`, JODConverter): pool, perfil descartável, timeout, macro e rede bloqueadas, textos e HTML sem recurso externo (D56) | OPUS | `feat(conversao)` |
| 3 | Planilhas: largura, orientação, abas e linhas ocultas, modos `LARGURA` e `PAGINA_POR_ABA`, escala mínima, página única só quando cabe, conferência da última linha (D58) | OPUS | `feat(conversao)` |
| 4 | Imagens PNG e JPG por PDFBox: uma página, EXIF, transparência, limite de megapixels (D57) | SONNET | `feat(conversao)` |
| 5 | Conversão no pipeline do dossiê (`PreparadorDePdf`): falha de conversão é falha do documento, retomada sem reconverter, cancelamento, "convertido de" no sumário, marcador e capa (D54) | OPUS | `feat(dossie)` |
| 6 | Endpoint de conteúdo `GET /documentos/{origem}/{id}/conteudo` com acesso a cada requisição, cache temporário, fila e `errorCode`s (D55) | OPUS | `feat(documentos)` |
| 7 | Front: ação Visualizar e modal (PDF por Blob, faixa de convertido, erro, seleção, documento grande, sigiloso) | SONNET | `feat(front)` |
| 8 | Testes com arquivos hostis (macro, vínculo, ZIP "bomba", 0 byte, milhares de abas…) e LibreOffice no CI | OPUS | `test(conversao)` |
| 9 | Medição de desempenho (tempo, heap, memória do `soffice`, disco) que achou a perda de linhas em planilha grande (R41), corrigida no bloco 3 | SONNET (e ajuste OPUS) | `perf(conversao)` |
| 10 | Roteiro de homologação da S3, decisões D56 a D58, casos de teste e verificação final | SONNET | `docs(homologacao)` |

Entregas (resumo do plano original):
- Conversão com LibreOffice (JODConverter): XLS/XLSX/ODS com ajuste à largura e orientação automática, abas ocultas fora; DOC/DOCX/ODT/RTF/HTML; imagens como página (por PDFBox).
- Validação de PDF (senha, corrompido) tratada como falha (RN09).
- Endpoint de conteúdo para o modal (`formato=pdf` converte quando necessário).
- Front: modal de visualização do documento (mostra a versão convertida). A pré-visualização do dossiê já existia (S2).
- Testes com arquivos de exemplo **sintéticos**: planilha larga, DOCX, imagem, PDF/Office com senha, arquivo corrompido, além dos hostis.

Aceite:
- Planilhas reais dos processos de teste aparecem no dossiê sem cortes que impeçam a leitura. Se o ajuste à largura ficar ilegível, testar o modo "uma página por aba" e **registrar a escolha** (roteiro da S3, passo 6; D58).
- Tempo de geração anotado para os processos de teste.

**Estado:** implementada e testada **sem sistema externo real** (arquivos sintéticos gerados por código, portas falsas, servidor HTTP local, LibreOffice 24.2 num container de teste e no CI, Playwright com API simulada). Verificação final: back-end `./mvnw -B test` (LibreOffice ativo no container de teste) verde, front com build, Vitest (253) e e2e (36) verdes, `ArchitectureTest`, Compose com os três arquivos e as variáveis externas vazias dentro do container, YAMLs válidos. A medição de desempenho está em [`10`](10-pendencias-e-riscos.md) ("Medição de desempenho da conversão"). **Pendente: R42 (memória do container) e a escolha do modo de planilha, que dependem da homologação.**

### Validação da S3

**PENDENTE: homologação manual pelo responsável**, com arquivos reais de base de desenvolvimento. Roteiro: [`homologacao/S3-roteiro-homologacao.md`](homologacao/S3-roteiro-homologacao.md). Arquivos reais **não** entram no repositório (pasta local `amostras-locais/`, no `.gitignore`).

| Verificação (roteiro) | Resultado |
|---|---|
| 1. Compose local sobe e o LibreOffice está disponível | |
| 2. Planilhas, textos e imagens vêm selecionados; contagem do aviso bate com o SIGA | |
| 3. Visualizar planilha larga, DOC e imagem: nada cortado; faixa "Versão convertida" | |
| 4. Dossiê completo: ordem, "convertido de", numeração, "Documentos não incluídos" | |
| 5. Falha de conversão: decisão sem reconverter o resto; conversão desligada | |
| 6. `LARGURA` × `PAGINA_POR_ABA` na pior planilha: **modo escolhido** e motivo | |
| 7. Tempo, tamanho do PDF, disco, memória do container (R42) | |

## S4 — Regras de acesso e complementos

**Objetivo:** aplicar as regras de perfil e fechar o fluxo do usuário.

**Forma de trabalho:** 10 blocos, cada um terminando em um ponto de commit, executados por duas sessões do Claude Code (OPUS e SONNET) que recebem o mesmo prompt; o estado de cada bloco está em [`progresso/s4.md`](progresso/s4.md).

| Bloco | Entrega | Sessão | Commit sugerido |
|---|---|---|---|
| 1 | Política de acesso única (`acesso.application.PoliticaDeAcesso`) usada por consulta, documento e dossiê; inventário de todos os endpoints na "Matriz de acesso por endpoint" de [`07`](07-api.md); três regras novas no ArchUnit; dois reforços (pedido de outro usuário e dono que deixa de ser o interessado) (D60) | OPUS | `refactor(acesso)` |
| 2 | Front: menu e guarda de rota pelas `permissoes` de `/auth/me` (RN08), sem cadeado de demonstração; mensagens de acesso revisadas; aviso do Operador sem mexer na altura do quadro do topo | SONNET | `feat(front)` |
| 3 | Geometrias em `geometrias`, fora da lista, da seleção e do dossiê; `GET /requerimentos/{id}/geometrias/arquivo` em ZIP tudo ou nada (`GEOMETRIAS_INCOMPLETAS`, `GEOMETRIAS_GRANDES_DEMAIS`), nomes seguros e únicos; sigilosas omitidas para o Operador (D61) | OPUS | `feat(documentos)` |
| 4 | Front: seção Geometrias com "Baixar geometrias" (Blob), lista de falhas com nova tentativa, 413 e aviso de sigilosas omitidas | SONNET | `feat(front)` |
| 5 | SCORP: consulta das pessoas do requerimento, `integration.scorp` (credencial como a da API de arquivos, paralelo limitado, falha tolerante), origem `SCORP` "Pessoal" com id estável sem a chave, `avisos` na lista, `SCORP_SIGILO_PADRAO` (D62) | OPUS | `feat(scorp)` |
| 6 | Front: origem "Pessoal" (coluna, filtro, modal, ordem) e faixa de aviso do SCORP com "Consultar novamente" | SONNET | `feat(front)` |
| — | Correção à parte: rolagem horizontal do detalhe no celular (`.table-wrap` com `position: relative`), com e2e | SONNET | `fix(front)` |
| 7 | `GET /dossies` por perfil (Operador só os seus, `geradoPor` só para Gestor e Administrador, `somenteMeus`), situação `BAIXADO`, histórico em memória por `DOSSIE_HISTORICO_HORAS` (D63) | OPUS | `feat(dossie)` |
| 8 | Front: tela "Dossiês gerados" (situação, expiração, ações, filtros, atualização a cada 30 s) e gaveta no modo "pedido existente" para Decidir | SONNET | `feat(front)` |
| 9 | `MatrizDeAcessoTest`: 153 cenários em todos os 15 endpoints de `/api`, endpoint sem linha na matriz quebra o teste, mascaramento no PDF e varredura de corpo e cabeçalhos; nenhuma lacuna encontrada (R48) | OPUS | `test(acesso)` |
| 10 | Roteiro de homologação da S4, coerência dos documentos e verificação final | SONNET | `docs(homologacao)` |

Entregas (resumo do plano original):
- Módulo `acesso` em memória: Operador (só como interessado, RN07), Gestor (todos e sigilosos), Administrador (irrestrito); regras validadas no back-end (RNF10). **A RN07 na consulta já foi entregue na S1 (D24) e, na lista de documentos e na geração, na S2 (D36);** aqui entram o restante da matriz (sigilo no modal) e a validação por `MockMvc` de toda ela.
- Sigilo no modal (a lista e a geração já aplicam, S2); mensagens `NAO_INTERESSADO` e `DOCUMENTO_SIGILOSO`.
- Geometrias (SHP/KML/KMZ) em seção própria com "Baixar geometrias" (RN10).
- `integration.scorp`: documentos pessoais na lista (falha do SCORP não impede a listagem).
- Front: menu por perfil (RN08), tela "Dossiês gerados" com status e expiração.

Aceite:
- Os três perfis se comportam conforme a matriz do [12](12-configuracoes.md), testados por `MockMvc`.
- Origem "Pessoal" listada para um processo de teste com documentos no SCORP (se a autenticação do SCORP com o token for confirmada; senão, registrar como pendente).

**Estado:** implementada e testada **sem sistema externo real** (SCORP e API de arquivos simulados por servidor HTTP local, geometrias geradas por código, Oracle 23 Free por Testcontainers, Playwright com API simulada). Verificação final: back-end `./mvnw -B clean verify` verde (727 testes, 56 pulados: os 6 de fumaça, desligados por padrão, e os 50 de LibreOffice, sem `soffice` no Windows; esses 50 rodaram verdes no container de teste com LibreOffice), `ArchitectureTest` e `MatrizDeAcessoTest` (153 cenários) verdes; front com build, Vitest (326) e e2e (79) verdes; Compose com os três arquivos e as 15 variáveis externas vazias dentro do container; 8 YAMLs válidos; `git diff --check` limpo; varredura do diff da S4 sem credencial, URL institucional ou dado real. **Pendente da homologação:** autenticação do SCORP e validade da chave do SCORP em `/api/arquivo` (P06), diferença com a Vista do SIGA (P21), sigilo padrão do SCORP (P30), shapefile com auxiliares (R45) e tempos do ZIP e da lista com o SCORP real (R46, R47).

### Validação da S4

**PENDENTE: homologação manual pelo responsável**, com dados reais de base de desenvolvimento. Roteiro: [`homologacao/S4-roteiro-homologacao.md`](homologacao/S4-roteiro-homologacao.md). ZIPs, PDFs e arquivos reais **não** entram no repositório (pasta local `amostras-locais/`, no `.gitignore`).

| Verificação (roteiro) | Resultado |
|---|---|
| 1. Menu por perfil (RN08), URL bloqueada, troca de usuário | |
| 2. Consulta de pessoas no banco real; `ScorpFumacaTest`; **autenticação do SCORP (P06)** | |
| 3. Origem "Pessoal" na lista; **diferença com a Vista do SIGA (P21)**; SCORP fora do ar e "Consultar novamente" | |
| 4. Dossiê com documentos pessoais: chave do SCORP em `/api/arquivo`, ordem, "Pessoal" no sumário, numeração | |
| 5. Geometrias: ZIP aberto num visualizador, shapefile (R45), tempo (R46), sigilosas omitidas para o Operador | |
| 6. Perfis contra a matriz do [`12`](12-configuracoes.md): sigiloso, requerimento alheio, dossiê de outro usuário | |
| 7. "Dossiês gerados": situações, expiração, ações, histórico em memória | |
| 8. **`SCORP_SIGILO_PADRAO` escolhido (P30)** e motivo | |
| 9. Logs sem segredo, tempo da lista com SCORP (R47), repositório limpo | |

## S5 — Gestão: auditoria e configurações

**Objetivo:** mostrar rastreabilidade e administração, ainda em memória, e deixar a geração do dossiê acompanhável em segundo plano.

**Forma de trabalho:** 12 blocos, cada um terminando em um ponto de commit, executados por duas sessões do Claude Code (OPUS e SONNET) que recebem o mesmo prompt; o estado de cada bloco está em [`progresso/s5.md`](progresso/s5.md).

| Bloco | Entrega | Sessão | Commit sugerido |
|---|---|---|---|
| 1 | Front: a geração continua em segundo plano ao fechar a gaveta. `AcompanhamentoDeDossieService` é dono do pedido e do polling; fundo, Esc e "Fechar" minimizam; indicador na barra do topo, aviso com "Abrir" e um pedido por vez (D71) | SONNET | `fix(front)` |
| 2 | Módulo `auditoria`: evento imutável com retrato limpo, porta `RegistrarEvento` que nunca lança, repositório em memória só de inclusão, teto e retenção por grupo (D65) | OPUS | `feat(auditoria)` |
| 3 | Eventos de documentos (consulta, visualização, dossiê, falha, download e geometrias) sem mudar nenhuma resposta; retrato guardado no pedido para a thread de geração (D66) | OPUS | `feat(auditoria)` |
| 4 | Consulta da auditoria por perfil (Operador só os seus processos, sigiloso mascarado antes de filtrar), detalhe e CSV com proteção contra injeção de fórmula (D67) | OPUS | `feat(auditoria)` |
| 5 | Front: auditoria de documentos (período, indicadores, filtros, destaque de sigilosos, gaveta de detalhe, CSV por Blob) | SONNET | `feat(front)` |
| 6 | Eventos `LOGIN` e `PERFIL` (por evento de aplicação, sem `acesso` depender da auditoria) e a tela de acessos no mesmo componente, só Administrador | SONNET | `feat(auditoria)` |
| 7 | Módulo `configuracao`: usuários e perfis em memória, com as proteções (último Administrador, próprio usuário) e um evento `USUARIO` por alteração (D68) | OPUS | `feat(configuracao)` |
| 8 | Parâmetros em tempo de execução (sobreposição em memória, classes de aplicação, retrato do pedido), conexões com teste e evento `CONFIG` único (D69) | OPUS | `feat(configuracao)` |
| 9 | Front: Configurações com abas e a aba Usuários e perfis | SONNET | `feat(front)` |
| 10 | Front: abas de parâmetros, conexões e retenção, com alterações pendentes e salvamento único; telas sob demanda (D72) | SONNET | `feat(front)` |
| 11 | `MatrizDeAcessoTest` e varredura de vazamento estendidas; acesso conferido na entrega, com o perfil vigente (D70, R53, R58) | OPUS | `test(acesso)` |
| 12 | Roteiro de homologação da S5, decisões D71 e D72, riscos R59 e R60, coerência dos documentos e verificação final | SONNET | `docs(homologacao)` |

Entregas (resumo do plano original):
- Módulo `auditoria`: porta `RegistrarEvento` chamada por consulta, documento, dossiê e login; eventos de documentos e de acessos; consulta por perfil (Operador só como interessado, com sigilosos mascarados); detalhe e exportação CSV.
- Módulo `configuracao`: usuários e perfis, parâmetros do dossiê, conversão, visualização, conexões (com teste) e retenção; salvamento com evento de alteração.
- Parâmetros passam a valer em tempo de execução (expiração, origens, timeouts, modo das planilhas), cada um com a sua classe de aplicação.
- Front: telas de auditoria (documentos e acessos) e de configurações, e a geração do dossiê acompanhável em segundo plano.

Aceite:
- Cada ação das sprints anteriores gera o evento correspondente.
- Alterar um parâmetro muda o comportamento sem reiniciar a aplicação.
- Clicar fora da gaveta durante a geração não interrompe nem perde o acompanhamento.

**Estado:** implementada e testada **sem sistema externo real**. Auditoria, usuários e parâmetros ficam **em memória** (D09): somem ao reiniciar o serviço (R50, R57), e a tela avisa. A homologação com dados e arquivos reais é do responsável: [`homologacao/S5-roteiro-homologacao.md`](homologacao/S5-roteiro-homologacao.md). A verificação final da S5 está em [`progresso/s5.md`](progresso/s5.md).

### Validação da S5

**PENDENTE: homologação manual pelo responsável**, com dados reais de base de desenvolvimento. Roteiro: [`homologacao/S5-roteiro-homologacao.md`](homologacao/S5-roteiro-homologacao.md). CSVs, PDFs e capturas de tela reais **não** entram no repositório (pasta local `amostras-locais/`, no `.gitignore`).

| Verificação (roteiro) | Resultado |
|---|---|
| 1. Geração em segundo plano: clicar fora, indicador, aviso, reabrir, fechar no instante do envio, falha e decisão com a gaveta fechada | |
| 2. Auditoria de documentos com cada perfil: eventos, destaque, detalhe, filtros e CSV no Excel (acentos, separador, célula `=`) | |
| 3. Divisão por perfil: Operador vê os dos seus processos (de qualquer usuário), sigiloso mascarado, busca que não revela; Acessos só do Administrador | |
| 4. Usuários e perfis: inclusão, mudança, último Administrador, próprio usuário, entrada de novo e desativado, eventos `USUARIO` | |
| 5. Parâmetros: evento `CONFIG` único, classes de aplicação, restaurar padrão, origens; **`LARGURA` × `PAGINA_POR_ABA` (D58)** | |
| 6. Conexões: banco do SIGA, API de arquivos (horas até expirar) e SCORP; nenhuma credencial na tela nem na rede | |
| 7. Reinício do serviço: auditoria, usuários e parâmetros somem (comportamento documentado) | |
| 8. Logs sem token ou chave, `.env.local` ignorado, repositório limpo | |

## S6 — Validação e apresentação

> **Decisão do responsável:** em qual sprint entra o material de apresentação (esta seção, original e sem reescrita, e a linha "S6 | Validação e apresentação" da visão geral) é decidido pelo responsável do projeto, não por esta documentação.

**Objetivo:** evidências para a decisão.

Entregas:
- Execução de todos os casos de `casos-de-teste.md`, com resultado, tempo e páginas de cada dossiê.
- Comparativo: Vista atual do SIGA × SIGRASTRO para os mesmos processos (gera/não gera, planilhas incluídas, documentos listados).
- Ajustes finais de usabilidade e correções.
- Material de apresentação: problema, solução, demonstração, evidências e pedidos (Pandora ou credencial de serviço, ambiente no SIGA, schema próprio Oracle 19c).
- Atualização de `10-pendencias-e-riscos.md` com o que fica para a versão definitiva.

Aceite: os critérios de validação do [01](01-visao-geral.md) atendidos e documentados.

---

## Definição de pronto (vale para todas as sprints)

- Código segue [13 — Padrões técnicos](13-padroes-tecnicos.md); `ArchitectureTest` verde.
- `./mvnw clean verify`, build e testes do Angular passando (reportar quantos testes de integração com Testcontainers rodaram e quantos foram pulados); `docker compose ... config` válido; `git diff --check` limpo.
- Testes da sprint escritos (unitários de domínio, `MockMvc` dos endpoints novos, integração dos adapters tocados).
- Nenhuma credencial, token ou URL institucional no repositório.
- Documentação em `docs/` atualizada **durante** a sprint quando ela mudar uma regra, endpoint ou decisão.
- Quando houver migration do schema próprio: `docs/database/` (dicionário de dados em HTML e MER) regenerado na mesma entrega.
- Demonstração da sprint feita com dados reais (a partir da S1).

## Riscos para o cronograma

| Risco | Sprint afetada | Mitigação |
|---|---|---|
| Pré-requisitos 1 e 2 atrasarem | S1 em diante | S0 não depende deles; solicitar já |
| Token expira com frequência nos testes | S2–S6 | Erro claro e recaptura; registrar como argumento para a credencial de serviço |
| Planilhas largas ilegíveis | S3 | Alternativa "uma página por aba" já prevista |
| Autenticação do SCORP diferente do token | S4 | Tratar como pendência sem bloquear a sprint |
| Processos com muitos documentos lentos | S2–S3 | Medir na S3 e ajustar `DOSSIE_THREADS` e timeouts |
