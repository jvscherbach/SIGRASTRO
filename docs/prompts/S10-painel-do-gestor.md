# S10: painel do Gestor

> **Forma de trabalho:** a sprint é dividida em **9 blocos**, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. Pode ser rodada à mão (o responsável alterna as sessões, revisa e commita) ou pelo `/orquestrar` em branch `auto/*` (ver `docs/claude-code/orquestracao.md`).

## Contexto

As sprints S7 a S9 mostram **um requerimento por vez**: onde está, por onde passou e como estão as taxas. A S10 entrega a **visão de conjunto** para o Gestor: o **painel**, com três gráficos.

1. **Protocolados × concluídos ao longo do ano:** quantos requerimentos foram protocolados em cada mês e quantos desses foram concluídos.
2. **Fila por etapa:** quantos requerimentos estão em cada etapa agora.
3. **Carga por responsável:** quantos requerimentos cada servidor tem na etapa atual.

**Recorte para teste (decisão do responsável):** por enquanto o painel considera **somente os requerimentos protocolados no ano corrente**. O ano vem de um parâmetro (`rastro.painelAno`; **0 = ano corrente**, no fuso de Cuiabá), para poder mudar sem alterar código. A tela sempre diz o ano considerado.

**Fonte dos dados:** a nota **P42** de `docs/10` já decidiu que o painel **não lê o Camunda requerimento a requerimento** nem em tempo real. A hipótese lá era uma tabela de apoio no banco próprio do SIGRASTRO. **O SIGRASTRO não tem banco próprio** (fila, auditoria e configurações vivem em memória), então o responsável decidiu o caminho mais simples: um **instantâneo em memória**, refeito por **leitura periódica e limitada** do SIGA e do Camunda. Ele some ao reiniciar e se refaz sozinho. Nada de banco novo, nada de escrita no Oracle. Registre em `docs/09` e atualize a P42.

O serviço é usado em **processos jurídicos**: número errado é pior do que número ausente. Onde a regra é provisória, o painel **diz** (nota ou selo), em vez de afirmar.

Ficam **fora**: lista dos requerimentos de cada barra (clicar na barra), tempo parado em etapa, prazos, exportação, filtros por tipo ou período, o destaque de "pago que virou isento" (S11) e qualquer banco novo.

Leia antes de começar: `docs/02`, `docs/03`, `docs/04`, `docs/07`, `docs/08`, `docs/09`, `docs/10` (P42, P40 a P45, R73 a R79), `docs/11`, `docs/12`, `docs/13`, `docs/14`, **`docs/15`** (S7 a S9), `docs/design/sigrastro/LEIA-ME.md` (padrão visual vigente), `docs/progresso/s9.md` e o código dos módulos `tramitacao`, `financeiro`, `auditoria` e `configuracao`. **Reuse** o catálogo de etapas, o catálogo de grupos, o limite de leituras ao Camunda, o construtor de fixtures, o relógio injetável, a utilidade de data e o agendamento existente (`AgendamentoConfig`).

## Decisões já tomadas (registre em `docs/09`; os números D, P e R seguem o próximo livre)

**Quem vê:** só **Gestor** (`OPERADOR_SIGILO`) e **Administrador**. O Operador não vê o item de menu e recebe `403 PERFIL_SEM_PERMISSAO` na API. Nova permissão `verPainel`, na mesma linha de `acessarConfiguracoes`.

**Unidade de contagem:** o **requerimento** (`REQUERIMENTO.ID`), nunca o número do processo (que não é único, P01). A tela diz "requerimentos". O responsável falou em "processos": confirmar no roteiro se a contagem por requerimento é a que ele quer.

**Base do ano:** requerimentos com `DATA_PROTOCOLO_PROCESSO` dentro do ano (comparação por **intervalo** `>= 1º de janeiro` e `< 1º de janeiro seguinte`, não por função na coluna). Requerimento sem data de protocolo **não entra** nos gráficos; só é contado num aviso ("N sem data de protocolo"). **A escolha da coluna é provisória (NÃO CONFIRMADO):** registre em `docs/10` e no roteiro.

**Protocolados por mês:** os da base agrupados pelo mês de `DATA_PROTOCOLO_PROCESSO`.

