# 15 — Tramitação

Módulo `tramitacao` do Rastro (S7 em diante): dado um requerimento, mostrar **em que etapa ele está agora, desde quando e com quem**, em linguagem que não exige conhecer o Camunda. O Rastro só **lê e mostra**: o SIGA segue na análise e na decisão.

O serviço é usado em **processos jurídicos**: dado cruzado errado é pior do que dado ausente. Onde a fonte é ambígua, o Rastro **diz que não sabe** (selo de aviso ou "Etapa não mapeada"), em vez de adivinhar.

Escopo por sprint: **S7** posição atual; **S8** histórico, "com quem esteve em cada etapa" e fluxograma; **S9** taxas, DAR e pagamentos. Prompt da S7: [`prompts/S7-tramitacao-onde-esta-e-com-quem.md`](prompts/S7-tramitacao-onde-esta-e-com-quem.md); progresso em [`progresso/s7.md`](progresso/s7.md).

## Decisões da S7

Registradas em [09](09-decisoes.md) (D82).

- **Fonte da posição = Camunda.** A situação do Licenciamento (`REQUERIMENTO.SITUACAO_PROCESSO`) só confirma e alimenta o plano B (S8). Divergências conhecidas viram **selos de aviso** (bloco 5), nunca correção silenciosa.
- **Catálogo de etapas em arquivo versionado, não no código** (seção abaixo).
- **Etapa atual pode ser mais de uma** (emissões em paralelo, vários analistas, complementação paralela). Mostra-se a etapa **principal** (a de menor `ordem` entre as ativas do ramo principal) e, ao lado, o selo **"+N em paralelo"** com a lista. A complementação é sempre listada à parte ("Complementação em andamento").
- **Responsável:** tarefa com `ASSIGNEE_` → nome do servidor (`SERVIDOR`, por CPF); tarefa de grupo → **área** pelo arquivo de grupos (bloco 4); tarefa do interessado → "Interessado (cadastrante)"; sistema → "Sistema". **CPF nunca sai.** O nome do servidor aparece conforme `RASTRO_MOSTRAR_NOME_RESPONSAVEL` (padrão ligado **no protótipo**; a regra definitiva depende de aprovação da SEMA, P32 em [10](10-pendencias-e-riscos.md)).
- **"Verificação da revisão" = concluído/arquivado** (regra técnica 9): etapa exibida com o selo "Processo concluído".
- **Leitura direta por `PROC_INST_ID_`**, sem tabela de apoio. **Cache curto em memória** (TTL `RASTRO_POSICAO_CACHE_SEGUNDOS`, padrão 30; 0 desliga). Tabela de apoio fica para o painel do Gestor.
- **Camunda indisponível ou lento não derruba o Rastro:** a posição responde `INDISPONIVEL` (com a situação do Licenciamento, se houver) e o resto do detalhe continua.
- **Em memória** onde houver estado (cache, auditoria); nada é gravado no Oracle.

## Catálogo de etapas

O vocabulário é o do usuário ("Análise do processo", não `taskAnalisarRequerimento`). As etapas simplificadas e os ids técnicos ficam em YAML, **um arquivo por modelo**, em `api/src/main/resources/rastro/etapas/` (`hidrico.yml`, `lac.yml`), lidos uma vez na subida por `tramitacao.infrastructure.catalogo.CatalogoDeEtapasEmArquivo`.

### Modelo de domínio (`tramitacao.domain`)

