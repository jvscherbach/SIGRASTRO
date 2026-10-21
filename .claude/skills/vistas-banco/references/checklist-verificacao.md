# Checklist de verificação

## Testes

- Domínio e casos de uso: unitários (regras RN01–RN11, permissões por perfil, "completo ou nenhum").
- `integration.siga`: integração contra Oracle 23 Free (Testcontainers, um container compartilhado) com recorte do modelo do SIGA criado só em `src/test`. Sobem em 1 a 2 minutos e usam cerca de 2 GB de RAM; ficam desabilitados sem Docker (`disabledWithoutDocker`), então `clean verify` passa pulando-os: **reporte quantos rodaram e quantos foram pulados**. **Provam o SQL no 23 Free, não no 19c**: a compatibilidade com 19c vem da lista de recursos proibidos (`persistencia-e-migrations.md`) e da homologação no SIGA real.
- Teste de fumaça contra o SIGA real: existe, é só leitura e só roda com `SIGA_SMOKE=true`; é executado pelo responsável (ver `docs/homologacao/`), nunca pelo `verify` nem por automação.
- `integration.licenciamento`/`integration.scorp`: servidor HTTP simulado (ex.: WireMock), cobrindo 200, 401, 404, 503 e timeout.
- Geração de PDF: testes com arquivos de exemplo em `src/test/resources` (PDF, XLSX largo, DOCX, imagem, PDF com senha, PDF corrompido).
- Contrato HTTP: `MockMvc`, incluindo `ProblemDetail` e `errorCode`.
- Arquitetura: `ArchitectureTest` (ArchUnit).
- Front-end: testes unitários do Angular para serviços e interceptor de erro.
- Fixtures só em `src/test`; nunca endpoint, entidade ou migration fictícia em código de produção.

## Antes de considerar pronto

```bash
./mvnw -Dtest=ArchitectureTest test
./mvnw clean verify
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml config --services
git diff --check
```

- `config --services` no local deve listar `api` e `front` (e o banco próprio, quando existir). Nunca o banco do SIGA.
- Se a entrega tem migration: `docs/database/` regenerado a partir do Oracle local e coerente com a versão do Flyway.
- Revisão das mudanças por `git diff`. Commit e push ficam com o responsável pelo projeto.
- Nunca `docker compose down -v`.

## O que o ArchitectureTest garante

- Garante: dependências entre camadas e módulos; ausência de JPA/JDBC/Spring Web no domínio; SQL contra o SIGA só em `integration.siga`; DTOs externos só em `integration.*`; localização de entidades, repositories e `PersistenceMapper`.
- Não garante: semântica dos contratos, correção das consultas ao SIGA, correção do mapeamento domínio↔entidade.
