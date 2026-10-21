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
| S3 | Conversão e visualização | Planilhas, textos e imagens convertidos dentro do dossiê; modal e pré-visualização |
| S4 | Regras de acesso | Perfis, sigilo, interessado, geometrias e tela "Dossiês gerados" |
| S5 | Gestão | Auditoria (documentos e acessos) e configurações, em memória |
| S6 | Validação e apresentação | Casos de teste executados, evidências e material da apresentação |

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

**Status:** implementada e testada com portas falsas, `MockMvc` e Oracle 23 Free local (Testcontainers): `./mvnw clean verify` com 181 testes, 0 falhas, só os 2 de fumaça pulados; Compose verificado sem as variáveis do SIGA (health `UP`, 503 `SIGA_INDISPONIVEL`, os três perfis entram). **A validação com dados reais está pendente de homologação manual** (ver "Validação da S1.1"). O prompt da S1.1 foi recebido pela conversa e não está em `docs/prompts/`.

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

**PENDENTE: homologação manual pelo responsável.** Roteiro, script de grants, consultas de conferência e tabelas de comparação: [`homologacao/S1.1-roteiro-homologacao.md`](homologacao/S1.1-roteiro-homologacao.md). Registrar o resultado aqui e abrir pendência em [10](10-pendencias-e-riscos.md) para o que não bater.

| Processo | Campo | SIGA | SIGA Vistas | Bate? |
|---|---|---|---|---|
| _a preencher_ | | | | |

## S2 — Documentos e dossiê em PDF

**Objetivo:** provar a geração do dossiê dos processos que hoje falham (somente PDFs nesta sprint).

Entregas:
- `integration.siga`: documentos de todas as origens mapeadas, com vigência (RN01), sigilo (RN06), deduplicação por chave (RN05), data cronológica (RN03); compactados desconsiderados (RN11).
- `integration.licenciamento`: download por streaming para disco temporário, com timeout e tradução de erros (401 → 502; 404/503/timeout → falha do documento).
- Módulo `documento`: lista com pré-seleção dos vigentes; chave nunca exposta (RNF09).
- Módulo `dossie`: pedido assíncrono, fila em memória, montagem com PDFBox (capa, sumário, marcadores, numeração, tamanho e rotação preservados), regra "completo ou nenhum" (RN09) com "tentar novamente" e "remover e gerar", expiração e tarefa agendada de limpeza (D22).
- Front: lista de documentos com busca, filtro de origem, ordenação e seleção; barra de seleção; gaveta de geração; download.

Aceite:
- Dossiê gerado com sucesso para **todos** os processos da lista de testes que hoje falham na Vista do SIGA (considerando só os PDFs).
- Falha simulada (timeout) interrompe a geração sem entregar arquivo; nova tentativa funciona.
- Nenhuma conexão do SIGA aberta durante download e montagem.

## S3 — Conversão e visualização

**Objetivo:** provar que planilhas e outros formatos entram no PDF legíveis.

Entregas:
- Conversão com LibreOffice (JODConverter): XLS/XLSX/ODS com ajuste à largura e orientação automática, abas ocultas fora; DOC/DOCX/ODT/RTF/HTML; imagens como página.
- Validação de PDF (senha, corrompido) tratada como falha (RN09).
- Endpoint de conteúdo para o modal (`formato=pdf` converte quando necessário).
- Front: modal de visualização do documento (mostra a versão convertida) e pré-visualização do dossiê sem gerar de novo.
- Testes com arquivos de exemplo (XLSX largo, DOCX, imagem, PDF com senha, PDF corrompido).

Aceite:
- Planilhas reais dos processos de teste aparecem no dossiê sem cortes que impeçam a leitura. Se o ajuste à largura ficar ilegível, testar o modo "uma página por aba" e registrar a escolha.
- Tempo de geração anotado para os processos de teste.

## S4 — Regras de acesso e complementos

**Objetivo:** aplicar as regras de perfil e fechar o fluxo do usuário.

Entregas:
- Módulo `acesso` em memória: Operador (só como interessado, RN07), Gestor (todos e sigilosos), Administrador (irrestrito); regras validadas no back-end (RNF10). **A RN07 na consulta já foi entregue na S1 (D24);** aqui entram o restante da matriz (sigilo na lista, no modal e na geração) e a validação por `MockMvc` de toda ela.
- Sigilo na lista, no modal e na geração; mensagens `NAO_INTERESSADO` e `DOCUMENTO_SIGILOSO`.
- Geometrias (SHP/KML/KMZ) em seção própria com "Baixar geometrias" (RN10).
- `integration.scorp`: documentos pessoais na lista (falha do SCORP não impede a listagem).
- Front: menu por perfil (RN08), tela "Dossiês gerados" com status e expiração.

Aceite:
- Os três perfis se comportam conforme a matriz do [12](12-configuracoes.md), testados por `MockMvc`.
- Origem "Pessoal" listada para um processo de teste com documentos no SCORP (se a autenticação do SCORP com o token for confirmada; senão, registrar como pendente).

## S5 — Gestão: auditoria e configurações

**Objetivo:** mostrar rastreabilidade e administração, ainda em memória.

Entregas:
- Módulo `auditoria`: porta `RegistrarEvento` chamada por consulta, documento e dossiê; eventos de documentos e de acessos; consulta por perfil (Operador só como interessado, com sigilosos mascarados); detalhe e exportação CSV.
- Módulo `configuracao`: usuários e perfis, parâmetros do dossiê, conversão, conexões (com teste) e retenção; salvamento com evento de alteração.
- Parâmetros passam a valer em tempo de execução (expiração, origens, timeouts, modo das planilhas).
- Front: telas de auditoria (documentos e acessos) e de configurações.

Aceite:
- Cada ação das sprints anteriores gera o evento correspondente.
- Alterar um parâmetro muda o comportamento sem reiniciar a aplicação.

## S6 — Validação e apresentação

**Objetivo:** evidências para a decisão.

Entregas:
- Execução de todos os casos de `casos-de-teste.md`, com resultado, tempo e páginas de cada dossiê.
- Comparativo: Vista atual do SIGA × SIGA Vistas para os mesmos processos (gera/não gera, planilhas incluídas, documentos listados).
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
