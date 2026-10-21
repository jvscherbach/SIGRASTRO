# Privilégios mínimos

## Três identidades de banco

| Usuário | Onde | Privilégios | Quem provisiona |
|---|---|---|---|
| `VISTAS_LEITURA_SIGA` | banco do SIGA | `CREATE SESSION` + `SELECT` só nas tabelas (ou views `VW_`) listadas em `docs/04-modelo-de-dados.md` | DBA do SIGA |
| `VISTAS` (owner) | schema próprio | dono dos objetos; usado só pelo Flyway | DBA da instância |
| `APP_VISTAS` | schema próprio | DML mínimo por tabela (abaixo), via role | DBA da instância |

O usuário de leitura do SIGA **nunca** recebe `INSERT`, `UPDATE`, `DELETE`, `EXECUTE` ou privilégio de sistema além de `CREATE SESSION`.

## DML da aplicação no schema próprio

```sql
CREATE ROLE ROLE_APP_VISTAS;
GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO_DOCUMENTO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT ON VISTAS.AUDITORIA_EVENTO_ALTERACAO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT, UPDATE ON VISTAS.USUARIO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT, UPDATE ON VISTAS.PARAMETRO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT, UPDATE, DELETE ON VISTAS.DOSSIE_PEDIDO TO ROLE_APP_VISTAS;
GRANT SELECT, INSERT, DELETE ON VISTAS.DOSSIE_PEDIDO_DOCUMENTO TO ROLE_APP_VISTAS;
GRANT ROLE_APP_VISTAS TO APP_VISTAS;
```

- Auditoria: **sem `UPDATE` nem `DELETE`** para a aplicação (somente inclusão, D13).
- Expurgo da auditoria por retenção: rotina agendada com usuário próprio (`VISTAS_EXPURGO`) com `DELETE` só nas tabelas de auditoria e só por data — não pela aplicação.
- `DELETE` em `DOSSIE_PEDIDO` existe só para a limpeza de pedidos expirados.

## Evite

- `GRANT ANY`, grants para `PUBLIC`, `DROP`/`ALTER` para a aplicação.
- Reaproveitar usuário de banco de outro sistema da SEMA (inclusive do próprio SIGA).
- Credenciais no repositório; elas vêm do ambiente.

## Auditoria periódica dos grants

```sql
SELECT GRANTEE, OWNER, TABLE_NAME, PRIVILEGE FROM DBA_TAB_PRIVS
WHERE GRANTEE IN ('APP_VISTAS','ROLE_APP_VISTAS','VISTAS_LEITURA_SIGA') ORDER BY 1, 3;
SELECT * FROM DBA_SYS_PRIVS WHERE GRANTEE IN ('APP_VISTAS','VISTAS_LEITURA_SIGA');
```

Qualquer privilégio de sistema além de `CREATE SESSION` é alerta. Em ambiente institucional, a revisão é pedida ao DBA.
