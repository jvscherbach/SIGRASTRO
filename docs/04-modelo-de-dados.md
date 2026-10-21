# 04 — Modelo de dados

Somente leitura. Schemas: `SIGA2_LICENCIAMENTO` (negócio), `SIGA2_CAMUNDA` (motor de processos) e `SIGA2_OUTORGA` (títulos legados).

## Requerimento e processo

| Tabela | Uso | Colunas principais |
|---|---|---|
| `REQUERIMENTO` | Requerimento | `ID`, `NUMERO` (`N/AAAA`), `NUMERO_PROCESSO`, `DATA_ABERTURA`, `DATA_PROTOCOLO_PROCESSO`, `DATA_ALTERACAO`, `SITUACAO_PROCESSO`, `ID_PESSOA`, `ID_TIPO_PROCESSO`, `ID_TIPO_REQUERIMENTO`, `ID_PROCESSO`, `PESO_PRIORIDADE` |
| `PROCESSO` | Processo | `ID`, `NUMERO`, `NUMERO_PROTOCOLO`, `DATA_ABERTURA` |
| `PESSOA` | Interessado | `ID`, `ID_CORPORATIVO` (chave no SCORP), `NOME`, `CPF_CNPJ` |
| `TIPO_PROCESSO` | Tipo de processo | `DESCRICAO` |
| `TIPO_REQUERIMENTO` | Tipo de requerimento | descrição (**NÃO CONFIRMADO** o nome da coluna) |
| `HISTORICO_REQUERIMENTO` | Eventos do requerimento | `ID_REQUERIMENTO`, `DATA_CADASTRO`, `EVENTO` |

Observações:

- Regra do SIGA: cada processo nasce de um único requerimento (relação 1:1). A busca atual usa `REQUERIMENTO.NUMERO_PROCESSO`; a diferença em relação a `PROCESSO.NUMERO` precisa ser verificada.
- `SITUACAO_PROCESSO` guarda o nome do enum (ex.: `EM_ANALISE`). A descrição para exibição é mapeada no back-end (tabela em [Apêndice A](#apêndice-a--situações)).
- **NÃO CONFIRMADO**: as tabelas de objetivo, finalidade, atividade e o bloco "Tipo / Objetivo" exibidos na tela atual, e a regra que transforma `PESO_PRIORIDADE` em "Atendimento normal". É preciso mapeá-los antes da implementação da tela.

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

Para processo, troque o filtro por `r.NUMERO_PROCESSO = :numero`. O resultado é um único requerimento (relação 1:1).

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

Sem linhas significa que a instância está encerrada ou parada em gateway/evento.

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

## Privilégios do usuário de leitura (`VISTAS_LEITURA_SIGA`)

Somente `CREATE SESSION` e `SELECT` em (na versão definitiva, preferir views `VW_` criadas pelo DBA):

- `SIGA2_LICENCIAMENTO`: `REQUERIMENTO`, `PROCESSO`, `PESSOA`, `TIPO_PROCESSO`, `TIPO_REQUERIMENTO`, `HISTORICO_REQUERIMENTO`, `REQUERIMENTO_REPRESENTANTE_LEGAL`, `REQUERIMENTO_RESPONSAVEL_TECNICO`, `IMOVEL`, `COMPLEMENTACAO`, `COMPLEMENTACAO_ITEM`, `PARECER_TECNICO`, as tabelas de documentos acima, `DOCUMENTO_PROCESSO_SIGNATARIO` e as tabelas de objetivo/finalidade/atividade (a mapear).
- `SIGA2_CAMUNDA`: `ACT_RU_EXECUTION`, `ACT_RU_TASK`, `ACT_RE_PROCDEF`, `ACT_HI_PROCINST`, `ACT_HI_TASKINST`.

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
