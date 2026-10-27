# Estrutura de pastas

Regra geral: **só existe a pasta que o código da fase atual usa**. Nada de árvore completa com pastas vazias. A estrutura abaixo é a da fase de protótipo; o que entra depois está marcado em "Versão definitiva".

## Raiz do repositório

```
siga-vistas/
├── .claude/skills/vistas-banco/   # skill com estes padrões (SKILL.md + references/)
├── docs/                          # documentação do projeto (padroes/, prototipo/, prompts/, homologacao/ e, depois, database/)
├── api/                           # back-end Spring Boot (serviço `api` do Compose)
├── front/                         # front-end Angular (serviço `front` do Compose)
├── compose.yaml                   # configurações comuns de api e front
├── compose.local.yaml             # portas, volume temporário dos dossiês
├── .env.local.example             # nomes das variáveis, sem valores
├── .gitignore                     # inclui .env.local e .env.*
└── README.md                      # como subir o ambiente local
```

`docs/padroes/` e `.claude/skills/vistas-banco/references/` têm o mesmo conteúdo: alterar um exige alterar o outro.

## Back-end (`api/`)

```
api/
├── Dockerfile                     # JDK 21 + LibreOffice
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
└── src/
    ├── main/
    │   ├── java/br/gov/mt/sema/vistas/
    │   │   ├── VistasApplication.java
    │   │   ├── config/                    # datasource do SIGA, RestClient, agendamento, OpenAPI
    │   │   ├── shared/
    │   │   │   ├── api/                   # PageResponse, filtro de correlação
    │   │   │   ├── api/error/             # handler ProblemDetail, errorCode
    │   │   │   └── application/exception/ # exceções genéricas seguras
    │   │   ├── acesso/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/
    │   │   │   ├── domain/
    │   │   │   └── infrastructure/memoria/
    │   │   ├── consulta/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/           # portas implementadas por integration.siga
    │   │   │   └── domain/
    │   │   ├── documento/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/           # portas implementadas por integration.siga/licenciamento/scorp/libreoffice
    │   │   │   └── domain/
    │   │   ├── dossie/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/
    │   │   │   ├── domain/
    │   │   │   └── infrastructure/
    │   │   │       ├── memoria/           # fila e pedidos
    │   │   │       ├── pdf/               # PDFBox
    │   │   │       ├── armazenamento/     # disco temporário
    │   │   │       └── agendamento/       # limpeza de expirados e órfãos
    │   │   ├── auditoria/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/           # porta RegistrarEvento
    │   │   │   ├── domain/
    │   │   │   └── infrastructure/memoria/
    │   │   ├── configuracao/
    │   │   │   ├── api/ (dto/)
    │   │   │   ├── application/
    │   │   │   ├── domain/
    │   │   │   └── infrastructure/memoria/
    │   │   └── integration/
    │   │       ├── siga/                  # único pacote com SQL do SIGA (JDBC)
    │   │       ├── licenciamento/ (dto/)  # API de arquivos
    │   │       ├── scorp/ (dto/)          # documentos pessoais
    │   │       └── libreoffice/           # conversor de documentos (JODConverter, S3)
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── sql/siga/                  # consultas ao SIGA (.sql)
    └── test/
        ├── java/br/gov/mt/sema/vistas/
        │   ├── ArchitectureTest.java
        │   └── <mesmos pacotes de main>
        └── resources/
            ├── arquivos/                  # PDF, XLSX largo, DOCX, imagem, PDF com senha, PDF corrompido
            └── sql/siga/                  # recorte do modelo do SIGA usado só em teste
```

Regras:

- `consulta` não tem `infrastructure/`: quem implementa suas portas são os pacotes `integration.*`. `documento` também não tem adapter de porta próprio; desde a S3 tem só `infrastructure/agendamento/` (limpeza do cache do modal).
- `dto/` de `integration.*` é interno ao pacote; nunca aparece em módulo de negócio.
- A configuração do datasource do SIGA fica em `integration/siga/` (só ele pode usar `javax.sql` e Spring JDBC). Testes de integração dele usam Testcontainers com o recorte do SIGA em `src/test/resources/sql/siga/`.
- Testes espelham os pacotes de `main`. Fixtures só em `src/test`.

## Front-end (`front/`)

```
front/
├── Dockerfile                     # build do Angular + nginx
├── nginx.conf                     # serve o app e encaminha /api para a api
├── angular.json, package.json, tsconfig*.json
└── src/
    ├── app/
    │   ├── core/                  # interceptors (token, erro por errorCode, correlação), guards de perfil
    │   ├── layout/                # barra lateral, topo
    │   ├── shared/                # quadros sobrepostos, tags, modal, gaveta, tabela
    │   └── features/
    │       ├── login/
    │       ├── consulta/
    │       ├── detalhe/           # dados, geometrias, documentos, visualizador
    │       ├── dossies/
    │       ├── auditoria/         # documentos, acessos
    │       └── configuracoes/
    ├── assets/                    # logo, brasão, ilustrações SVG
    └── styles/                    # tokens da paleta, tema claro
```

Regras:

- Cada `feature` tem seus componentes, rotas e serviço de API; não importa arquivos de outra `feature` (o que for comum vai para `shared/` ou `core/`).
- Itens de menu por perfil seguem RN08: o que o perfil não usa não é renderizado.
- Referência visual: `docs/prototipo/siga-vistas-prototipo.html`.

## Versão definitiva (não criar antes)

| O que | Onde | Quando |
|---|---|---|
| Serviço `oracle` (Oracle 23 Free, tag fixa) | `compose.local.yaml` | primeira tabela do schema próprio |
| Migrations Flyway | `api/src/main/resources/db/migration/` | idem |
| Persistência JPA | `<modulo>/infrastructure/persistence/{entity,repository,mapper}/` | idem, substituindo `memoria/` |
| `integration/arrecadacao/` | `api/.../integration/` | quando a obtenção das taxas for confirmada |
| Dicionário de dados (HTML) e MER | `docs/database/` (`dicionario-de-dados.html`, `mer.html`, `mer.mmd`, `mer-conceitual.mmd`, `complementos.yaml`, `README.md`) | a partir da primeira migration; atualizado a cada migration |
| Script de geração do dicionário e do MER | `scripts/database/` | idem |
| Overrides de homologação/produção | `compose.homolog.yaml`, `compose.prod.yaml` ou manifests do orquestrador | quando a infraestrutura for definida |
| `.env.homolog.example`, `.env.prod.example` | raiz | idem |

Os nomes `api/` e `front/` acompanham os serviços do Compose.
