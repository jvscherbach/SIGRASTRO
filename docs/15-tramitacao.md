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
9. **"Verificação da revisão do processo" (`taskVerificarRevisaoProcesso`) funciona como arquivamento:** o processo é considerado **concluído quando "Registrar comprovante de publicação" encerra**, a tarefa nunca é recriada na prática e a instância **fica ativa nela** (grupos `grpSurhRevisarDecisao*`). Não é "revisão em andamento com alguém". **Na posição (P38, D83; S8 bloco 8):** a API devolve `estado: CONCLUIDO` (`PosicaoAtual.concluidaNaVerificacao()`), sem `etapaAtual`, com `final.rotulo = "Concluído"` e a data de entrada na verificação, **sem** evento de fim (sem bola preta) e mantendo o aviso neutro `CONCLUIDO_ANTES_DA_VERIFICACAO`. O domínio (`MontadorDaPosicao`, `PosicaoAtual`) não mudou: a etapa continua `verificacao_da_revisao_do_processo`; a tradução é do contrato HTTP (`PosicaoResponse`). Na tramitação, o mesmo fato é o `DesfechoDoCaminho` `CONCLUIDO` (`final.tipo = CONCLUIDO_NA_VERIFICACAO`).
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

A tabela de **resultado esperado por cenário** (etapa, com quem, avisos) e a conferência na tela estão no roteiro [roteiros/s7-homologacao.md](roteiros/s7-homologacao.md), seção 1 (bloco 11).

As situações são **hipóteses fictícias** coerentes com a lista do Apêndice A de docs/04 (P34); a tabela de compatibilidade situação × etapa é do bloco 5. Como criar um cenário novo: docs/13, "Dados fictícios de teste".

### Cenários do caminho percorrido (S8, bloco 1; ids 8001 a 8199)

Gravados por `CenariosFicticiosDaS8` (mesmo mecanismo: Oracle de teste + `CamundaFicticioBuilder`, script `07-historico-s8.sql` para `HISTORICO_REQUERIMENTO` e as datas de `REQUERIMENTO`). Os cenários da S7 (H01 a H21 etc.) **mantêm** o caminho mínimo e a posição atual; os da S8 trazem o caminho com início e fim reais, em `ACT_HI_ACTINST`, `ACT_HI_TASKINST`, `ACT_HI_PROCINST` (filhas e complementação), `ACT_HI_DETAIL` e `ACT_HI_VARINST` (lista branca). Responsáveis fictícios: conferência 02, atribuição 03, revisão do parecer 04, analistas e validadores 05 a 07, publicação 08, emissão (filhas) 09, verificação 10, suporte 12; cadastrante = interessado fictício. Tempos relativos a 02/03/2026 08:00. **Sequência abaixo = atividades encerradas em ordem; "ativo" = o que ficou aberto.** Legenda: `M(x)` = ciclo multi-instância de `x` (corpo + uma tarefa por participante); "taxável" = emissão da taxa → espera de pagamento → protocolo.

| Cenário | Id | Fluxo | O que prova | Caminho (resumo) | Ativo / final |
|---|---|---|---|---|---|
| HC01 | 8001 | Hídrico | feliz taxável, concluído pela publicação | cadastro, conferência, taxa, pagamento, protocolo, atribuição, M(aceite), M(análise), revisão, M(validação), emissão (autorizações + portaria em paralelo), publicação | verificação da revisão (ativa = arquivado/concluído, regra 9) |
| HC02 | 8002 | Hídrico | isento: sem emissão da taxa nem espera | cadastro, conferência (`isento`), protocolo (isenção confirmada), atribuição, M(aceite) | M(análise) |
| HC03 | 8003 | Hídrico | conferência reprova e volta ao cadastro; `statusConferencia` regravado | cadastro, conferência (`reprovado`), cadastro (2ª), conferência (2ª, `taxavel`), taxa | espera de pagamento |
| HC04 | 8004 | Hídrico | validação devolvida `RA` (volta à análise) e depois `RP` (volta à revisão) | … M(validação) `RA`, M(análise), revisão, M(validação) `RP`, revisão | M(validação) |
| HC05 | 8005 | Hídrico | troca de analista na revisão e na validação: sempre volta à análise | M(análise), revisão (`isRevisaoAnalise`), M(análise), revisão, M(validação) (`isRevisaoAnalise`), M(análise) | revisão |
| HC06 | 8006 | Hídrico | recusa no aceite (2 tarefas `deleted`) e redistribuição | atribuição, M(aceite, recusa), atribuição, M(aceite), M(análise), atribuição (redistribuição) | M(aceite) |
| HC07 | 8007 | Hídrico | correção **com** nova taxa, retorno direto à análise | M(análise, pedido de correção), status da correção, correção, taxa (correção), pagamento (correção) | M(análise) |
| HC08 | 8008 | Hídrico | correção **sem** nova taxa | M(análise, pedido de correção), status da correção, correção | M(análise) |
| HC09 | 8009 | Hídrico | correção expirada segue para a análise (P40) | … correção `deleted`, evento de borda fictício | M(análise) |
| HC10 | 8010 | Hídrico | um analista pede correção; 2 tarefas `deleted` | M(análise: 1 `completed`, 2 `deleted`), status da correção | correção (interessado) |
| HC11 | 8011 | Hídrico | ruído: Suporte, "Report Kafka", script ×4, evento de repetição | cadastro, **Suporte**, conferência, **Report Kafka**, taxa ×4, **repetição por erro** | espera de pagamento |
| HC12 | 8012 | Hídrico | emissão com 3 filhas, 1 devolvida (`isRevisaoParecer`), refeita | … M(validação), emissão (3, 1 devolvida), revisão, M(validação), emissão (3) | publicação |
| HC13 | 8013 | Hídrico | `REVISAR` na verificação recria a emissão | … emissão, publicação, verificação (`REVISAR`), emissão | publicação |
| HC14 | 8014 | Hídrico | complementação paralela concluída durante a análise (id 8814) | … M(análise) com `procGestaoComplementacao` encerrada em paralelo | revisão |
| HC15 | 8015 | Hídrico | milhares de atividades (3.500 repetições do script): passa do limite padrão de 3.000 linhas | cadastro, conferência, taxa ×3.500 | espera de pagamento |
| HF01 a HF04 | 8021 a 8024 | Hídrico | finais com histórico: `Event_1wiuc48` (concluído, depois da verificação), `Event_1kq2k0z`, `Event_0abm55w` (cadastro expirado), `Event_0h1oige` (situação `AGUARDANDO_PAGAMENTO_DE_TAXAS` mantida) | | final |
| LC01 | 8051 | LAC | feliz: conferência, taxa, pagamento, termo, termo assinado, concluído | cadastro, conferência, taxa, pagamento, protocolo, termo, envio do termo, conclusão | final `evtProcessoConcluido` |
| LC02 | 8052 | LAC | isento | cadastro, conferência, protocolo, termo | envio do termo assinado |
| LC03 | 8053 | LAC | reprova, correção, volta à conferência, sem nova taxa na correção | cadastro, conferência, correção, conferência (2ª), taxa | espera de pagamento |
| LF01 a LF05 | 8061 a 8065 | LAC | finais: `evtFimRequerimentoAPedidoCadastrante`, `evtEncerrarRequerimentoPrazoCadExpirado`, `evtEncerrarProcessoPrazoCorrecaoExpirado` (**mesma situação** do expirado no cadastro), `evtEncerrarProcessoFaltaPagto`, `Event_0x7hz1p` | | final |
| PB01 | 8091 | Hídrico (2021) | plano B completo: 11 eventos de `HISTORICO_REQUERIMENTO`, datas de protocolo, `CONCLUIDO`, **sem instância** | `CRIACAO_REQUERIMENTO` … `CONCLUSAO_REQUERIMENTO` | — |
| PB02 | 8092 | Hídrico (2021) | plano B com **evento desconhecido** (`EVENTO_FICTICIO_DESCONHECIDO`), expirado no cadastro | 4 eventos + `DATA_EXPIRACAO_REQUERIMENTO` | — |
| PB03 | 8093 | Hídrico (2021) | plano B **sem nenhum evento** ("Sem detalhamento disponível") | só situação e datas | — |

Regras das fixtures: `HISTORICO_REQUERIMENTO.CPF_USUARIO` existe (a tabela real a tem) com CPF fictício inválido, **para que o bloco 5 e a varredura do bloco 11 provem que o plano B nunca o lê**. Os cenários finais não têm "etapa ativa"; o evento de fim é a última atividade, com `START_TIME_` = `END_TIME_` = fim da instância. Hipóteses não confirmadas: P40. Testes: `CamundaFicticioBuilderCaminhoTest` (a sequência exata de cada cenário), `CamundaFicticioBuilderValidacaoTest` (o que o construtor recusa) e `DadosFicticiosDaS7Test` (nenhum CPF/CNPJ válido).

### Cenários do financeiro (S9, bloco 1; ids 9001 a 9199)

