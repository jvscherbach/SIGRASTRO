# S8: tramitação (2 de 2): histórico, com quem esteve e fluxograma simplificado

> **Forma de trabalho:** a sprint é dividida em **12 blocos**, cada um terminando em um **ponto de commit**. Três sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

A S7 entregou a **posição atual** (etapa, desde quando, com quem). A S8 entrega o **caminho percorrido**: todas as etapas pelas quais o requerimento passou, com data de entrada e saída, **com quem esteve em cada uma**, as voltas que de fato aconteceram, o final, e um **fluxograma simplificado, não técnico**, que mostra visualmente onde o processo está. Para requerimentos **sem instância** no Camunda (2021/2022), o **plano B** mostra a linha do tempo aproximada pelo que o Licenciamento guarda.

Ficam **fora**: taxas, DAR, pagamentos, "Taxa vencida" por data e "Isento de Taxa" com data (S9); painel do Gestor (S10); pago que virou isento (S11). O fluxograma desta sprint já nasce com as **caixas** "Emissão da taxa", "Aguardando pagamento" e "Isenção confirmada" (vêm do caminho no Camunda); os dados financeiros entram na S9.

O serviço é usado em **processos jurídicos**: dado cruzado errado é pior do que dado ausente. Se a reconstrução for ambígua, o Rastro **diz** ("histórico aproximado", "etapa não mapeada"), em vez de desenhar o que não aconteceu.

Leia antes de começar: `docs/02`, `docs/03`, `docs/07`, `docs/08`, `docs/09`, `docs/10`, `docs/11`, `docs/12`, `docs/13`, `docs/14`, **`docs/15` (S7: catálogo de etapas, regras técnicas, avisos)**, `docs/progresso/s7.md` e o código do módulo `tramitacao`. Reuse o catálogo, o construtor de fixtures (`CamundaFicticioBuilder`), a porta de responsável e o parâmetro `RASTRO_MOSTRAR_NOME_RESPONSAVEL` da S7; **não duplique**.

## Decisões já tomadas (registre em `docs/09`)

- **O fluxograma simplificado mostra só o que o processo já percorreu** (mais a etapa atual destacada). **Nunca** desenha etapas futuras nem o "mapa completo" do Camunda.
- **Tudo em linha reta:** uma sequência de caixas. Voltas e repetições **só aparecem se realmente aconteceram**, como nova caixa na linha (ex.: Análise → Revisão do parecer → Análise, com o rótulo "2ª passagem"), nunca como seta de retorno.
- **Só etapas do usuário:** tarefas humanas e a espera "Aguardando pagamento". Os gateways ficam como **caixa normal com o estado**, não losango. **Sem** "Suporte" nem "Aguardando emissão de taxa". As emissões entram numa **única caixa "Emissão do ato"** que lista só os atos que ocorreram (autorização, outros atos, certificado, portaria).
- **Caixa própria "Emissão da taxa"** (se passou por ela, a taxa foi emitida) e "Aguardando pagamento". Se **isento**, em vez dessas duas aparece **"Isenção confirmada"**. A **correção do requerimento** tem **sua própria** "Emissão da taxa" e "Aguardando pagamento", quando ocorreram. A classificação taxável × isento vem **da rota seguida** (passou ou não pelo gateway de pagamento), com `ACT_HI_DETAIL` só como confirmação, nunca do valor final de `statusConferencia`.
- **"Verificação da revisão do processo" é caixa própria**, antes do final. **Finais** como **bola preta** (círculo preenchido) com o **rótulo escrito em cima**: Concluído, Cancelado pelo cadastrante, Expirado no cadastro, Expirado na correção (LAC), Cancelado por falta de pagamento, Arquivado (LAC), e o do Hídrico "Encerrado por taxa não paga no prazo".
- **Com quem esteve:** por etapa, a área e (conforme o parâmetro) o nome; nas multi-instância, os participantes. **CPF nunca sai.**
- **Plano B** (sem instância): **sem fluxograma**; linha do tempo aproximada a partir de `HISTORICO_REQUERIMENTO` e das datas de `REQUERIMENTO`, com o selo fixo **"Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo"**. Sem voltas finas, sem aceite/revisão/validação individuais.
- **Camunda lento ou fora:** o histórico responde `INDISPONIVEL`, e o plano B **não** é usado como substituto silencioso (seria mostrar dado aproximado como se fosse exato): mostra o aviso e a posição do Licenciamento.
- **Leitura direta por `PROC_INST_ID_`**, com cache curto, **limite de linhas** por requerimento e recusa explícita acima do limite (ex.: instância com milhares de atividades por erro repetido: colapsa repetições e, se ainda passar do limite, devolve o que coube com aviso `HISTORICO_TRUNCADO`).

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