| Tipo | Conteúdo |
|---|---|
| `Etapa` | `id` estável (minúsculas, dígitos e sublinhado), `rotulo`, `ordem` (única no ramo), `tipo`, `responsavel` (papel), `ramo`, `multiInstancia`, `idsTecnicos` (`ACT_ID_`/`TASK_DEF_KEY_`), `processosFilhos` (chave de definição das filhas de `callActivity`) e `semPassagemPor` (condição de percurso) |
| `TipoDeEtapa` | `TAREFA_HUMANA`, `ESPERA`, `AUTOMATICA`, `EMISSAO`, `FINAL` (este só nos finais) |
| `PapelDoResponsavel` | `INTERESSADO`, `ANALISTA`, `CHEFIA`, `GRUPO`, `SISTEMA` (o papel no desenho; a pessoa ou área é resolvida no bloco 4) |
| `Ramo` | `PRINCIPAL`, `CORRECAO` |
| `FinalDoFluxo` | evento de fim: `id`, `rotulo`, `idTecnico` (`END_ACT_ID_`) |
| `IdIgnorado` | atividade que **nunca vira etapa**: `idTecnico`, `descricao`, `motivo` (`SUPORTE`, `RUIDO`, `INATIVO`). É a "marca ignorada" do prompt: em vez de uma etapa marcada, o id fica numa lista própria, para que nunca possa ser devolvido como etapa |
| `ModeloDeFluxo` | chave do Camunda, rótulo, `instancias` (valores de `TIPO_PROCESSO.INSTANCIA_REQUERIMENTO`), etapas, finais e ignorados; só existe válido (`ModeloDeFluxo.criar` recusa conteúdo com problema) |
| `ResolucaoDeEtapa` | resultado de resolver um id: `NaEtapa`, `Encerrado`, `Ignorado`, `NaoMapeado` ("Etapa não mapeada"), `NaoSuportado` ("Fluxo ainda não suportado pelo Rastro"). **Nunca lança** |
| `CatalogoDeEtapas` | porta: `modelos()`, `modelo(chave)`, `modeloDaInstancia(instancia)`, `versao()` (resumo SHA-256 dos arquivos, 12 hex; entra na chave do cache do bloco 4) e `resolver(chave, idTecnico, percorridos)` |

### Formato do arquivo

```yaml
chave: procOutorgaAguaSupSub          # PROC_DEF_KEY_ (nunca PROC_DEF_ID_)
rotulo: Outorga de recursos hídricos
instancias: [RECURSO_HIDRICO]         # TIPO_PROCESSO.INSTANCIA_REQUERIMENTO que usam este modelo
etapas:
  - id: analise_do_processo
    rotulo: Análise do processo
    ordem: 8
    ramo: PRINCIPAL                   # opcional; padrão PRINCIPAL
    tipo: TAREFA_HUMANA
    responsavel: ANALISTA
    multiInstancia: true              # opcional; padrão false; só em TAREFA_HUMANA
    idsTecnicos: [taskAnalisarRequerimento]
    processosFilhos: []               # opcional
    semPassagemPor: []                # opcional: a etapa só vale sem passagem por estes ids
finais:
  - {id: concluido, rotulo: Concluído, idTecnico: Event_1wiuc48}
ignorados:
  - {idTecnico: Activity_0miwhxt, descricao: Suporte (grupo suporte), motivo: SUPORTE}
```

O arquivo é lido com o construtor seguro do SnakeYAML (o YAML não instancia classes) e **campo desconhecido é erro** (erro de digitação não passa calado).

### Validação na subida

Qualquer problema derruba a subida com **todos** os problemas numa só mensagem (`ModeloDeFluxoInvalido`, com o nome do arquivo): catálogo errado contamina toda posição mostrada.

- id técnico em **no máximo um** lugar do modelo (etapa, final ou ignorado; repetido na mesma etapa também é erro);
- `ordem` maior que zero e **única por ramo**;
- todo final com rótulo e id técnico;
- modelo sem etapa, sem final, sem chave ou sem rótulo;
- id estável fora do padrão ou repetido (etapas e finais);
- etapa com tipo `FINAL`, sem tipo, sem papel, sem id técnico nem processo filho; multi-instância fora de tarefa humana;
- processo filho em mais de uma etapa;
- condição `semPassagemPor` sobre id técnico que o modelo não conhece;
- entre arquivos: chave do Camunda ou instância do requerimento repetida; nenhum arquivo encontrado.

### Resolução em tempo de execução

