# S7: tramitação (1 de 2): onde o processo está e com quem

> **Forma de trabalho:** a sprint é dividida em **11 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

O projeto passa a se chamar **Rastro**: um complemento de **consulta** ao SIGA Licenciamento (o SIGA segue na análise e na decisão; o Rastro só **lê e mostra**). A Vista do Processo (dossiê em PDF) foi a primeira entrega, e vira um módulo. A S7 abre o módulo de **tramitação**: dado um requerimento (ou processo), mostrar **em que etapa ele está agora, desde quando e com quem**, em linguagem que não exige conhecer o Camunda.

Escopo da S7 (e só ele): **posição atual**. O histórico completo, "com quem esteve em cada etapa" e o fluxograma simplificado são da **S8**; taxas, DAR e pagamentos são da **S9**; o painel do Gestor e a mudança de pago para isento vêm depois. Não antecipe nada disso.

O serviço é usado em **processos jurídicos**: dado cruzado errado é pior do que dado ausente. Onde a fonte é ambígua, o Rastro **diz que não sabe** (selo de aviso), em vez de adivinhar.

Leia antes de começar: `docs/01`, `docs/02` (RF e RN, incluindo RN07 e RN08), `docs/03`, `docs/04` (seção Camunda), `docs/05`, `docs/07` (matriz de acesso), `docs/08`, `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14` e `docs/progresso/s5.md` (e `s6.md`, se existir). Se houver `docs/referencias/` (BPMN, relatórios dos levantamentos), leia também.

## Decisões já tomadas (registre em `docs/09`)