**Concluídos por mês:** os da base com `SITUACAO_PROCESSO = CONCLUIDO`, agrupados pelo mês de `DATA_ALTERACAO`. **A data é aproximada** (última alteração, não o instante da conclusão): a tela rotula "Concluídos (mês aproximado)". Provisório e **NÃO CONFIRMADO**; registre em `docs/10`. Concluídos são **sempre um subconjunto dos protocolados do ano**.

**Fila por etapa e carga por responsável:** vêm do **Camunda**, em lote e só **dentro da atualização do instantâneo**. A atualização lê as **tarefas ativas** dos requerimentos da base que **não** estão concluídos, cancelados nem arquivados, em lotes de até 500 ids (`BUSINESS_KEY_ IN (:ids)`), **sem laço por linha**. Cada tarefa é resolvida **pelo catálogo de etapas existente** (modelo pelo tipo de processo). Regras de contagem:
- conta **requerimentos distintos** por etapa; um requerimento em etapas paralelas conta **uma vez em cada** (por isso a soma das barras pode passar do total: a tela avisa);
- **só a instância raiz** do requerimento entra: a complementação em andamento (processo filho) **não** vira linha nem barra do painel; o requerimento segue contado na etapa da raiz. Registre a limitação em `docs/10`;
- tipo de processo fora do catálogo (Trifásico, LAS, outros) entra em **"Fora do catálogo: N"**; requerimento sem instância no Camunda entra em **"Sem instância: N"**; tarefa com id não mapeado entra em **"Etapa não mapeada: N"**. Nunca some em silêncio;
- ordem das barras = ordem do catálogo; barras zeradas aparecem (o gestor vê a etapa vazia).

**Responsável:** só o **nome** (mesma resolução `SERVIDOR`, depois `PESSOA`, do `tarefa-atual.sql`, agora em lote), nunca CPF, nem mascarado. Tarefa de grupo conta na **área** (catálogo de grupos), tarefa do interessado em "Interessado (cadastrante)", sem atribuição em "Não atribuída", `ASSIGNEE_` sem nome resolvido em "Não identificado". Respeita `RASTRO_MOSTRAR_NOME_RESPONSAVEL` e a RN12: com o parâmetro desligado, o gráfico mostra **só as categorias sem nome** (área, interessado, não atribuída) e a nota "Nomes ocultos por configuração". Mostra os **15 maiores** e agrupa o resto em "Outros (N servidores)".

**Instantâneo:** um objeto imutável em memória com `geradoEm`, o ano, os números dos três gráficos e os avisos. Estados: `PREPARANDO` (ainda não houve a primeira leitura), `PRONTO`, `DESATUALIZADO` (a última tentativa falhou; mostra o último bom, com a data), `INDISPONIVEL` (nunca houve leitura boa). **Uma requisição do painel nunca consulta o Camunda nem o Oracle do SIGA:** ela só lê o instantâneo. A primeira leitura é disparada na primeira requisição (não na subida do sistema) e roda **em segundo plano** com *single flight* (nunca duas ao mesmo tempo). Depois, atualiza sozinho a cada N minutos. Falha mantém o último instantâneo bom. Atualização manual limitada por um intervalo mínimo.

**Limites (proteção do SIGA e do Camunda de produção):** teto de requerimentos por atualização, tempo limite total da atualização, passagem de **cada** lote pelo `LimiteDeLeiturasDoCamunda` e tempo limite por consulta. Passou do teto: o instantâneo é marcado `truncado`, com aviso "Mostrando os N primeiros requerimentos do ano" (os totais dos gráficos 2 e 3 ficam parciais; **o gráfico 1 não depende do teto**, porque é agregado no SQL).

**Parâmetros novos** (seção Rastro das Configurações, só Administrador, classe **imediato**, mesmas propriedades `rastro.painel.*` e variáveis `RASTRO_PAINEL_*`):
- `rastro.painelAno`: 0 (ano corrente), faixa 0 ou 2000 a 2100;
- `rastro.painelAtualizacaoMinutos`: padrão 30, faixa 5 a 1440;
- `rastro.painelLimiteRequerimentos`: padrão 3000, faixa 100 a 20000;
- `rastro.painelTimeoutSegundos`: padrão 60, faixa 10 a 300;
- `rastro.painelIntervaloMinimoManualSegundos`: padrão 120, faixa 30 a 3600.