- **Id técnico fora do catálogo não é erro**: resolve para `NaoMapeado` ("Etapa não mapeada"; o id técnico só aparece para o Administrador, bloco 6).
- **Chave de modelo desconhecida** (Trifásico, LAS, que não estão em produção) resolve para `NaoSuportado`: "Fluxo ainda não suportado pelo Rastro", sem erro. O mesmo vale para `modeloDaInstancia` com instância desconhecida.
- **Ids ignorados** resolvem para `Ignorado`, nunca para etapa.
- **Condição de percurso:** "Isenção confirmada" (`taskGerarNumeroProtocolo`) só vale quando a instância **não** passou pelo gateway de pagamento. Com passagem, o Rastro não inventa rótulo: resolve para `NaoMapeado` (rótulo da geração de protocolo depois do pagamento **NÃO CONFIRMADO**; decidir com o responsável).
- **Instâncias filhas** de emissão resolvem pela chave de definição (`resolverProcessoFilho`).

### Conteúdo inicial (fonte: levantamento 2)

`docs/referencias/bpmn/` **não existe** no repositório: os ids abaixo **não foram conferidos contra os arquivos BPMN** na S7 (bloco 1). Conferir quando o BPMN for versionado ou a cada publicação (R68).

**Hídrico (`procOutorgaAguaSupSub`, instância `RECURSO_HIDRICO`)**

| ordem | etapa (`id`) | ids técnicos | tipo | papel |
|---|---|---|---|---|
| 1 | Cadastro do requerimento (`cadastro_do_requerimento`) | `taskCadastrarRequerimento` | tarefa humana | interessado |
| 2 | Conferência dos documentos (`conferencia_dos_documentos`) | `taskConferirDocumentosApresentados` | tarefa humana | grupo |
| 3 | Emissão da taxa (`emissao_da_taxa`) | `taskAtualizarStatusRequerimentoArrecadacao`, `taskAtualizarStatusRequerimentoEmissaoTaxa1`, `taskEmitirTaxaSefaz` | automática | sistema |
| 4 | Aguardando pagamento (`aguardando_pagamento`) | `gtwAguardarPagamento` | espera | interessado |
| 5 | Isenção confirmada (`isencao_confirmada`) | `taskGerarNumeroProtocolo` (só sem passagem por `gtwAguardarPagamento`) | automática | sistema |
| 6 | Atribuição ao analista (`atribuicao_ao_analista`) | `taskAtribuirRequerimento`, `taskMontarListaAnalistas` | tarefa humana | grupo |
| 7 | Aceite pelo analista (`aceite_pelo_analista`) | `taskAceitarAtribuicao` | tarefa humana, multi-instância | analista |
| 8 | Análise do processo (`analise_do_processo`) | `taskAnalisarRequerimento` | tarefa humana, multi-instância | analista |
| 9 | Revisão do parecer técnico (`revisao_do_parecer_tecnico`) | `taskRevisarParecerTecnico` | tarefa humana | chefia |
| 10 | Validação do parecer técnico (`validacao_do_parecer_tecnico`) | `taskValidarParecerTecnico`, `taskAtualizarStatusParecerAssinado` | tarefa humana, multi-instância | analista |
| 11 | Emissão do ato (`emissao_do_ato`) | `taskEmitirAutorizacoes`, `taskEmitirOutrosAtos`, `taskEmitirCertificadoCadastros`, `taskEmitirPortarias`; filhas `procSurhEmitirAutorizacoes`, `procSurhEmitirOutrosAtos`, `procSurhCertificadoCadastro`, `procSurhEmissaoPortaria` | emissão | grupo (signatários nas filhas) |
| 12 | Registro do comprovante de publicação (`registro_do_comprovante_de_publicacao`) | `taskRegistarComprovantePublicacao` (grafia do levantamento), `taskUpdateStatusConcluido`, `taskInformarTitulacoesDisponiveis` | tarefa humana | grupo |
| 13 | Verificação da revisão do processo (`verificacao_da_revisao_do_processo`) | `taskVerificarRevisaoProcesso` | tarefa humana | grupo |
| C1 | Correção do requerimento (`correcao_do_requerimento`) | `taskAtualizarStatusAguardandoRevisao`, `taskCorrecaoRequerimento` | tarefa humana | interessado |
| C2 | Emissão da taxa (correção) (`emissao_da_taxa_correcao`) | `taskEmitirTaxaSefazCorrecao` | automática | sistema |
| C3 | Aguardando pagamento (correção) (`aguardando_pagamento_correcao`) | `gtwAguardarPagamentoRevisao` | espera | interessado |