`CenariosFicticiosDaS9`, carregados por `SigaOracleTeste` depois dos da S8 (script `08-financeiro-s9.sql`: `SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA`; **tipos das colunas NÃO CONFIRMADOS**, só a lista de colunas e o enum de `STATUS` vêm do prompt). Número do requerimento `<id>/2026`, DAR fictício `FICT-nnnn` (o construtor recusa outro prefixo), `CHAVE_GED`, `MENSAGEM` e `ID_TITULACAO` **sempre nulos**. "Hoje" dos testes: `HOJE` = 2026-03-20 (Cuiabá) = `dias(18)`; `RELOGIO` fixo em 10h de Cuiabá e `RELOGIO_NA_VIRADA_UTC` (01:30Z do dia seguinte, ainda "hoje" em Cuiabá). A API fluente é `processo.comLancamento(PAGA).comDar("FICT-0001").emitidoEm(...).vencendoEm(...).pagoEm(...)`, e `.comLancamento(...)` encadeia o próximo; também vale para requerimento sem instância e, sem lançamento, o requerimento fica sem taxa.

| Código | Id | Cenário | Lançamentos (válidos em **negrito**) | Camunda |
|---|---|---|---|---|
| FIN01 | 9001 | taxa paga | **`PAGA`**, DAR, emitida dia 2, vence dia 12, paga dia 5 | análise (como o HC01 até aí) |
| FIN02 | 9002 | aguardando, no prazo | **`AGUARDANDO_PAGAMENTO`**, DAR, vence **amanhã** | espera de pagamento |
| FIN03 | 9003 | aguardando **vencido por data** | **`AGUARDANDO_PAGAMENTO`** (o status ainda é aguardando), DAR, venceu **ontem** | espera de pagamento |
| FIN04 | 9004 | já `VENCIDA` | **`VENCIDA`**, DAR | espera de pagamento |
| FIN05 | 9005 | isento | **`ISENTA`** sem DAR, `TAXA_ISENTA = 1`, processado `P` | atribuição (como o HC02) |
| FIN06 | 9006 | isento, processado `N` | **`ISENTA`** sem DAR, processado `N` | atribuição |
| FIN07 | 9007 | cancelada sem DAR + válido | `CANCELADA` sem DAR (**falso**, ignorar) e **`AGUARDANDO_PAGAMENTO`** com DAR | espera de pagamento |
| FIN08 | 9008 | cancelada com DAR | **`CANCELADA`** com DAR e `DATA_FIM` (dia 6) | espera de pagamento |
| FIN09 | 9009 | aguardando emissão | **`AGUARDANDO_EMISSAO`** sem DAR | espera de pagamento |
| FIN10 | 9010 | ruído | `AGUARDANDO_EMISSAO` com `TAXA_ISENTA = 1` (**ignorar**, docs/10) | espera de pagamento |
| FIN11 | 9011 | taxa inicial paga + taxa da correção aguardando | **`PAGA`** (emitida dia 2) e **`AGUARDANDO_PAGAMENTO`** (emitida dia 14, **depois** de entrar na correção no dia 12; `PASSOU_POR_REAPROV_TAXA_CORREC = 1`, só diagnóstico) | espera do pagamento da correção (HC07 parado na espera) |
| FIN12 | 9012 | pago que virou isento na correção | **`PAGA`** com DAR e depois **`ISENTA`** (dia 14); os dois à vista (o destaque é da S11) | espera do pagamento da correção |
| FIN13 | 9013 | sem lançamento | nenhum | espera de pagamento |
| FIN14 | 9014 | volume | 10 `CANCELADA` sem DAR (**falsos**) e **1 `AGUARDANDO_PAGAMENTO`** | espera de pagamento |
| FIN15 | 9015 | o H18 da S7 com taxa vencida por data | **`AGUARDANDO_PAGAMENTO`**, DAR, venceu ontem | **encerrada** em `Event_0h1oige`, situação `AGUARDANDO_PAGAMENTO_DE_TAXAS` |
| FIN16 | 9016 | **sem instância** (plano B) com taxa paga | **`PAGA`**, DAR | nenhuma |
| VENCE_ONTEM, VENCE_HOJE, VENCE_AMANHA | 9101 a 9103 | apoio aos testes de data | **`AGUARDANDO_PAGAMENTO`** vencendo ontem, **hoje** e amanhã | espera de pagamento |

Os `VENCE_*` ficam fora da numeração FIN01 a FIN16 do roteiro. Vencimento gravado à meia-noite (a hora real da coluna é **NÃO CONFIRMADA**; `vencendoEm(LocalDateTime)` grava com hora para provar que a conversão para data não desloca o dia). Testes: `CenariosFicticiosDaS9Test` (Docker: o que cada cenário grava, colunas proibidas nulas, DAR fictício, usuário de leitura sem escrita), `CamundaFicticioBuilderFinanceiroValidacaoTest` (sem Docker: recusa de DAR sem prefixo, relógio de teste) e `DadosFicticiosDaS7Test` (varre os arquivos novos).

## Posição atual (bloco 3)

### Peças

| Peça | Onde | Papel |
|---|---|---|
| `ConsultarPosicaoAtual` | `tramitacao.application` | caso de uso: acesso pelo contrato de `consulta` (`VerificarAcessoAoRequerimento`: 404 e RN07, 403) **antes** de tudo; tipo do requerimento → modelo do catálogo (`modeloDaInstancia`); fora do catálogo = `NAO_SUPORTADO` sem ir ao Camunda; qualquer falha depois do acesso (conexão, tempo limite) = `INDISPONIVEL`, nunca 5xx. O log da falha leva só o id do requerimento e a classe da exceção (o texto do driver pode trazer parâmetros) |
| `PosicaoNoFluxo` | `tramitacao.application.port` | porta: `requerimento(id)` (instância e situação) e `ler(id, chaveDoModelo)` (fatos crus) |
| `CamundaPosicaoJdbc` | `integration.siga` | **único** componente com o SQL da posição (`resources/sql/siga/tramitacao/*.sql`); não interpreta nada |
| `LeituraDoFluxo` | `tramitacao.domain` | fatos crus: instâncias raiz, execuções, tarefas (com grupos), filhas, complementações, atividades percorridas |
| `MontadorDaPosicao` | `tramitacao.domain` | função pura que transforma a leitura em `PosicaoAtual` pelo catálogo; testada sem banco (`MontadorDaPosicaoTest`) |
| `PosicaoAtual`, `ElementoDaPosicao` | `tramitacao.domain` | estado, principal, em paralelo, complementação, final, "desde", multi-instância, marcas e ids não mapeados |
| `IdentificadorDoResponsavel` | `tramitacao.domain` | `ASSIGNEE_` (CPF) protegido: `toString()` não mostra o valor; só o bloco 4 o lê, para consultar `SERVIDOR` |

A situação do Licenciamento é lida (`RequerimentoNoFluxo.situacao`) e **não é usada** aqui: o confronto é do bloco 5.

### Consultas (no máximo 7 por requerimento)

| # | Arquivo | Quando | O que lê |
|---|---|---|---|
| 1 | `requerimento.sql` | sempre | `TIPO_PROCESSO.INSTANCIA_REQUERIMENTO` e `REQUERIMENTO.SITUACAO_PROCESSO`, por chave primária |
| 2 | `instancias.sql` | tipo suportado | `ACT_HI_PROCINST` com `BUSINESS_KEY_ = TO_CHAR(:id)`, `PROC_DEF_KEY_ = :chave` e sem instância-mãe, da mais recente para a mais antiga (até 10) |
| 3 | `execucoes.sql` | instância ativa | `ACT_RU_EXECUTION` com `ACT_ID_`, o início da atividade aberta (`ACT_HI_ACTINST`) e a assinatura de evento (`ACT_RU_EVENT_SUBSCR`) |
| 4 | `filhas.sql` | instância ativa | filhas ativas pela instância-mãe (`SUPER_EXEC_` aponta para uma execução da mãe), chave por `ACT_RE_PROCDEF.KEY_` |
| 5 | `tarefas.sql` | instância ativa | `ACT_RU_TASK` da raiz e das filhas (lista curta em `IN`), com os grupos `candidate` de `ACT_RU_IDENTITYLINK` |
| 6 | `complementacoes.sql` | instância ativa | `COMPLEMENTACAO` do requerimento → instância `procGestaoComplementacao` ativa com `BUSINESS_KEY_ = TO_CHAR(COMPLEMENTACAO.ID)` |
| 7 | `percorridas.sql` | instância ativa | `ACT_HI_ACTINST` encerradas (tarefa só se `DELETE_REASON_ = 'completed'` em `ACT_HI_TASKINST`), até 500, da mais recente |

Sem instância ou encerrada: 2 consultas; tipo não suportado: 1. O bloco 4 acrescenta no máximo 1 (`responsaveis.sql`), só quando há `ASSIGNEE_` com cara de CPF: teto de **8**. Não há consulta por linha (provado por `CamundaPosicaoJdbcTest`, que conta os comandos preparados: 7 no H10 e no H07, 2 no H15, 1 no Trifásico). Todas por `PROC_INST_ID_` ou chave primária, exceto a 2 e a 6 (pela `BUSINESS_KEY_`, como na S1).

