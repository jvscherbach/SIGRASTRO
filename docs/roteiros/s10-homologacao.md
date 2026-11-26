# Roteiro de homologação da S10: painel do Gestor

**Quem executa:** o responsável pelo projeto, manualmente. O desenvolvimento (Claude Code) **nunca** se conecta ao SIGA real, ao Camunda real, ao SCORP nem à API de arquivos (D27, D34). Os cenários PN01 a PN16 são **fictícios**, gravados no Oracle de teste (Testcontainers) pelo `CamundaFicticioBuilder` e pelo `CenariosFicticiosDaS10` (ids 10001 a 12999; relógio fixo de 2026, ano corrente). A conferência com dados reais é a seção 5, **fora do repositório**.

**O que a S10 entrega e este roteiro confere:**

- o painel do Gestor em `/painel`, com o ano considerado sempre visível (blocos 1 a 5 e 7);
- o **instantâneo em memória**, refeito por leitura periódica e limitada do SIGA e do Camunda; a tela só lê o instantâneo, nunca consulta o SIGA ou o Camunda numa requisição (blocos 2 a 4);
- os três gráficos: protocolados × concluídos por mês (gráfico 1), fila por etapa (gráfico 2) e carga por responsável (gráfico 3), com as regras de contagem das seções 1 e 2 (bloco 3);
- os cinco estados (`PREPARANDO`, `PRONTO`, `DESATUALIZADO`, `INDISPONIVEL` e o aviso `truncado`) e o botão **Atualizar agora**, com o `429` mostrando o tempo que falta (blocos 4, 5 e 7);
- a permissão: só Gestor e Administrador veem o painel; o Operador não vê o item e leva `403 PERFIL_SEM_PERMISSAO` (bloco 5);
- a auditoria do painel (tipo `PAINEL`): um evento por consulta e um por atualização manual aceita; a recusada (`429`) e a negada não registram (bloco 5);
- os cinco parâmetros do painel na aba **Rastro** das Configurações, só para o Administrador (bloco 8);
- a varredura de vazamento, a matriz de acesso e a resiliência (bloco 6): nenhum CPF, id técnico do Camunda ou chave de arquivo na tela, na API, na auditoria ou no log; com nomes ocultos, nenhum nome de servidor.

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em [`docs/14`](../14-plano-de-sprints.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

> **Dados pessoais e arquivos reais:** CPF, CNPJ, nomes, números de processo, números de requerimento e ids reais ficam como marcadores (`<ID_REQUERIMENTO>`, `<CPF_DE_TESTE>`, `<TOTAL_PROTOCOLADOS_2026>`). **Nenhuma captura de tela, exportação de auditoria ou consulta com dado real vai para o repositório**: guarde numa pasta local fora dele (`amostras-locais/` está no `.gitignore`) e apague depois.

> **Uso do `.env.local`:** você edita o `.env.local` sozinho, sem colar o conteúdo em lugar nenhum. Os comandos abaixo só dizem **se** uma variável está preenchida, nunca o valor.

## 0. Preparação

Suba **sempre** com os três arquivos de Compose (o terceiro esvazia, no serviço `api`, as variáveis que levam a sistemas externos):

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml ps
```

Confira, **dentro do container** e sem imprimir valores, que as variáveis externas estão vazias. Se alguma vier `preenchida`, derrube os containers (sem `-v`) e não siga:

```powershell
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c '[ -z "$SIGA_DB_URL" ] && echo vazia || echo preenchida'
```

Repita o comando trocando `SIGA_DB_URL` por `LICENCIAMENTO_API_URL` e `SCORP_API_URL`.

- [ ] OK / [ ] Falhou — observação:

Resultado esperado: `api` e `front` em execução; abra `http://localhost:8080`. Usuários de demonstração na tela de login: `operador.demo`, `gestor.demo` e `admin.demo`. O Operador não deve ver o item **Painel** no menu; o Gestor e o Administrador devem ver.

**Situação do ambiente de tela:** a população fictícia existe **só no Oracle de teste** (ver R66 em [`docs/10`](../10-pendencias-e-riscos.md)), e o ambiente com SIGA fictício para abri-la na tela ainda **não existe**. Por isso a coluna **Tela** fica `Não executado` até haver esse ambiente; a coluna **Prova automática** diz o que os testes já provam. Quem marcar `OK` na tela precisa tê-la aberto de fato.

**Atenção ao ano e ao relógio:** o painel considera o **ano corrente de Cuiabá** quando `rastro.painelAno` é `0`. Os números das seções 2 e 3 valem para o ano de **2026** (relógio de teste de 31/12/2026 às 12h em Cuiabá). Com o relógio real, o ano pode ser outro: confira o ano que a tela mostra no cabeçalho antes de comparar os números.

## 1. Como ler a tela

- **Unidade de contagem:** o **requerimento** (`REQUERIMENTO.ID`), nunca o número do processo, que não é único (P01). A tela diz "requerimentos". Confirmar com o responsável na seção 5 (é a contagem que ele quer?).
- **Recorte do ano:** só entram os requerimentos com data de protocolo **dentro do ano considerado** (intervalo de 1º de janeiro até antes de 1º de janeiro do ano seguinte). A coluna usada é **provisória** (P46 (a)).
- **Gráfico 1, concluídos:** a série de concluídos usa a **data de alteração** (última alteração, não o instante da conclusão). A tela rotula "Concluídos (mês aproximado)" e traz a nota da data. Concluídos são sempre um subconjunto dos protocolados do ano; um concluído de outro ano não entra no total do ano.
- **"N sem data de protocolo":** requerimentos sem data de protocolo com data de abertura no ano. Não entram nos meses, só no aviso. Podem incluir rascunhos (P46 (c)).
- **Gráfico 2, fila por etapa (agora pizza, S11 bloco 8):** uma fatia e uma linha da legenda por **modelo e etapa** (etapa zerada só na legenda, com "0", e o percentual é a participação na soma das etapas), com o rótulo "modelo · etapa", na ordem do catálogo; barras zeradas aparecem. Um requerimento em etapas paralelas conta **uma vez em cada** barra, por isso a soma pode passar do total (a tela avisa com a nota e usa `totalNaFila`). "Fora do catálogo", "Sem instância" e "Etapa não mapeada" são linhas à parte, nunca somem.
- **Lista por barra (S11, bloco 9):** a lista de requerimentos de cada analista, que a S10 deixou fora do escopo, está entregue na aba lateral "Processos por analista" (D101, docs/15): o total do analista na lista é o da barra e o roteiro da S11 a homologa. Quem não tem responsável ("Fora do catálogo", "Sem instância") não tem lista.
- **Gráfico 3, carga por responsável:** os 15 maiores servidores e "Outros (N servidores)". Categorias sem nome (área do grupo, "Interessado (cadastrante)", "Não atribuída", "Não identificado") têm ícone próprio. Com `RASTRO_MOSTRAR_NOME_RESPONSAVEL` desligado, só essas categorias aparecem, com a nota "Nomes ocultos por configuração", e nenhum nome.
- **Estados:** `PREPARANDO` (selo, sem gráficos; a tela relê a cada 5 s por até 2 minutos); `PRONTO`; `DESATUALIZADO` ("Última leitura falhou; mostrando a de ...", com os gráficos do último retrato bom); `INDISPONIVEL` (selo de erro, sem gráficos); e o aviso `truncado` ("Mostrando os N primeiros requerimentos do ano"), quando a base passa do teto (os totais dos gráficos 2 e 3 ficam parciais; o gráfico 1 não depende do teto).
- **Nunca aparecem:** CPF (nem mascarado), id técnico do Camunda (id de tarefa, chave do modelo), chave de arquivo. Para qualquer perfil.

## 2. Cenários PN01 a PN16

A tela agrega **todos** os cenários numa só resposta: cada linha diz o que o cenário acrescenta aos números. Os totais que a tela deve mostrar com a população inteira são os da seção 2.1.

| Cenário | Id | O que abrir | O que a tela deve mostrar | Prova automática (docs/progresso/s10.md) | Tela | Resultado |
|---|---|---|---|---|---|---|
| PN01 | 10001 a 10006 | `/painel`, no ano de 2026 | Protocolados de **jan** (1), **mar** (2), **jun** (1), **set** (1) e **dez** (1) na série do gráfico 1; dois no mesmo mês (mar) aparecem como dois | `CenariosFicticiosDaS10Test` (`pn01aPn04ProtocoladosPorMesNoAnoPorIntervalo`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN02 | 10011 a 10013 | o mesmo painel | Protocolados em **fev** (2) e **mai** (1); concluídos por **mês aproximado** em **abr** (2) e **ago** (1), fora do mês de protocolo; o total de concluídos do ano é 3 | `CenariosFicticiosDaS10Test` (`pn02ConcluidosPorMesAproximadoSaoSubconjuntoDosProtocolados`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN03 | 10031, 10032 | o mesmo painel e o aviso de "sem data de protocolo" | **10031** (de 2025, concluído em fev/2026) **não** entra no total de concluídos do ano nem na série de 2026; **10032** (sem data de protocolo) entra só no aviso "1 sem data de protocolo" | `CenariosFicticiosDaS10Test` (`pn03OFora2025EOSemDataNaoEntramNaBase`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN04 | 10021, 10022 | o mesmo painel (gráfico 1 e gráfico 2) | Cancelado (Hídrico) e arquivado (LAC) contam em **jul** (2) como protocolados, mas **não** aparecem em barra alguma da fila nem na carga | `CenariosFicticiosDaS10Test` (`pn04CanceladoEArquivadoSaoProtocoladosMasForaDaFila`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN05 | 10041 a 10045 | gráfico 2 (barras do Hídrico) e gráfico 3 | Hídrico em etapas diferentes, conforme a tabela de cenários de docs/15: análise (S01) em 2 requerimentos, revisão (S02), validação (S03) e aceite (S02) em 1 cada; cada requerimento com responsável atribuído a um dos três servidores fictícios (`Servidor Exemplo 01` a `03`, só se os nomes estiverem visíveis); os totais por servidor não estão nesta linha (NÃO CONFIRMADO no roteiro; ver o teste) | `CenariosFicticiosDaS10Test` (`pn05OsTresServidoresTemAsTarefasDescritas`) e `PainelDoSigaJdbcTest` (`pn05_a_pn15_tarefas_ativas_da_raiz_com_o_nome_resolvido`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN06 | 10051, 10052 | gráfico 2 (LAC) e gráfico 3 | LAC: envio do termo (tarefa do interessado) e conferência (grupo de atendimento): "Interessado (cadastrante)" recebe 1 na carga, e a área do grupo de atendimento recebe 1 | `CenariosFicticiosDaS10Test` (`pn06LacEmEtapasDoLac`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN07 | 10071 | gráfico 2 (etapa paralela) | Requerimento em **duas** etapas (análise, no ramo principal, e correção, no ramo do interessado): conta **1 em cada** barra; total na fila conta 1; aparece a nota de que a soma das barras passa do total | `CenariosFicticiosDaS10Test` (`pn07DuasTarefasAtivasEmRamosDiferentes`) e `RegrasDeContagemDoPainelTest` (`requerimentoParaleloContaUmaVezEmCadaEtapaEAvisaQueASomaPassa`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN08 | 10081 | gráfico 2 e gráfico 3 | Complementação em andamento **não** vira barra nem linha à parte; o requerimento segue contado **só na etapa da raiz** (análise S02, 1); nada em "Etapa não mapeada" por causa da complementação | `CenariosFicticiosDaS10Test` (`pn08ComplementacaoEmAndamentoNaoViraTarefaDaRaiz`) e `RegrasDeContagemDoPainelTest` (`complementacaoNaoViraLinhaEORequerimentoSegueNaEtapaDaRaiz`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN09 | 10091 | gráfico 3 | Tarefa de **grupo**: entra na **área** do grupo (categoria de área, sem nome de servidor) | `CenariosFicticiosDaS10Test` (`pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN10 | 10101 | gráfico 3 | Tarefa do **interessado** (cadastro): entra em "Interessado (cadastrante)", sem nome | `CenariosFicticiosDaS10Test` (`pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN11 | 10111 | gráfico 3 | Tarefa **sem atribuição**: entra em "Não atribuída", sem nome | `CenariosFicticiosDaS10Test` (`pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN12 | 10121 | gráfico 3 | Responsável com CPF **sem servidor** cadastrado: entra em "Não identificado", sem nome e **sem CPF** (nem mascarado) | `CenariosFicticiosDaS10Test` (`pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada`) e `PainelDoSigaJdbcTest` (`nenhum_cpf_volta_na_resposta`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN13 | 10131 | gráfico 2 | Tarefa com id técnico **não mapeado**: entra em **"Etapa não mapeada"** (contagem +1), sem o id técnico na tela | `CenariosFicticiosDaS10Test` (`pn09aPn13GrupoInteressadoSemAtribuicaoSemServidorENaoMapeada`) e `RegrasDeContagemDoPainelTest` (`instanciaAtivaSemTarefaDeEtapaNaoSomeEmSilencio`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN14 | 10141 | gráfico 2 | Tipo **fora do catálogo** (Trifásico): entra em **"Fora do catálogo"** (+1), nunca em barra de etapa | `CenariosFicticiosDaS10Test` (`pn14ForaDoCatalogoEPn15SemInstancia`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN15 | 10151 | gráfico 2 | Hídrico **sem instância** no Camunda: entra em **"Sem instância"** (+1), nunca em barra de etapa | `CenariosFicticiosDaS10Test` (`pn14ForaDoCatalogoEPn15SemInstancia`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| PN16 | 11001 a 12200 | gráfico 1 e gráfico 2 (volume) | **1.200** requerimentos de 2026: **+100 em cada mês** na série e **+300 em cada etapa** do volume (conferência, atribuição, revisão de S01 a S03 e cadastro, conforme docs/15); com o teto no padrão (3.000) **não** aparece `truncado`. Para provar o aviso, baixar `rastro.painelLimiteRequerimentos` (faixa 100 a 20.000) e atualizar: aparece "Mostrando os N primeiros requerimentos do ano" (o gráfico 1 não muda) | `CenariosFicticiosDaS10Test` (`pn16VolumeDe1200DistribuidoPorMesEPorEtapa`) e `PainelDoSigaJdbcTest` (`pn16_em_tres_lotes_de_ate_500_pelo_limite_de_leituras`, `teto_traz_os_primeiros_por_id_e_liga_truncado`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |

### 2.1 Totais esperados com a população inteira

Os números abaixo são os de docs/15 (conferidos pelo `CenariosFicticiosDaS10Test` contra o Oracle de teste). **Os totais por barra de etapa e por responsável não são repetidos aqui**: quem confere é o teste, por cenário.

- **Protocolados por mês** (PN01 a PN15, sem o volume): `2, 3, 2, 2, 2, 1, 2, 2, 3, 3, 3, 2` (27 no ano); **com o PN16, 1.227** (100 a mais em cada mês).
- **Concluídos por mês aproximado:** abr 2, ago 1; **3 no ano**, sempre dentro dos protocolados do ano.
- **Fora da fila** (concluídos, cancelado e arquivado): 5.
- **Sem data de protocolo:** 1 (o 10032). A conta real filtra por faixa de ids, porque as fixtures da S7 a S9 também deixam a coluna vazia.
- [ ] OK / [ ] Falhou — observação:

Marque a Prova automática da seção 2 com os testes da tabela; a tela só entra quando o ambiente com SIGA fictício existir (R66).

## 3. Estados, perfis e atualização

| Passo | O que fazer | O que a tela deve mostrar | Prova automática | Tela | Resultado |
|---|---|---|---|---|---|
| E1 | Entrar com `operador.demo` | Sem o item **Painel** no menu; a rota `/painel` leva de volta à Consulta, com o aviso de perfil | `painel.e2e.ts` e `perfis.e2e.ts`; `PainelHttpTest` (403) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E2 | Entrar com `gestor.demo` (e `admin.demo`) | Item **Painel** na seção Gestão; cabeçalho com "Ano de 2026 (requerimentos protocolados no ano)", "Dados de dd/mm/aaaa hh:mm" e próxima atualização | `painel.component.spec.ts`; `MatrizDeAcessoTest` | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E3 | Primeira abertura depois de subir o sistema | Selo `PREPARANDO`, sem gráficos; a tela relê sozinha a cada 5 s, por até 2 minutos; depois mostra "A leitura está demorando" e o botão | `painel.e2e.ts`; `AtualizarPainelTest` (uma só leitura com 20 requisições simultâneas) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E4 | Clicar em **Atualizar agora** logo depois de uma atualização | `429`: "Tente de novo em N segundos", com o tempo que falta; sem atualização nova | `PainelHttpTest`; `painel.e2e.ts` (429) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E5 | Clicar em **Atualizar agora** depois do intervalo mínimo | `202`; a tela relê até a data dos dados mudar | `AtualizarPainelTest` (manual recusada antes e aceita depois) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E6 | Com o ambiente fictício (R66), derrubar o Camunda fictício depois de uma leitura boa | Selo `DESATUALIZADO`: "Última leitura falhou; mostrando a de ..."; os gráficos seguem com o último retrato bom; sem erro 5xx na tela | `VarreduraDoPainelHttpTest` (`camunda_fora_do_ar_deixa_o_painel_desatualizado_sem_5xx_e_o_resto_segue`); `AtualizarPainelTest` | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E7 | Sem nenhuma leitura boa (SIGA fora do ar desde a subida) | Selo `INDISPONIVEL`, sem gráficos; sem 5xx | `VarreduraDoPainelHttpTest` (`sem_dado_anterior_a_falha_deixa_indisponivel_sem_5xx_e_a_leitura_da_serie_tambem`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E8 | Com `RASTRO_MOSTRAR_NOME_RESPONSAVEL` desligado (Configurações, só Administrador) e depois de a tela recarregar | Nenhum nome de servidor na tela; gráfico 3 só com as categorias sem nome e a nota "Nomes ocultos por configuração" | `VarreduraDoPainelSemNomeHttpTest`; `RegrasDeContagemDoPainelTest` (`comNomesOcultosSoCategoriasSemNomeENotaEnenhumNomeNoResultado`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E9 | Mudar `rastro.painelAno` para 2025 (ou 0, ano corrente) e atualizar | O cabeçalho mostra o ano escolhido; o recorte do outro ano não aparece; a mudança vale na próxima atualização | `VarreduraDoPainelHttpTest` (`o_recorte_do_ano_vale_e_a_mudanca_do_parametro_vale_no_proximo_ciclo`) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E10 | Abrir no celular a 360 px | Sem rolagem horizontal na tela nem na barra do topo | `painel.e2e.ts` (360 px) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E11 | Abrir **Ver como tabela** em cada gráfico | A tabela tem os mesmos números do gráfico, com totais | `painel.e2e.ts` (tabela igual ao gráfico) | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E12 | Abrir as Configurações como Administrador, aba **Rastro** | Cinco campos "Painel: ..." com faixa, padrão do ambiente, "Vale já" e texto de apoio; o `0` no ano é o ano corrente. Gestor e Operador não acessam a tela (403) | `ParametrosHttpTest`; `configuracoes-parametros.e2e.ts` | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |
| E13 | Conferir a auditoria (tela de Auditoria, como Administrador) | Eventos `PAINEL` por consulta e por atualização manual **aceita**; a recusada (429) não aparece; o detalhe traz só o ano e o estado, sem nome de servidor | `VarreduraDoPainelHttpTest` (auditoria em lista e CSV); `PainelHttpTest` | Não executado (R66) | [ ] OK / [ ] Falhou — observação: |

## 4. Pontos provisórios e riscos abertos

Estes pontos são **decisões provisórias** ou **NÃO CONFIRMADO**. A tela os diz como nota ou selo; nenhum deles deve ser tratado como número afirmado. O que a homologação com dados reais confirma está na seção 5.

- **Coluna do protocolo** (P46 (a)): `DATA_PROTOCOLO_PROCESSO` no intervalo do ano, decisão provisória do responsável.
- **Data de conclusão aproximada** (P46 (b)): o mês dos concluídos é o de `DATA_ALTERACAO`, não o instante da conclusão. A tela rotula "Concluídos (mês aproximado)".
- **"Sem data de protocolo" do ano** (P46 (c)): usa `DATA_ABERTURA` no ano e pode incluir rascunhos.
- **Tipo das colunas de data** (P46 (d)): DATE ou TIMESTAMP **NÃO CONFIRMADO**; a consulta não converte a coluna.
- **Instância raiz** (P46 (e)): a complementação, que também é raiz no Camunda, sai pela chave da definição; o requerimento segue só na etapa da raiz.
- **Responsável** (P46 (f)): nome por `SERVIDOR`, depois `PESSOA`, só com CPF de 11 dígitos; um CPF que esteja só em `PESSOA` aparece pelo nome, como na S7.
- **Teto de linhas por requerimento** (P46 (g)): passou, o lote vem marcado `alemDoLimite` (aviso `LEITURA_PARCIAL`).
- **Custo da atualização no Camunda** (R82): cada atualização lê a série e a base no SIGA e as tarefas em lotes de até 500 no Camunda. O instantâneo fica **em memória, por instância**: com mais de uma instância, cada uma atualiza por conta própria. Reiniciar zera o instantâneo.
- **Varredura de vazamento** (R83): feita por padrão no Oracle de teste (`VarreduraDoPainelHttpTest`, `VarreduraDoPainelSemNomeHttpTest`); um campo novo no contrato não seria pego sem a varredura.
- **Índices recomendados** (P42 (a), (e), (f)): só documento, para a equipe do SIGA avaliar. Sem eles, cada atualização varre `REQUERIMENTO` duas vezes e as execuções ativas a cada lote.
- **Limitações conhecidas da tela:** uma barra de total zero ainda mostra um fio escuro na borda (detalhe visual, não corrigido); o rótulo das barras é "modelo · etapa", porque Hídrico e LAC repetem nomes de etapa.
- **Pendência para o responsável (ferramentas):** copiar `docs/padroes/modulos-e-dependencias.md` para `.claude/skills/vistas-banco/references/modulos-e-dependencias.md`, porque o modo automático não grava em `.claude/`. Hoje os dois arquivos estão diferentes (a regra do `CLAUDE.md` pede o mesmo conteúdo).

## 5. Conferência com dados reais (feita pelo responsável, **fora do repositório**)

Nenhum número real entra no repositório. Anote os resultados numa pasta local (`amostras-locais/`) e confira com as consultas do próprio SIGA e do Camunda, nunca com dado copiado para cá.

1. **Total protocolado em 2026:** comparar a tela com `SELECT COUNT(*)` na mesma base, filtrando `DATA_PROTOCOLO_PROCESSO` no intervalo de 2026. Marcador: `<TOTAL_PROTOCOLADOS_2026>`. [ ] OK / [ ] Divergente (anote a diferença).
2. **Total concluído:** `SITUACAO_PROCESSO = 'CONCLUIDO'` com protocolo em 2026 (o concluído de outro ano não entra). Marcador: `<TOTAL_CONCLUIDOS_2026>`. [ ] OK / [ ] Divergente.
3. **Três etapas:** escolha três etapas do catálogo e compare a barra da tela com a contagem de requerimentos distintos no Camunda (com a regra de instância raiz). Marcadores: `<ETAPA_1_TOTAL>`, `<ETAPA_2_TOTAL>`, `<ETAPA_3_TOTAL>`. [ ] OK / [ ] Divergente.
4. **Três responsáveis:** escolha três servidores e compare o total de requerimentos de cada um com a contagem no Camunda. Marcadores: `<RESPONSAVEL_1_TOTAL>`, `<RESPONSAVEL_2_TOTAL>`, `<RESPONSAVEL_3_TOTAL>`. [ ] OK / [ ] Divergente.
5. **Coluna do protocolo (P46 (a)):** confirmar se `DATA_PROTOCOLO_PROCESSO` é a coluna que o responsável quer, ou se é outra. [ ] Confirmada / [ ] Trocar para: `<COLUNA_DE_PROTOCOLO>`.
6. **Data de conclusão aproximada (P46 (b)):** confirmar se `DATA_ALTERACAO` é aceitável para "Concluídos (mês aproximado)" ou se há coluna melhor. [ ] Aceita / [ ] Trocar para: `<COLUNA_DE_CONCLUSAO>`.
7. **Sem data de protocolo (P46 (c)):** `SELECT COUNT(*)` na mesma base, com `DATA_PROTOCOLO_PROCESSO` nulo e `DATA_ABERTURA` no ano. Marcador: `<SEM_DATA_DE_PROTOCOLO>`. [ ] OK / [ ] Divergente.
8. **Tipo das colunas de data (P46 (d)):** conferir se são DATE ou TIMESTAMP. [ ] DATE / [ ] TIMESTAMP / [ ] Outro.
9. **Unidade de contagem:** confirmar se a contagem por **requerimento** é a que o responsável quer, e não por **processo** (o número de processo não é único, P01). [ ] Requerimento / [ ] Processo (abra pendência em docs/10).
10. **Tempo e impacto da atualização em horário comum (R82, P46):** medir o tempo total de uma atualização (a tela mostra `geradoEm` antes e depois), o número de lotes e a percepção de carga no Camunda e no SIGA. Anote o tempo: `<TEMPO_DA_ATUALIZACAO>`. Se as consultas forem lentas, veja os índices do P42 (a), (e) e (f).
11. **Tamanho do teto:** com o total real, confira se `rastro.painelLimiteRequerimentos` (padrão 3.000) é suficiente. Se a tela mostrar `truncado`, registre o número de requerimentos da base: `<TOTAL_DA_BASE>`.

## 6. Verificação da sprint (sessão nova)

Em uma **sessão nova** do Claude Code, rode `/verificar-sprint 10`. O comando dispara `revisor-seguranca`, `executor-de-testes` e `atualizador-de-docs` em paralelo e grava `docs/progresso/verificacao-s10.md`. Ele não corrige código nem commita. Anote o resultado e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que falhar.

- [ ] OK / [ ] Falhou — observação:

## 7. Fechamento

- [ ] Registre em [`docs/14`](../14-plano-de-sprints.md) a homologação da S10 (com dados reais e com o ambiente fictício, quando existir).
- [ ] Atualize o [`docs/progresso/s10.md`](../progresso/s10.md) com o resultado dos passos.
- [ ] Varredura final de dados reais no repositório: nenhum CPF, CNPJ, nome de pessoa ou número de processo real.