Finais: `Event_1wiuc48` Concluído; `Event_1kq2k0z` Cancelado pelo cadastrante; `Event_0abm55w` Expirado no cadastro; `Event_0h1oige` Encerrado por taxa não paga no prazo (encerra **sem atualizar a situação**; aviso no bloco 5).

Ignorados: `Gateway_aguardando_emtx` (aguardando emissão de taxa, ruído), `Activity_0miwhxt` (Suporte, `SUPORTE`), `taskAnalisarReducaoTaxa` (ramo provavelmente inativo). **"Report Kafka" e os eventos de repetição por erro de API não têm id técnico levantado (NÃO CONFIRMADO)**: até entrarem na lista, aparecem como "Etapa não mapeada".

**LAC (`procLicenciamentoLac`, instância `LICENCIAMENTO_ADESAO`)**

| ordem | etapa (`id`) | ids técnicos | tipo | papel |
|---|---|---|---|---|
| 1 | Cadastro do requerimento (`cadastro_do_requerimento`) | `taskCadastrarRequerimento` | tarefa humana | interessado |
| 2 | Conferência dos documentos (`conferencia_dos_documentos`) | `taskConferirDocumentosApresentados` | tarefa humana | grupo (e conferidor) |
| 3 | Emissão da taxa (`emissao_da_taxa`) | `taskAtualizarStatusEncParaArrecadacao`, `taskAtualizarStatusAguardEmissaoTaxa`, `taskEmitirTaxaSefaz` | automática | sistema |
| 4 | Aguardando pagamento (`aguardando_pagamento`) | `gwtAguardarPagtoOuVencto` | espera | interessado |
| 5 | Isenção confirmada (`isencao_confirmada`) | `taskGerarNumeroProtocolo` (só sem passagem por `gwtAguardarPagtoOuVencto`) | automática | sistema |
| 6 | Termo de adesão gerado (`termo_de_adesao_gerado`) | `taskGerarTermoAdesaoLAC` | automática | sistema |
| 7 | Envio do termo assinado (`envio_do_termo_assinado`) | `taskEnviarTermoAdesao` | tarefa humana | interessado |
| 8 | Conclusão (`conclusao`) | `taskUpdateStatusConcluido`, `taskEnviarEmailTitulacoes` | automática | sistema |
| C1 | Correção do requerimento (`correcao_do_requerimento`) | `taskCorrecaoRequerimento` | tarefa humana | interessado |

Finais: `evtProcessoConcluido` Concluído; `evtFimRequerimentoAPedidoCadastrante` Cancelado pelo cadastrante; `evtEncerrarRequerimentoPrazoCadExpirado` Expirado no cadastro; `evtEncerrarProcessoPrazoCorrecaoExpirado` Expirado na correção; `evtEncerrarProcessoFaltaPagto` Cancelado por falta de pagamento; `Event_0x7hz1p` Arquivado (termo não enviado no prazo). No LAC, reprovar na conferência leva à correção e **volta direto à conferência, sem nova taxa**.

Ignorados do LAC: nenhum listado pelo levantamento 2 (a tarefa "Suporte" foi observada no Hídrico; se existir no LAC, **NÃO CONFIRMADO**).

As lacunas acima (Report Kafka e repetições, Suporte no LAC, protocolo depois do pagamento, grafia de `taskRegistarComprovantePublicacao`) estão reunidas na pendência P33 de [10](10-pendencias-e-riscos.md).

### Como alterar o catálogo

1. Edite o YAML do modelo (ou crie um arquivo novo para um modelo novo, com `chave` e `instancias` próprias).
2. Rode `./mvnw -Dtest=CatalogoDeEtapasEmArquivoTest test`: o teste confere cada id da tabela acima; acrescente a linha nova nos `@CsvSource`.
3. Atualize as tabelas desta seção. Mudar o conteúdo muda `versao()`, e o cache da posição deixa de servir o resultado antigo.
4. Se o BPMN trocar um id numa nova versão, **mantenha o id antigo** na mesma etapa (instâncias antigas continuam nele) e acrescente o novo (R68).