- **Tempo limite:** `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS` (padrão 10) em cada consulta, num `JdbcTemplate` próprio (não muda as demais consultas do SIGA); estourado, a posição fica `INDISPONIVEL`. O `SIGA_DB_READ_TIMEOUT_MS` do driver continua valendo por baixo. O bloco 10 leva o parâmetro para a tela.
- **`TO_CHAR` e índices:** nas comparações entre coluna `NVARCHAR2` do Camunda e valor `VARCHAR2`/número, o `TO_CHAR` fica do lado do **valor** (parâmetro ou `COMPLEMENTACAO.ID`), e a coluna fica sem função, para não perder o índice de `BUSINESS_KEY_` (a igualdade entre `NVARCHAR2` e `VARCHAR2` não gera `ORA-12704`; o erro aparece ao misturar os dois em `CASE`, `COALESCE` ou `UNION`, que estas consultas não fazem). Em `percorridas.sql`, sem índice envolvido, o `TO_CHAR` está nos dois lados.
- **Filhas por `SUPER_EXEC_`** (regra 5 aceita `SUPER_PROCESS_INSTANCE_ID_` ou `SUPER_EXEC_`): pelo runtime só vêm as ativas, e a coluna é chave estrangeira no Camunda 7 (índice **NÃO CONFIRMADO** no SIGA).

### Regras do montador

1. **Instância:** nenhuma = `SEM_INSTANCIA` (plano B na S8); mais de uma = vale a mais recente por início, com a marca `VARIAS_INSTANCIAS`.
2. **Encerrada:** o final pelo `END_ACT_ID_`; final fora do catálogo = `ENCERRADO` sem final, com `ETAPA_NAO_MAPEADA`. `STATE_` e a existência de `DELETE_REASON_` (não o texto) ficam num diagnóstico interno, que não vai para a resposta.
3. **Ativa:** cada ponto ativo vira um `ElementoDaPosicao`:
   - **tarefas da raiz**, agrupadas por id técnico; etapa multi-instância vira **um** ponto com "x de y"; "desde" = criação da tarefa, marcado como **"desde a última entrada nesta etapa"** (a tarefa é recriada a cada volta);
   - **cada filha** de emissão é um ponto próprio (duas filhas = principal + "+1 em paralelo"), com as tarefas da filha (signatários) e "desde" = início da filha;
   - **execuções** paradas fora de tarefa: espera só se **confirmada** por assinatura de evento ("desde" = criação da assinatura); sem assinatura, o Rastro não supõe e mostra "Etapa não mapeada". Automática parada: "desde" = início da atividade aberta. A chamada (`callActivity`) já mostrada pela filha e a execução de uma tarefa já listada não duplicam;
   - id **ignorado** de ruído some; id **desconhecido** vira ponto "Etapa não mapeada" (`idsNaoMapeados`, só para o Administrador).
4. **Principal:** a de menor ordem do ramo principal; sem ela, a de menor ordem da correção; sem nenhuma mapeada, a não mapeada mais antiga. Empate de ordem: o mais antigo, e depois a ordem da leitura (que o SQL fixa). O resto vai para "em paralelo". A complementação fica **à parte** (quantidade e desde quando).
5. **Suporte** (`Activity_0miwhxt`): nunca é posição. Sem outro ponto mapeado, a principal é a **última etapa real percorrida** (a encerrada mais recente que está no catálogo e não é ignorada), **sem "desde"** (não se sabe desde quando ela está parada); `emSuporteDesde` guarda o início da tarefa de suporte. Sempre com a marca `EM_SUPORTE`.
6. **Multi-instância:** o ciclo atual começa no corpo aberto `<tarefa>#multiInstanceBody`; concluídas = tarefas do mesmo id técnico com `DELETE_REASON_ = 'completed'` iniciadas a partir do ciclo; total = concluídas + ativas. Sem o corpo (ciclo desconhecido), só as ativas: o "y" **não** é inventado.
7. **Condição de percurso** do catálogo (isenção) usa as atividades percorridas.
8. Instância ativa sem nenhum ponto: principal vazia, com `ETAPA_NAO_MAPEADA`.

**NÃO CONFIRMADO** (conferir na homologação com o Camunda real, P35): (a) que a execução parada no gateway por evento tem `ACT_ID_` do gateway e a assinatura ligada a ela (o montador não usa `IS_ACTIVE_`, justamente para não depender disso); (b) que o corpo da multi-instância aparece como execução `<tarefa>#multiInstanceBody` e como atividade aberta no histórico; (c) que 500 atividades percorridas bastam para a condição de percurso e a última etapa real em processos longos (R70).

## Responsável: com quem está (bloco 4)

### Peças

| Peça | Onde | Papel |
|---|---|---|
| `RegraDoResponsavel` | `tramitacao.domain` | função pura: o tipo de responsável de cada ponto (tabela abaixo) |
| `ResponsavelDaEtapa` | `tramitacao.domain` | tipo, papel, área, nome (opcional), participantes (concluiu/pendente) e "e mais N"; `texto()` = nome, senão área, senão papel |
| `IdentificacaoDoResponsavel` | `tramitacao.domain` | o que o SIGA diz de um `ASSIGNEE_`: nome em `SERVIDOR` e se é o interessado; **sem o CPF** |
| `CatalogoDeGrupos` | `tramitacao.domain` (porta) | id do grupo → área; implementado por `CatalogoDeGruposEmArquivo` (`resources/rastro/grupos.yaml`, validado na subida) |
| `IdentificacaoDosResponsaveis` | `tramitacao.application.port` | uma consulta por requerimento; implementada em `CamundaPosicaoJdbc` (`tramitacao/responsaveis.sql`) |
| `ResolverResponsavel` | `tramitacao.application` | junta os `ASSIGNEE_` de todos os pontos, consulta uma vez, aplica a regra e **tira os identificadores (CPF) da posição** |
| `ParametrosDoRastro` | `tramitacao.application.port` | nome do responsável, limite de nomes e cache; ligado em `config.ParametrosDoRastroDoAmbiente` |
| `CacheDaPosicao` | `tramitacao.application.port` | cache curto em memória (`infrastructure/memoria/CacheDaPosicaoEmMemoria`) |

### Regra

| Situação do ponto | Responsável mostrado |
|---|---|
| Tarefa com `ASSIGNEE_` = CPF do interessado do requerimento (`REQUERIMENTO.ID_PESSOA`) | "Interessado (cadastrante)" (vale em qualquer etapa) |
| Tarefa com `ASSIGNEE_` = CPF que está em `SERVIDOR` | nome do servidor (versão mais recente por CPF), com o papel da etapa ("Analista", "Chefia"...) e a área do grupo, se houver |
| `ASSIGNEE_` sem correspondência, ou que não é CPF de 11 dígitos | "Responsável não identificado" (nunca o valor cru) |
| Tarefa de grupo sem `ASSIGNEE_` | a área do grupo (`grupos.yaml`); grupo desconhecido ou `0`: "Equipe responsável" |
| Multi-instância, ou mais de uma tarefa no ponto | lista de participantes (os que concluíram o ciclo e os pendentes), com nome ou papel |
| Ponto sem tarefa | pelo papel da etapa no catálogo: espera de pagamento e etapas do interessado, "Interessado (cadastrante)"; automática, "Sistema"; etapa de grupo sem tarefa ativa (ex.: última etapa real no suporte), "Equipe responsável"; não mapeada sem tarefa, "Sistema" |

A espera de pagamento segue o **papel do catálogo** (interessado), e não "gateway = Sistema": quem precisa agir é o interessado, como na tabela do bloco 1.

- **Uma consulta por requerimento:** só os `ASSIGNEE_` com exatamente 11 dígitos, sem repetição, até 500, num único `IN`. Junta `SERVIDOR` (nome) e a `PESSOA` do requerimento (interessado). Sem nenhum CPF, a consulta não é feita. Teto da leitura: **8 consultas** (7 da posição + 1).
- **Falha na consulta** não derruba a posição: os servidores ficam "Responsável não identificado", e o log registra só o id do requerimento e a classe da exceção (o texto do driver pode trazer os parâmetros, que são CPFs; provado por teste com CPF fictício na mensagem).
- **Depois da resolução** os `ASSIGNEE_` saem das tarefas e das concluídas (`ElementoDaPosicao.comResponsavel`): nada a jusante (cache, resposta, auditoria, log) recebe o CPF. O identificador é um tipo próprio cujo `toString()` não mostra o valor.

### Parâmetros (classe "imediato": valem na próxima leitura)

