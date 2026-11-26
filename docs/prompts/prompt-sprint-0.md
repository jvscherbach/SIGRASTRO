# SIGA Vistas — Sprint 0: Fundação

## Contexto

Você vai iniciar a implementação do **SIGA Vistas**, um serviço externo ao SIGA Licenciamento que consulta requerimentos e processos, lista os documentos e gera um dossiê em PDF em ordem cronológica.

O repositório já contém:

- `docs/` — documentação completa do projeto (requisitos, regras, telas, API, decisões, padrões e plano de sprints).
- `docs/prototipo/siga-vistas-prototipo.html` — protótipo navegável que é a **referência visual** do front-end.
- `.claude/skills/vistas-banco/` — skill com os padrões técnicos do projeto.

Esta é a **Sprint 0 (Fundação)**, descrita em `docs/14-plano-de-sprints.md`. O objetivo é deixar o esqueleto do projeto pronto, com back-end e front-end subindo no Docker, sem nenhuma funcionalidade de negócio ainda (exceto o login simulado).

## Leitura obrigatória antes de começar

1. A skill `vistas-banco` (`SKILL.md` e as referências citadas abaixo).
2. `docs/13-padroes-tecnicos.md` e, em `docs/padroes/`: `estrutura-de-pastas.md`, `modulos-e-dependencias.md`, `integracao-externa.md`, `http-erros-openapi.md`, `ambientes-e-docker.md`, `checklist-verificacao.md`, `agendamento.md`.
3. `docs/03-arquitetura.md`, `docs/07-api.md` (seções Acesso e Erros), `docs/08-telas.md`, `docs/12-configuracoes.md` (perfis e permissões), `docs/02-requisitos.md` (RF19, RN07, RN08).
4. `docs/prototipo/siga-vistas-prototipo.html` — abra e estude o CSS (tokens em `:root`, barra lateral, topo, quadros sobrepostos, botões, tags) e a tela de login.

Se encontrar conflito entre este prompt e a documentação, **pare e relate** o conflito no relatório final em vez de escolher sozinho.

## Regras gerais

- Crie **somente** as pastas e pacotes que esta sprint usa (regra de `estrutura-de-pastas.md`). Nada de pastas vazias ou classes placeholder.
- **Não** adicione dependência de banco de dados (JDBC, JPA, driver Oracle, Flyway) nesta sprint. O acesso ao SIGA começa na S1.
- **Não** adicione Spring Security nesta sprint. A autenticação simulada é feita por um filtro próprio (descrito abaixo).
- Nenhuma credencial, token, URL institucional ou valor real em arquivo versionado.
- Imagens Docker sempre com **tag de versão fixa** (nunca `latest`).
- Não altere arquivos em `docs/` nem em `.claude/`. Se algo da documentação precisar mudar, liste a proposta no relatório final.
- **Não execute nenhum comando git além de `git diff`.** Não faça commit, push, branch, stash, checkout nem nada equivalente.

## Escopo

### 1. Raiz do repositório

Criar:

- `compose.yaml` — serviços `api` e `front` (build a partir de `api/` e `front/`), rede interna, `front` depende de `api`.
- `compose.local.yaml` — portas locais: `front` em `8080:80`; `api` em `8081:8080` (para acessar OpenAPI e health diretamente); volume nomeado `dossies-tmp` montado na `api` em `/var/tmp/vistas`; `env_file: .env.local`.
- `.env.local.example` — **todas** as variáveis listadas em `docs/03-arquitetura.md` e `docs/padroes/ambientes-e-docker.md`, com placeholders neutros (ex.: `SIGA_DB_URL=jdbc:oracle:thin:@//host:1521/servico`). Variáveis que ainda não são usadas ficam comentadas com `# a partir da S1`, `# versão definitiva` etc.
- `.gitignore` — Java/Maven, Node/Angular, IDEs, `.env.local` e `.env.*` (exceto `*.example`).
- `README.md` na raiz (`siga-vistas/README.md`) — ver seção **4. README com tutorial** abaixo.

### 2. Back-end (`api/`)

Projeto Maven com Maven Wrapper.