- **Fonte da posição = Camunda.** A situação do Licenciamento só confirma e alimenta o plano B (S8). Divergências conhecidas viram **selos de aviso** (bloco 5), nunca correção silenciosa.
- **Catálogo de etapas em arquivo versionado, não no código.** O vocabulário é o do usuário ("Análise do processo", não `taskAnalisarRequerimento`). Etapas simplificadas e ids técnicos ficam em YAML, validado na subida; id técnico desconhecido **não derruba a consulta**: mostra "Etapa não mapeada" com o id técnico apenas para o Administrador.
- **Etapa atual pode ser mais de uma** (emissões em paralelo, vários analistas, complementação paralela). Regra: mostrar a etapa **principal** (a de menor ordem entre as ativas do fluxo principal) e, ao lado, o selo **"+N em paralelo"** com a lista. A complementação é sempre listada à parte ("Complementação em andamento").
- **Responsável:** tarefa com `ASSIGNEE_` → nome do servidor (`SERVIDOR`, por CPF); tarefa de grupo → **área** pelo arquivo de grupos (bloco 4); tarefa do interessado → "Interessado (cadastrante)"; sistema → "Sistema". **CPF nunca sai.** O nome do servidor aparece conforme o parâmetro `RASTRO_MOSTRAR_NOME_RESPONSAVEL` (padrão ligado **no protótipo**; a regra definitiva depende de aprovação da SEMA: registre em `docs/10`).
- **"Verificação da revisão" = concluído/arquivado** (regra 9 acima): etapa exibida com o selo "Processo concluído".
- **Leitura direta por `PROC_INST_ID_`**, sem tabela de apoio (o detalhe de um processo é barato). **Cache curto em memória** (TTL `RASTRO_POSICAO_CACHE_SEGUNDOS`, padrão 30; 0 desliga) para proteger o Camunda. Tabela de apoio fica para o painel do Gestor.
- **Camunda indisponível ou lento não derruba o Rastro:** a posição responde `INDISPONIVEL` (com a situação do Licenciamento, se houver) e o resto do detalhe continua funcionando.
- **Em memória** onde houver estado (cache, auditoria); nada é gravado no Oracle.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou número de DAR real em teste, doc, exemplo, log ou repositório). CPF fictício é sempre **inválido de propósito** (ex.: `000.000.000-01`) e nomes são evidentes (`Servidor Exemplo 01`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Prefira o que o projeto já tem; para diagramas, **sem biblioteca**: HTML, CSS e SVG simples.
- **Somente leitura no banco.** O usuário do banco só tem `SELECT`. Todo SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados** (nunca concatenar valor), `FETCH FIRST n ROWS ONLY`, tempo limite de consulta e concentrado **num único componente de acesso** (risco R04 de `docs/10`). Nada é gravado no Oracle.
- **Dados pessoais:** `ASSIGNEE_`, vínculos de identidade e variáveis do Camunda guardam **CPF e nome**. O CPF **nunca** sai em resposta, log, erro, evento de auditoria, CSV ou tela, **nem parcialmente mascarado**. Variáveis do Camunda só podem ser lidas de uma **lista fixa** (lista branca) sem dado pessoal. A chave do arquivo, tokens e cookies também nunca saem (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Datas e horas sempre no fuso de **Cuiabá (`America/Cuiaba`)**, pela utilidade única já existente (S5.1). Data pura (sem hora) nunca muda de dia na conversão.

## Regras técnicas do Camunda e do Licenciamento (levantamentos 1 e 2, confirmadas em homologação e produção)

Estas regras valem para todo SQL desta sprint. Quem as quebra gera dado errado em processo jurídico.

1. **Ligação processo ↔ Camunda:** não há coluna com o id da instância no SIGA. A ligação é `BUSINESS_KEY_ = TO_CHAR(REQUERIMENTO.ID)` na **execução raiz** (`ACT_RU_EXECUTION` com `PARENT_ID_ IS NULL` e `ID_ = PROC_INST_ID_`; no histórico, `ACT_HI_PROCINST`). **Nunca** `TO_NUMBER(BUSINESS_KEY_)` (há valores não numéricos: `ORA-01722`).
2. **Sempre filtrar pela chave da definição** (`ACT_RE_PROCDEF.KEY_` ou `ACT_HI_PROCINST.PROC_DEF_KEY_`): `procOutorgaAguaSupSub` (Hídrico) e `procLicenciamentoLac` (LAC). Os ids de requerimento, de complementação e de título **colidem** como `BUSINESS_KEY_`. **Nunca** filtrar por `PROC_DEF_ID_` (há até 63 versões do Hídrico).
3. **Escopo:** Hídrico e LAC. Trifásico/LAS não estão em produção: o requerimento desses tipos recebe a resposta "fluxo ainda não suportado pelo Rastro", sem erro.
4. **Esperas de pagamento não são tarefas.** São *gateways* baseados em evento: não aparecem em `ACT_RU_TASK`. A posição "aguardando pagamento" se acha em `ACT_RU_EXECUTION.ACT_ID_` (e `ACT_RU_EVENT_SUBSCR`, **sem o "RIPTION"**) quando ativa, e no gateway com `END_TIME_` em `ACT_HI_ACTINST` quando já passou.
5. **Emissões são instâncias filhas** (`callActivity`: `procSurhEmitirAutorizacoes`, `procSurhEmitirOutrosAtos`, `procSurhCertificadoCadastro`, `procSurhEmissaoPortaria`), com `BUSINESS_KEY_` = id do título. O vínculo com o requerimento é **pela instância-mãe** (`SUPER_PROCESS_INSTANCE_ID_` / `SUPER_EXEC_`), nunca pela business key. Podem ocorrer várias em paralelo.
6. **Multi-instância** (`taskAceitarAtribuicao`, `taskAnalisarRequerimento`, `taskValidarParecerTecnico`, e as validações de signatários nas filhas): uma tarefa por analista/signatário. `ACT_RU_TASK.EXECUTION_ID_` **não é confiável**: use `PROC_INST_ID_` + `TASK_DEF_KEY_`. Na análise, um analista pedir correção encerra as demais tarefas do ciclo (elas ficam com `DELETE_REASON_` diferente de `completed`).
7. **Complementação** é uma instância **independente** e **paralela** (`procGestaoComplementacao`, `BUSINESS_KEY_ = COMPLEMENTACAO.ID`, não altera `SITUACAO_PROCESSO`). Pode existir em qualquer fase da análise.
8. **Situação do Licenciamento não é a etapa.** `REQUERIMENTO.SITUACAO_PROCESSO` tem 29 valores grosseiros e diverge do fluxo em casos conhecidos: `CONCLUIDO` é gravado **antes** de "Verificação da revisão"; "Pagamento Vencido 60 dias" da taxa inicial do Hídrico encerra a instância **sem atualizar** a situação (bug de origem: fica `AGUARDANDO_PAGAMENTO_DE_TAXAS`); no LAC "expirado na correção" usa a **mesma situação** do expirado no cadastro; `AGUARDANDO_EMISSAO_DE_TAXAS` cobre dois pontos do fluxo; nada grava `ARQUIVADO_TEMPORARIAMENTE`. A situação é usada para **confirmar** e para o plano B, nunca para derivar a etapa.
9. **"Verificação da revisão do processo" (`taskVerificarRevisaoProcesso`) funciona como arquivamento:** o processo é considerado **concluído quando "Registrar comprovante de publicação" encerra**, a tarefa nunca é recriada na prática e a instância **fica ativa nela** (grupos `grpSurhRevisarDecisao*`). Não é "revisão em andamento com alguém".
10. **Histórico:** etapa **percorrida** = atividade com `END_TIME_` e, se for tarefa, `DELETE_REASON_ = 'completed'`. Ruído a ignorar: tarefa **"Suporte"** (`Activity_0miwhxt`, grupo `suporte`), "Report Kafka", eventos de repetição por erro de API (colapsar repetições consecutivas de scripts) e `taskAnalisarReducaoTaxa` (ramo de redução provavelmente inativo). `historyLevel = 3`; no Hídrico o histórico só existe desde 02/2022 e, até 2022, há requerimentos **sem instância** (2021/2022: lacunas). **De 2023 em diante todo requerimento tem instância no Camunda.**
11. **`statusConferencia`** (isento/taxavel/reprovado) é **sobrescrito** em 3 pontos: o valor final não é o da primeira passagem. Para classificar uma passagem, use a **rota seguida** (passou ou não pelo gateway de pagamento) ou o valor da época em `ACT_HI_DETAIL` (`TYPE_ = 'VariableUpdate'`). No Hídrico o gateway não tem ramo padrão.
12. **Oracle:** `ORA-12704` (misturar `NVARCHAR2` e `VARCHAR2`: aplique `TO_CHAR` nos dois lados), `ORA-30482` (não existe `LISTAGG(DISTINCT)`), `ORA-32039` (CTE com nome de tabela real). Sem índice por `TASK_DEF_KEY_`/`ASSIGNEE_` em `ACT_HI_TASKINST`; `ACT_HI_ACTINST` tem ~1,5 milhão de linhas: consulte **sempre por `PROC_INST_ID_`** (barato). `LANCAMENTO_TAXA` e `HISTORICO_REQUERIMENTO` provavelmente sem índice em `ID_REQUERIMENTO`: sem varredura de tabela inteira.
13. **Grupos reais observados** (nomes de grupos não são dado pessoal): `GrpProcSurhResponsavelAtendimento`, `grpSurhConferirDocumento{Subterranea|Superficial|Barramento}`, `grpProcSurhConferirDocumentoMultidisciplinar`, `grpProcSurhResponsavelDistribuicao`, `grpSurhAtribuirRequerimento{Subterranea|Superficial|Barramento}`, `grpProcSurhAtribuirRequerimentoMultidisciplinar`, `GrpProcPublicacaoOficial`, `grpSurhRegistrarPublicacao{Subterranea|Superficial|Barramento}`, `grpProcSurhRegistrarPublicacaoMultidisciplinar`, `grpSurhRevisarDecisao{Subterranea|Superficial|Barramento|Multidisciplinar}`, `GrpProcSurhEmissorTitulos`, `GrpProcResponsavelAtendimentoLac`, `suporte`. Valor `0` ou desconhecido = "equipe responsável", sem erro.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s7.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s7.md` (crie no bloco 1, com a tabela dos 11 blocos); a marcação entra no mesmo commit;
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
| 1 | Catálogo de etapas simplificadas (Hídrico e LAC) e validador | **OPUS** | Regra de negócio central; erro aqui contamina todo o Rastro |
| 2 | Ambiente de verificação: Camunda e Licenciamento fictícios | **SONNET** | Dados sintéticos com cenário fechado |
| 3 | Leitura da posição atual no Camunda | **OPUS** | SQL com várias armadilhas, paralelismo e filhas |
| 4 | Responsável: servidor, área, interessado e multi-instância | **OPUS** | Dado pessoal e regras de exibição |
| 5 | Divergências conhecidas e selos de aviso | **OPUS** | Reconciliação situação × fluxo; honestidade do dado |
| 6 | API da posição e auditoria | **SONNET** | Contrato pronto, padrão existente |
| 7 | Matriz de acesso e varredura de vazamento do módulo | **OPUS** | Prova das regras de segurança |
| 8 | Front: bloco "Onde está agora" | **SONNET** | Interface a partir de contrato pronto |
| 9 | Front: processo com vários requerimentos e estados de erro | **SONNET** | Interface |
| 10 | Configurações: parâmetros do Rastro | **SONNET** | Reuso da tela de Configurações |
| 11 | Roteiro de homologação e fechamento | **SONNET** | Documentação |

---

## Bloco 1 (OPUS): catálogo de etapas simplificadas

**Commit sugerido:** `feat(tramitacao): catálogo de etapas simplificadas do Hídrico e do LAC com validador`

- Novo módulo `tramitacao` (domain, application, api, infrastructure) no pacote raiz atual (hoje `br.gov.mt.sema.vistas`; se a renomeação R1 já ocorreu, o novo). Dependências conforme o ArchUnit de `docs/13`: `tramitacao` não depende de `documento` nem de `dossie`; reuso do que `consulta` já oferece **só por porta**.
- **Domínio:** `Etapa` (id estável em minúsculas com sublinhado, rótulo, `ordem`, `tipo`: `TAREFA_HUMANA`, `ESPERA`, `AUTOMATICA`, `EMISSAO`, `FINAL`; `responsavel`: `INTERESSADO`, `ANALISTA`, `CHEFIA`, `GRUPO`, `SISTEMA`; `ramo`: `PRINCIPAL` ou `CORRECAO`; lista de ids técnicos `ACT_ID_`; marca `ignorada`), `ModeloDeFluxo` (chave do Camunda, rótulo, etapas, finais, ids ignorados) e `CatalogoDeEtapas` (porta).
- **Arquivos de dados** (YAML em `src/main/resources/rastro/etapas/`, um por modelo) lidos na subida e **validados**: id técnico em **no máximo uma** etapa; todo `ordem` único por ramo; todo final tem rótulo; modelo sem etapa = erro de inicialização claro. Id técnico fora do catálogo **não** é erro de execução (ver decisões).
- **Conteúdo inicial** (fonte: levantamento 2). Os ids vêm dos BPMN; se `docs/referencias/bpmn/` existir, **valide os ids contra os arquivos** e relate diferenças no resumo do bloco.

**Hídrico (`procOutorgaAguaSupSub`)**

| ordem | etapa | ids técnicos | tipo | responsável |
|---|---|---|---|---|
| 1 | Cadastro do requerimento | `taskCadastrarRequerimento` | tarefa humana | interessado |
| 2 | Conferência dos documentos | `taskConferirDocumentosApresentados` | tarefa humana | grupo |
| 3 | Emissão da taxa | `taskAtualizarStatusRequerimentoArrecadacao`, `taskAtualizarStatusRequerimentoEmissaoTaxa1`, `taskEmitirTaxaSefaz` | automática | sistema |
| 4 | Aguardando pagamento | `gtwAguardarPagamento` | espera | interessado |
| 5 | Isenção confirmada | `taskGerarNumeroProtocolo` (só quando **não** houve passagem por `gtwAguardarPagamento`) | automática | sistema |
| 6 | Atribuição ao analista | `taskAtribuirRequerimento`, `taskMontarListaAnalistas` | tarefa humana | grupo |
| 7 | Aceite pelo analista | `taskAceitarAtribuicao` | tarefa humana (multi-instância) | analista |
| 8 | Análise do processo | `taskAnalisarRequerimento` | tarefa humana (multi-instância) | analista |
| 9 | Revisão do parecer técnico | `taskRevisarParecerTecnico` | tarefa humana | chefia |
| 10 | Validação do parecer técnico | `taskValidarParecerTecnico`, `taskAtualizarStatusParecerAssinado` | tarefa humana (multi-instância) | analista |
| 11 | Emissão do ato | `taskEmitirAutorizacoes`, `taskEmitirOutrosAtos`, `taskEmitirCertificadoCadastros`, `taskEmitirPortarias` (e as filhas `procSurhEmitirAutorizacoes`, `procSurhEmitirOutrosAtos`, `procSurhCertificadoCadastro`, `procSurhEmissaoPortaria`) | emissão | grupo / signatários |
| 12 | Registro do comprovante de publicação | `taskRegistarComprovantePublicacao`, `taskUpdateStatusConcluido`, `taskInformarTitulacoesDisponiveis` | tarefa humana | grupo |
| 13 | Verificação da revisão do processo | `taskVerificarRevisaoProcesso` | tarefa humana | grupo |
| F | Concluído | fim `Event_1wiuc48` | final | |
| C1 | Correção do requerimento (ramo correção) | `taskAtualizarStatusAguardandoRevisao`, `taskCorrecaoRequerimento` | tarefa humana | interessado |
| C2 | Emissão da taxa (correção) | `taskEmitirTaxaSefazCorrecao` | automática | sistema |
| C3 | Aguardando pagamento (correção) | `gtwAguardarPagamentoRevisao` | espera | interessado |
| — | Ignorados | `Gateway_aguardando_emtx` ("Aguardando emissão de taxa"), `Activity_0miwhxt` ("Suporte"), `taskAnalisarReducaoTaxa`, "Report Kafka" e eventos de repetição por erro | | |

Finais do Hídrico: `Event_1wiuc48` Concluído; `Event_1kq2k0z` Cancelado pelo cadastrante; `Event_0abm55w` Expirado no cadastro; `Event_0h1oige` **Encerrado por taxa não paga no prazo (sem atualizar a situação)**.

**LAC (`procLicenciamentoLac`)**

| ordem | etapa | ids técnicos | tipo | responsável |
|---|---|---|---|---|
| 1 | Cadastro do requerimento | `taskCadastrarRequerimento` | tarefa humana | interessado |
| 2 | Conferência dos documentos | `taskConferirDocumentosApresentados` | tarefa humana | grupo (e conferidor) |
| 3 | Emissão da taxa | `taskAtualizarStatusEncParaArrecadacao`, `taskAtualizarStatusAguardEmissaoTaxa`, `taskEmitirTaxaSefaz` | automática | sistema |
| 4 | Aguardando pagamento | `gwtAguardarPagtoOuVencto` | espera | interessado |
| 5 | Isenção confirmada | `taskGerarNumeroProtocolo` (sem passagem pelo gateway de pagamento) | automática | sistema |
| 6 | Termo de adesão gerado | `taskGerarTermoAdesaoLAC` | automática | sistema |
| 7 | Envio do termo assinado | `taskEnviarTermoAdesao` | tarefa humana | interessado |
| 8 | Conclusão | `taskUpdateStatusConcluido`, `taskEnviarEmailTitulacoes` | automática | sistema |
| F | Concluído | fim `evtProcessoConcluido` | final | |
| C1 | Correção do requerimento | `taskCorrecaoRequerimento` | tarefa humana | interessado |

Finais do LAC: `evtProcessoConcluido` Concluído; `evtFimRequerimentoAPedidoCadastrante` Cancelado pelo cadastrante; `evtEncerrarRequerimentoPrazoCadExpirado` Expirado no cadastro; `evtEncerrarProcessoPrazoCorrecaoExpirado` Expirado na correção; `evtEncerrarProcessoFaltaPagto` Cancelado por falta de pagamento; `Event_0x7hz1p` Arquivado (termo não enviado no prazo). No LAC, reprovar na conferência leva à correção e **volta direto à conferência, sem nova taxa**.

- Testes: validador (id duplicado, ordem repetida, final sem rótulo, modelo vazio); cada id técnico da tabela acima resolve para a etapa esperada; id desconhecido resolve para "não mapeada"; os ids ignorados nunca viram etapa; Trifásico/LAS (chave desconhecida) resolve para "não suportado".
- Docs: crie `docs/15-tramitacao.md` (modelo de etapas, regras, decisões desta sprint; as **regras técnicas** acima, completas), `docs/03` (módulo), `docs/09`, `docs/10` (riscos: BPMN muda de versão e ids mudam; manter alias e conferir a cada publicação), `docs/13` (`padroes/modulos-e-dependencias.md`), `docs/progresso/s7.md` (crie com a tabela dos 11 blocos).

## Bloco 2 (SONNET): ambiente de verificação com Camunda e Licenciamento fictícios

**Commit sugerido:** `test(tramitacao): fixtures fictícias de Camunda e Licenciamento para verificação e testes`

Descubra em `compose.verificacao.yaml`, `docs/13` e nos testes existentes (Testcontainers Oracle) **como o ambiente de verificação e os testes recebem dados** e **estenda o mesmo mecanismo**; não crie um segundo.

- **DDL mínimo** (somente as colunas que o Rastro lê; nomes padrão do Camunda 7): `ACT_RE_PROCDEF`, `ACT_RU_EXECUTION`, `ACT_RU_TASK`, `ACT_RU_IDENTITYLINK`, `ACT_RU_EVENT_SUBSCR`, `ACT_HI_PROCINST`, `ACT_HI_ACTINST`, `ACT_HI_TASKINST`, `ACT_HI_VARINST`, `ACT_HI_DETAIL` no schema `SIGA2_CAMUNDA`; e no `SIGA2_LICENCIAMENTO`: `SERVIDOR` (`CPF`, `NOME`, `MATRICULA`, `CARGO`), `COMPLEMENTACAO` (`ID`, `ID_REQUERIMENTO`, `DATA_INICIO`, `DATA_ENTREGA`, `SITUACAO`), além do que já existe.
- **Construtor de fixtures** (`CamundaFicticioBuilder`, só em código de teste/verificação, reutilizável na S8 e na S9): API fluente `processo(HIDRICO|LAC, idRequerimento).naEtapa("taskAnalisarRequerimento").comAnalistas(3).desde(data)`, `.esperandoPagamento()`, `.comFilhas(...)`, `.encerradoEm("Event_...")`, `.comTarefaDeSuporte()`, `.comComplementacao()`. Ele escreve as linhas coerentes de `ACT_RU_*` (e `ACT_HI_PROCINST` para as encerradas). **Caminho histórico detalhado é da S8**: aqui só o necessário para a posição atual.
- **Cenários fictícios obrigatórios** (um requerimento cada, com número `N/2026` evidente e dados com marca "FICTÍCIO"): **Hídrico:** H01 em cadastro (tarefa do interessado); H02 em conferência (grupo); H03 aguardando pagamento (gateway + assinatura de evento); H04 isento indo à atribuição; H05 atribuição (grupo); H06 aceite com 2 de 3 analistas já concluídos; H07 análise com 3 analistas; H08 revisão do parecer (chefia); H09 validação com 3 signatários; H10 emissão com **duas filhas em paralelo** (autorização e portaria); H11 registro da publicação; H12 verificação da revisão (arquivado/concluído); H13 correção com o interessado; H14 correção aguardando pagamento; H15 concluído (instância encerrada em `Event_1wiuc48`); H16 cancelado pelo cadastrante; H17 expirado no cadastro; H18 **taxa vencida** (instância encerrada em `Event_0h1oige` e situação `AGUARDANDO_PAGAMENTO_DE_TAXAS`); H19 com tarefa **Suporte** ativa; H20 análise com **complementação em paralelo**; H21 **sem instância** (legado). **LAC:** L01 conferência; L02 aguardando pagamento; L03 correção; L04 envio do termo; L05 concluído; L06 arquivado (termo não enviado); L07 expirado na correção. Mais **um requerimento Trifásico** fictício (não suportado).
- **Servidores fictícios** com CPF inválido de propósito, e um **grupo desconhecido** e um grupo `0` para testar o fallback.
- Perfil de execução `verificacao` carrega os cenários ao subir; os testes usam o mesmo construtor. Documente em `docs/13` como criar um cenário novo.
- Testes: o construtor gera exatamente o que cada cenário descreve (contagem de tarefas, filhas, assinaturas); nenhum dado real no repositório (varredura de CPF/CNPJ válidos e nomes).

## Bloco 3 (OPUS): leitura da posição atual no Camunda

**Commit sugerido:** `feat(tramitacao): leitura da posição atual do requerimento no Camunda`

- Porta `PosicaoNoFluxo` (aplicação) e adaptador JDBC **único** `CamundaPosicaoJdbc`. Entrada: `idRequerimento` e `chaveDoModelo` (obtida de `TIPO_PROCESSO.INSTANCIA_REQUERIMENTO` e do catálogo: `RECURSO_HIDRICO` → Hídrico, `LICENCIAMENTO_ADESAO` → LAC; outro valor → "não suportado").
- **Algoritmo** (cada passo é uma consulta curta por `PROC_INST_ID_`):
  1. **Instância raiz:** `ACT_HI_PROCINST` com `BUSINESS_KEY_ = TO_CHAR(:id)` e `PROC_DEF_KEY_ = :chave`. Mais de uma: a mais recente por `START_TIME_`, com selo de aviso. Nenhuma: resposta `SEM_INSTANCIA` (o plano B é da S8).
  2. **Encerrada** (`END_TIME_` preenchido): posição = **final** pelo `END_ACT_ID_` mapeado no catálogo (com `STATE_` e `DELETE_REASON_` para diagnóstico interno).
  3. **Ativa:** tarefas ativas (`ACT_RU_TASK` por `PROC_INST_ID_`: `TASK_DEF_KEY_`, `NAME_`, `ASSIGNEE_`, `CREATE_TIME_`, e vínculos de grupo em `ACT_RU_IDENTITYLINK` com `TYPE_ = 'candidate'`); **esperas** (execuções ativas por `ACT_ID_` em `ACT_RU_EXECUTION`, confirmadas por `ACT_RU_EVENT_SUBSCR`); **filhas** (instâncias com `SUPER_PROCESS_INSTANCE_ID_` = raiz, e suas tarefas ativas); **complementação** (instância `procGestaoComplementacao` com business key = id de uma `COMPLEMENTACAO` do requerimento, **filtrando pela chave**).
  4. Cada elemento ativo resolve para uma **etapa** do catálogo; a **principal** e os **paralelos** seguem a decisão da sprint. "Desde quando" = `CREATE_TIME_` da tarefa (na análise, a tarefa é recriada a cada devolução: rotule "desde a última entrada nesta etapa").
  5. **Tarefa "Suporte" ativa:** não é a posição. Mostre a **última etapa real percorrida** (a mais recente de `ACT_HI_ACTINST` por `PROC_INST_ID_` que esteja no catálogo e não seja ignorada) com o selo "Em atendimento de suporte técnico".
  6. **Multi-instância:** resumo "x de y concluídos": tarefas concluídas do ciclo atual (`ACT_HI_TASKINST` por `PROC_INST_ID_` e `TASK_DEF_KEY_`, com `END_TIME_` e `DELETE_REASON_ = 'completed'`, a partir do início do corpo da multi-instância) contra o total do ciclo. Se não for possível determinar o ciclo, mostre só as ativas, sem inventar o "y".
- Consultas com **limite de linhas e tempo limite** (parâmetros existentes de `docs/12`), sem `TO_NUMBER` em coluna, com `TO_CHAR` nos dois lados de toda comparação de texto.
- **Não derive nada da situação do Licenciamento aqui** (bloco 5).
- Testes (fixtures do bloco 2): um teste por cenário H01 a H20, L01 a L07 e o Trifásico; colisão de ids (requerimento e complementação com o mesmo número); duas instâncias para o mesmo requerimento; modelo errado para o tipo; `EXPLAIN`/contagem de consultas: no máximo N consultas por requerimento (N documentado, sem laço por linha).

## Bloco 4 (OPUS): responsável (com quem está)

**Commit sugerido:** `feat(tramitacao): responsável pela etapa (servidor, área, interessado) sem expor CPF`

- Porta `ResolverResponsavel`. Regras: `ASSIGNEE_` com CPF → **nome do servidor** em `SERVIDOR` (consulta em lote por CPF, uma por requerimento, limite de linhas); sem correspondência → "Responsável não identificado" (nunca o CPF); `ASSIGNEE_` que é o CPF do **cadastrante/interessado** (cadastro e correção) → "Interessado (cadastrante)"; tarefa de grupo → **área** pelo arquivo `src/main/resources/rastro/grupos.yaml` (id do grupo → rótulo curto, ex.: "Conferência de documentos (subterrânea)"); grupo desconhecido ou `0` → "Equipe responsável"; script/gateway → "Sistema".
- **Arquivo de grupos** com os nomes reais da regra 13, em rótulos de negócio; validado na subida (id repetido, rótulo vazio).
- **Multi-instância:** lista de responsáveis do ciclo (cada analista com o estado: concluiu / pendente), e "x de y" do bloco 3. Equipe grande: máximo configurável de nomes na resposta (padrão 10) com "e mais N".
- **Parâmetro `RASTRO_MOSTRAR_NOME_RESPONSAVEL`** (porta `ParametrosDoRastro`, ligado em `config` como os demais): desligado = só a área e o papel ("Analista", "Chefia"), sem nome. Ele vale **na próxima leitura** (classe "imediato", como em S5).
- **Nunca** devolva, registre ou coloque em chave de cache o CPF: o cache usa id do requerimento e versão do catálogo. Log de erro sem CPF (teste que força erro com CPF fictício e verifica o log).
- Testes: cada tipo de responsável; CPF sem servidor; grupo desconhecido e `0`; parâmetro ligado e desligado; equipe de 12 analistas; varredura de CPF no JSON de resposta e no log.

## Bloco 5 (OPUS): divergências conhecidas e selos de aviso

**Commit sugerido:** `feat(tramitacao): confronto entre situação do Licenciamento e etapa, com selos de aviso`

- Porta `Reconciliar` (entrada: posição do bloco 3 + `SITUACAO_PROCESSO`). A saída é a posição **inalterada** mais uma lista de **avisos com código estável**. Catálogo de avisos em arquivo (código, texto curto ao usuário, gravidade `INFO`/`ATENCAO`):
  - `FLUXO_ENCERRADO_SEM_STATUS`: instância encerrada em `Event_0h1oige` (Hídrico) com situação `AGUARDANDO_PAGAMENTO_DE_TAXAS`. Texto: "O fluxo foi encerrado por taxa não paga no prazo, mas o SIGA ainda mostra *aguardando pagamento*." **Não troque a situação mostrada**; o aviso "Taxa vencida" por data é da S9.
  - `CONCLUIDO_ANTES_DA_VERIFICACAO`: situação `CONCLUIDO` com a instância ainda ativa em "Verificação da revisão". Texto: "Processo concluído; a verificação da revisão é só arquivamento." (selo `INFO`, não é divergência a corrigir).
  - `SITUACAO_NAO_CONFERE`: situação do Licenciamento incompatível com a etapa (tabela de compatibilidade em arquivo, por etapa, com a lista de situações aceitas; `AGUARDANDO_EMISSAO_DE_TAXAS` aceita duas etapas). Texto neutro: "A situação do SIGA ('X') não corresponde exatamente à etapa do fluxo. Vale a etapa do fluxo."
  - `EXPIRADO_NA_CORRECAO_LAC`: final `evtEncerrarProcessoPrazoCorrecaoExpirado` com a situação do expirado no cadastro (o rótulo vem do **final**, não da situação).
  - `VARIAS_INSTANCIAS`, `EM_SUPORTE`, `ETAPA_NAO_MAPEADA`, `SEM_INSTANCIA`.
- A **etapa do fluxo sempre prevalece** sobre a situação, exceto em `SEM_INSTANCIA` (S8).
- Selos exibem texto, não só cor ou ícone.
- Testes: cada código, com cenário (H12, H15, H18, H19, L07, requerimento com situação incompatível criado no teste, H21); a posição devolvida é **idêntica** com e sem o confronto (o confronto só acrescenta avisos).
- Docs: `docs/15` (tabela de avisos), `docs/10` (a correção do bug de origem do Licenciamento fará o aviso `FLUXO_ENCERRADO_SEM_STATUS` desaparecer sozinho).

## Bloco 6 (SONNET): API da posição e auditoria

**Commit sugerido:** `feat(tramitacao): endpoints da posição atual e evento de auditoria`

- `GET /api/requerimentos/{id}/posicao`: devolve `situacaoLicenciamento` (valor e descrição), `modelo` (rótulo), `estado` (`ATIVO`, `ENCERRADO`, `SEM_INSTANCIA`, `NAO_SUPORTADO`, `INDISPONIVEL`), `etapaAtual` (id, rótulo, tipo, desde, responsável), `emParalelo[]`, `complementacao`, `multiInstancia` (x de y e participantes), `avisos[]` (código, texto, gravidade) e `final` (rótulo) quando encerrado. **Nenhum** id técnico (`ACT_ID_`) para quem não é Administrador; nenhum CPF; nenhuma chave de arquivo.
- `GET /api/processos/{numero}/posicao`: uma posição por requerimento vinculado (mesmo contrato, em lista).
- Erros com `ProblemDetail` e os códigos de `docs/07`; `INDISPONIVEL` é **200** com o estado, não 5xx (o Rastro degrada). 404 para requerimento inexistente.
- **Auditoria:** novo tipo `TRAMITACAO` (grupo `DOCUMENTOS`, como a consulta) pela porta `RegistrarEvento`, com retrato do usuário e números, **sem** responsável nem CPF no evento. Atualize `docs/11` e a tela de auditoria (filtro e descrição do tipo).
- Docs: `docs/07` (rotas e matriz), `docs/02` (próximos RF livres a partir de RF29: posição atual, responsável, avisos; RN de "etapa prevalece sobre situação").
- Testes de contrato (MockMvc) por perfil e por cenário.

## Bloco 7 (OPUS): matriz de acesso e varredura de vazamento

**Commit sugerido:** `test(tramitacao): matriz de acesso e varredura de vazamento de CPF e ids técnicos`

- Estenda a matriz de acesso de `docs/07` e o teste de matriz da S4/S5 com as duas rotas novas, por perfil (`OPERADOR`, `OPERADOR_SIGILO`, `ADMINISTRADOR`) e sem login (401). O Operador vê posição de qualquer requerimento? **Siga a regra vigente de consulta** (RN07) e documente; se a regra de vínculo do interessado se aplica à consulta, aplique também aqui.
- **Varredura de vazamento:** para **todos** os cenários e perfis, serialize a resposta e o log e procure: CPF (com e sem máscara, 11 dígitos soltos), ids técnicos (`task`, `gtw`, `Event_`, `Activity_`, `proc`) para quem não é Administrador, chave de arquivo, token, nomes de grupo técnicos. Zero ocorrências. Teste de **negação**: desligar o parâmetro de nome tira os nomes de todas as respostas.
- Teste de **resiliência**: Camunda fora do ar ou com tempo limite estourado → `INDISPONIVEL`, sem 5xx e sem travar o detalhe do requerimento; consulta lenta não segura a thread além do limite.
- Docs: `docs/10` (riscos fechados e novos), `docs/12` (variáveis).

## Bloco 8 (SONNET): front, "Onde está agora"

**Commit sugerido:** `feat(front): bloco "Onde está agora" no detalhe do requerimento`

- No detalhe do requerimento, **acima** dos documentos, um bloco compacto: **etapa atual** (rótulo grande, desde quando), **com quem** (área e, se permitido, nome), selo de tipo (tarefa, espera, automática, final), **"+N em paralelo"** (expansível), **"Complementação em andamento"** quando houver, e **x de y** da multi-instância com os participantes (concluiu/pendente).
- **Avisos** com texto e ícone (não só cor), gravidade `INFO`/`ATENCAO`; o tom é neutro e **explica**, não alarma.
- Estados: carregando (esqueleto), `ENCERRADO` com o final ("Concluído", "Cancelado pelo cadastrante" etc.), `SEM_INSTANCIA` ("Histórico detalhado indisponível para este requerimento"; sem diagrama; o plano B vem na S8), `NAO_SUPORTADO`, `INDISPONIVEL` com **"Tentar novamente"** (e o resto da tela segue normal).
- Tempo: "há 3 dias" com a data completa no `title`/tooltip e no texto acessível; sempre no fuso de Cuiabá pela utilidade única.
- O bloco **não altera a altura do quadro do topo** nem empurra a lista de documentos em mais de uma "linha" ao carregar (sem salto de layout: reserve a altura).
- Acessibilidade: rótulos legíveis por leitor de tela, foco por teclado, contraste AA; celular sem rolagem horizontal.
- Testes (Vitest e Playwright com API simulada): um por estado e por selo; o texto nunca contém `task`, `gtw` ou CPF; navegação por teclado; celular.
- Docs: `docs/08` (tela de detalhe, bloco novo).

## Bloco 9 (SONNET): front, processo com vários requerimentos e estados de erro

**Commit sugerido:** `feat(front): posição por requerimento no detalhe do processo e tratamento de erros`

- No **detalhe do processo** (vários requerimentos em abas ou lista), cada requerimento mostra seu bloco "Onde está agora" **resumido** (etapa, com quem, selo) e expande para o completo. A aba do requerimento mostra um **ponto de estado** (texto alternativo incluído).
- Falha em **um** requerimento não derruba os demais (cada posição carrega e falha sozinha).
- **Lista de resultados da busca por interessado:** coluna opcional "Etapa atual" (carregamento sob demanda das posições visíveis, no máximo N por vez, N em `docs/12`; sem disparar dezenas de consultas ao Camunda de uma só vez).
- Testes: processo com 3 requerimentos (um com falha, um sem instância, um encerrado); a lista de busca limita as consultas simultâneas; celular.

## Bloco 10 (SONNET): configurações do Rastro

**Commit sugerido:** `feat(config): parâmetros do Rastro nas Configurações (nome do responsável, cache, limites)`

- Na tela de Configurações (só Administrador; evento único com antes e depois, como na S5), nova seção **Rastro**: mostrar nome do responsável (imediato), TTL do cache de posição (imediato), máximo de nomes por equipe (imediato), limite de posições simultâneas na lista de busca (imediato) e **tempo limite das consultas ao Camunda** (próxima leitura). Credenciais **não** aparecem.
- Teste de conexão do Camunda **separado** do teste do Licenciamento (diagnóstico: leitura de uma tabela do Camunda, sem dado). Trate o falso alarme conhecido (HTTP 503/400 e tempos de 5 s das conexões HTTP) **sem mexer** nos testes de API e SCORP, que são da S6.
- Testes: alteração gera um evento com antes e depois; o Operador não vê a seção (RN08, 403 `PERFIL_SEM_PERMISSAO`).
- Docs: `docs/12`.

## Bloco 11 (SONNET): roteiro de homologação e fechamento

**Commit sugerido:** `docs(tramitacao): roteiro de homologação e fechamento da S7`

- `docs/roteiros/s7-homologacao.md` (modelo dos roteiros anteriores), com **uma linha por cenário** H01 a H21, L01 a L07 e Trifásico: o que abrir, o que a tela deve mostrar (etapa, com quem, selos) e campo "Resultado". Inclua a **conferência com dados reais (feita pelo responsável, fora do repositório)**: 5 requerimentos em etapas diferentes, comparando o Rastro com a tela do SIGA e com o Camunda; **nenhum número real entra no repositório**.
- Atualize `docs/14` com o plano das sprints seguintes: **S8** tramitação (histórico, com quem esteve e fluxograma), **S9** financeiro (taxas, DAR, pagamentos, taxa vencida, isento), **S10** painel do Gestor, **S11** pago que virou isento; e o bloco de **renomeação para Rastro (R1)**. `docs/progresso/s7.md` completo, `docs/10` revisado.
- Varredura final: nenhum CPF, CNPJ, nome real ou número real no repositório; `git diff --stat` e resumo da sprint.

---

## Critérios de aceite (ao final do bloco 11)

1. Para os cenários H01 a H21, L01 a L07 e o Trifásico, o detalhe mostra a **etapa certa**, desde quando e com quem (ou só a área, com o parâmetro desligado).
2. **Nenhum CPF**, id técnico (para não Administrador), chave de arquivo ou token em resposta, tela, log ou auditoria.
3. A etapa do fluxo prevalece sobre a situação do Licenciamento, e as divergências conhecidas aparecem como **avisos neutros**, sem alterar a posição.
4. O Camunda fora do ar **não derruba** o detalhe do requerimento.
5. Todo SQL novo é `SELECT`, parametrizado, com limite e tempo limite, num único componente; nada é gravado no Oracle.
6. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s7.md` atualizados.