| Variável | Padrão | Efeito |
|---|---|---|
| `RASTRO_MOSTRAR_NOME_RESPONSAVEL` | `true` (protótipo) | desligado, só a área e o papel, sem nome, também nos participantes. Regra definitiva com a SEMA (P32) |
| `RASTRO_MAX_NOMES_POR_EQUIPE` | 10 (1 a 100) | participantes listados; o resto vira "e mais N" (`maisParticipantes`) |
| `RASTRO_POSICAO_CACHE_SEGUNDOS` | 30 (0 a 3600; 0 desliga) | validade da posição em cache |

O cache guarda a posição **já resolvida e sem CPF**, com a chave **id do requerimento + versão do catálogo** (`CatalogoDeEtapas.versao()`), nunca o usuário nem CPF. O acesso (RN07) é conferido a cada chamada, **antes** do cache. `INDISPONIVEL` não entra no cache. Nome e limite de nomes são aplicados **depois** do cache, por isso mudam na próxima leitura mesmo com a posição guardada. Teto de 2.000 entradas (acima dele saem as vencidas e, se preciso, todas).

### Grupos (`resources/rastro/grupos.yaml`)

Os 22 grupos reais da regra 13 (com as variantes subterrânea, superficial, barramento e multidisciplinar expandidas), cada um com um rótulo de área em linguagem de negócio. Validado na subida: id repetido, rótulo vazio, id `0` (reservado) e campo desconhecido derrubam a subida. Os rótulos são uma **proposta** (P36): conferir com a área.

## Avisos: situação do Licenciamento × fluxo (bloco 5)

A **etapa do fluxo sempre prevalece** sobre a situação do Licenciamento (exceto sem instância, cujo plano B é da S8). O confronto (`tramitacao.domain.Reconciliar`, função pura) recebe a posição e a `SITUACAO_PROCESSO` e devolve `PosicaoConfrontada`: a **mesma** posição (o mesmo objeto; provado por teste em todos os cenários), a situação ao lado e uma lista de avisos com **código estável**. Nada é corrigido em silêncio: a situação mostrada nunca é trocada.

### Catálogo de avisos (`resources/rastro/confronto.yaml`)

| Código | Gravidade | Quando | Texto ao usuário |
|---|---|---|---|
| `FLUXO_ENCERRADO_SEM_STATUS` | ATENÇÃO | Hídrico encerrado em `Event_0h1oige` (taxa não paga) com a situação `AGUARDANDO_PAGAMENTO_DE_TAXAS` (bug de origem, regra 8) | O fluxo foi encerrado por taxa não paga no prazo, mas o SIGA ainda mostra "aguardando pagamento". |
| `CONCLUIDO_ANTES_DA_VERIFICACAO` | INFO | situação `CONCLUIDO` com a instância ativa em "Verificação da revisão" (regra 9) | Processo concluído; a verificação da revisão é só arquivamento. |
| `SITUACAO_NAO_CONFERE` | ATENÇÃO | situação fora das aceitas para a etapa principal (ativa) ou para o final (encerrada) | A situação do SIGA ('X') não corresponde exatamente à etapa do fluxo. Vale a etapa do fluxo. |
| `FINAL_E_SITUACAO_DIVERGEM` | ATENÇÃO | **S8, bloco 4:** situação fora das aceitas para o **final** (encerrada); antes era `SITUACAO_NAO_CONFERE`, que agora vale só para a etapa ativa | A situação do SIGA ('X') não corresponde ao encerramento do fluxo. Vale o encerramento do fluxo. |
| `SITUACAO_ARQUIVADO_TEMPORARIAMENTE` | INFO | **S8, bloco 4:** situação `ARQUIVADO_TEMPORARIAMENTE`, que nada no fluxo grava (regra 8); vence o "não confere" | O SIGA mostra "arquivado temporariamente", situação que o fluxo não registra. Vale a etapa do fluxo. |
| `EXPIRADO_NA_CORRECAO_LAC` | INFO | LAC encerrado em `evtEncerrarProcessoPrazoCorrecaoExpirado` com a situação do expirado no cadastro (regra 8) | O prazo da correção expirou. O SIGA mostra a mesma situação do prazo de cadastro expirado; vale o final do fluxo. |
| `VARIAS_INSTANCIAS` | ATENÇÃO | mais de uma instância raiz (vale a mais recente) | Há mais de um fluxo para este requerimento; vale o mais recente. |
| `EM_SUPORTE` | INFO | tarefa "Suporte" ativa | Em atendimento de suporte técnico. |
| `ETAPA_NAO_MAPEADA` | ATENÇÃO | ponto ativo ou final fora do catálogo | O fluxo está num ponto que o Rastro ainda não sabe nomear. |
| `SEM_INSTANCIA` | INFO | requerimento sem instância no Camunda | Histórico detalhado indisponível para este requerimento. |
| `HISTORICO_APROXIMADO` | INFO | **S8, bloco 5:** a tramitação veio do plano B | Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo |
| `HISTORICO_TRUNCADO` | ATENCAO | **S8, bloco 6:** alguma instância passou do limite de linhas; o caminho é o que coube | Histórico incompleto: este requerimento tem registros demais e só parte do caminho é mostrada. |

O selo mostra o **texto** (não só cor ou ícone). `{situacao}` no texto é trocado pelo valor da situação. O "Taxa vencida" por data é da S9, não daqui.

### Regras do confronto

1. Ativa: confronta a situação com a **etapa principal** mapeada; encerrada: com o **final**. Sem instância, não suportado, indisponível, principal não mapeada ou situação ausente: **sem confronto** (não se acusa divergência sem base).
2. Se a situação está entre as **divergências conhecidas** daquela etapa ou final (`conhecidas`), sai o aviso próprio (ele vence o "não confere").
3. Senão, situação `ARQUIVADO_TEMPORARIAMENTE` (S8): `SITUACAO_ARQUIVADO_TEMPORARIAMENTE` (INFO), também fora de qualquer tabela.
4. Senão, se a etapa ou o final tem lista de situações **aceitas** e a situação não está nela: `SITUACAO_NAO_CONFERE` (etapa) ou `FINAL_E_SITUACAO_DIVERGEM` (final; S8, bloco 4).
5. As marcas da leitura (`VARIAS_INSTANCIAS`, `EM_SUPORTE`, `ETAPA_NAO_MAPEADA`, `SEM_INSTANCIA`) viram os avisos de mesmo nome, depois dos do confronto.
6. `INDISPONIVEL` leva a situação, quando ela já foi lida antes da falha.

### Tabela de compatibilidade

Em `confronto.yaml`, por chave do modelo, cada etapa e cada final com `aceitas` (e `conhecidas`, quando há divergência documentada). `AGUARDANDO_EMISSAO_DE_TAXAS` é aceita em **duas** etapas do Hídrico (taxa inicial e taxa da correção). A lista das 29 situações (docs/04, Apêndice A) fica no próprio arquivo, e a subida falha com situação desconhecida, código de aviso desconhecido ou sem texto, modelo, etapa ou final inexistentes no catálogo de etapas, divergência apontando para aviso que não é de divergência, ou campo desconhecido. Todas as etapas e finais dos dois modelos têm entrada (teste). A tabela é uma **hipótese** montada a partir das regras técnicas e dos nomes das situações: **NÃO CONFIRMADA** (P37), conferir na homologação.

Quando o SIGA corrigir o bug de origem da taxa vencida, a situação passa a `CANCELADO_POR_FALTA_DE_PAGAMENTO` (aceita no final) e o aviso `FLUXO_ENCERRADO_SEM_STATUS` **desaparece sozinho**, sem mudança no Rastro.

## API e auditoria (bloco 6)

