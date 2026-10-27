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
| Licenciamento (3040/2022, LAC) | Atividade confere; **diverge no Objetivo:** o SIGA mostra "LAC" e o SIGA Vistas mostra "—" (ver P20); o grupo da atividade aparecia junto da atividade (corrigido na S2, A2) |
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
- Módulo `acesso` em memória: Operador (só como interessado, RN07), Gestor (todos e sigilosos), Administrador (irrestrito); regras validadas no back-end (RNF10). **A RN07 na consulta já foi entregue na S1 (D24) e, na lista de documentos e na geração, na S2 (D36);** aqui entram o restante da matriz (sigilo no modal) e a validação por `MockMvc` de toda ela.
- Sigilo no modal (a lista e a geração já aplicam, S2); mensagens `NAO_INTERESSADO` e `DOCUMENTO_SIGILOSO`.
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
