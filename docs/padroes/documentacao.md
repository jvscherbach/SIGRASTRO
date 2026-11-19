# Documentação do banco

## `COMMENT ON` obrigatório (schema próprio)

```sql
COMMENT ON TABLE AUDITORIA_EVENTO IS 'Evento de auditoria do Rastro (somente inclusão). Grupos DOCUMENTOS e ACESSOS.';
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

## Entregáveis em `docs/database/` (obrigatório a partir da primeira migration)

Toda entrega que criar ou alterar objeto do schema próprio (migration Flyway) atualiza, **na mesma entrega**:

| Arquivo | Conteúdo |
|---|---|
| `docs/database/dicionario-de-dados.html` | Dicionário de dados em HTML (detalhes abaixo) |
| `docs/database/mer.html` | MER mapeado renderizado (Mermaid `erDiagram`), com as tabelas agrupadas por módulo e as referências lógicas ao SIGA tracejadas |
| `docs/database/mer.mmd` | Fonte Mermaid do MER mapeado (versionada; é a origem do `mer.html`) |
| `docs/database/mer-conceitual.mmd` | MER abstrato (entidades e relacionamentos, sem tipos) |
| `docs/database/README.md` | Como os arquivos são gerados, versão do Flyway a que correspondem, data da geração |

### Dicionário de dados (HTML)

- Arquivo único e autocontido (CSS embutido; sem dependência de rede para ler), tema claro com a paleta do projeto, navegável por índice de tabelas e busca por nome de tabela/coluna.
- Cabeçalho: schema, versão do Flyway (última linha de sucesso em `flyway_schema_history`), data e hora da geração, SGBD de origem (Oracle 23 Free local) e o aviso de compatibilidade com 19c.
- Por tabela: módulo dono, comentário da tabela, colunas (ordem, nome, tipo com tamanho/precisão, obrigatoriedade, default, identity, comentário), PK, FKs (com tabela referenciada e link), UKs, CHECKs (condição), índices (colunas e justificativa) e **decisões de design não óbvias** (retratos da auditoria, referências lógicas ao SIGA, desnormalizações aceitas).
- Seção final **"Leitura do SIGA"**: tabelas e colunas do SIGA consumidas por `integration.siga`, com a consulta que usa cada uma (a partir do inventário de `docs/04-modelo-de-dados.md`). Deixar claro que não são objetos do schema próprio.
- Abreviações usadas nos nomes (dicionário de abreviações).

### Como gerar

- Gerado **a partir do banco**, não escrito à mão: um script em `scripts/database/` executa as consultas acima (e `ALL_TAB_COLS`, `USER_CONS_COLUMNS`, `flyway_schema_history`) contra o **Oracle 23 Free local** do Compose, já com as migrations aplicadas, e grava o HTML. As justificativas de índice e as decisões de design vêm dos `COMMENT ON` e de um arquivo complementar versionado (`docs/database/complementos.yaml`).
- O mesmo script gera o `mer.mmd` a partir das constraints; o agrupamento por módulo e as referências lógicas ao SIGA vêm do `complementos.yaml`.
- O script nunca se conecta ao banco do SIGA nem a homologação/produção.
- Divergência entre `docs/database/` e as migrations é motivo para a entrega não ser considerada pronta.