## Protocolo das três sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s8.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s8.md` (crie no bloco 1, com a tabela dos 12 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET|HAIKU>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET|HAIKU>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

**Blocos da sessão HAIKU (documentação mecânica, para economizar tokens).** O Haiku só recebe blocos de documentação cujo conteúdo já está decidido e escrito em outros arquivos (cenários, tabelas, planos). Nesses blocos: **não invente** cenário, número, regra nem nome; copie e organize só o que existe em `docs/`, nos testes e nas fixtures (leia-os antes); o que não achar, escreva como **NÃO CONFIRMADO** em vez de preencher; **nenhum dado real**; rode a varredura de dados pessoais ao final. Se o bloco exigir uma decisão de projeto ou de código, **pare** e responda `Este bloco precisa da sessão SONNET.` O responsável revisa o `git diff` do bloco com mais atenção do que nos demais.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`. A mensagem **não** começa com espaço.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Fixtures históricas: caminhos com voltas, ruído e filhas | **SONNET** | Dados sintéticos com cenário fechado |
| 2 | Leitura do caminho percorrido (`ACT_HI_*`) | **OPUS** | SQL por instância, filhas, ruído, limite |
| 3 | Reconstrução das etapas, ciclos e voltas | **OPUS** | Regra central; erro vira dado jurídico errado |
| 4 | Finais e divergências do final | **OPUS** | Mapeamento fim × situação, bug de origem e LAC |
| 5 | Plano B para requerimento sem instância | **OPUS** | Dado aproximado nunca pode parecer exato |
| 6 | API da tramitação | **SONNET** | Contrato pronto |
| 7 | Desempenho, limites, cache e degradação | **OPUS** | Proteção do Camunda de produção |
| 8 | Front: linha do tempo com quem esteve | **SONNET** | Interface |
| 9 | Front: fluxograma simplificado | **SONNET** | Interface (HTML, CSS e SVG) |
| 10 | Front: plano B e estados de erro | **SONNET** | Interface |
| 11 | Acesso, auditoria e varredura de vazamento | **OPUS** | Prova das regras de segurança |
| 12 | Roteiro de homologação e fechamento | **HAIKU** | Documentação a partir de cenários já definidos |

---

## Bloco 1 (SONNET): fixtures históricas

**Commit sugerido:** `test(tramitacao): fixtures históricas de caminhos, voltas e ruído no Camunda fictício`

Estenda o `CamundaFicticioBuilder` da S7 (não crie outro) para escrever **o caminho histórico** (`ACT_HI_PROCINST`, `ACT_HI_ACTINST`, `ACT_HI_TASKINST`, `ACT_HI_DETAIL`, `ACT_HI_VARINST`), com `START_TIME_`, `END_TIME_`, `ASSIGNEE_`, `DELETE_REASON_`, `CALL_PROC_INST_ID_` e `SUPER_PROCESS_INSTANCE_ID_` coerentes. API sugerida: `.passou("taskCadastrarRequerimento", dias(0), dias(1)).passouPelaEspera("gtwAguardarPagamento", ...).voltouPara(...)`.

Cenários (além dos da S7, que ganham caminho): 
- **HC01** caminho feliz taxável: cadastro → conferência → emissão da taxa → aguardando pagamento → protocolo → atribuição → aceite (3 analistas) → análise (3) → revisão do parecer → validação (3) → emissão (autorização + portaria em paralelo) → publicação → verificação da revisão (ativa, arquivado) → **concluído pela publicação**.
- **HC02** isento: sem emissão de taxa nem espera; "Isenção confirmada".
- **HC03** conferência **reprova** e volta ao cadastro (2ª passagem do cadastro); `statusConferencia` regravado (`reprovado` → `taxavel`).
- **HC04** validação devolvida `RA` (volta à análise) e depois `RP` (volta à revisão do parecer).
- **HC05** **troca de analista** em revisão/validação: devolve **sempre à análise**.
- **HC06** **redistribuição** (volta à atribuição) e recusa no aceite.
- **HC07** correção do requerimento **com nova taxa** (emissão da taxa de correção + aguardando pagamento) e retorno **direto à análise**, sem nova atribuição.
- **HC08** correção **sem nova taxa**.
- **HC09** **correção expirada no Hídrico** (segue para a análise, não encerra).
- **HC10** análise onde **um analista pede correção** e as outras duas tarefas ficam com `DELETE_REASON_ = 'deleted'` (não contam como percorridas).
- **HC11** ruído: tarefa **Suporte** passada, **script repetido 4 vezes** por erro de API, "Report Kafka", evento de repetição por erro.
- **HC12** emissão com **três** filhas e uma **devolvida** da emissão para a revisão (`isRevisaoParecer`).
- **HC13** **REVISAR** na verificação (recria a emissão): caso raro, mas o modelo permite.
- **HC14** complementação paralela concluída durante a análise.
- **HC15** instância com **milhares de atividades** (teste do limite).
- Finais: Hídrico `Event_1wiuc48`, `Event_1kq2k0z`, `Event_0abm55w`, `Event_0h1oige` (com situação `AGUARDANDO_PAGAMENTO_DE_TAXAS`); LAC `evtProcessoConcluido`, `evtFimRequerimentoAPedidoCadastrante`, `evtEncerrarRequerimentoPrazoCadExpirado`, `evtEncerrarProcessoPrazoCorrecaoExpirado` (com a **mesma situação** do expirado no cadastro), `evtEncerrarProcessoFaltaPagto`, `Event_0x7hz1p`.
- **LAC:** LC01 caminho feliz (conferência → emissão da taxa → pagamento → termo → termo assinado → concluído), LC02 isento, LC03 reprovado → correção → conferência (sem nova taxa).
- **Plano B:** PB01 e PB02, requerimentos de 2021 **sem instância**, com eventos de `HISTORICO_REQUERIMENTO` (criar a tabela fictícia: `ID_REQUERIMENTO`, `DATA_CADASTRO`, `EVENTO`) e datas de `REQUERIMENTO`; PB03 sem nenhum evento.
- Testes: o construtor produz exatamente o descrito; nenhum dado real; documente em `docs/13`.

## Bloco 2 (OPUS): leitura do caminho percorrido

**Commit sugerido:** `feat(tramitacao): leitura do caminho percorrido do requerimento no histórico do Camunda`

- Porta `CaminhoPercorrido` e adaptador JDBC **no mesmo componente de acesso** da S7. Para a instância raiz (S7, bloco 3) e para as **filhas** (`SUPER_PROCESS_INSTANCE_ID_`) e a **complementação**: `ACT_HI_ACTINST` (`ACT_ID_`, `ACT_NAME_`, `ACT_TYPE_`, `START_TIME_`, `END_TIME_`, `ASSIGNEE_`, `TASK_ID_`, `CALL_PROC_INST_ID_`) e `ACT_HI_TASKINST` (`TASK_DEF_KEY_`, `ASSIGNEE_`, `START_TIME_`, `END_TIME_`, `DELETE_REASON_`), **sempre por `PROC_INST_ID_`**, ordenado por `START_TIME_` e id, com `FETCH FIRST` pelo limite configurável (padrão 3.000 linhas por instância).
- Entrega ao domínio uma lista **bruta e ordenada** de `AtividadeBruta`, ainda sem interpretação (a interpretação é do bloco 3). Ruído **não é removido aqui**.
- Sem `TO_NUMBER` em coluna, `TO_CHAR` nos dois lados, parâmetros nomeados.
- Testes (fixtures): ordem estável (empate de `START_TIME_`), filhas e complementação incluídas, limite respeitado e marcado como truncado, instância sem nenhuma linha, requerimento com duas instâncias.

## Bloco 3 (OPUS): reconstrução das etapas, ciclos e voltas

**Commit sugerido:** `feat(tramitacao): reconstrução do caminho em etapas simplificadas, ciclos e voltas`

Serviço de domínio puro `ReconstruirCaminho` (sem JDBC; fácil de testar), entrada: atividades brutas + catálogo + responsáveis; saída: lista ordenada de `PassagemPelaEtapa`.

- **Percorrida** = atividade com `END_TIME_` e, se tarefa, `DELETE_REASON_ = 'completed'`. Tarefas `deleted`/canceladas **não** contam. A **etapa atual** (sem `END_TIME_`) entra como passagem aberta.
- **Colapsar ruído:** ids ignorados do catálogo fora; **repetições consecutivas** do mesmo script/etapa automática viram **uma** passagem (guarde a contagem só para diagnóstico); eventos de repetição por erro fora.
- **Multi-instância:** uma passagem por **ciclo** da etapa (agrupe pelo corpo da multi-instância ou, se faltar, por proximidade de `START_TIME_` dentro da mesma etapa), com a lista de participantes e, para cada um, início/fim/estado; tarefas encerradas por condição de término ficam marcadas "encerrada sem ação" e **não** são "percorridas".
- **Emissão:** as filhas e a callActivity viram **uma única passagem "Emissão do ato"**, com os **atos que ocorreram** (autorização, outros atos, certificado, portaria) e, por ato, início e fim; paralelas sobrepostas não geram passagens separadas.
- **Voltas:** quando uma etapa aparece de novo depois de uma posterior, a nova passagem recebe `ciclo = 2, 3, ...` e o **motivo** inferido por regra explícita e testada (devolução na validação `RA`/`RP` por `ACT_HI_DETAIL` das variáveis de decisão da **lista branca** `decisaoValidacaoParecerTecnico`, `revisarRequerimento`, `decisaoVerificarRevisao`, `isRevisaoAnalise`, `isRevisaoParecer`, `statusConferencia`; troca de analista; redistribuição; correção). **Sem motivo determinável, o motivo fica vazio**; nunca inventado.
- **Taxável × isento** pela **rota**: passou por `gtwAguardarPagamento`/`gwtAguardarPagtoOuVencto` = taxável (emissão da taxa + espera); sem a passagem e com `taskGerarNumeroProtocolo` = isenção. `statusConferencia` do `ACT_HI_DETAIL` só **confirma**; se discordar da rota, a **rota vence** e sai o aviso `ROTA_DIVERGE_DO_STATUS` (só no diagnóstico do Administrador).
- **Ramo da correção** (`taskCorrecaoRequerimento` e a taxa/espera da correção) com `ramo = CORRECAO`. No Hídrico, após a correção o fluxo volta **direto à análise**; no LAC, à **conferência** sem nova taxa. "Correção expirada" no Hídrico segue para a análise (não é final).
- **Verificação da revisão** é passagem normal e **a última**, com o selo "Processo concluído" (regra 9). `REVISAR` (recria a emissão) é raro, mas gera nova passagem de emissão.
- Cada passagem: etapa (do catálogo), `ciclo`, `ramo`, `entrada`, `saida` (vazia se atual), `duracao`, `responsaveis[]`, `motivoDaVolta`, `emParalelo`.
- Testes (HC01 a HC15, LC01 a LC03): sequência exata esperada por cenário; HC10 não conta as tarefas `deleted`; HC11 colapsa ruído; HC12 e HC13; ciclos numerados; **propriedade:** nenhuma etapa futura aparece; **propriedade:** passagens ordenadas por entrada; **propriedade:** o CPF nunca aparece em nenhuma passagem.
- Docs: `docs/15` (regras de reconstrução com a tabela de motivos de volta).

## Bloco 4 (OPUS): finais e divergências do final

**Commit sugerido:** `feat(tramitacao): finais do fluxo e divergências entre evento de fim e situação`

- Mapeie `END_ACT_ID_` → **final** do catálogo (bola preta com rótulo): ver S7 e as tabelas de finais. O rótulo vem **do evento de fim**, nunca da situação (no LAC "expirado na correção" e "expirado no cadastro" têm a mesma situação).
- Avisos (reuse o catálogo da S7): `FLUXO_ENCERRADO_SEM_STATUS` (Hídrico, `Event_0h1oige`, situação `AGUARDANDO_PAGAMENTO_DE_TAXAS`; **mantenha** a situação como o Licenciamento diz e o final como "Encerrado por taxa não paga no prazo"; "Taxa vencida" por data é da S9), `FINAL_E_SITUACAO_DIVERGEM` (outras combinações fora da tabela de compatibilidade) e `CONCLUIDO_ANTES_DA_VERIFICACAO`.
- Instância **ativa na verificação da revisão** = **concluído** (sem bola preta, que só existe com o evento de fim).
- `ARQUIVADO_TEMPORARIAMENTE` nunca é gravado por ninguém: se aparecer como situação, aviso `INFO` neutro.
- Testes com todos os finais dos cenários (Hídrico e LAC) e a combinação divergente.

## Bloco 5 (OPUS): plano B para requerimento sem instância

**Commit sugerido:** `feat(tramitacao): plano B (histórico aproximado) para requerimento sem instância no Camunda`

- Usado **somente** quando a raiz **não existe** em `ACT_HI_PROCINST` (estado `SEM_INSTANCIA`), nunca por falha do Camunda.
- Fontes (um único componente, `SELECT` com limite): `HISTORICO_REQUERIMENTO` (`DATA_CADASTRO`, `EVENTO`; **sem** `CPF_USUARIO`) e as datas de `REQUERIMENTO` (`DATA_ABERTURA`, `DATA_PROTOCOLO_PROCESSO`, `DATA_CANCELAMENTO`, `DATA_EXPIRACAO_REQUERIMENTO`, `DATA_EXPIRACAO_PROCESSO`) mais a situação.
- Mapeie os **eventos** (CRIACAO_REQUERIMENTO, AGUARDANDO_CONFERENCIA, CHECKLIST_APROVADO/REPROVADO, AGUARDANDO_PAGAMENTO_TAXA, PAGAMENTO_CONFIRMADO, PROTOCOLO, EM_ANALISE, REVISAR/VALIDAR_PARECER_TECNICO, TITULOS_EMITIDOS, TITULACOES_DISPONIVEIS, CONCLUSAO_REQUERIMENTO) para as **mesmas etapas simplificadas** do catálogo, em arquivo próprio (`eventos-historico.yaml`), evento desconhecido = ignorado com contagem. Resultado: linha do tempo **aproximada**, sem ciclos, sem aceite, revisão e validação individuais, **sem responsável** (a tabela não tem CPF e o plano B não consulta `SERVIDOR`).
- Marca `aproximado = true` e o aviso fixo `HISTORICO_APROXIMADO`. Sem nenhum evento: só situação e datas ("Sem detalhamento disponível").
- Testes (PB01 a PB03): mapeamento, ordem por data, ausência de responsável, **não** usar o plano B quando o Camunda está indisponível, evento desconhecido.

## Bloco 6 (SONNET): API da tramitação

**Commit sugerido:** `feat(tramitacao): endpoint da tramitação completa do requerimento`

- `GET /api/requerimentos/{id}/tramitacao`: `modelo`, `estado` (`ATIVO`, `ENCERRADO`, `SEM_INSTANCIA`, `NAO_SUPORTADO`, `INDISPONIVEL`), `aproximado`, `passagens[]` (etapa: id estável e rótulo; `ciclo`; `ramo`; `entrada`; `saida`; `duracaoSegundos`; `responsaveis[]` conforme o parâmetro; `participantes[]` na multi-instância; `atos[]` na emissão; `motivoDaVolta`; `atual`; `emParalelo`), `final` (rótulo e tipo) e `avisos[]`. O **fluxograma** é desenhado pelo front a partir de `passagens` (o back-end **não** devolve imagem).
- `GET /api/processos/{numero}/tramitacao`: um objeto por requerimento.
- Ids técnicos só para o Administrador (diagnóstico); nunca CPF, chave, token.
- Erros em `ProblemDetail`; `INDISPONIVEL` é 200. Auditoria: reuse o tipo `TRAMITACAO` da S7 com o detalhe "histórico".
- Docs: `docs/07`, `docs/02` (próximos RF livres: histórico, com quem esteve, fluxograma, plano B).
- Testes de contrato por perfil e por cenário.
- **Pendência da S6 (CI do servidor MCP):** acrescente ao `ci.yml` um passo **barato**, sem recurso novo nem pago (por exemplo, no job `mudancas`, que sempre roda, ou num job condicionado a mudanças em `tools/`): `npm --prefix tools/mcp-rastro ci`, `npm --prefix tools/mcp-rastro test` e `npm --prefix tools/mcp-rastro audit --omit=dev --audit-level=high`. Confira se o `dependabot.yml` cobre `tools/mcp-rastro/` (npm) e, se não cobrir, inclua. Atualize `docs/13` (CI) e `docs/claude-code/mcp.md`.

## Bloco 7 (OPUS): desempenho, limites, cache e degradação

**Commit sugerido:** `perf(tramitacao): limites, cache e degradação do histórico para proteger o Camunda`

- Número **máximo** de consultas por requerimento (documentado e **testado**, sem laço por linha), limite de linhas por instância, **tempo limite** por consulta e total por requisição (`RASTRO_TRAMITACAO_TIMEOUT_SEGUNDOS`).
- **Cache em memória** da tramitação por requerimento (TTL próprio; invalida junto com a posição) com teto de entradas; **uma** consulta por requerimento mesmo com várias requisições simultâneas (*single flight*).
- **Limite de concorrência** de leituras ao Camunda (semáforo configurável) para o painel e a lista de busca não derrubarem a produção.
- **Recomendações de índice** (apenas **documento**, o usuário é somente leitura): `ACT_HI_TASKINST(TASK_DEF_KEY_, START_TIME_)`, `LANCAMENTO_TAXA(ID_REQUERIMENTO)`, `HISTORICO_REQUERIMENTO(ID_REQUERIMENTO)`; e a **tabela de apoio** do painel do Gestor (S10) como decisão futura. Registre em `docs/10`.
- Testes: teste de carga leve com fixtures (200 requerimentos em sequência e 20 em paralelo): sem estouro de limite, sem pico de conexões; Camunda lento simulado → `INDISPONIVEL` dentro do tempo; cache respeita TTL.

## Bloco 8 (SONNET): front, linha do tempo com quem esteve

**Commit sugerido:** `feat(front): linha do tempo da tramitação com quem esteve em cada etapa`

- No detalhe do requerimento, nova seção **Tramitação** (abaixo de "Onde está agora" e acima dos documentos, recolhível): lista vertical de **passagens** com etapa, entrada e saída, **duração** legível ("2 dias e 3 h"), **com quem esteve** (área e nome conforme o parâmetro; participantes da multi-instância com concluiu/pendente), motivo da volta ("2ª passagem: devolvido na validação do parecer"), atos da emissão, e selo da **etapa atual**.
- Ramo da correção com **recuo ou marcador** visual e texto ("Correção do requerimento"). Voltas aparecem na ordem real.
- Filtro leve: "ocultar etapas automáticas" (padrão ligado). A lista completa continua acessível.
- Final com rótulo ("Concluído em 12/03/2026") e avisos neutros da S7/S8.
- Acessibilidade: lista semântica, texto alternativo, contraste AA, foco por teclado, celular sem rolagem horizontal; datas pela utilidade única (Cuiabá).
- Testes (Vitest e Playwright com API simulada): HC01, HC03, HC04, HC07, HC10, HC12, LC03; filtro; celular; nenhum `task`/`gtw`/CPF no DOM.
- Docs: `docs/08`.

## Bloco 9 (SONNET): front, fluxograma simplificado

**Commit sugerido:** `feat(front): fluxograma simplificado da tramitação, em linha reta`

Componente `FluxogramaSimplificado` (HTML, CSS e SVG simples, **sem biblioteca**), alimentado por `passagens`:

- **Linha reta** de caixas com setas simples; em tela estreita a linha **quebra em linhas** (sem rolagem horizontal da página). **Só o que já passou** + a etapa atual destacada. **Nunca** etapas futuras.
- **Caixas** com o rótulo de negócio e, abaixo, a data de entrada; **estado** da caixa por texto e ícone (concluída, atual, aguardando) além da cor. Gateways como **caixa normal com o estado**, nunca losango.
- **Emissão da taxa** e **Aguardando pagamento** quando taxável; **Isenção confirmada** quando isento. A correção com **suas** caixas de taxa e pagamento, em faixa identificada ("Correção do requerimento").
- **Emissão do ato:** **uma caixa** com só os atos que ocorreram.
- **Repetições** como novas caixas na linha, com o rótulo **"2ª passagem"** (nunca seta de retorno). Multi-instância: **uma caixa** com "3 analistas" e o "x de y" se ativa.
- **Verificação da revisão do processo:** caixa própria. **Final:** **bola preta** com o **rótulo escrito em cima** ("Concluído", "Cancelado pelo cadastrante", "Expirado no cadastro", "Expirado na correção", "Cancelado por falta de pagamento", "Arquivado", "Encerrado por taxa não paga no prazo"). Sem final = sem bola (em andamento).
- **Não renderiza** para plano B, `NAO_SUPORTADO` ou `INDISPONIVEL`: mostra o texto correspondente.
- **Acessibilidade:** o fluxograma tem **equivalente textual** (a linha do tempo do bloco 8 é o texto alternativo; o SVG tem `role="img"` e `aria-label` resumido), foco e tooltip por teclado, contraste AA. **Impressão:** legível em preto e branco (formas e texto, não só cor); botão "Imprimir tramitação" (CSS de impressão).
- Testes (Vitest e Playwright): HC01 (feliz), HC02 (isento), HC03 (2ª passagem do cadastro), HC07 (correção com taxa), HC12 (emissão com atos), HC15 (limite), finais Hídrico e LAC; **propriedades:** nenhuma caixa de etapa futura; nenhum losango; rótulo de final sempre acima da bola; sem rolagem horizontal em 360 px; impressão em P&B.
- Docs: `docs/08` (fluxograma, regras visuais e a lista de rótulos).

## Bloco 10 (SONNET): front, plano B e estados de erro

**Commit sugerido:** `feat(front): histórico aproximado e estados de erro da tramitação`

- **Plano B:** linha do tempo **sem fluxograma**, com o selo fixo "Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo", datas e eventos mapeados; PB03 mostra só situação e datas ("Sem detalhamento disponível").
- `NAO_SUPORTADO`, `INDISPONIVEL` (com "Tentar novamente"; o resto da tela segue), `ENCERRADO` com final; `HISTORICO_TRUNCADO` com o aviso.
- Falha na tramitação **não** afeta "Onde está agora" nem os documentos (e o inverso).
- Testes: PB01 a PB03, cada estado, e a independência entre blocos.

## Bloco 11 (OPUS): acesso, auditoria e varredura de vazamento

**Commit sugerido:** `test(tramitacao): matriz de acesso, auditoria e varredura de vazamento da S8`

- Estenda a matriz de acesso e a auditoria às rotas de tramitação (por perfil e sem login). **Varredura de vazamento** em respostas, logs e DOM: CPF (formas e 11 dígitos soltos), ids técnicos para quem não é Administrador, chave de arquivo, token, `ASSIGNEE_`. Zero ocorrências em **todos** os cenários HC, LC e PB.
- Teste de negação: parâmetro de nome desligado remove nomes de **passagens, participantes e fluxograma**.
- Teste de que o **plano B nunca** consulta `SERVIDOR` e nunca devolve responsável.
- Docs: `docs/10` (riscos fechados e novos), `docs/12`.
- **Pendência da S6 (MCP com dados fictícios, R66):** hoje o servidor MCP só opera se o Rastro se declara em demonstração, o que exige SIGA, API de arquivos e SCORP **vazios**, então no ambiente de verificação ele só mostra o caminho seguro (503). Com o ambiente fictício de Camunda e Licenciamento da S7, **estude e proponha** como o modo de demonstração pode reconhecer um **SIGA fictício local** (por exemplo, o Oracle do ambiente de verificação) **sem enfraquecer a guarda** (continua recusando qualquer destino que não seja o fictício conhecido). Se for seguro, implemente com testes; se não for, deixe a recomendação e a recusa em `docs/09` e feche o R66 como "aceito" em `docs/10`. O MCP continua somente leitura e mascarado.

## Bloco 12 (HAIKU): roteiro de homologação e fechamento

**Commit sugerido:** `docs(tramitacao): roteiro de homologação e fechamento da S8`

- `docs/roteiros/s8-homologacao.md`: uma linha por cenário (HC01 a HC15, LC01 a LC03, PB01 a PB03): o que abrir, o que a linha do tempo e o fluxograma devem mostrar (sequência exata, rótulos, finais) e "Resultado". Inclua a **conferência com dados reais (feita pelo responsável, fora do repositório)**: 5 requerimentos do Hídrico (um com correção e um com devolução) e 2 do LAC, comparando o fluxograma com o que o Camunda e o SIGA mostram; **nenhum número real entra no repositório**.
- Atualize `docs/14` e `docs/progresso/s8.md`; `docs/10` revisado; `docs/15` completo.
- Varredura final de dados reais e `git diff --stat`.

---

## Critérios de aceite (ao final do bloco 12)

1. Para HC, LC e PB, a linha do tempo mostra **exatamente** as etapas percorridas, as voltas que ocorreram, com quem esteve e o final correto.
2. O fluxograma é **em linha reta**, **só com o que passou**, sem losangos, com o final como bola preta rotulada e a "Verificação da revisão" como caixa própria; imprime em preto e branco.
3. **Plano B** sempre rotulado como aproximado; nunca usado quando o Camunda falha.
4. Nenhum CPF, id técnico (para não Administrador), chave ou token em resposta, tela, log ou auditoria.
5. Camunda lento ou fora **não** derruba o detalhe; consultas limitadas, com tempo limite e cache; nada gravado no Oracle.
6. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s8.md` atualizados.
