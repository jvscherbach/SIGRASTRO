# 04 — Modelo de dados

Somente leitura. Schemas: `SIGA2_LICENCIAMENTO` (negócio), `SIGA2_CAMUNDA` (motor de processos), `SIGA2_OUTORGA` (objeto da outorga e títulos legados) e `SIGA2_ARRECADACAO` (descrição das atividades).

## Requerimento e processo

| Tabela | Uso | Colunas principais |
|---|---|---|
| `REQUERIMENTO` | Requerimento | `ID`, `NUMERO` (`N/AAAA`), `NUMERO_PROCESSO`, `DATA_ABERTURA`, `DATA_PROTOCOLO_PROCESSO`, `DATA_ALTERACAO`, `SITUACAO_PROCESSO`, `ID_PESSOA`, `ID_TIPO_PROCESSO`, `ID_TIPO_REQUERIMENTO`, `ID_PROCESSO`, `PESO_PRIORIDADE` |
| `PROCESSO` | Histórico do que foi protocolado (o SIGA Vistas **não lê**; ver [P01](#número-de-processo-requerimentonumero_processo)) | `ID`, `NUMERO`, `NUMERO_PROTOCOLO`, `DATA_ABERTURA` |
| `PESSOA` | Interessado (e nome de reserva do responsável pela tarefa) | `ID`, `ID_CORPORATIVO` (chave no SCORP), `NOME`, `CPF_CNPJ` (só dígitos, 11 ou 14) |
| `TIPO_PROCESSO` | Tipo de processo | `DESCRICAO` |
| `TIPO_REQUERIMENTO` | Tipo do requerimento | `ID`, `DESCRICAO` |
| `HISTORICO_REQUERIMENTO` | Eventos do requerimento | `ID_REQUERIMENTO`, `DATA_CADASTRO`, `EVENTO` |
| `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO` | Prioridade de atendimento | ver [P01](#mapeamento-de-objeto-e-prioridade-p01) |
| `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO` | Objeto do licenciamento | ver [P01](#mapeamento-de-objeto-e-prioridade-p01) |
| `SERVIDOR` | Nome do responsável pela tarefa (cache do SCORP) | `ID`, `CPF`, `NOME`, `DATA_ATUALIZACAO` |

Observações:

- `REQUERIMENTO.NUMERO_PROCESSO` **não é único** (1:N): a busca por número de processo pode devolver mais de um requerimento. A chave é sempre `REQUERIMENTO.ID`. Detalhes na seção P01.
- `SITUACAO_PROCESSO` guarda o nome do enum (ex.: `EM_ANALISE`). A descrição para exibição é mapeada no back-end (tabela em [Apêndice A](#apêndice-a--situações)).
- Objeto (objetivo, finalidade, atividade, Tipo/Objetivo), prioridade e tipo do requerimento: mapeados na [seção P01](#mapeamento-de-objeto-e-prioridade-p01). `REQUERIMENTO.PESO_PRIORIDADE` **não** é usado.

## Documentos

| Origem | Tabela | Chave do arquivo | Data | Vínculo com o requerimento | Vigência | Sigilo |
|---|---|---|---|---|---|---|
| `DOCUMENTO_PROCESSO` | `DOCUMENTO_PROCESSO` | `CHAVE` | `DATA_CRIACAO` | `ID_REQUERIMENTO` | `DATA_FIM`, chave `"0"` | `SIGILOSO` |
| `DOCUMENTO_REQUERIMENTO` | `DOCUMENTO_REQUERIMENTO` | `CHAVE` | `DATA_UPLOAD` | `ID_REQUERIMENTO` | `DATA_FIM` | `SIGILOSO` |
| `COMPLEMENTACAO` | `COMPLEMENTACAO_ITEM_ARQUIVO` | `CHAVE_PANDORA` | `DATA_INICIO` | `COMPLEMENTACAO_ITEM` → `COMPLEMENTACAO.ID_REQUERIMENTO` | `DATA_FIM` | `SIGILOSO` |
| `IMOVEL` | `DOCUMENTO_IMOVEL` | `CHAVE` | `DATA_INICIO` | `IMOVEL.ID_REQUERIMENTO` | `DATA_FIM` | `SIGILOSO` |
| `REPRESENTANTE_LEGAL` | `REQ_REP_LEGAL_DOCUMENTO` | `CHAVE` | `DATA_INICIO` | `REQUERIMENTO_REPRESENTANTE_LEGAL.ID_REQUERIMENTO` | `DATA_FIM` | `SIGILO` |
| `RESPONSAVEL_TECNICO` | `REQ_RESP_TECNICO_DOCUMENTO` | `CHAVE` | `DATA_INICIO` | `REQUERIMENTO_RESPONSAVEL_TECNICO.ID_REQUERIMENTO` | `DATA_FIM` | `SIGILO` |
| `PARECER_ANEXO` | `PARECER_TECNICO_ANEXO` | `CHAVE_PANDORA` | `DATA_CRIACAO` | `PARECER_TECNICO.ID_REQUERIMENTO` | `DATA_EXCLUSAO` | — |
| `JUNTADA` | `JUNTADA_PROCESSO` | `CHAVE` | `DATA_CADASTRO` | `ID_REQUERIMENTO_PRINCIPAL` | — | `IND_SIGILOSO` |
| `TITULACAO` | `TITULACAO_DOCUMENTO` | `CHAVE` | `DATA_CRIACAO` | `ID_REQUERIMENTO` | — | — |
| `PRORROGACAO` | `PRORROG_PRAZO_COMPLEM_ANEXO` | `CHAVE` | `DATA_INICIO` | `COMPLEMENTACAO.ID_REQUERIMENTO` | — | — |
| `EXTRATO_PUBLICACAO` | `EXTRATO_PUBLICACAO` | `CHAVE` | `DATA_CRIACAO` | **NÃO CONFIRMADO** | `DATA_FIM` | — |
| `SCORP` | API do SCORP | `chave` | `dataInicio` | `PESSOA.ID_CORPORATIVO` (interessado, representantes, responsáveis técnicos) | `dataFim` | — |
| `DAR` | Arrecadação | **NÃO CONFIRMADO** | — | — | — | — |

As seis primeiras origens são as que a Vista atual exibe. As demais são origens não exibidas hoje. Antes de incluí-las, é preciso confirmar no banco se já possuem linha correspondente em `DOCUMENTO_PROCESSO` (mesma chave). Se possuírem, a deduplicação (RN05) resolve.

Auxiliares:

- `DOCUMENTO_PROCESSO_SIGNATARIO` (`ID_DOCUMENTO_PROCESSO`, `DATA_ASSINATURA`, `DATA_FIM`): data de assinatura usada na ordem cronológica.
- `SIGA2_OUTORGA.TITULO_LEGADO_ANEXO`: referencia `DOCUMENTO_PROCESSO`; já coberto pela primeira origem.
- `GRUPO_DOCUMENTO` (`DOCUMENTO_PROCESSO`) e `TIPO` (demais) guardam nomes de enum. A descrição para exibição é mapeada no back-end.

**Excluída sempre:** `PESSOA_DOCUMENTO_DOENCA_GRAVE` (RN02).

**Sem chave armazenada:** a cópia do requerimento em PDF só existe se houver `DOCUMENTO_PROCESSO` com `GRUPO_DOCUMENTO = 'COPIA_PROCESSO'`. O protótipo usa a última cópia salva e não tenta regenerar.

**Metadados ausentes no banco:** tipo MIME, extensão real e tamanho só são conhecidos ao obter o arquivo (cabeçalhos `Content-Type` e `Content-Length`). A extensão do `NOME_ARQUIVO` serve de indicação prévia.

## Camunda

| Tabela | Uso |
|---|---|
| `ACT_RU_EXECUTION` | Instância ativa. `BUSINESS_KEY_` = `REQUERIMENTO.ID` (texto), na execução raiz |
| `ACT_RU_TASK` | Tarefa ativa: `NAME_`, `TASK_DEF_KEY_`, `ASSIGNEE_`, `CREATE_TIME_` |
| `ACT_RE_PROCDEF` | Definição do processo: `KEY_`, `NAME_` |
| `ACT_HI_PROCINST`, `ACT_HI_TASKINST` | Histórico (uso futuro) |

## Consultas de referência

Validar os nomes de colunas no banco antes de usar.

### Requerimento por número

```sql
SELECT r.ID, r.NUMERO, r.NUMERO_PROCESSO, r.DATA_ABERTURA, r.DATA_ALTERACAO,
       r.SITUACAO_PROCESSO, tp.DESCRICAO AS TIPO_PROCESSO,
       p.NOME AS INTERESSADO, p.CPF_CNPJ, p.ID_CORPORATIVO
FROM   SIGA2_LICENCIAMENTO.REQUERIMENTO r
JOIN   SIGA2_LICENCIAMENTO.TIPO_PROCESSO tp ON tp.ID = r.ID_TIPO_PROCESSO
LEFT   JOIN SIGA2_LICENCIAMENTO.PESSOA p   ON p.ID  = r.ID_PESSOA
WHERE  r.NUMERO = :numero;
```

Para processo, troque o filtro por `r.NUMERO_PROCESSO = :numero`. O resultado pode ter mais de um requerimento (1:N); ordene por `r.DATA_ABERTURA DESC, r.ID DESC`.

### Tarefa atual

```sql
SELECT t.NAME_, t.TASK_DEF_KEY_, t.ASSIGNEE_, t.CREATE_TIME_, d.NAME_ AS PROCESSO
FROM   SIGA2_CAMUNDA.ACT_RU_EXECUTION e
JOIN   SIGA2_CAMUNDA.ACT_RU_TASK t    ON t.PROC_INST_ID_ = e.PROC_INST_ID_
JOIN   SIGA2_CAMUNDA.ACT_RE_PROCDEF d ON d.ID_ = t.PROC_DEF_ID_
WHERE  e.PARENT_ID_ IS NULL
  AND  e.ID_ = e.PROC_INST_ID_
  AND  e.BUSINESS_KEY_ = TO_CHAR(:idRequerimento);
```

Sem linhas significa que a instância está encerrada ou parada em gateway/evento. A versão implementada (`tarefa-atual.sql`) não seleciona `ASSIGNEE_`: devolve só a situação e o nome do responsável (seção P01).

### Documentos

Um `UNION ALL` com uma parte por origem, cada uma retornando:

`ORIGEM, ID, ID_REQUERIMENTO, CHAVE, TITULO, NOME_ARQUIVO, TIPO, DATA_DOC, DATA_FIM, SIGILO`

A consulta **não filtra** vigência: todos os registros são listados, e a situação é calculada pela RN01. Exemplo da primeira parte:

```sql
SELECT 'DOCUMENTO_PROCESSO' AS ORIGEM, dp.ID, dp.ID_REQUERIMENTO, dp.CHAVE, dp.TITULO,
       dp.NOME_ARQUIVO, dp.GRUPO_DOCUMENTO AS TIPO,
       COALESCE((SELECT MAX(s.DATA_ASSINATURA)
                 FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO_SIGNATARIO s
                 WHERE s.ID_DOCUMENTO_PROCESSO = dp.ID AND s.DATA_FIM IS NULL),
                dp.DATA_CRIACAO) AS DATA_DOC,
       dp.DATA_FIM, dp.SIGILOSO AS SIGILO
FROM   SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO dp
WHERE  dp.ID_REQUERIMENTO = :idRequerimento
```

## Leitura implementada na S1 e na S1.1 (inventário)

Tudo que o `integration.siga` lê hoje. É a base da lista de grants do usuário de leitura e dos roteiros de homologação ([`S1`](homologacao/S1-roteiro-homologacao.md), [`S1.1`](homologacao/S1.1-roteiro-homologacao.md)). Os nomes da S1.1 vêm da seção P01; **os tipos das colunas ainda não foram conferidos no banco real** (o recorte de teste usa hipóteses). Nenhuma consulta lê a tabela `PROCESSO`.

| Arquivo (`resources/sql/siga/`) | Tabelas | Colunas lidas |
|---|---|---|
| `requerimento-por-numero.sql`, `requerimento-por-id.sql`, `requerimentos-por-interessado.sql`, `requerimentos-contagem.sql` | `SIGA2_LICENCIAMENTO.REQUERIMENTO` | `ID`, `NUMERO`, `NUMERO_PROCESSO`, `DATA_ABERTURA`, `DATA_ALTERACAO`, `SITUACAO_PROCESSO`, `ID_PESSOA`, `ID_TIPO_PROCESSO`, `ID_TIPO_REQUERIMENTO` (só por id e por número) |
| idem | `SIGA2_LICENCIAMENTO.PESSOA` (LEFT JOIN: interessado pode ser nulo) | `ID`, `NOME`, `CPF_CNPJ` |
| idem (não usada na contagem) | `SIGA2_LICENCIAMENTO.TIPO_PROCESSO` (LEFT JOIN) | `ID`, `DESCRICAO` |
| `requerimento-por-numero.sql`, `requerimento-por-id.sql` | `SIGA2_LICENCIAMENTO.TIPO_REQUERIMENTO` (LEFT JOIN) | `ID`, `DESCRICAO` |
| `objetivos-outorga.sql` | `SIGA2_OUTORGA.REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA` | `REQUERIMENTO_OUTORGA.ID`, `ID_REQUERIMENTO`; `OBJETIVO_REQUERIMENTO_OUTORGA.ID`, `ID_REQUERIMENTO_OUTORGA`, `ID_OBJETIVO_OUTORGA` (**NÃO CONFIRMADO**), `DATA_FIM`; `OBJETIVO_OUTORGA.ID`, `DESCRICAO`, `TIPO_OUTORGA` |
| `finalidades-atividades-outorga.sql` | `SIGA2_OUTORGA.REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE`; `SIGA2_ARRECADACAO.ATIVIDADE` (LEFT JOIN) | `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID`, `ID_REQUERIMENTO`, `ID_FINALIDADE_ATIVIDADE` (**NÃO CONFIRMADO**); `FINALIDADE_ATIVIDADE.ID`, `ID_FINALIDADE` (**NÃO CONFIRMADO**), `ID_ATIVIDADE_ARRECADACAO`; `FINALIDADE.ID`, `DESCRICAO`; `ATIVIDADE.ID`, `DESCRICAO` |
| `objeto-licenciamento.sql` | `SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO` (LEFT JOIN) | `REQUERIMENTO_LICENCIAMENTO.ID`, `ID_REQUERIMENTO`, `DESCRICAO_ATIVIDADE`, `DESCRICAO_GRUPO_ATIVIDADE`; `OBJETIVO_LICENCIAMENTO.ID`, `ID_REQUERIMENTO_LICENCIAMENTO`, `DESCRICAO_OBJETIVO_LICENCIAMENTO` |
| `prioridade.sql` | `SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO` | `PRIORIDADE_PROCESSO.ID`, `ID_REQUERIMENTO`; `PRIORIDADE_PROCESSO_CRITERIO.ID_PRIORIDADE_PROCESSO`, `ID_TIPO_PRIORIDADE_PROCESSO` |
| `tarefa-atual.sql` | `SIGA2_CAMUNDA.ACT_RU_EXECUTION` | `ID_`, `PROC_INST_ID_`, `PARENT_ID_`, `BUSINESS_KEY_` |
| idem | `SIGA2_CAMUNDA.ACT_RU_TASK` | `PROC_INST_ID_`, `PROC_DEF_ID_`, `NAME_`, `ASSIGNEE_` (só dentro do SQL; não é selecionado), `CREATE_TIME_` |
| idem | `SIGA2_CAMUNDA.ACT_RE_PROCDEF` | `ID_`, `NAME_` |
| idem | `SIGA2_LICENCIAMENTO.SERVIDOR`, `PESSOA` | `SERVIDOR.ID`, `CPF`, `NOME`, `DATA_ATUALIZACAO`; `PESSOA.ID`, `CPF_CNPJ`, `NOME` |

As três colunas de ligação marcadas como **NÃO CONFIRMADO** não constam da seção P01 (que nomeia a cadeia de junção, mas não a coluna de chave estrangeira). Foram usados os nomes do padrão do SIGA (`ID_<TABELA>`); a conferência em `ALL_TAB_COLUMNS` está no roteiro da S1.1.

Comportamento das consultas:

- **Por número de requerimento:** `REQUERIMENTO.NUMERO = :numero`, exato. Mais de uma linha é anomalia: usa-se a mais recente (maior `ID`) e registra-se aviso no log.
- **Por número de processo:** `REQUERIMENTO.NUMERO_PROCESSO = :numero` pela mesma consulta paginada da lista: devolve **todos** os requerimentos daquele número (1:N), com a restrição do interessado (RN07) no SQL. Para o Operador, se nenhum é dele, o caso de uso confere se o número existe (mesma consulta, sem restrição, 1 linha) para decidir entre 403 e 404.
- **Lista** (`CPF/CNPJ`, nome, número): predicados opcionais acrescentados pelo adapter ao `WHERE`: CPF/CNPJ por comparação direta (`p.CPF_CNPJ = :cpfCnpj`, só dígitos; o domínio normaliza a entrada), nome por `UPPER(p.NOME) LIKE :nome ESCAPE '\'` (com `%`, `_` e `\` digitados escapados) e, para perfis restritos ao interessado (RN07), `p.CPF_CNPJ = :restricao`. Ordem: `DATA_ABERTURA DESC, ID DESC`; paginação `OFFSET/FETCH`; total por `COUNT(*)` com os mesmos filtros. **Risco:** o `LIKE` com `UPPER` e a busca por `NUMERO_PROCESSO` (sem índice) não usam índice; ver R09 e R14.
- **Tarefa atual:** consulta de referência acima, acrescida de `ORDER BY t.CREATE_TIME_ DESC FETCH FIRST 1 ROWS ONLY` e da resolução do nome do responsável (seção P01). Sem linhas = `SEM_TAREFA`; qualquer falha de leitura = `INDISPONIVEL`.
- **Objeto e prioridade:** uma consulta por bloco, sem junção entre os blocos. Falha de leitura derruba o detalhe (503 `SIGA_INDISPONIVEL` ou 500), sem estado parcial.
- Situação: `SITUACAO_PROCESSO` é traduzida pelo Apêndice A; valor fora da lista aparece com o texto cru.

## Mapeamento de objeto e prioridade (P01)

> Origem: análise do código do SIGA Licenciamento (front `cabecalho-requerimento`, back `RequerimentoOutorgaService`, `PrioridadeProcessoService`, `ServidorService`, entidades JPA) e conferência no banco pelo responsável em 21/10/2026. Os nomes de colunas foram confirmados em `ALL_TAB_COLUMNS`. O usuário de leitura do SIGA Vistas tem `SELECT` em todas as tabelas abaixo. A tela atual foi comparada campo a campo com o processo 6023/2025 (outorga), e todos os campos bateram.

### Campos

| Campo da tela | Origem | Junção a partir de `REQUERIMENTO.ID` | Cardinalidade | Regra |
|---|---|---|---|---|
| Objetivo (outorga) | `SIGA2_OUTORGA.OBJETIVO_OUTORGA.DESCRICAO` | `REQUERIMENTO_OUTORGA.ID_REQUERIMENTO` → `OBJETIVO_REQUERIMENTO_OUTORGA.ID_REQUERIMENTO_OUTORGA` → `OBJETIVO_OUTORGA.ID` | 1:N | Só objetivos vigentes: `OBJETIVO_REQUERIMENTO_OUTORGA.DATA_FIM IS NULL OR DATA_FIM > SYSTIMESTAMP` |
| Tipo (bloco Tipo/Objetivo) | `SIGA2_OUTORGA.OBJETIVO_OUTORGA.TIPO_OUTORGA` | igual ao Objetivo | 1:N | Valores `SUBTERRANEA`, `SUPERFICIAL`, `BARRAMENTO`. O SIGA Vistas exibe "Subterrânea", "Superficial" e "Barramento"; valor desconhecido aparece cru |
| Finalidade (outorga) | `SIGA2_OUTORGA.FINALIDADE.DESCRICAO` | `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_REQUERIMENTO` → `FINALIDADE_ATIVIDADE.ID` → `FINALIDADE.ID` | 1:N pares finalidade/atividade | Remoção física no SIGA (sem vigência) |
| Atividade (outorga) | `SIGA2_ARRECADACAO.ATIVIDADE.DESCRICAO` | mesma cadeia da Finalidade, por `FINALIDADE_ATIVIDADE.ID_ATIVIDADE_ARRECADACAO = ATIVIDADE.ID` | 1 por par | O SIGA busca pela API da Arrecadação; o SIGA Vistas lê a tabela diretamente (mesmo texto, conferido no processo 6023/2025, atividade 4054). `LEFT JOIN`: sem linha, "—" |
| Objetivo (licenciamento) | `SIGA2_LICENCIAMENTO.OBJETIVO_LICENCIAMENTO.DESCRICAO_OBJETIVO_LICENCIAMENTO` | `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` → `OBJETIVO_LICENCIAMENTO.ID_REQUERIMENTO_LICENCIAMENTO` | 1:N | Sem vigência |
| Atividade (licenciamento) | `SIGA2_LICENCIAMENTO.REQUERIMENTO_LICENCIAMENTO.DESCRICAO_ATIVIDADE` (e `DESCRICAO_GRUPO_ATIVIDADE`) | `REQUERIMENTO_LICENCIAMENTO.ID_REQUERIMENTO` | 1:1 | — |
| Prioridade de atendimento | `SIGA2_LICENCIAMENTO.PRIORIDADE_PROCESSO_CRITERIO` | `PRIORIDADE_PROCESSO.ID_REQUERIMENTO` → `PRIORIDADE_PROCESSO_CRITERIO.ID_PRIORIDADE_PROCESSO` | 1:N critérios | Pelo menos 1 critério: "Atendimento prioritário"; nenhum: "Atendimento normal". **Não usar `REQUERIMENTO.PESO_PRIORIDADE`** (o cadastro manual de critérios não o atualiza) |
| Tipo do requerimento | `SIGA2_LICENCIAMENTO.TIPO_REQUERIMENTO.DESCRICAO` | `REQUERIMENTO.ID_TIPO_REQUERIMENTO` (NOT NULL) | N:1 | Texto de cadastro, exibido mesmo se o tipo estiver inativo |

**Regras de apresentação:**
- Objetivos e pares Finalidade/Atividade são **listas independentes**, como na tela do SIGA. Não existe pareamento objetivo↔finalidade, e nunca se faz JOIN entre as duas listas.
- Processos de outorga têm linhas em `REQUERIMENTO_OUTORGA` e os de licenciamento, em `REQUERIMENTO_LICENCIAMENTO`. O SIGA Vistas consulta os dois blocos e mostra o que tiver dados; sem dados, "—". O bloco Tipo/Objetivo só aparece quando há objetivos de outorga.

### Critérios de prioridade (`PRIORIDADE_PROCESSO_CRITERIO.ID_TIPO_PRIORIDADE_PROCESSO`)

Descrições do enum `EnumTipoPrioridadeProcesso` do código do SIGA. O domínio oficial fica em outro serviço; a equivalência é provável, mas **NÃO CONFIRMADA**. Código desconhecido aparece como "Critério N".

| Código | Descrição |
|---|---|
| 1 | Decisão Administrativa |
| 2 | Pessoa com idade igual ou superior a 80 anos |
| 3 | Pessoa com idade igual ou superior a 60 anos |
| 4 | Pessoa com deficiência física ou mental |
| 5 | Pessoa com doença grave |
| 6 | Trata de embargo ou suspensão |
| 7 | Pré-requisito para licenciamento |
| 8 | Utilidade pública |
| 9 | Risco de grave prejuízo |

### Responsável pela tarefa

- `SIGA2_CAMUNDA.ACT_RU_TASK.ASSIGNEE_` guarda o CPF do servidor, só com 11 dígitos. Formatos encontrados em 21/10/2026: 7.745 tarefas com CPF de 11 dígitos, 39.374 nulas e 5 em outro formato (`demo` e `admin`, do console do motor de fluxo, sem uso; e uma mensagem de erro gravada no campo).
- **Nome:** `SIGA2_LICENCIAMENTO.SERVIDOR.NOME`, versão mais recente por CPF (`ORDER BY DATA_ATUALIZACAO DESC, ID DESC`); se não houver, `SIGA2_LICENCIAMENTO.PESSOA.NOME` por `CPF_CNPJ`, a mais recente por `ID`. A `SERVIDOR` é um cache do SCORP versionado por inserção, e o nome muda entre versões (casamento, correção de grafia).
- Cobertura em 21/10/2026, entre tarefas com responsável: 1.561 com nome nas duas tabelas, 5.597 só em `PESSOA`, 1 só em `SERVIDOR` e 591 em nenhuma.
- Exibição:
  - `ASSIGNEE_` nulo ou vazio: "Não atribuída";
  - CPF de 11 dígitos com nome encontrado: o nome;
  - CPF sem nome, ou qualquer outro formato: "Responsável não identificado".
  - O CPF do servidor **nunca** sai na API nem aparece na tela.
- Visibilidade (regra do SIGA Vistas; o SIGA não tem regra própria): o nome só aparece para **Gestor** (`OPERADOR_SIGILO`) e **Administrador**. O Operador não recebe o campo.

### Número de processo (`REQUERIMENTO.NUMERO_PROCESSO`)

- Não é único: não há constraint nem índice em `NUMERO_PROCESSO` (conferido em `ALL_IND_COLUMNS`), e o código do SIGA não impede repetição. O SIGA também tem um fluxo que copia o número de processo legado para um segundo requerimento.
- A tabela `PROCESSO` é um histórico do que foi protocolado, usado pelo SIGA para abrir alteração, renovação, cancelamento ou transferência sobre título emitido. O SIGA Vistas **não a consulta** e não filtra por origem: só a `REQUERIMENTO` importa (decisão do responsável).
- Em 21/10/2026 havia 1 número repetido, `445/2022`, nos requerimentos 3391/2022 (concluído, protocolado) e 3798/2023 (cancelado pelo cadastrante, sem data de protocolo), ambos no mesmo `ID_PROCESSO` e de origem SIGA. É anomalia de dado, a comunicar à equipe do SIGA.
- Regra do SIGA Vistas: a busca por número de processo trata o resultado como **1:N**. Com 1 resultado, abre o detalhe; com mais de 1, lista todos para o usuário escolher. A chave é sempre `REQUERIMENTO.ID`.
- Sem índice em `NUMERO_PROCESSO`, a busca por número de processo lê a tabela inteira, cerca de 63 mil requerimentos (risco de desempenho baixo, registrado em `docs/10`).

### Busca por CPF/CNPJ

`PESSOA.CPF_CNPJ` guarda só dígitos (11 ou 14). A busca compara direto (`p.CPF_CNPJ = :digitos`), sem `REGEXP_REPLACE`.

### Tabelas do SIGA lidas pelo SIGA Vistas (para o grant do usuário de leitura)

`SIGA2_LICENCIAMENTO`: `REQUERIMENTO`, `PESSOA`, `TIPO_PROCESSO`, `TIPO_REQUERIMENTO`, `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO`, `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO`, `SERVIDOR`.
`SIGA2_OUTORGA`: `REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE`.
`SIGA2_ARRECADACAO`: `ATIVIDADE`.
`SIGA2_CAMUNDA`: as tabelas já usadas pela tarefa atual.

### Ainda NÃO CONFIRMADO

- Descrições oficiais dos critérios de prioridade (domínio fora do banco do SIGA Licenciamento).
- Conferência na tela de um processo de **licenciamento** e de um processo **com critério de prioridade** (pendente no roteiro de homologação).
- Tarefas atribuídas a grupo (`ASSIGNEE_` nulo com candidatos): hoje aparecem como "Não atribuída".

## Privilégios do usuário de leitura (`VISTAS_LEITURA_SIGA`)

Somente `CREATE SESSION` e `SELECT` em (na versão definitiva, preferir views `VW_` criadas pelo DBA):

- `SIGA2_LICENCIAMENTO`: `REQUERIMENTO`, `PESSOA`, `TIPO_PROCESSO`, `TIPO_REQUERIMENTO`, `PRIORIDADE_PROCESSO`, `PRIORIDADE_PROCESSO_CRITERIO`, `REQUERIMENTO_LICENCIAMENTO`, `OBJETIVO_LICENCIAMENTO`, `SERVIDOR`, `HISTORICO_REQUERIMENTO`, `REQUERIMENTO_REPRESENTANTE_LEGAL`, `REQUERIMENTO_RESPONSAVEL_TECNICO`, `IMOVEL`, `COMPLEMENTACAO`, `COMPLEMENTACAO_ITEM`, `PARECER_TECNICO`, as tabelas de documentos acima e `DOCUMENTO_PROCESSO_SIGNATARIO`. `PROCESSO` não é necessária (P03).
- `SIGA2_OUTORGA`: `REQUERIMENTO_OUTORGA`, `OBJETIVO_REQUERIMENTO_OUTORGA`, `OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE`, `FINALIDADE_ATIVIDADE`, `FINALIDADE` (e `TITULO_LEGADO_ANEXO` na S2, se for preciso).
- `SIGA2_ARRECADACAO`: `ATIVIDADE`.
- `SIGA2_CAMUNDA`: `ACT_RU_EXECUTION`, `ACT_RU_TASK`, `ACT_RE_PROCDEF`, `ACT_HI_PROCINST`, `ACT_HI_TASKINST`.

**Grants necessários até a S1.1** (só estes): `CREATE SESSION` e `SELECT` nas tabelas da lista "Tabelas do SIGA lidas pelo SIGA Vistas" (seção P01), mais `SIGA2_CAMUNDA.ACT_RU_EXECUTION`, `ACT_RU_TASK` e `ACT_RE_PROCDEF`. Os testes de integração criam um usuário com exatamente esses grants. O script de referência para o DBA está no [roteiro da S1.1](homologacao/S1.1-roteiro-homologacao.md).

## Apêndice A — Situações

| Valor | Descrição |
|---|---|
| `EM_CADASTRO` | Em cadastro |
| `EM_CORRECAO_CADASTRO` | Em correção de cadastro |
| `AGUARDANDO_CONFERENCIA` | Aguardando conferência |
| `EM_CONFERENCIA` | Em conferência |
| `CHECKLIST_REPROVADO` | Checklist reprovado |
| `ENCAMINHADO_PARA_ARRECADACAO` | Encaminhado para arrecadação |
| `CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE` | Cancelado por solicitação do cadastrante |
| `CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO` | Cancelado por expiração de prazo de cadastro |
| `CANCELADO_POR_FALTA_DE_PAGAMENTO` | Cancelado por falta de pagamento |
| `AGUARDANDO_EMISSAO_DE_TAXAS` | Aguardando emissão de taxas |
| `AGUARDANDO_PAGAMENTO_DE_TAXAS` | Aguardando pagamento de taxas |
| `AGUARDANDO_ASSINATURA_LAC` | Aguardando assinatura LAC |
| `AGUARDANDO_ATRIBUICAO` | Aguardando atribuição |
| `AGUARDANDO_ANALISE` | Aguardando análise |
| `AGUARDANDO_ELABORACAO` | Aguardando elaboração |
| `AGUARDANDO_ACEITE_ATRIBUICAO` | Aguardando aceite de atribuição |
| `EM_ANALISE` | Em análise |
| `AGUARDANDO_PUBLICACAO` | Aguardando publicação |
| `AGUARDANDO_CHECKLIST_DOCUMENTOS` | Aguardando checklist de documentos |
| `ARQUIVADO_DEFINITIVAMENTE` | Arquivado definitivamente |
| `ARQUIVADO_TEMPORARIAMENTE` | Arquivado temporariamente |
| `CONCLUIDO` | Concluído |
| `CANCELADO` | Cancelado |
| `AGUARDANDO_REVISAO` | Aguardando revisão |
| `EM_REVISAO` | Em revisão |
| `REQUERIMENTO_REVISADO` | Requerimento revisado |
| `AGUARDANDO_CORRECAO` | Aguardando correção |
| `EM_CORRECAO` | Em correção |
| `REQUERIMENTO_CORRIGIDO` | Requerimento corrigido |

As descrições exatas devem ser copiadas do enum `EnumSituacaoProcesso` do SIGA.
