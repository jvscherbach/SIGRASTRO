# Padrões técnicos — detalhamento

Resumo e índice em [../13-padroes-tecnicos.md](../13-padroes-tecnicos.md). Estes arquivos são os mesmos da skill `vistas-banco` usada no Claude Code; ao alterar um, altere o outro.

| Arquivo | Assunto |
|---|---|
| [estrutura-de-pastas.md](estrutura-de-pastas.md) | Árvore do repositório, back-end e front-end |
| [modulos-e-dependencias.md](modulos-e-dependencias.md) | Camadas, regras de dependência, contratos internos |
| [integracao-externa.md](integracao-externa.md) | Fronteiras com o banco do SIGA e as APIs externas |
| [persistencia-e-migrations.md](persistencia-e-migrations.md) | Dois datasources, protótipo em memória, Flyway |
| [http-erros-openapi.md](http-erros-openapi.md) | `ProblemDetail`, `errorCode`, correlação, paginação, OpenAPI |
| [ambientes-e-docker.md](ambientes-e-docker.md) | Ambientes, Compose e variáveis |
| [agendamento.md](agendamento.md) | Tarefas agendadas (Spring `@Scheduled`) |
| [checklist-verificacao.md](checklist-verificacao.md) | Testes e comandos antes de considerar pronto |
| [nomenclatura.md](nomenclatura.md) | Nomes dos objetos do schema próprio |
| [tipos-dados.md](tipos-dados.md) | Tipos de coluna |
| [normalizacao.md](normalizacao.md) | 3FN e retratos da auditoria |
| [indexacao.md](indexacao.md) | Índices |
| [documentacao.md](documentacao.md) | Comentários, dicionário de dados em HTML e MER em `docs/database/` |
| [mer.md](mer.md) | MER abstrato e mapeado |
| [seguranca-privilegios.md](seguranca-privilegios.md) | Usuários e privilégios de banco |
| [connection-pooling.md](connection-pooling.md) | Pools HikariCP |