## Regras técnicas do Camunda e do Licenciamento

Levantamentos 1 e 2, confirmadas em homologação e produção. Valem para todo SQL do módulo: quem as quebra gera dado errado em processo jurídico.

1. **Ligação processo ↔ Camunda:** não há coluna com o id da instância no SIGA. A ligação é `BUSINESS_KEY_ = TO_CHAR(REQUERIMENTO.ID)` na **execução raiz** (`ACT_RU_EXECUTION` com `PARENT_ID_ IS NULL` e `ID_ = PROC_INST_ID_`; no histórico, `ACT_HI_PROCINST`). **Nunca** `TO_NUMBER(BUSINESS_KEY_)` (há valores não numéricos: `ORA-01722`).
2. **Sempre filtrar pela chave da definição** (`ACT_RE_PROCDEF.KEY_` ou `ACT_HI_PROCINST.PROC_DEF_KEY_`): `procOutorgaAguaSupSub` (Hídrico) e `procLicenciamentoLac` (LAC). Os ids de requerimento, de complementação e de título **colidem** como `BUSINESS_KEY_`. **Nunca** filtrar por `PROC_DEF_ID_` (há até 63 versões do Hídrico).
3. **Escopo:** Hídrico e LAC. Trifásico/LAS não estão em produção: o requerimento desses tipos recebe "fluxo ainda não suportado pelo Rastro", sem erro.
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

**Dados pessoais:** `ASSIGNEE_`, vínculos de identidade e variáveis do Camunda guardam **CPF e nome**. O CPF **nunca** sai em resposta, log, erro, evento de auditoria, CSV ou tela, **nem parcialmente mascarado**. Variáveis do Camunda só podem ser lidas de uma **lista fixa** (lista branca) sem dado pessoal.

## Ambiente de verificação: Camunda e Licenciamento fictícios (bloco 2)

**Mecanismo:** o mesmo dos testes de integração desde a S1, o recorte do SIGA no **Oracle 23 Free por Testcontainers** (`SigaOracleTeste`, scripts `api/src/test/resources/sql/siga/01` a `06`). Não há um segundo mecanismo. O `compose.verificacao.yaml` continua **sem** SIGA (as variáveis externas vazias, D34): os cenários existem só no Oracle de teste (R66, R69). O "perfil `verificacao`" do prompt é este: ao subir, o `SigaOracleTeste` roda `06-camunda-s7.sql` e grava todos os cenários (`CenariosFicticiosDaS7.carregar`); os testes dos blocos 3 a 7 consultam por id.

