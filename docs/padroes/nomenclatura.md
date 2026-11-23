# Nomenclatura (padrão em português)

Vale para o **schema próprio** do SIGRASTRO. O banco do SIGA tem nomes próprios que não alteramos; nas consultas, eles são usados como estão.

## Arquivo de migration ≠ objetos dentro dela

```
V1__create_audit_event_table.sql          <- arquivo: inglês, convenção Flyway do projeto
  CREATE TABLE AUDITORIA_EVENTO (          <- objetos: português, MAIÚSCULO
    ID_AUDITORIA_EVENTO NUMBER(12) GENERATED ALWAYS AS IDENTITY,
    DATA_HORA TIMESTAMP WITH TIME ZONE NOT NULL,
    ...
    CONSTRAINT PK_AUDITORIA_EVENTO PRIMARY KEY (ID_AUDITORIA_EVENTO)
  );
```

Nunca traduzir o nome do arquivo, nunca deixar tabela/coluna em inglês.

## Regras gerais

- Identificadores em MAIÚSCULO, sem aspas.
- Sem acento e sem cedilha (`ALTERACAO`, não `ALTERAÇÃO`).
- `snake_case`; tabelas no singular.
- Limite de identificador: até confirmar o `COMPATIBLE` da instância institucional, manter até **30 bytes**.
- Schema próprio: `VISTAS` (nome provisório, acompanha o nome do projeto). Usuários: `VISTAS` (owner), `APP_VISTAS` (aplicação), `VISTAS_LEITURA_SIGA` (leitura do SIGA).

## Por tipo de objeto

| Objeto | Padrão | Exemplo no SIGRASTRO |
|---|---|---|
| Tabela | Substantivo singular | `AUDITORIA_EVENTO`, `DOSSIE_PEDIDO`, `USUARIO` |
| PK | `ID_<TABELA>` | `ID_DOSSIE_PEDIDO` |
| FK | Igual à PK referenciada | `ID_AUDITORIA_EVENTO` em `AUDITORIA_EVENTO_DOCUMENTO` |
| Referência lógica ao SIGA | `ID_<CONCEITO>_SIGA` ou o nome do SIGA, sem FK | `ID_REQUERIMENTO` (sem constraint) |
| Constraint PK | `PK_<TABELA>` | `PK_AUDITORIA_EVENTO` |
| Constraint FK | `FK_<TABELA>_<TABELA_REF>` (abreviar se passar de 30) | `FK_AUD_EVT_DOC_AUD_EVT` |
| Constraint UK | `UK_<TABELA>_<COLUNA>` | `UK_USUARIO_LOGIN` |
| Constraint CHECK | `CK_<TABELA>_<REGRA>` | `CK_USUARIO_PERFIL` |
| Índice | `IX_<TABELA>_<COLUNA(S)>` | `IX_AUD_EVT_GRUPO_DATA` |
| View | `VW_<NOME>` | `VW_SIGA_DOCUMENTO` (views de leitura do SIGA, criadas pelo DBA) |
| Trigger | `TRG_<TABELA>_<EVENTO>` | evitar; regra fica na aplicação |

## Correspondência módulo → tabela

| Módulo | Tabelas |
|---|---|
| `acesso` | `USUARIO` |
| `configuracao` | `PARAMETRO` |
| `auditoria` | `AUDITORIA_EVENTO`, `AUDITORIA_EVENTO_DOCUMENTO`, `AUDITORIA_EVENTO_ALTERACAO` |
| `dossie` | `DOSSIE_PEDIDO`, `DOSSIE_PEDIDO_DOCUMENTO` |

## Colunas de controle

- `DATA_CRIACAO` e `DATA_ATUALIZACAO` em tabelas alteráveis (`USUARIO`, `PARAMETRO`, `DOSSIE_PEDIDO`).
- Tabelas de auditoria são só inclusão: `DATA_HORA` do evento; sem `DATA_ATUALIZACAO`.
- Situação de usuário por `ATIVO NUMBER(1)` (o histórico de mudanças já fica na auditoria de acessos).

## Abreviações (registrar no dicionário de dados)

`AUD` (auditoria), `EVT` (evento), `DOC` (documento), `PED` (pedido), `ALT` (alteração), `DT` (data), `QTD` (quantidade).