`GET /requerimentos/{id}/posicao` e `GET /processos/{numero}/posicao` (número com hífen: `7900-2022`); contrato, campos só do Administrador e erros em [docs/07, "Tramitação"](07-api.md#tramitação-s7). O caso de uso `AbrirPosicao` confere o acesso (requerimento pelo `VerificarAcessoAoRequerimento`; processo pela `BuscarRequerimentos` da consulta, uma vez), lê pelo `ConsultarPosicaoAtual`, aplica o **RN12** ao responsável (Operador sem nome) e registra o evento **`TRAMITACAO`** (grupo `DOCUMENTOS`; só usuário e números). Os ids técnicos só saem para o Administrador (`PoliticaDeAcesso.podeVerIdsTecnicosDoFluxo`). `INDISPONIVEL` é 200.

### Tramitação completa (S8, bloco 6)

`GET /requerimentos/{id}/tramitacao` e `GET /processos/{numero}/tramitacao` (contrato em [docs/07](07-api.md#tramitação-completa-s8-bloco-6)). O caso de uso `AbrirTramitacao` (`tramitacao.application`) é o par do `AbrirPosicao`: confere o acesso **antes** de montar (404, RN07 403; o processo, uma vez pela busca da consulta), chama o `MontarTramitacao.montar(id)`, aplica o **RN12** (`CaminhoReconstruido.semNomes()` para o Operador: nome some das passagens e dos participantes) e registra um evento **`TRAMITACAO`** por requerimento devolvido, com o detalhe `histórico <rótulo> <número>` (sem responsável, CPF nem id técnico). Acesso negado não registra; `INDISPONIVEL` registra.

- **DTO** (`TramitacaoResponse`): passagens (etapa com id estável e rótulo, ciclo, ramo, entrada, saída, duração, atual, em paralelo, selo "processo concluído", motivo da volta com código e texto, `responsaveis[]` ou `participantes[]` na multi-instância, `atos[]`), complementações, taxa pela rota, final (`FINAL` = bola preta; `CONCLUIDO_NA_VERIFICACAO` = sem bola), avisos, `planoB` e `truncado`. O plano B **não tem campo de responsável**.
- **Só para o Administrador** (campos que **não existem** para os demais): `passagens[].idTecnico`, `passagens[].repeticoes`, `final.idTecnico`, `idsNaoMapeados`, `diagnosticos`. A chave do modelo (`procOutorgaAguaSupSub`) nunca sai: o campo `modelo` é o rótulo.
- **Aviso novo `HISTORICO_TRUNCADO`** (`ATENCAO`, texto em `confronto.yaml`): o `MontarTramitacao` o acrescenta quando o limite de linhas por instância foi atingido (`CaminhoReconstruido.truncado()`).
- **Fora deste bloco:** cache da tramitação, tempo total, limite de concorrência e teste de carga (S8, bloco 7: seção "Desempenho, limites, cache e degradação"); matriz de acesso estendida com varredura de vazamento por cenário (bloco 11). A matriz por perfil das duas rotas já está no `MatrizDeAcessoTest`.

## Acesso, vazamento e resiliência (bloco 7)

### Matriz de acesso

As duas rotas seguem a **regra vigente da consulta** (RN07): o Operador só vê a posição dos requerimentos em que é o interessado (403 `NAO_INTERESSADO` nos outros, 404 no inexistente, conferidos **antes** de ler o Camunda e sem registrar evento); Gestor (`OPERADOR_SIGILO`) e Administrador (`ADMIN`) veem todos; sem sessão, 401. No processo, o filtro do interessado é o da busca por número de processo. Exibição: nome do servidor só para Gestor e Administrador (RN12) e conforme o parâmetro; ids técnicos só para o Administrador. Provado por `MatrizDeAcessoTest` (rotas por perfil) e `TramitacaoHttpTest` (o que sai para cada perfil). O prompt cita `OPERADOR`, `OPERADOR_SIGILO` e `ADMINISTRADOR`: no projeto são `OPERADOR`, `OPERADOR_SIGILO` (rótulo "Gestor") e `ADMIN`.

### Varredura de vazamento ponta a ponta

**S8, bloco 11 (tramitação completa):** as mesmas varreduras passaram a cobrir `GET /api/requerimentos/{id}/tramitacao` e `GET /api/processos/{numero}/tramitacao`, com o serviço inteiro contra o Oracle de teste, em **todos** os requerimentos fictícios das duas faixas (S7, 7000 a 7299; S8, 8000 a 8299: HC01 a HC15, HF01 a HF04, LC01 a LC03, LF01 a LF05, PB01 a PB03 e os criados pelos testes) × Operador, Gestor e Administrador, e o log. Zero CPF (solto, com e sem máscara, incluindo o `CPF_USUARIO` fictício do plano B), grupo técnico, chave, token ou coluna do Camunda; ids técnicos e os campos `idTecnico`, `repeticoes`, `idsNaoMapeados` e `diagnosticos` só para o Administrador; nenhum nome de servidor para o Operador; plano B com `aproximado` e sem nenhum responsável (`VarreduraDaTramitacaoHttpTest`). Com `RASTRO_MOSTRAR_NOME_RESPONSAVEL` desligado, nenhum nome em passagens, participantes ou signatários, para nenhum perfil, e o papel continua (`VarreduraSemNomeDoResponsavelHttpTest`). O plano B é provado também pelo SQL de fato emitido ao Oracle (`PlanoBSemResponsavelTest`: nunca `SERVIDOR`, `PESSOA`, CPF nem `ASSIGNEE_`; o caminho do Camunda, ao contrário, consulta `SERVIDOR` para o nome, o que mostra que a captura funciona) e pelo contrato `HistoricoAproximadoResponse` (nenhum campo de responsável, nome ou CPF). A matriz de acesso das rotas por perfil e sem login é a do bloco 6 (`MatrizDeAcessoTest`, `TramitacaoCompletaHttpTest`).

**S7, bloco 7 (posição):**

`VarreduraDaTramitacaoHttpTest` sobe o **serviço inteiro** com o datasource do SIGA apontado para o Oracle de teste (usuário de leitura; Testcontainers, nenhum sistema real) e chama as duas rotas para **todos** os requerimentos fictícios (7001 a 7103 e 7200 a 7205, hoje 37) com os **três perfis**. Resposta e log são varridos:

- **CPF**: 11 dígitos soltos, com máscara ou mascarado parcialmente (`***.123.456-**`); no log, os CPFs fictícios dos cenários, o CNPJ do Operador e os nomes dos servidores;
- **ids técnicos** (`task*`, `gtw*`, `gwt*`, `evt*`, `proc*`, `Event_*`, `Activity_*`, `Gateway_*`, `#multiInstanceBody`) para quem não é Administrador;
- **grupos técnicos** (`grp*`/`Grp*`), **chave de arquivo**, **token**, `ASSIGNEE_`, `STATE_`, para todos;
- nome de servidor para o Operador.

Zero ocorrências, nenhum 5xx; e a varredura não é vazia (o Gestor vê o nome, o Administrador vê os ids). `VarreduraSemNomeDoResponsavelHttpTest` (**teste de negação**) repete tudo com `RASTRO_MOSTRAR_NOME_RESPONSAVEL=false`: nenhum `"nome":"…"` nem "Servidor Exemplo" em nenhuma resposta; ficam o papel e a área. O requerimento 7205 (análise com 3 analistas, interessado = o do Operador de demonstração) é criado pelo próprio teste para cobrir a visão do Operador.

### Resiliência

- **Camunda fora do ar** ou **tempo limite estourado**: a posição responde **200 com `INDISPONIVEL`** (com o modelo e a situação, se já lidos), nunca 5xx, e o **detalhe do requerimento** (`GET /requerimentos/{id}`) segue normal (`ResilienciaDaTramitacaoHttpTest`, com o caso de uso real e só a leitura do Camunda trocada).
- **Consulta lenta não segura a thread além do limite:** toda consulta da posição e dos responsáveis recebe `setQueryTimeout(RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS)` (provado por proxy no `CamundaPosicaoJdbcTest`: 8 consultas, 8 tempos limite); o driver Oracle cancela a consulta (`ORA-01013`) e o caso de uso devolve `INDISPONIVEL`. Um servidor que **aceita a conexão e não responde** devolve a thread em menos de 5 s (tempo de conexão do pool; teste com um socket mudo local).
- Sem SIGA configurado, cada porta tem o seu substituto (`PosicaoNaoConfigurada`, `ResponsaveisNaoConfigurados`): uma classe por porta, para que trocar uma por dublê nos testes não leve a outra.

## Caminho percorrido: leitura do histórico (S8, bloco 2)

Só leitura **crua**: a interpretação (etapas, ciclos, voltas, finais) é do bloco 3 em diante.

| Peça | Onde | Papel |
|---|---|---|
| `CaminhoPercorrido` | `tramitacao.application.port` | porta: `lerCaminho(idRequerimento, chaveDoModelo)` devolve `CaminhoBruto`; falha de banco sai como exceção (quem chama transforma em `INDISPONIVEL`; o plano B **nunca** substitui uma falha) |
| `CamundaPosicaoJdbc` | `integration.siga` | o **mesmo** componente de acesso da posição (R04); implementa a nova porta |
| `CaminhoBruto` | `tramitacao.domain` | `instancias` (raiz, filhas, complementações), `instanciasRaiz`, `atividades` ordenadas, `variaveisDaEpoca`, `variaveisFinais`; `semInstancia()`, `raiz()`, `truncado()` |
| `InstanciaDoHistorico` | `tramitacao.domain` | uma linha de `ACT_HI_PROCINST`: origem (`RAIZ`, `FILHA`, `COMPLEMENTACAO`), chave, início, fim, `END_ACT_ID_`, instância-mãe, `truncada` e quantas atividades vieram |
| `AtividadeBruta` | `tramitacao.domain` | uma linha de `ACT_HI_ACTINST` com a tarefa de `ACT_HI_TASKINST` (`TarefaBruta`): ids, tipo, início, fim, `ASSIGNEE_` protegido, `CALL_PROC_INST_ID_` e o motivo da tarefa (`ABERTA`, `CONCLUIDA`, `EXCLUIDA`, `OUTRO`; o texto de `DELETE_REASON_` não sai) |
| `VariavelBruta` | `tramitacao.domain` | variável da **lista branca** (`PERMITIDAS`: as seis do prompt); outro nome é recusado, e valor com cara de CPF ou com mais de 100 caracteres é descartado |

### Consultas (no máximo 6 por requerimento; 1 sem instância)

| # | Arquivo | O que lê |
|---|---|---|
| 1 | `instancias.sql` (a mesma da posição) | raiz mais recente (e quantas raízes há) |
| 2 | `historico-filhas.sql` | `ACT_HI_PROCINST` com `SUPER_PROCESS_INSTANCE_ID_` = raiz, encerradas ou não (até 50) |
| 3 | `historico-complementacoes.sql` | `COMPLEMENTACAO` do requerimento → `ACT_HI_PROCINST` `procGestaoComplementacao`, encerradas ou não (até 20) |
| 4 | `historico-atividades.sql` | `ACT_HI_ACTINST` + `ACT_HI_TASKINST` (chave primária) de **todas** as instâncias, uma consulta, `IN` por `PROC_INST_ID_`, ordenada por início e id |
| 5 | `historico-variaveis-epoca.sql` | `ACT_HI_DETAIL` (`VariableUpdate`), só os nomes da lista branca, até 2.000 |
| 6 | `historico-variaveis-finais.sql` | `ACT_HI_VARINST`, só os nomes da lista branca, até 200 |

Sem laço por linha (provado por `CaminhoPercorridoJdbcTest`, que conta os comandos preparados: 6 em HC01, HC12, HC14 e HC15; 1 nos PB). Parâmetros nomeados, `TO_CHAR` onde há `NVARCHAR2` em `CASE` (regra 12), nenhum `TO_NUMBER`. Tempo limite por consulta: o mesmo `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS`.

- **Ordem:** `START_TIME_` e id da atividade (o id carrega um contador crescente por instância, então o empate de início sai na ordem de gravação). Ruído, repetições e tarefas encerradas sem ação **não são removidos aqui**.
- **Ordem:** `START_TIME_` e id da atividade (o id carrega um contador crescente por instância, então o empate de início sai na ordem de gravação). Ruído e tarefas encerradas sem ação **não são removidos aqui**. **Desde o bloco 7**, repetições seguidas do mesmo `ACT_ID_` encerradas e sem tarefa (script repetido por erro) são **colapsadas no SQL** em primeira e última linha, com `AtividadeBruta.representa` dizendo quantas linhas cada uma vale (a reconstrução soma em `repeticoes`).
- **Limite por instância:** `RASTRO_TRAMITACAO_LIMITE_LINHAS` (propriedade `rastro.tramitacao.limite-de-linhas`, padrão **3.000**), contado **depois do colapso** (bloco 7). A janela é `ROW_NUMBER() OVER (PARTITION BY PROC_INST_ID_ ...)`; a linha 3.001 só indica o excesso e é descartada. Se ainda passar, a instância fica `truncada` e vem **com as primeiras** atividades, na ordem de gravação, e a tramitação sai com o aviso `HISTORICO_TRUNCADO`. O HC15 (3.500 repetições) cabe depois do colapso. Teto teórico de linhas: 71 instâncias (1 raiz + 50 filhas + 20 complementações) × limite.
- **Variáveis:** lidas da raiz **e das filhas** (a variável de decisão pode ficar numa filha: NÃO CONFIRMADO, P40). `TEXT_` e `LONG_` vêm separados: o Camunda guarda booleano em `LONG_` (0 ou 1), enquanto as fixtures da S8 gravam o texto `true`; o bloco 3 interpreta e confere (P40).
- **NÃO CONFIRMADO (P40):** índice em `ACT_HI_PROCINST(SUPER_PROCESS_INSTANCE_ID_)` (se faltar, as consultas 2 são varreduras: recomendação de índice em P42); que `PARENT_ACT_INST_ID_` aponte para o corpo da multi-instância (as fixtures não o preenchem; o bloco 3 agrupa pelo corpo e, se faltar, por proximidade); o valor de `LONG_` dos booleanos reais.

## Reconstrução do caminho (S8, bloco 3)

`ReconstruirCaminho` (`tramitacao.domain`, função pura, sem banco) transforma o `CaminhoBruto` do bloco 2, com o modelo do catálogo e a identificação dos responsáveis (a mesma porta da S7, `IdentificacaoDosResponsaveis`; `ReconstruirCaminho.paraConsultar` lista os CPFs), em `CaminhoReconstruido`: lista ordenada de `PassagemPelaEtapa`, complementações ao lado, taxa do ramo principal e da correção, ids não mapeados, contagem de ignorados e diagnósticos. O final (bola preta) é do bloco 4; o plano B, do bloco 5.

| Peça | Conteúdo |
|---|---|
| `PassagemPelaEtapa` | etapa (vazia = "Etapa não mapeada"), `ciclo`, `ramo`, `entrada`, `saida` (vazia = atual), `duracao()`, `responsaveis[]`, `multiInstancia`, `atos[]`, `motivoDaVolta`, `emParalelo`, `repeticoes` (diagnóstico), `processoConcluido` (selo da verificação da revisão); `semNomes()` aplica o parâmetro de nome |
| `ResponsavelDaPassagem` | tipo, papel, nome (nunca CPF), início, fim e estado: `CONCLUIU`, `PENDENTE`, `ENCERRADA_SEM_ACAO` |
| `AtoDaEmissao` | tipo (autorização, outros atos, certificado, portaria, não mapeado), início, fim, estado (`EMITIDO`, `NAO_CONCLUIDO`, `EM_ANDAMENTO`, `INDETERMINADO`); `ocorreu()` = emitido |
| `MotivosDaVolta` / `MotivoDaVolta` | regras explícitas do motivo (tabela abaixo) |

### Regras

1. **Percorrida** = atividade com fim e, se tarefa, `completed`. Tarefa `deleted` ou com outro motivo **não** vira passagem (no ciclo multi-instância, ela aparece como participante "encerrada sem ação"). A atividade aberta é a passagem atual.
2. **Ruído:** id ignorado pelo catálogo (Suporte, `Gateway_aguardando_emtx`, `taskAnalisarReducaoTaxa`) sai e é contado. Id fora do catálogo: se for **tarefa humana**, vira passagem "Etapa não mapeada"; senão (scripts, eventos, gateways, "Report Kafka", repetição por erro) sai do caminho e vai para `idsNaoMapeados` (só Administrador). O protocolo depois do pagamento (`taskGerarNumeroProtocolo` com passagem pela espera) fica nesse grupo: o rótulo dele continua NÃO CONFIRMADO (P33).
3. **Repetições consecutivas** da mesma etapa viram uma passagem (`repeticoes` conta quantas atividades); por isso scripts repetidos por erro e os vários ids de uma etapa automática somem numa caixa só. **Duas tarefas humanas seguidas da mesma etapa** continuam duas passagens (a segunda é uma volta real).
4. **Multi-instância:** uma passagem por ciclo, entre o início e o fim do corpo `<tarefa>#multiInstanceBody`; tarefas do mesmo id iniciadas dentro dele são os participantes. Sem corpo, as tarefas seguidas da etapa formam o ciclo. Ciclo encerrado sem nenhuma tarefa concluída não é percorrido.
5. **Emissão:** `callActivity` seguidas da etapa "Emissão do ato" e as filhas ligadas por `CALL_PROC_INST_ID_` viram **uma** passagem, com um ato por filha; os responsáveis são os signatários das tarefas das filhas ("Signatário"). Filha sem a chamada lida (raiz truncada) vira emissão própria, pela data. Filha que termina sem `COMPLETED` (devolvida, P40) fica `NAO_CONCLUIDO`. O tipo do ato é pela chave do processo filho ou da chamada (as do levantamento 2, em `AtoDaEmissao.Tipo`).
6. **Ciclo** = n-ésima passagem pela etapa (contagem por etapa, na ordem). **Em paralelo** = a passagem se sobrepõe no tempo (estritamente) a outra passagem ou a uma complementação.
7. **Taxável × isento pela rota:** passou (ou está) numa espera do ramo principal = `TAXAVEL`; senão, passou por "Isenção confirmada" = `ISENTA`; senão `INDETERMINADA`. Correção: espera própria = `TAXAVEL`, sem ela = `SEM_NOVA_TAXA`. O último `statusConferencia` da época que seja `isento` ou `taxavel` só confirma; se discordar, a rota vence e sai `ROTA_DIVERGE_DO_STATUS` (só no diagnóstico).
8. **Ramo da correção** vem do catálogo (C1 a C3). "Correção expirada" no Hídrico: a tarefa de correção `deleted` não conta, a passagem da correção fica com o que foi percorrido (o script de status) e o fluxo segue para a análise; não é final.
9. **Verificação da revisão do processo** é passagem normal, com `processoConcluido = true` (regra técnica 9).

### Motivos da volta

O motivo só existe a partir do 2º ciclo. A regra olha a passagem **imediatamente anterior** e o **valor da época** das variáveis da lista branca entre a entrada da anterior e a entrada da volta (inclusive). Sem a rota e a variável exigidas, ou com duas decisões disputando a mesma volta: **sem motivo**.

| Volta para | Passagem anterior | Exige | Motivo (`MotivoDaVolta`) |
|---|---|---|---|
| qualquer etapa do ramo principal | do ramo da correção | — | `CORRECAO_DO_REQUERIMENTO` ("depois da correção do requerimento") |
| Cadastro | Conferência | `statusConferencia` = `reprovado` | `REPROVADO_NA_CONFERENCIA` |
| Análise | Validação | `decisaoValidacaoParecerTecnico` = `RA` **ou** `isRevisaoAnalise` verdadeiro (não os dois) | `DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE` ou `TROCA_DE_ANALISTA` |
| Análise | Revisão do parecer | `isRevisaoAnalise` verdadeiro, sem `RA` | `TROCA_DE_ANALISTA` |
| Revisão do parecer | Validação | `decisaoValidacaoParecerTecnico` = `RP`, sem troca | `DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO` |
| Revisão do parecer | Emissão do ato | `isRevisaoParecer` verdadeiro | `DEVOLVIDO_NA_EMISSAO` |
| Emissão do ato | Verificação da revisão | `decisaoVerificarRevisao` = `REVISAR` | `REVISAR_NA_VERIFICACAO` |
| Atribuição | Aceite | algum participante do aceite "encerrada sem ação" | `RECUSA_NO_ACEITE` |
| Atribuição | Análise | — | `REDISTRIBUICAO` |

"Verdadeiro" = `true` (texto) ou `1` (`LONG_`). Os valores comparam sem diferença de maiúsculas.

**Conferência das hipóteses do P40 (bloco 3):** o BPMN e os levantamentos **não estão no repositório**, então nenhuma hipótese pôde ser confirmada aqui. Por isso: (a) os valores esperados das variáveis ficam num único lugar (`MotivosDaVolta`) e são **NÃO CONFIRMADOS**; (b) toda regra baseada em decisão exige rota **e** variável; (c) as duas regras só de rota (recusa no aceite, redistribuição) e a correção vêm do prompt (cenários HC06 e HC07 a HC09); (d) a correção expirada (P40 b) e a devolução da emissão como `INTERNALLY_TERMINATED` (P40 e) só mudam o estado mostrado, nunca criam passagem. Valor diferente na homologação = motivo vazio (não um motivo errado).

Testes: `ReconstruirCaminhoComFixturesTest` (Docker: sequência exata de HC01 a HC15 e LC01 a LC03, motivos, emissão, correção, taxa, complementação, limite; propriedades em todos os HC, HF, LC e LF: nenhuma etapa futura, ordem por entrada, no máximo uma passagem atual, nenhum CPF) e `ReconstruirCaminhoTest` (sem Docker: ciclo sem corpo, repetições, tarefa excluída, etapa não mapeada, motivo vazio sem variável, decisões em conflito, variável fora da janela, booleano numérico, rota × status, isenção, parâmetro de nome).

## Finais e divergências do final (S8, bloco 4)

`AvaliarDesfecho` (`tramitacao.domain`, função pura) recebe o caminho cru e reconstruído, o modelo e a situação do Licenciamento e devolve `DesfechoDoCaminho`: estado (`SEM_INSTANCIA`, `EM_ANDAMENTO`, `CONCLUIDO`, `ENCERRADO`), final do catálogo, quando e avisos. O confronto reusa `Reconciliar` (mesmo arquivo `confronto.yaml`), agora com `confrontarEtapa` e `confrontarFinal` públicos.

| Situação do caminho | Estado | Bola preta | Rótulo | Avisos |
|---|---|---|---|---|
| Instância encerrada com `END_ACT_ID_` no catálogo | `ENCERRADO` | sim | o do **evento de fim** (nunca da situação) | divergência conhecida do final, senão `FINAL_E_SITUACAO_DIVERGEM` fora das aceitas |
| Encerrada com evento fora do catálogo | `ENCERRADO` | não | — | `ETAPA_NAO_MAPEADA` |
| Ativa na verificação da revisão (regra 9) | `CONCLUIDO` (desde a entrada na verificação, que é o fim da publicação) | não | "Concluído" | `CONCLUIDO_ANTES_DA_VERIFICACAO` sempre; `SITUACAO_NAO_CONFERE` se a situação não é `CONCLUIDO` |
| Ativa em outra etapa | `EM_ANDAMENTO` | não | — | (o confronto da etapa atual é o da posição) |
| Sem instância | `SEM_INSTANCIA` | não | — | `SEM_INSTANCIA` (plano B, bloco 5) |

- **Hídrico, taxa vencida (`Event_0h1oige`):** a situação fica como o Licenciamento diz (`AGUARDANDO_PAGAMENTO_DE_TAXAS`), o rótulo é "Encerrado por taxa não paga no prazo" e sai `FLUXO_ENCERRADO_SEM_STATUS`. "Taxa vencida" por data é da S9.
- **LAC, expirado na correção:** mesma situação do expirado no cadastro; o rótulo vem do evento ("Expirado na correção") e sai `EXPIRADO_NA_CORRECAO_LAC`.
- **`ARQUIVADO_TEMPORARIAMENTE`:** em qualquer estado, aviso `INFO` neutro, no lugar do "não confere".
- **Sem situação lida:** nenhuma divergência é acusada.
- **Rótulo do LAC `Event_0x7hz1p`:** o catálogo diz "Arquivado (termo não enviado no prazo)" (homologado na S7); o prompt da S8 lista "Arquivado". Mantido o do catálogo; o bloco 9 decide se a caixa usa a forma curta (decisão do responsável).
- **Mudança de contrato da posição (S7):** um final com situação fora da tabela passa a dar `FINAL_E_SITUACAO_DIVERGEM` em vez de `SITUACAO_NAO_CONFERE` (o front não trata os códigos um a um: mostra o texto). A P38 (verificação = status "Concluído" na posição) **não** foi feita neste bloco.

Testes: `AvaliarDesfechoTest` (sem Docker: todos os finais do Hídrico e do LAC, mesma situação com rótulos diferentes, combinação divergente, evento não mapeado, sem situação, arquivado temporariamente, verificação, em andamento, sem instância), `ReconciliarTest` (final divergente, arquivado temporariamente) e `DesfechoComFixturesTest` (Docker: HF01 a HF04, LC01, LF01 a LF05 com a situação gravada, HC01 concluído, em andamento, combinação divergente, PB01).

## Plano B: histórico aproximado (S8, bloco 5)

Para requerimento **sem instância** no Camunda (legado de 2021/2022; de 2023 em diante todo requerimento tem instância). **Dado aproximado nunca pode parecer exato.**

| Peça | Onde | Papel |
|---|---|---|
| `MontarTramitacao` | `tramitacao.application` | escolhe a fonte: tipo fora do catálogo = `NAO_SUPORTADO`; caminho do Camunda; raiz inexistente = `SEM_INSTANCIA` e **só então** o plano B; **qualquer falha** (Camunda, plano B, leitura do requerimento) = `INDISPONIVEL`, **sem** tentar o plano B no lugar. Sem acesso, auditoria nem contrato HTTP (bloco 6), sem cache nem tempo total (bloco 7) |
| `HistoricoDoLicenciamento` | `tramitacao.application.port` | porta do plano B; implementada em `CamundaPosicaoJdbc` (o mesmo componente de acesso, R04) com 2 consultas: `plano-b-requerimento.sql` (datas e situação, por chave primária) e `plano-b-eventos.sql` (`DATA_CADASTRO` e `EVENTO`, até 500; **nunca** `CPF_USUARIO`) |
| `MapaDeEventosDoHistorico` | `tramitacao.domain` (porta) | evento → etapa do modelo ou marco; implementado por `MapaDeEventosEmArquivo` (`resources/rastro/eventos-historico.yaml`, validado na subida contra o catálogo: modelo e etapa existentes, `etapa` ou `marco` e só um, nome em maiúsculas, campo desconhecido é erro) |
| `MontarHistoricoAproximado` | `tramitacao.domain` | função pura que monta `HistoricoAproximado` |
| `HistoricoAproximado` | `tramitacao.domain` | `etapas` (etapa, entrada, saída aproximada, quantos eventos), `marcos` (abertura, protocolo, conclusão, cancelamento, expirações), situação, eventos ignorados e truncados; **sem campo de responsável**; `aproximado()` sempre verdadeiro |
| `TramitacaoDoRequerimento` | `tramitacao.domain` | resultado completo: estado, modelo, situação, caminho e desfecho (Camunda) **ou** plano B, nunca os dois, e os avisos |

Regras:

1. Eventos em ordem de data; cada um vira, pelo mapa, uma etapa, um marco ou nada (desconhecido: ignorado e contado). Eventos seguidos da mesma etapa viram uma etapa; a mesma etapa mais adiante vira outra linha, **sem** número de ciclo nem motivo.
2. **Sem** aceite, revisão e validação separados: `REVISAR_PARECER_TECNICO` e `VALIDAR_PARECER_TECNICO` caem na análise.
3. Saída = entrada da etapa seguinte; a última fecha no primeiro marco de encerramento depois dela (conclusão, cancelamento, expiração), senão fica sem saída.
4. Marcos: as colunas de `REQUERIMENTO` e os eventos `PROTOCOLO` e `CONCLUSAO_REQUERIMENTO`; um por tipo, a coluna vence o evento.
5. Sem nenhuma etapa: "Sem detalhamento disponível" (`HistoricoAproximado.SEM_DETALHAMENTO`), com a situação e as datas.
6. Aviso fixo `HISTORICO_APROXIMADO` (INFO): "Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo" (o mesmo texto de `HistoricoAproximado.SELO`; um teste confere). Sem fluxograma (o front, blocos 9 e 10).
7. **Sem responsável:** o plano B não consulta `SERVIDOR` nem `PESSOA` e não lê `CPF_USUARIO` (as fixtures preenchem essa coluna justamente para provar isso).

Mapa (`eventos-historico.yaml`, hipótese pelo nome, NÃO CONFIRMADO, P41):

| Evento | Hídrico | LAC |
|---|---|---|
| `CRIACAO_REQUERIMENTO` | Cadastro do requerimento | Cadastro do requerimento |
| `AGUARDANDO_CONFERENCIA`, `CHECKLIST_APROVADO`, `CHECKLIST_REPROVADO` | Conferência dos documentos | Conferência dos documentos |
| `AGUARDANDO_PAGAMENTO_TAXA`, `PAGAMENTO_CONFIRMADO` | Aguardando pagamento | Aguardando pagamento |
| `PROTOCOLO` | marco | marco |
| `EM_ANALISE`, `REVISAR_PARECER_TECNICO`, `VALIDAR_PARECER_TECNICO` | Análise do processo | (ignorado) |
| `TITULOS_EMITIDOS` | Emissão do ato | Conclusão |
| `TITULACOES_DISPONIVEIS` | Registro do comprovante de publicação | Conclusão |
| `CONCLUSAO_REQUERIMENTO` | marco | marco |

O `HISTORICO_REQUERIMENTO` não tem índice conhecido em `ID_REQUERIMENTO` (R73): a consulta só roda para requerimento sem instância. Testes: `MontarHistoricoAproximadoTest`, `MapaDeEventosEmArquivoTest`, `MontarTramitacaoTest` (sem Docker: Camunda fora ou lento = `INDISPONIVEL` e o plano B nem é consultado; falha no plano B; não suportado; nome desligado; identificação falhando) e `PlanoBComFixturesTest` (Docker: PB01 a PB03, 2 consultas, nenhum CPF, SQL sem `CPF`, `SERVIDOR`, `PESSOA`).

## Desempenho, limites, cache e degradação (S8, bloco 7)

Protege o Camunda de produção. Tudo em memória, nada gravado no Oracle.

| Peça | Onde | Papel |
|---|---|---|
| `LerTramitacao` | `tramitacao.application` | entre `AbrirTramitacao` (que já conferiu o acesso) e `MontarTramitacao`: cache, uma leitura por requerimento, tempo total e o parâmetro de nome depois do cache |
| `CacheDaTramitacao` / `CacheDaTramitacaoEmMemoria` | porta / `infrastructure/memoria` | chave = id do requerimento + `CatalogoDeEtapas.versao()` (como a posição: mudou o catálogo, as duas deixam de servir o antigo), validade `RASTRO_TRAMITACAO_CACHE_SEGUNDOS`, teto de **500** entradas (acima dele saem as vencidas e, se não bastar, tudo); `INDISPONIVEL` **nunca** entra |
| `LimiteDeLeiturasDoCamunda` | `tramitacao.application` | teto de leituras simultâneas ao Camunda nesta instância (`RASTRO_CAMUNDA_LEITURAS_SIMULTANEAS`), **compartilhado pela posição e pela tramitação** (lista da busca, outros requerimentos do processo, painel); sem vaga dentro da espera = `INDISPONIVEL` |

Fluxo de uma leitura (`LerTramitacao.ler`):

1. **Cache** (se a validade for maior que zero): achou, devolve. O valor guardado tem os nomes; o parâmetro `RASTRO_MOSTRAR_NOME_RESPONSAVEL` é aplicado **depois** (vale na próxima leitura) e o RN12 do Operador depois disso, em `AbrirTramitacao`. O CPF nunca entra no cache (sai na montagem).
2. **Uma leitura por requerimento** (*single flight*): pedidos simultâneos do mesmo requerimento (e mesma versão do catálogo) esperam a mesma leitura, com ou sem cache. A leitura sai do "em andamento" **antes** de entregar o resultado: quem chega depois vai ao cache ou faz a própria leitura (nunca reaproveita um `INDISPONIVEL`).
3. A leitura roda numa thread própria (até 32), com **vaga** no limite de leituras. Quem pediu espera no máximo o **tempo total** (`RASTRO_TRAMITACAO_TIMEOUT_SEGUNDOS`, contando a espera por vaga); estourado, `INDISPONIVEL` na hora. A leitura que ficou para trás termina sozinha (cada consulta tem o próprio tempo limite, `RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS`) e ainda alimenta o cache.
4. Tempo estourado, sem vaga, sem thread livre ou erro: `INDISPONIVEL`; o **plano B nunca substitui** a falha. O log leva só o id e o motivo (nunca a mensagem do driver).

Na **posição** (S7), `ConsultarPosicaoAtual` passa a ler o Camunda com vaga no mesmo limite; a espera é o tempo limite de uma consulta. O resto da posição não muda (cache próprio, `RASTRO_POSICAO_CACHE_SEGUNDOS`).

**Consultas por requerimento (máximo documentado e testado):** tramitação com instância, **8** (tipo e situação 1; caminho 6: instâncias, filhas, complementações, atividades, variáveis da época e finais; responsáveis 1, só se houver CPF); plano B, **4** (tipo 1, instâncias 1, plano B 2); tipo não suportado, **1**. Sem laço por linha. Posição (S7): 8.

**Colapso de repetições antes do limite:** o SQL das atividades (`historico-atividades.sql`) junta, por "ilhas" (diferença entre a numeração geral e a numeração por `ACT_ID_`), as atividades seguidas do mesmo id encerradas e sem tarefa, deixando a primeira e a última; o limite de linhas é contado depois. Tarefas e atividades abertas nunca são colapsadas. Se ainda passar do limite: o que coube, com `HISTORICO_TRUNCADO`.

Testes: `LerTramitacaoTest` (cache e validade, cache desligado, `INDISPONIVEL` fora do cache, versão do catálogo, 20 pedidos simultâneos = 1 leitura, com e sem cache, Camunda lento = `INDISPONIVEL` em menos de 1,8 s com tempo total de 1 s e a leitura tardia alimentando o cache, teto de leituras simultâneas, sem vaga = `INDISPONIVEL`, nome depois do cache), `LimiteDeLeiturasDoCamundaTest`, `CacheDaTramitacaoEmMemoriaTest`, `CaminhoPercorridoJdbcTest` (colapso: HC11 com 2 linhas valendo 4, HC15 com 3.500 repetições sem truncar e com a etapa atual; limite 3 trunca o HC15 depois do colapso) e `CargaDaTramitacaoTest` (Docker, pilha real: 200 leituras em sequência sem falha, sem truncar e com no máximo 8 consultas cada; 20 em paralelo sem passar de 3 conexões abertas ao mesmo tempo com o teto 3; 20 pedidos do mesmo requerimento com cache = uma leitura).

**Recomendações de índice** (só documento: o usuário do Rastro é somente leitura; decisão da equipe do SIGA): ver P42 em [10](10-pendencias-e-riscos.md).

## Roteiro de homologação (S8, bloco 12)

[`roteiros/s8-homologacao.md`](roteiros/s8-homologacao.md): uma linha por cenário (HC01 a HC15, LC01 a LC03, HF01 a HF04, LF01 a LF05, PB01 a PB03), com a sequência esperada da linha do tempo, o final ou estado do fluxograma e a coluna **Tela** (`Não executado` enquanto não houver ambiente com SIGA fictício, R66). A seção 6 é a conferência com dados reais, feita pelo responsável fora do repositório, com marcadores no lugar de números.

As sequências vêm de `ReconstruirCaminhoComFixturesTest` (sequência exata, motivos das voltas) e os finais de `CenariosFicticiosDaS8` e `DesfechoComFixturesTest`; o plano B, de `PlanoBComFixturesTest`. Continuam **NÃO CONFIRMADO** na tela: a linha sem caixas de HF03 e LF02 (cadastro expirado sem etapa percorrida) e a forma de exibir o início de HF01. As hipóteses de P40 e P41 seguem valendo para a homologação.

## Dependências do módulo

`tramitacao` não depende de `documento` nem de `dossie` (`ArchitectureTest`); o reuso do que `consulta` oferece é **só por porta**. O domínio não conhece YAML nem Spring (`ArchitectureTest`, regra `dominio_da_tramitacao_sem_yaml`). O SQL contra `SIGA2_CAMUNDA` e `SIGA2_LICENCIAMENTO` fica em `integration.siga` (bloco 3), como todo SQL do SIGA.