- **DDL mínimo** (`06-camunda-s7.sql`): colunas novas em `ACT_RE_PROCDEF`, `ACT_RU_EXECUTION` e `ACT_RU_TASK` (já existiam na S1); tabelas novas `ACT_RU_IDENTITYLINK`, `ACT_RU_EVENT_SUBSCR`, `ACT_HI_PROCINST`, `ACT_HI_ACTINST`, `ACT_HI_TASKINST`, `ACT_HI_VARINST`, `ACT_HI_DETAIL` (só estrutura nas duas últimas), em `NVARCHAR2` como no Camunda 7 (obriga o `TO_CHAR` dos dois lados, regra 12); no Licenciamento, `TIPO_PROCESSO.INSTANCIA_REQUERIMENTO`, `SERVIDOR.MATRICULA`/`CARGO` e `COMPLEMENTACAO.DATA_INICIO`/`DATA_ENTREGA`/`SITUACAO`. Grants de `SELECT` ao usuário de leitura. Hipóteses em P34.
- **`CamundaFicticioBuilder`** (`api/src/test/java/.../integration/siga/`): API fluente (`processo(HIDRICO, id).naEtapa(...).comAnalistas(3).concluidos(2).desde(data).gravar()`, `.esperandoPagamento()`, `.comFilhas(...)`, `.encerradoEm(...)`, `.comTarefaDeSuporte()`, `.comComplementacao(id)`, `.percorrendo(...)`, `.semInstancia()`, `.paraGrupo(...)`, `.doInteressado()`, `.atribuidoA(...)`). Lê o **catálogo real** pela porta `CatalogoDeEtapas` (`CatalogoDeEtapasDeTeste.padrao()`): id fora do catálogo, final que não é final, filha de outro modelo e cenário incoerente (isenção com passagem pelo gateway de pagamento, multi-instância em etapa simples) são recusados antes de gravar. Grava `REQUERIMENTO`, `ACT_HI_PROCINST` (sempre, ativa ou encerrada), a execução raiz com `BUSINESS_KEY_` (as execuções filhas não levam business key), tarefas, grupos candidatos, assinatura de evento, filhas com `SUPER_EXEC_`/`SUPER_PROCESS_INSTANCE_ID_` e o histórico mínimo (`ACT_HI_ACTINST`/`ACT_HI_TASKINST` do que está aberto, do percurso dado e das tarefas concluídas do ciclo). Instância `fict-<id>`; filhas `fict-<id>-filha-<n>` com business key `<id>*10+n` (id do título); complementação `fict-<id>-compl`.
- **Dados fixos:** tipos de processo 71 (`RECURSO_HIDRICO`), 72 (`LICENCIAMENTO_ADESAO`) e 73 (`LICENCIAMENTO_TRIFASICO_FICTICIO`, fora do catálogo); interessado "Interessado Fictício S7" (pessoa 7000); "Servidor Exemplo 01" a "12" (o 04 é "Chefia Fictícia"). CPFs `000000000NN` (NN de 01 a 12, 91 do interessado e 99 sem servidor): **inválidos de propósito**, provado por `DadosFicticiosDaS7Test`.

### Cenários (ids 7001 a 7199; número `<id>/2026`)