**Independência:** o painel não afeta nem é afetado pela consulta, pela tramitação, pelo financeiro nem pelos documentos. Falha do painel não derruba nada.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou número de DAR real em teste, doc, exemplo, log ou repositório). CPF fictício é sempre **inválido de propósito** (ex.: `000.000.000-01`) e nomes são evidentes (`Servidor Exemplo 01`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago**: bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. **Gráficos sem biblioteca**: HTML, CSS e SVG simples, como o fluxograma da S8.
- **Somente leitura no banco.** Todo SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados** (nunca concatenar valor), `FETCH FIRST n ROWS ONLY` onde couber, tempo limite de consulta e concentrado **num único componente de acesso** (`integration.siga`, risco R04). Nada é gravado no Oracle.
- **Dados pessoais:** `ASSIGNEE_` guarda CPF. O CPF **nunca** sai em resposta, log, erro, auditoria, CSV ou tela, **nem mascarado**. Variáveis do Camunda: nenhuma é lida nesta sprint.
- **Módulos:** o módulo novo `painel` entra no `ArchitectureTest` e em `docs/padroes/modulos-e-dependencias.md`. Ele **não acessa o banco nem o HTTP direto** (só portas); depende de `tramitacao` e de `consulta` apenas por contratos publicados, como o `financeiro` faz; **sem ciclo**. O adaptador `painel`↔SIGA fica em `integration.siga`.
- **Testes por bloco:** rode **só os testes ligados ao bloco** (a suíte completa do back-end leva cerca de 9 minutos e roda **uma vez no fim da sprint**; no modo automático, quem a roda é o `/orquestrar`). Nunca repita a suíte completa dentro de um bloco.
- **Git:** no modo manual, nenhum comando além de `git diff` (quem commita é o responsável). No modo automático, o commit é só pelo `commit-bloco`. **Nunca push.**
- Datas e horas sempre no fuso de **Cuiabá**, pela utilidade única existente. Toda regra que depende de "hoje" ou "ano corrente" usa o `Clock` do projeto (testes com data fixa, inclusive virada do ano e do dia, e fuso UTC × Cuiabá).

## Protocolo das sessões

1. **Identifique a sessão:** a primeira mensagem traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s10.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes ligados a ele verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/s10.md` (crie no bloco 1, com a tabela dos 9 blocos); a marcação entra no mesmo commit;
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

5. **Pare.** No modo manual, mesma sessão no próximo bloco: espere "continuar"; outra sessão: a atual termina ali.

**Blocos da sessão HAIKU (mecânicos).** Só recebe blocos cujo conteúdo já está decidido neste prompt ou em outros arquivos. **Não invente** cenário, número, regra nem nome; o que não achar, escreva como **NÃO CONFIRMADO**; nenhum dado real; rode a varredura de dados pessoais ao final. Se o bloco exigir decisão de projeto ou de código, **pare** e responda `Este bloco precisa da sessão SONNET.`

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Fixtures do painel | **SONNET** | Dados sintéticos com cenário fechado |
| 2 | Leitura agregada no SIGA e no Camunda (SQL e adaptador) | **OPUS** | Consulta em lote contra produção, limites e CPF |
| 3 | Regras de contagem do painel | **OPUS** | Etapas paralelas, grupos, não mapeados e nomes |
| 4 | Instantâneo em memória e atualização | **OPUS** | Concorrência, falha parcial e proteção do SIGA |
| 5 | API, permissão, auditoria e parâmetros | **SONNET** | Contrato pronto |
| 6 | Acesso, vazamento e resiliência | **OPUS** | Prova das regras de segurança |
| 7 | Front: tela do painel e gráficos | **SONNET** | Interface |
| 8 | Configurações: parâmetros do painel | **HAIKU** | Tela guiada pelo catálogo, sem lógica nova |
| 9 | Roteiro de homologação e fechamento | **HAIKU** | Documentação a partir de cenários já definidos |

---

## Bloco 1 (SONNET): fixtures do painel

**Commit sugerido:** `test(painel): fixtures fictícias de requerimentos do ano, etapas e responsáveis`

Estenda o construtor de fixtures da S7 a S9 (sem criar outro mecanismo) para montar uma **população** de requerimentos no Oracle de teste, com `DATA_PROTOCOLO_PROCESSO`, `DATA_ALTERACAO` e `SITUACAO_PROCESSO` controlados, e tarefas ativas no Camunda fictício. Faixa de ids nova, livre e registrada em `docs/13` (sugestão: 10000 a 10999). Relógio fixo, com o **ano corrente = 2026** e um requerimento de **2025** para provar o recorte.

Cenários (números `N/2026` evidentes, três servidores fictícios `Servidor Exemplo 01` a `03` com CPF inválido de propósito):
- **PN01** distribuição de protocolados em vários meses de 2026 (inclusive janeiro, dezembro e dois no mesmo mês); **PN02** concluídos (`CONCLUIDO`) em meses diferentes dos protocolados; **PN03** um requerimento de **2025** (fora do recorte) e um sem `DATA_PROTOCOLO_PROCESSO`; **PN04** cancelado e arquivado (fora da fila, mas contados como protocolados).
- **PN05** requerimentos do **Hídrico** em etapas diferentes do catálogo, com responsável atribuído a cada um dos três servidores; **PN06** do **LAC**; **PN07** etapa **paralela** (dois ramos ativos); **PN08** complementação em andamento (filha ativa; o requerimento segue na etapa da raiz); **PN09** tarefa de **grupo** (área); **PN10** tarefa do **interessado**; **PN11** tarefa **sem atribuição**; **PN12** `ASSIGNEE_` com CPF **sem servidor** cadastrado (Não identificado); **PN13** id técnico **não mapeado**; **PN14** tipo **fora do catálogo** (Trifásico); **PN15** requerimento **sem instância** no Camunda.
- **PN16** volume: 1.200 requerimentos sintéticos de 2026 (para o teste de lotes de 500 e do teto).
- Testes: o construtor gera exatamente o descrito (contagens conferidas); nenhum dado real; documente em `docs/13` e em `docs/15`.
- Docs: `docs/progresso/s10.md` (crie com a tabela dos 9 blocos), `docs/13`.

## Bloco 2 (OPUS): leitura agregada no SIGA e no Camunda

**Commit sugerido:** `feat(painel): leitura agregada por mês e tarefas ativas em lote no SIGA e no Camunda`

- Novo módulo `painel` (`domain`, `application`; portas `ResumoDoAnoNoSiga` e `TarefasAtivasEmLote`) e **um adaptador único** em `integration.siga` (`PainelDoSigaJdbc`), com SQL em `sql/siga/painel/`:
  1. `protocolados-e-concluidos-por-mes.sql`: **uma** consulta agregada (`GROUP BY` mês) sobre `REQUERIMENTO`, com o intervalo do ano, devolvendo por mês os protocolados e os concluídos (`SITUACAO_PROCESSO = 'CONCLUIDO'`, mês de `DATA_ALTERACAO`) e a contagem "sem data de protocolo" do ano. **Não depende do teto de requerimentos.**
  2. `requerimentos-da-base.sql`: ids, tipo de processo e situação dos requerimentos do ano, `FETCH FIRST :limite + 1` (o excedente liga `truncado`), ordenados por `ID`.
  3. `tarefas-ativas-em-lote.sql`: tarefas ativas das instâncias **raiz** (`PARENT_ID_ IS NULL`, `ID_ = PROC_INST_ID_`) com `BUSINESS_KEY_ IN (:ids)`, devolvendo requerimento, `TASK_DEF_KEY_`, chave do modelo (`PROC_DEF_KEY_`), tarefa de grupo ou do interessado e o **nome** do responsável resolvido como no `tarefa-atual.sql` (SERVIDOR, depois PESSOA; só tenta com 11 dígitos; **CPF nunca é selecionado nem devolvido**). Lotes de até 500; cada lote passa pelo `LimiteDeLeiturasDoCamunda` e tem tempo limite de consulta.
- Sem SIGA configurado: adaptador `...NaoConfigurado` (como as demais portas), resultando em `INDISPONIVEL`.
- Recomende em `docs/10` (P42) o índice em `ACT_RU_EXECUTION(BUSINESS_KEY_)` se faltar, e `REQUERIMENTO(DATA_PROTOCOLO_PROCESSO)`; só como documento.
- Testes (Oracle de teste): agregação por mês contra PN01 a PN04 (inclusive o de 2025 fora e o sem data); recorte do ano por intervalo, com relógio em UTC e em Cuiabá na virada de 31/12 para 01/01; PN16 em 3 lotes (1.200 ids) e o teto (excedente liga `truncado`); tarefas ativas de PN05 a PN15; **o SQL enviado ao banco não menciona** `ASSIGNEE_` fora da resolução do nome, e nenhum CPF volta na resposta (varredura); banco fora do ar e conexão muda (tempo limite).
- Docs: `docs/10`, `docs/13`, `docs/padroes/modulos-e-dependencias.md`, `ArchitectureTest`.

## Bloco 3 (OPUS): regras de contagem do painel

**Commit sugerido:** `feat(painel): regras de contagem da fila por etapa e da carga por responsável`

Funções puras do domínio (sem Spring, sem relógio escondido):
- `MontarSerieDoAno`: 12 meses sempre presentes (mês sem dado = 0), protocolados e concluídos, totais, "sem data de protocolo"; concluídos nunca passam dos protocolados do ano (se passarem, aviso `INCONSISTENCIA_NA_SERIE` e não esconda).
- `ContarFilaPorEtapa`: resolve cada tarefa pelo `CatalogoDeEtapas` existente (**nunca lança**), conta requerimentos distintos por etapa na ordem do catálogo, barras zeradas incluídas; `Fora do catálogo`, `Sem instância` e `Etapa não mapeada` à parte; complementação (filha) ignorada; um requerimento paralelo conta uma vez em cada etapa; nota quando a soma passa do total.
- `ContarCargaPorResponsavel`: nome, área (catálogo de grupos), "Interessado (cadastrante)", "Não atribuída", "Não identificado"; **15 maiores** e "Outros (N servidores)"; com `mostrarNome` falso, só as categorias sem nome e a nota de nomes ocultos; empate ordena por nome e depois por posição fixa (resultado estável).
- Modelos de saída sem nenhum campo de CPF (o tipo não tem onde guardar).
- Testes (sem Docker): PN01 a PN15 em memória; paralelas; os três "à parte"; 17 servidores gerando "Outros (2 servidores)"; nomes ocultos; estabilidade da ordem; propriedade "requerimento fora do catálogo nunca entra numa barra de etapa" em 50 sorteios.
- Docs: `docs/09`, `docs/15` (seção "Painel").

## Bloco 4 (OPUS): instantâneo em memória e atualização

**Commit sugerido:** `feat(painel): instantaneo em memoria com atualizacao periodica limitada`

- `InstantaneoDoPainel` (imutável) e `GuardarInstantaneo` (referência atômica): estados `PREPARANDO`, `PRONTO`, `DESATUALIZADO` (mantém o último bom e a data da falha), `INDISPONIVEL`; `truncado` e avisos.
- `AtualizarPainel`: lê a série (bloco 2.1), a base (2.2) e as tarefas em lotes (2.3), monta com as regras do bloco 3 e **troca o instantâneo de uma vez**. *Single flight* (uma atualização por vez); tempo limite total; teto de requerimentos; cada lote pelo limite de leituras; falha no meio **não troca** o instantâneo e registra só a **classe** da exceção no log (sem mensagem, sem CPF).
- Disparo: a **primeira** requisição inicia a leitura em segundo plano (resposta `PREPARANDO`); depois, atualização periódica pelo agendamento existente; **atualização manual** (`SolicitarAtualizacao`) respeitando o intervalo mínimo (devolve o tempo que falta).
- Parâmetros lidos **a cada ciclo** (mudança vale no próximo), pelo `Clock` do projeto (ano corrente em Cuiabá; teste na virada do ano).
- Testes (sem Docker, com dublês das portas): primeira requisição → `PREPARANDO` e uma só leitura mesmo com 20 requisições simultâneas; sucesso → `PRONTO`; falha depois de sucesso → `DESATUALIZADO` com o último bom; falha sem sucesso → `INDISPONIVEL`; teto → `truncado`; manual recusada antes do intervalo e aceita depois; atualização lenta não bloqueia a leitura do instantâneo; **uma requisição nunca chama as portas** (dublê que falha o teste se chamado).
- Docs: `docs/09`, `docs/15`, `docs/12` (parâmetros).

## Bloco 5 (SONNET): API, permissão, auditoria e parâmetros

**Commit sugerido:** `feat(painel): API do painel, permissao verPainel, auditoria e parametros`

- **Rotas:** `GET /api/painel` (devolve o instantâneo: `estado`, `ano`, `geradoEm`, `proximaAtualizacaoEm`, `truncado`, `serie[]` (mês, protocolados, concluídos), `semDataDeProtocolo`, `filaPorEtapa[]` (rótulo, total), `foraDoCatalogo`, `semInstancia`, `etapaNaoMapeada`, `cargaPorResponsavel[]` (rótulo, total, tipo: `SERVIDOR|AREA|INTERESSADO|NAO_ATRIBUIDA|NAO_IDENTIFICADO|OUTROS`), `nomesOcultos`, `avisos[]`) e `POST /api/painel/atualizacao` (`202`; `429 ATUALIZACAO_RECENTE` com `tentarEmSegundos`). Sem `PREPARANDO` travando a resposta: ela é imediata.
- **Permissão:** `Perfil.podeVerPainel()` (Gestor e Administrador), `permissoes.verPainel` no login e `403 PERFIL_SEM_PERMISSAO` para o Operador. Atualize a política de acesso e a `MatrizDeAcessoTest` (só as linhas e cenários das rotas novas).
- **Auditoria:** tipo `PAINEL` (como o `FINANCEIRO`, mesmo padrão de grupo e filtro da tela), um evento por **consulta** e um por **atualização manual**; detalhe só com o ano e o estado, **sem nomes de servidores**; negado não registra.
- **Parâmetros** no `CatalogoDeParametros` (bloco "Parâmetros novos" acima), classe **imediato**, com leitura a cada ciclo; credenciais nunca aparecem.
- Testes: `PainelHttpTest` (API real com dublê das portas: perfis, estados, `429`, auditoria), `MatrizDeAcessoTest` (+cenários), `ParametrosHttpTest` (+faixas, um evento com antes e depois, Operador e Gestor com 403 na alteração).
- Docs: `docs/07` (rotas e contrato), `docs/11` (`PAINEL`), `docs/12`, `docs/02` (RF e RN novos do painel).

## Bloco 6 (OPUS): acesso, vazamento e resiliência

**Commit sugerido:** `test(painel): matriz de acesso, varredura de vazamento e resiliência do painel`

- **Varredura de vazamento** com o Oracle de teste, em **todos os cenários PN** × 3 perfis: respostas da API, eventos de auditoria (lista e CSV) e log. Procura CPF (com e sem máscara e 11 dígitos soltos), ids técnicos do Camunda e chave de arquivo. Com `RASTRO_MOSTRAR_NOME_RESPONSAVEL` desligado, **nenhum nome de servidor** em nenhuma resposta. Um teste prova que a varredura **não é vazia** (os nomes aparecem quando permitido).
- **Camunda fora do ar ou lento durante a atualização:** instantâneo `DESATUALIZADO` ou `INDISPONIVEL`, sem 5xx; **consulta, tramitação, financeiro e documentos seguem iguais**; o inverso também vale.
- **Proteção do SIGA:** um teste conta as consultas feitas durante N requisições ao painel: **zero** fora da atualização; durante a atualização, no máximo o número de lotes previsto; o teto e o limite de leituras simultâneas são respeitados.
- Acesso: Operador e sem login (403 e 401), recorte do ano (o de 2025 nunca aparece), mudança de `rastro.painelAno` vale no próximo ciclo.
- Docs: `docs/10` (riscos novos, incluindo o custo da atualização no Camunda e o instantâneo por instância), `docs/12`.

## Bloco 7 (SONNET): front, tela do painel e gráficos

**Commit sugerido:** `feat(front): tela do painel do Gestor com tres graficos`

- Rota `/painel` (título `Painel · SIGRASTRO`), item **Painel** na seção Gestão do menu, visível só com `permissoes.verPainel` (RN08: quem não tem, não vê), guarda de rota como as demais. Use o **padrão visual vigente** (tokens do Cerrado, `docs/design/sigrastro/LEIA-ME.md`); nada de cor fixa.
- Cabeçalho da tela: "Ano de 2026 (requerimentos protocolados no ano)", "Dados de dd/mm/aaaa hh:mm", próxima atualização e o botão **Atualizar agora** (trata o `429` mostrando o tempo que falta). Selos de texto e ícone para `PREPARANDO` (com nova leitura automática a cada 5 s, no máximo 2 minutos, e o botão), `DESATUALIZADO` ("Última leitura falhou; mostrando a de ..."), `INDISPONIVEL` e `truncado`.
- **Gráfico 1:** barras agrupadas por mês (protocolados × concluídos), com rótulo "Concluídos (mês aproximado)" e a nota da data.
- **Gráfico 2:** barras horizontais da fila por etapa, na ordem do catálogo, com os totais ao lado; abaixo, "Fora do catálogo", "Sem instância" e "Etapa não mapeada" como linhas à parte; a nota quando a soma passa do total.
- **Gráfico 3:** barras horizontais da carga por responsável (15 maiores e "Outros"), categorias sem nome com ícone próprio; a nota "Nomes ocultos por configuração" quando for o caso.
- **Gráficos em HTML, CSS e SVG, sem biblioteca.** Cada gráfico tem `role="img"` com `aria-label` resumindo o dado **e** uma tabela equivalente (alternável por "Ver como tabela"); a informação **nunca depende só de cor** (rótulo, valor numérico e, onde houver duas séries, padrão ou posição diferente); contraste AA; foco visível; celular a 360 px sem rolagem horizontal; `prefers-reduced-motion` respeitado; impressão em preto e branco legível.
- Testes (Vitest e Playwright com API simulada): os cinco estados; PN01 a PN15 nos números; Operador sem o item e levado fora da rota; nenhum CPF nem id técnico no DOM; com nomes ocultos, nenhum nome no DOM; 360 px; tabela alternativa igual ao gráfico.
- Docs: `docs/08` (Painel).

## Bloco 8 (HAIKU): configurações, parâmetros do painel

**Commit sugerido:** `feat(config): parametros do painel nas Configuracoes`

- Os cinco parâmetros do painel aparecem na seção **Rastro** das Configurações (a tela é guiada pelo catálogo: confira que os campos apareceram, com rótulo, faixa, padrão do ambiente e etiqueta "Vale já"). Acrescente só o **texto de apoio** (o que cada um faz e que `0` no ano significa "ano corrente"). **Nenhuma lógica, validação ou serviço muda.** Se algo exigir lógica, **pare e relate**.
- Testes: o spec e o e2e de Configurações cobrem os campos novos; o Operador e o Gestor continuam sem acesso à tela (403).
- Docs: `docs/12`, `docs/08`.

## Bloco 9 (HAIKU): roteiro de homologação e fechamento

**Commit sugerido:** `docs(painel): roteiro de homologação e fechamento da S10`

- `docs/roteiros/s10-homologacao.md`: uma linha por cenário PN01 a PN16 (o que abrir, o que a tela deve mostrar, "Resultado"). Inclua a **conferência com dados reais (feita pelo responsável, fora do repositório)**: comparar com o SIGA e com o Camunda o total protocolado em 2026, o total concluído, três etapas e três responsáveis; confirmar a **coluna de protocolo** e a **data de conclusão aproximada** (decisões provisórias); confirmar se a contagem é por **requerimento** ou por **processo**; medir o **tempo e o impacto da atualização** em horário comum. **Nenhum número real entra no repositório.** Inclua o passo `/verificar-sprint 10` numa sessão nova.
- Atualize `docs/14` (S10 concluída com os 9 blocos; S11 segue sem prompt), `docs/10` (P42 resolvida como "instantâneo em memória no lugar da tabela de apoio"; pontos provisórios; riscos), `docs/progresso/s10.md`, `docs/15`, `docs/prompts/INDICE.md` (linha da S10).
- Varredura final de dados reais e `git diff --stat`.

---

## Critérios de aceite (ao final do bloco 9)

1. O painel mostra, para o **ano considerado**, os três gráficos, com os números de PN01 a PN16 corretos e sempre com o ano visível.
2. Nenhuma requisição ao painel consulta o Camunda ou o Oracle do SIGA; só a atualização consulta, em lotes, com teto, tempo limite e limite de leituras simultâneas.
3. Camunda ou SIGA fora do ar não derrubam nada: `DESATUALIZADO` com o último bom, ou `INDISPONIVEL`.
4. Nenhum CPF (nem mascarado), id técnico ou chave de arquivo em resposta, tela, log ou auditoria; com nomes ocultos, nenhum nome de servidor.
5. Só Gestor e Administrador veem o painel; o Operador não vê o item e leva 403.
6. Tudo que não é certo aparece como **nota ou selo** (data de conclusão aproximada, coluna de protocolo provisória, fora do catálogo, sem instância, não mapeado, truncado), nunca como número afirmado.
7. Todo SQL novo é `SELECT`, parametrizado, com limite e tempo limite, num único componente; nada é gravado no Oracle; gráficos sem biblioteca nova.
8. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/s10.md` atualizados.
