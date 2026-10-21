# Documentação do banco

## `COMMENT ON` obrigatório (schema próprio)

```sql
COMMENT ON TABLE AUDITORIA_EVENTO IS 'Evento de auditoria do SIGA Vistas (somente inclusão). Grupos DOCUMENTOS e ACESSOS.';
COMMENT ON COLUMN AUDITORIA_EVENTO.NUMERO_PROCESSO IS 'Retrato do número do processo no momento do evento. Não é sincronizado com o SIGA.';
COMMENT ON COLUMN AUDITORIA_EVENTO.ID_REQUERIMENTO IS 'Referência lógica a SIGA2_LICENCIAMENTO.REQUERIMENTO.ID (sem FK: banco externo).';
```

- Comentar toda tabela e toda coluna não autoexplicativa: enums, flags, retratos, referências lógicas ao SIGA.
- Toda coluna que guarda dado vindo do SIGA ou de integração externa diz de onde vem e se é retrato.

## Dicionário de dados do schema próprio

```sql
SELECT TABLE_NAME, COMMENTS FROM USER_TAB_COMMENTS WHERE TABLE_NAME NOT LIKE 'BIN$%' ORDER BY 1;

SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE, c.DATA_LENGTH, c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE, cc.COMMENTS
FROM USER_TAB_COLUMNS c
LEFT JOIN USER_COL_COMMENTS cc ON cc.TABLE_NAME = c.TABLE_NAME AND cc.COLUMN_NAME = c.COLUMN_NAME
ORDER BY c.TABLE_NAME, c.COLUMN_ID;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, SEARCH_CONDITION, R_CONSTRAINT_NAME
FROM USER_CONSTRAINTS WHERE CONSTRAINT_TYPE IN ('P','R','U','C') ORDER BY 1, 3;

SELECT i.TABLE_NAME, i.INDEX_NAME, i.UNIQUENESS,
       LISTAGG(ic.COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY ic.COLUMN_POSITION) AS COLUNAS
FROM USER_INDEXES i JOIN USER_IND_COLUMNS ic ON ic.INDEX_NAME = i.INDEX_NAME
GROUP BY i.TABLE_NAME, i.INDEX_NAME, i.UNIQUENESS ORDER BY 1;
```

Estrutura por tabela: descrição, colunas (tipo, obrigatoriedade, comentário), constraints, índices com justificativa e **decisões de design não óbvias** (ex.: retratos da auditoria).

## Inventário do que é lido do SIGA

Manter em `docs/04-modelo-de-dados.md` a lista de tabelas e colunas do SIGA consumidas por `integration.siga`, com a consulta que usa cada uma. É a base para:

- a lista de grants (ou views) do usuário de leitura;
- avaliar impacto quando o SIGA mudar o modelo.

## PDF do dicionário

Montar o conteúdo a partir das consultas e só depois gerar o PDF.
