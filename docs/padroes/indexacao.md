# Indexação

## 1. Toda FK com índice

Oracle não indexa FK automaticamente. Criar junto da constraint:

```sql
ALTER TABLE AUDITORIA_EVENTO_DOCUMENTO ADD CONSTRAINT FK_AUD_EVT_DOC_AUD_EVT
  FOREIGN KEY (ID_AUDITORIA_EVENTO) REFERENCES AUDITORIA_EVENTO (ID_AUDITORIA_EVENTO);
CREATE INDEX IX_AUD_EVT_DOC_ID_AUD_EVT ON AUDITORIA_EVENTO_DOCUMENTO (ID_AUDITORIA_EVENTO);
```

Referências ao SIGA (`ID_REQUERIMENTO`, `ID_DOCUMENTO`) são lógicas, sem FK (bancos diferentes), mas podem precisar de índice se forem filtro.

## 2. Indexar o acesso real

| Tabela | Índice | Consulta que atende |
|---|---|---|
| `AUDITORIA_EVENTO` | `(GRUPO, DATA_HORA)` | lista por período em cada tela de auditoria |
| `AUDITORIA_EVENTO` | `(ID_REQUERIMENTO, DATA_HORA)` | auditoria do Operador (só processos em que é o interessado) |
| `AUDITORIA_EVENTO` | `(LOGIN_USUARIO, DATA_HORA)` | filtro por usuário |
| `AUDITORIA_EVENTO` | `(SIGILOSO, DATA_HORA)` só se o filtro "somente sigilosos" se mostrar lento | filtro de sigilosos |
| `DOSSIE_PEDIDO` | `(STATUS, DATA_CRIACAO)` | fila de processamento |
| `DOSSIE_PEDIDO` | `(DATA_EXPIRACAO)` | rotina de expiração |
| `USUARIO` | `UK_USUARIO_LOGIN` | login |
| `USUARIO` | `(CPF_CNPJ)` | vínculo do interessado |

## 3. Não exagerar

A auditoria é a tabela de maior escrita. Cada índice extra custa em toda inclusão. Criar só com consulta real que o justifique.

## 4. Leitura do SIGA

Índices no banco do SIGA não são nossos. Se uma consulta do Vistas ficar lenta, levar o plano de execução ao DBA do SIGA; nunca criar índice lá por conta própria.

## Monitoramento

```sql
ALTER INDEX IX_AUD_EVT_GRUPO_DATA MONITORING USAGE;
SELECT * FROM DBA_INDEX_USAGE WHERE NAME = 'IX_AUD_EVT_GRUPO_DATA';
```

Em ambiente institucional, confirmar acesso de leitura a `DBA_INDEX_USAGE`.