- `groupId` `br.gov.mt.sema`, `artifactId` `siga-vistas-api`, Java **21**, Spring Boot na versão estável mais recente da linha **3.x** (registre a versão escolhida no relatório).
- Dependências: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`; teste: `spring-boot-starter-test`, `archunit-junit5`.

Pacote raiz `br.gov.mt.sema.vistas`:

**`VistasApplication`** e **`config/`**
- `OpenApiConfig`: título, versão, esquema `bearerAuth` (documentado como contrato; o token do login simulado não é autenticação real). Componentes de resposta comuns (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `InternalError`) com o schema de `ProblemDetail` do projeto.
- springdoc habilitado no perfil `local`; desabilitado nos demais por padrão, com `OPENAPI_ENABLED` como opt-in.
- Actuator expondo **somente** `health`.

**`shared/`**
- `shared.api.error`:
  - `ErrorCode` (enum) com **todos** os códigos da tabela de `docs/07-api.md`, cada um com status HTTP e título padrão.
  - `GlobalExceptionHandler` (`@RestControllerAdvice`) produzindo `ProblemDetail` com `errorCode`, `timestamp` (com fuso `America/Cuiaba`), `path`, `correlationId` e, em erro de validação, a lista de campos inválidos. Trate também 404 de rota inexistente, método não permitido, corpo ilegível e exceção genérica (`ERRO_INTERNO`, sem expor detalhe técnico).
- `shared.application.exception`: exceção base com `ErrorCode` e mensagem segura, e as especializações que esta sprint usa (ao menos `NaoAutenticadoException`, `PerfilSemPermissaoException`, `RecursoNaoEncontradoException`, `ParametroInvalidoException`).
- `shared.api`:
  - `CorrelationIdFilter`: aceita `X-Correlation-Id` com até 64 caracteres de `[A-Za-z0-9._-]`; se ausente ou inválido, gera UUID. Devolve no header de resposta, coloca no MDC (`correlationId`) e limpa ao final. Ordem explícita, **antes** do filtro de autenticação.
  - `PageResponse<T>` com `from(Page<T>)` — só o contrato, para uso a partir da S1. Como `Page` é do Spring Data, que não entra nesta sprint, **implemente `PageResponse` sem depender de Spring Data** (campos `conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas` e um construtor/fábrica genérico). O método `from(Page)` entra quando o Spring Data entrar.
- Log com `correlationId` no padrão de saída (`logback-spring.xml` ou propriedade de logging).

**Módulo `acesso`** (login simulado, em memória)
- `domain`: `Perfil` (`OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`) com as permissões da matriz de `docs/12-configuracoes.md` (métodos de consulta como `podeVerSigilosos()`, `podeVerAuditoriaAcessos()`, `podeAcessarConfiguracoes()`, `restritoAoInteressado()`); `Usuario` (login, nome, perfil, CPF/CNPJ, ativo).
- `application`: casos de uso `AutenticarUsuario` (login + senha → sessão) e `ObterUsuarioAtual`; portas `RepositorioUsuarios` e `RepositorioSessoes`.
- `infrastructure.memoria`: os três usuários de demonstração do protótipo da época (`operador` com CPF/CNPJ `12.345.678/0001-90`, o Gestor e `admin`; hoje removidos na L1), com senha de demonstração única definida em propriedade (valor padrão só no perfil `local`; removida na L1); sessões com token opaco aleatório (`SecureRandom`, 32 bytes, Base64 URL) e expiração de 8 h.
- `api`: `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`, com DTOs em `acesso.api.dto` e Bean Validation.
- Filtro de autenticação simulada (no próprio módulo, em `acesso.api`): exige `Authorization: Bearer <token>` em `/api/**`, exceto `POST /api/auth/login`; sem token ou com token inválido → 401 `NAO_AUTENTICADO` no formato `ProblemDetail`. Health e OpenAPI ficam fora do filtro. O usuário autenticado fica disponível por um contrato do módulo `acesso` (ex.: `UsuarioAtual`), para os outros módulos nas próximas sprints.

**`resources/`**
- `application.yml` com valores via variável de ambiente e `application-local.yml`. Diretório temporário de dossiês configurável (`vistas.dossie.diretorio`, padrão `/var/tmp/vistas`).

**Testes (`src/test`)**
- `ArchitectureTest` (ArchUnit) cobrindo:
  - regras 1 a 8 de `modulos-e-dependencias.md`;
  - `domain` sem dependência de Spring Web, `jakarta.persistence`, `java.sql`, `javax.sql` e `org.springframework.jdbc`;
  - uso de `java.sql`, `javax.sql` e `org.springframework.jdbc` permitido **somente** em `..integration.siga..`;
  - classes em `..integration..dto..` acessadas somente de dentro do próprio `integration.<sistema>`;
  - DTOs HTTP (`..api.dto..`) não usados fora do próprio módulo;
  - regras devem passar com pacotes ainda inexistentes (`allowEmptyShould(true)` onde necessário).
- `MockMvc`: login válido e inválido, `/api/auth/me` com e sem token, 404 de rota inexistente e erro de validação, conferindo o formato `ProblemDetail`, o `errorCode` e o header `X-Correlation-Id` (recebido e gerado).
- Unitários: `Perfil` (matriz de permissões), `CorrelationIdFilter` (aceita, rejeita e gera).

**`Dockerfile`**
- Multi-stage: build com imagem Eclipse Temurin 21 JDK; runtime com Eclipse Temurin 21 JRE.
- Runtime com LibreOffice headless já instalado (pacotes mínimos para Writer e Calc, sem interface gráfica) e fontes básicas (ex.: Liberation, DejaVu), para a S3. Registre no relatório o tamanho final da imagem.
- Usuário não-root, diretório `/var/tmp/vistas` com permissão para ele, `HEALTHCHECK` no `/actuator/health`.

### 3. Front-end (`front/`)

Angular na versão estável mais recente, com componentes standalone e roteamento. Registre a versão no relatório.

**Visual:** reproduzir o protótipo com fidelidade — mesmos tokens de cor, tipografia, barra lateral (com logo, nome "SIGA VISTAS", menu, brasão de MT em marca-d'água e rodapé), topo (menu hambúrguer, trilha de navegação, usuário com menu) e o padrão de quadros sobrepostos.

- Extraia do protótipo as duas imagens em `data:image/png;base64,...` (logo e brasão) para `src/assets/img/logo-sema.png` e `src/assets/img/brasao-mt.png`, usando um script temporário fora do repositório. Não copie base64 para o código.
- `src/styles/`: tokens do `:root` do protótipo como variáveis CSS, tema claro apenas, estilos base (botões, tags, campos, tabela, toast).

**Estrutura** (conforme `estrutura-de-pastas.md`):
- `core/`:
  - `AuthService` (login, logout, usuário atual; token em `sessionStorage`).
  - Interceptors: token (`Authorization: Bearer`), correlação (`X-Correlation-Id` por requisição) e erro (lê `errorCode` do `ProblemDetail` e mostra mensagem amigável em toast; 401 → limpa sessão e vai ao login).
  - Guards: autenticado e por perfil.
- `layout/`: shell com barra lateral e topo, responsivo como no protótipo (barra recolhível; em telas pequenas, menu sobreposto).
- `shared/`: toast e os componentes visuais básicos que as telas desta sprint usarem (ex.: quadro do topo com a ilustração, folha branca sobreposta).
- `features/login/`: tela de login igual à do protótipo, incluindo a ilustração de chapadas com curvas de nível (porte a função `cenaLogin()` do protótipo para um componente que gera o SVG). Os três perfis de demonstração da época entravam com um clique, chamando `POST /api/auth/login`.
- Rotas protegidas com página "em construção" no padrão visual (quadro do topo + folha sobreposta), indicando a sprint em que cada uma será entregue:
  - `/consulta` (S1), `/dossies` (S4), `/auditoria/documentos` (S5), `/auditoria/acessos` (S5, só Administrador), `/configuracoes` (S5, só Administrador).
- Menu por perfil conforme **RN08**: itens que o perfil não usa **não são renderizados** (no app real, "Acessos" e "Configurações" não aparecem para quem não é Administrador; o cadeado do protótipo era só demonstração). O guard também bloqueia o acesso direto pela URL.
- Menu do usuário no topo: nome, perfil e "Sair".

**Execução:**
- `proxy.conf.json` para `ng serve` encaminhar `/api` a `http://localhost:8081`.
- `Dockerfile` multi-stage: build com Node LTS; runtime com nginx (tag fixa).
- `nginx.conf`: SPA com fallback para `index.html`, `/api` encaminhado para `http://api:8080`, gzip, cache longo só para arquivos com hash.

**Testes:** unitários dos guards, do interceptor de erro e do `AuthService`, rodando em modo headless.

### 4. README com tutorial (`siga-vistas/README.md`)

Escreva para alguém que **não é desenvolvedor** conseguir subir o projeto sozinho, seguindo os passos na ordem. Linguagem simples, um comando por passo, e o que se espera ver depois de cada passo. Os comandos devem funcionar em Linux, macOS e Windows (quando houver diferença, mostrar as duas formas: Bash e PowerShell).

Conteúdo, nesta ordem:

1. **O que é o SIGA Vistas** — 2 parágrafos e link para `docs/README.md`.
2. **Estrutura do repositório** — tabela curta com `api/`, `front/`, `docs/`, `.claude/`, arquivos Compose e `.env.local.example`.
3. **Pré-requisitos** — Docker Desktop (ou Docker Engine + Compose v2) com a versão mínima; para rodar sem Docker: JDK 21 e Node LTS (versões exatas usadas no projeto). Como conferir cada versão instalada (`docker --version`, `docker compose version`, `java -version`, `node -v`).
4. **Tutorial 1 — Subir tudo com Docker (caminho recomendado)**:
   1. Entrar na pasta do projeto.
   2. Criar o `.env.local` a partir do `.env.local.example` (comando de cópia em Bash e PowerShell) e explicar que, nesta sprint, nenhuma variável precisa ser preenchida para o login funcionar; a partir da S1 será preciso preencher as do banco do SIGA.
   3. Subir: `docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build`.
   4. Acompanhar até ficar pronto: `docker compose ... ps` (o que significa `healthy`) e `docker compose ... logs -f api`.
   5. Abrir no navegador: front em `http://localhost:8080`, health em `http://localhost:8081/actuator/health`, OpenAPI em `http://localhost:8081/swagger-ui.html`.
   6. Entrar com os usuários de demonstração (tabela: perfil, o que cada um vê, senha de demonstração).
   7. Parar: `docker compose ... down`. Aviso em destaque: **nunca usar `down -v`**.
   8. Atualizar depois de mudanças no código: repetir o passo 3 (`--build`).
5. **Tutorial 2 — Rodar sem Docker (desenvolvimento)**:
   1. API: `cd api` e `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (e a forma `mvnw.cmd` no Windows); como saber que subiu.
   2. Front: em outro terminal, `cd front`, `npm ci`, `npm start` (ou `ng serve` com o proxy); abrir `http://localhost:4200`; explicar que o `proxy.conf.json` encaminha `/api` para a API na porta 8081 e como ajustar a porta da API local para casar com ele.
6. **Rodar os testes** — back-end (`./mvnw clean verify` e só arquitetura `./mvnw -Dtest=ArchitectureTest test`) e front-end (`npm test -- --watch=false`).
7. **Problemas comuns** — porta 8080/8081/4200 ocupada (como trocar no `compose.local.yaml` ou descobrir quem está usando), Docker não iniciado, primeira construção demorada (download de imagens e do LibreOffice), erro de permissão no volume `dossies-tmp`, `.env.local` ausente, sessão expirada voltando ao login.
8. **Links** — `docs/14-plano-de-sprints.md`, `docs/13-padroes-tecnicos.md` e o protótipo.

Teste o Tutorial 1 e o Tutorial 2 de ponta a ponta exatamente como escritos e corrija o README se algum passo não funcionar.

## Verificação

Rode e reporte o resultado de:

```bash
cd api && ./mvnw -Dtest=ArchitectureTest test && ./mvnw clean verify && cd ..
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml config --services
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml up -d --build
git diff --check
```

Com os containers no ar, confirme:

- `http://localhost:8081/actuator/health` → `UP`.
- `http://localhost:8081/swagger-ui.html` abre no perfil local.
- `http://localhost:8080` mostra o login; os três perfis entram; o menu muda conforme o perfil; uma URL de rota restrita digitada manualmente é bloqueada.
- Uma chamada a `/api/auth/me` sem token devolve 401 em `ProblemDetail` com `X-Correlation-Id`.

Ao final, derrube os containers com `docker compose ... down` (**nunca** `down -v`).

## Relatório final

Responda com:

1. Versões escolhidas (Spring Boot, Angular, Node, imagens Docker) e tamanho das imagens.
2. Árvore de pastas criada (até 4 níveis).
3. Resultado de cada comando de verificação.
4. Desvios em relação a este prompt ou à documentação, e o motivo.
5. Propostas de ajuste na documentação (`docs/`), se houver — sem editar os arquivos.
6. Pendências ou riscos percebidos para a S1.
7. Confirmação de que os dois tutoriais do README foram executados como escritos, com o resultado de cada um.
