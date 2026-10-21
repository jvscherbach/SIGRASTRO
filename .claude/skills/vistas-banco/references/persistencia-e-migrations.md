# Persistência e migrations

## Dois datasources, papéis diferentes

| Datasource | Uso | Tecnologia | Escrita | Migrations |
|---|---|---|---|---|
| `siga` | leitura do banco do SIGA | JDBC (`NamedParameterJdbcTemplate`) | **nunca** | **nunca** (o schema não é nosso) |
| `vistas` (definitivo) | schema próprio Oracle: usuários, perfis, parâmetros, auditoria, fila | Spring Data JPA | sim | Flyway |

- O datasource `siga` é configurado com `read-only` no HikariCP e com um usuário que só tem `SELECT` (ver `seguranca-privilegios.md`).
- A configuração do datasource `siga` (`SigaDataSourceConfig`) fica **dentro de `integration.siga`**, porque só esse pacote pode usar `javax.sql` e Spring JDBC (regra do `ArchitectureTest`). As autoconfigurações de `DataSource`, `JdbcTemplate` e transação do Spring Boot são excluídas em `application.yml`: o projeto declara seus próprios datasources (`siga` agora; `vistas` na versão definitiva).
- Os beans do SIGA só existem com `vistas.datasource.siga.url` **preenchida e não vazia** (`@ConditionalOnExpression`; `@ConditionalOnProperty` trataria a URL vazia como configurada). Sem a URL, o adapter "SIGA não configurado" responde `SIGA_INDISPONIVEL`.
- Flyway é ligado **apenas** ao datasource `vistas`. Nenhuma configuração de Flyway aponta para o SIGA.

## Protótipo: sem schema próprio

- Não há datasource `vistas`. Os módulos `acesso`, `auditoria`, `configuracao` e `dossie` usam **adapters em memória** em `<modulo>.infrastructure.memoria`, implementando as mesmas portas que os adapters JPA implementarão depois.
- Trocar para o banco próprio = adicionar adapters JPA e as migrations, sem mudar `domain` nem `application`.
- Não criar migrations, entidades ou tabelas "de preparação" antes de existir o schema próprio.

## Schema próprio (versão definitiva)

### Separação domínio × persistência

- Modelo de negócio em `<modulo>.domain`, **sem** anotações JPA.
- `@Entity`, `@Table`, `@Embeddable` em `<modulo>.infrastructure.persistence.entity`.
- Repositories Spring Data em `<modulo>.infrastructure.persistence.repository`.
- Conversão em `<modulo>.infrastructure.persistence.mapper`, classes com sufixo `PersistenceMapper`.

### Flyway

- `spring.jpa.hibernate.ddl-auto=validate` sempre. Hibernate nunca cria nem altera schema.
- Migrations em `src/main/resources/db/migration`, nome `V<versao>__<descricao_em_ingles_snake_case>.sql` (ex.: `V1__create_audit_event_table.sql`). Objetos **dentro** do arquivo em português (ver `nomenclatura.md`).
- Versão nunca reaproveitada; nunca editar migration aplicada — correção vira migration nova.
- Criar migration só junto de necessidade real.
- Banco: **Oracle**. Desenvolvimento em Oracle Database 23 Free; todo SQL, tipo e recurso usado precisa ser compatível com **Oracle 19c** (ambiente final). Não usar recursos exclusivos do 23 (ex.: tipo `BOOLEAN` em tabela, `IF [NOT] EXISTS` em DDL, `GROUP BY` por alias, domínios SQL, JSON relational duality, `SELECT` sem `FROM`).
- Flyway controlado por `FLYWAY_ENABLED` (padrão `true` quando o datasource `vistas` existir). Não desabilitar antes de existir processo substituto no pipeline.

### Tabelas previstas (ver `docs/11-auditoria.md` e `docs/12-configuracoes.md`)

| Módulo | Tabelas |
|---|---|
| `acesso` | `USUARIO` |
| `configuracao` | `PARAMETRO` |
| `auditoria` | `AUDITORIA_EVENTO`, `AUDITORIA_EVENTO_DOCUMENTO`, `AUDITORIA_EVENTO_ALTERACAO` |
| `dossie` | `DOSSIE_PEDIDO`, `DOSSIE_PEDIDO_DOCUMENTO` (fila durável) |

O PDF do dossiê **nunca** é gravado no banco (D08): fica em disco temporário até download ou expiração.

## Leitura do SIGA

- Consultas de referência em `docs/04-modelo-de-dados.md`; validar nomes de coluna no dicionário do banco antes de usar.
- Toda consulta com parâmetros nomeados, filtrando por `ID_REQUERIMENTO`; nada de `SELECT *`. Consultas em `src/main/resources/sql/siga/*.sql`; filtros opcionais são predicados fixos do adapter, nunca texto do usuário.
- Paginação com `OFFSET :inicio ROWS FETCH NEXT :tamanho ROWS ONLY` (Oracle 12c+). Restrições de acesso (ex.: vínculo com o interessado, RN07) entram **na consulta**, nunca como filtro posterior à lista (total e paginação precisam vir corretos).
- Testes do adapter `integration.siga` com dados de teste em banco local (Oracle 23 Free via Testcontainers, com um recorte do modelo do SIGA criado **só em `src/test/resources/sql/siga`**, inclusive o usuário de leitura com os mesmos grants pedidos ao DBA), nunca contra o SIGA real. O Testcontainers é dependência de teste; o Compose não ganha serviço de banco por causa disso.
