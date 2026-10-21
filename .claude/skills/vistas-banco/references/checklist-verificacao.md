# Checklist de verificação

## Testes

- Domínio e casos de uso: unitários (regras RN01–RN11, permissões por perfil, "completo ou nenhum").
- `integration.siga`: integração contra banco local com recorte do modelo do SIGA criado só em `src/test`.
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
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml config
git diff --check
```

- `config --services` no local deve listar `api` e `front` (e o banco próprio, quando existir). Nunca o banco do SIGA.
- Revisão das mudanças por `git diff`. Commit e push ficam com o responsável pelo projeto.
- Nunca `docker compose down -v`.

## O que o ArchitectureTest garante

- Garante: dependências entre camadas e módulos; ausência de JPA/JDBC/Spring Web no domínio; SQL contra o SIGA só em `integration.siga`; DTOs externos só em `integration.*`; localização de entidades, repositories e `PersistenceMapper`.
- Não garante: semântica dos contratos, correção das consultas ao SIGA, correção do mapeamento domínio↔entidade.