| Cenário | Id | Fluxo | Situação do Licenciamento | O que está ativo (ou o final) | Detalhe |
|---|---|---|---|---|---|
| H01 | 7001 | Hídrico | `EM_CADASTRO` | `taskCadastrarRequerimento` | interessado (`ASSIGNEE_` = CPF do cadastrante) |
| H02 | 7002 | Hídrico | `EM_CONFERENCIA` | `taskConferirDocumentosApresentados` | grupo `grpSurhConferirDocumentoSubterranea` |
| H03 | 7003 | Hídrico | `AGUARDANDO_PAGAMENTO_DE_TAXAS` | execução em `gtwAguardarPagamento` + 1 assinatura | sem tarefa |
| H04 | 7004 | Hídrico | `AGUARDANDO_ATRIBUICAO` | execução em `taskGerarNumeroProtocolo` | isento: **sem** `gtwAguardarPagamento` no histórico |
| H05 | 7005 | Hídrico | `AGUARDANDO_ATRIBUICAO` | `taskAtribuirRequerimento` | grupo `grpSurhAtribuirRequerimentoSuperficial` |
| H06 | 7006 | Hídrico | `AGUARDANDO_ACEITE_ATRIBUICAO` | `taskAceitarAtribuicao` (1 ativa) | multi-instância: 2 de 3 concluídos (servidores 01 a 03) |
| H07 | 7007 | Hídrico | `EM_ANALISE` | `taskAnalisarRequerimento` × 3 | servidores 01 a 03; processo `7900/2022` |
| H08 | 7008 | Hídrico | `EM_ANALISE` | `taskRevisarParecerTecnico` | chefia (servidor 04) |
| H09 | 7009 | Hídrico | `EM_ANALISE` | `taskValidarParecerTecnico` × 3 | signatários: servidores 05 a 07 |
| H10 | 7010 | Hídrico | `EM_ANALISE` | `callActivity` `taskEmitirAutorizacoes` e `taskEmitirPortarias` | **duas filhas** em paralelo (`procSurhEmitirAutorizacoes`, `procSurhEmissaoPortaria`), grupo `GrpProcSurhEmissorTitulos` |
| H11 | 7011 | Hídrico | `AGUARDANDO_PUBLICACAO` | `taskRegistarComprovantePublicacao` | grupo `grpSurhRegistrarPublicacaoBarramento` |
| H12 | 7012 | Hídrico | `CONCLUIDO` | `taskVerificarRevisaoProcesso` | arquivamento (regra 9); grupo `grpSurhRevisarDecisaoMultidisciplinar` |
| H13 | 7013 | Hídrico | `EM_CORRECAO` | `taskCorrecaoRequerimento` | interessado |
| H14 | 7014 | Hídrico | `AGUARDANDO_PAGAMENTO_DE_TAXAS` | execução em `gtwAguardarPagamentoRevisao` + 1 assinatura | ramo correção |
| H15 | 7015 | Hídrico | `CONCLUIDO` | final `Event_1wiuc48` | processo `7900/2022` |
| H16 | 7016 | Hídrico | `CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE` | final `Event_1kq2k0z` | |
| H17 | 7017 | Hídrico | `CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO` | final `Event_0abm55w` | |
| H18 | 7018 | Hídrico | `AGUARDANDO_PAGAMENTO_DE_TAXAS` | final `Event_0h1oige` | taxa vencida, situação não atualizada (regra 8) |
| H19 | 7019 | Hídrico | `EM_CONFERENCIA` | só `Activity_0miwhxt` (grupo `suporte`) | percurso: cadastro e conferência |
| H20 | 7020 | Hídrico | `EM_ANALISE` | `taskAnalisarRequerimento` (servidor 08) | complementação **7001** em paralelo: mesma business key do H01, separada só pela chave `procGestaoComplementacao` |
| H21 | 7021 | Hídrico | `CONCLUIDO` | — (sem instância) | legado, número `7021/2022`; processo `7900/2022` |
| L01 | 7051 | LAC | `EM_CONFERENCIA` | `taskConferirDocumentosApresentados` | grupo `GrpProcResponsavelAtendimentoLac` |
| L02 | 7052 | LAC | `AGUARDANDO_PAGAMENTO_DE_TAXAS` | execução em `gwtAguardarPagtoOuVencto` + 1 assinatura | |
| L03 | 7053 | LAC | `EM_CORRECAO` | `taskCorrecaoRequerimento` | interessado |
| L04 | 7054 | LAC | `AGUARDANDO_ASSINATURA_LAC` | `taskEnviarTermoAdesao` | interessado |
| L05 | 7055 | LAC | `CONCLUIDO` | final `evtProcessoConcluido` | |
| L06 | 7056 | LAC | `ARQUIVADO_DEFINITIVAMENTE` | final `Event_0x7hz1p` | termo não enviado |
| L07 | 7057 | LAC | `CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO` | final `evtEncerrarProcessoPrazoCorrecaoExpirado` | mesma situação do expirado no cadastro (regra 8) |
| Trifásico | 7091 | fora do catálogo | `EM_ANALISE` | — (sem instância) | `INSTANCIA_REQUERIMENTO` fora do catálogo: "não suportado" |
| G01 | 7101 | Hídrico | `EM_CONFERENCIA` | conferência | grupo desconhecido `grpFicticioDesconhecido` |
| G02 | 7102 | Hídrico | `EM_CONFERENCIA` | conferência | grupo `0` |
| G03 | 7103 | Hídrico | `EM_ANALISE` | revisão do parecer | CPF sem servidor (`Responsável não identificado`) |

As situações são **hipóteses fictícias** coerentes com a lista do Apêndice A de docs/04 (P34); a tabela de compatibilidade situação × etapa é do bloco 5. Como criar um cenário novo: docs/13, "Dados fictícios de teste".

## Dependências do módulo

`tramitacao` não depende de `documento` nem de `dossie` (`ArchitectureTest`); o reuso do que `consulta` oferece é **só por porta**. O domínio não conhece YAML nem Spring (`ArchitectureTest`, regra `dominio_da_tramitacao_sem_yaml`). O SQL contra `SIGA2_CAMUNDA` e `SIGA2_LICENCIAMENTO` fica em `integration.siga` (bloco 3), como todo SQL do SIGA.
