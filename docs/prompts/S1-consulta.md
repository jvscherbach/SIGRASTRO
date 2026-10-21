# SIGA Vistas — Sprint 1: Consulta

## Contexto

Você vai implementar a **Sprint 1 (Consulta)** do SIGA Vistas, descrita em `docs/14-plano-de-sprints.md`. É a primeira sprint que **lê o SIGA de verdade**: buscar requerimento ou processo, ver os dados e a tarefa atual. **Não há lista de documentos nem dossiê ainda** (S2).

A Sprint 0 está pronta e verificada. O que já existe:

- `api/` (Spring Boot 3.5.16, Java 21, Maven Wrapper): `shared` (`ErrorCode`, `ProblemDetails`, `GlobalExceptionHandler`, `CorrelationIdFilter`, `PageResponse.of(...)`, exceções em `shared.application.exception`), módulo `acesso` em memória (login simulado, `UsuarioAtual`, `Perfil` com `restritoAoInteressado()`), `OpenApiConfig`, `ArchitectureTest` e `ContratoHttpTest`.
- `front/` (Angular 21, standalone, zoneless, Vitest): `core` (`AuthService`, interceptors de token, correlação e erro, guards), `layout` (shell), `shared` (`QuadroTopoComponent`, `FolhaSobrepostaComponent`, `IconeComponent`, toast), `features/login`, `features/em-construcao`. As rotas `/consulta`, `/dossies`, `/auditoria/*` e `/configuracoes` apontam hoje para `EmConstrucaoComponent`.
- Docker: `compose.yaml`, `compose.local.yaml`, `.env.local.example` e Dockerfiles já validados (`api` com LibreOffice, `front` com nginx).

## Leitura obrigatória antes de começar

1. A skill `vistas-banco` (`SKILL.md` e as referências abaixo) e o `CLAUDE.md` da raiz.
2. `docs/13-padroes-tecnicos.md` e, em `docs/padroes/`: `estrutura-de-pastas.md`, `modulos-e-dependencias.md`, `integracao-externa.md`, `persistencia-e-migrations.md`, `connection-pooling.md`, `seguranca-privilegios.md`, `http-erros-openapi.md`, `ambientes-e-docker.md`, `checklist-verificacao.md`.
3. `docs/04-modelo-de-dados.md` (inteiro, incluindo as consultas de referência e o Apêndice A), `docs/02-requisitos.md` (RF01–RF05, RF30, RN04, RN07), `docs/07-api.md` (seção Consulta e Erros), `docs/08-telas.md` (seções 2 e 3), `docs/10-pendencias-e-riscos.md` (P01, P03, P10, P11, R04, R05), `docs/09-decisoes.md`.
4. `docs/prototipo/siga-vistas-prototipo.html`: estude as funções `vConsulta`, `secResultados`, `vDetalhe` e o CSS de `.glass`, `.kv`, `.kv-grid`, `.obj-grid`, `.obj-card`, `.req-item`, `.tile`, `.seg`, `.backlink`. O front da S1 reproduz essas telas com dados reais, **sem a seção de documentos**.
5. O código da S0 (`api/src`, `front/src`) para seguir o mesmo estilo, nomes e organização. Não reescreva o que já funciona.

Se encontrar conflito entre este prompt e a documentação, **pare e relate o conflito no relatório final** em vez de escolher sozinho. Itens marcados **NÃO CONFIRMADO** nos docs não podem ser inventados.

## Regras gerais

- Crie só os pacotes que esta sprint usa. Sem pastas vazias nem classes placeholder.
- O banco do SIGA é **somente leitura** (D14, RNF01). Só `integration.siga` escreve SQL contra ele; os módulos de negócio declaram portas.
- **Sem JPA, sem Flyway, sem schema próprio, sem Spring Security, sem Spring Data.** Leitura por `NamedParameterJdbcTemplate`.
- SQL em arquivos `.sql` em `api/src/main/resources/sql/siga/`, sempre com parâmetros nomeados, sem `SELECT *`, sem concatenar entrada do usuário. Todo SQL deve funcionar em **Oracle 19c** (paginação com `OFFSET :inicio ROWS FETCH NEXT :tamanho ROWS ONLY`; nada exclusivo do 23c).
- Nenhuma credencial, token ou URL institucional em arquivo versionado. Imagens Docker com tag fixa.
- Nunca registrar CPF/CNPJ completo em log (use máscara ou só os 4 últimos dígitos).
- O termo "Camunda" não aparece fora de `integration.siga` nem na interface (a tela diz "Tarefa atual").
- Não execute comandos git além de `git diff`/`git status`. Não faça commit, push, branch nem stash.
- Nunca rode `docker compose down -v`.
- **Documentação em `docs/`:** diferente da S0, nesta sprint você **deve atualizar `docs/` à medida que avança**, não só no fim (ver seção "Documentação"). Se alterar algo em `docs/padroes/`, altere o mesmo arquivo em `.claude/skills/vistas-banco/references/` (o conteúdo é espelhado).

## Situação dos pré-requisitos (leia com atenção)

Os pré-requisitos 1, 2 e 5 do plano (usuário `VISTAS_LEITURA_SIGA`, rede até o Oracle, mapeamento de objetivo/finalidade/atividade/prioridade) **podem não estar prontos**. Portanto:

- **A aplicação deve subir e passar nos testes sem acesso ao SIGA.** Sem `SIGA_DB_URL`, a `api` inicia normalmente, `/actuator/health` continua `UP`, e qualquer consulta responde `503 SIGA_INDISPONIVEL` em `ProblemDetail`. O mesmo vale se o SIGA estiver fora do ar: o Hikari não pode impedir a inicialização (`initialization-fail-timeout=-1`) e o health do banco do SIGA **não** entra no `/actuator/health` (a `api` não pode ficar `unhealthy` por causa do SIGA).
- **Não invente nomes de tabela ou coluna.** Use só o que está confirmado em `docs/04`. Para o que está **NÃO CONFIRMADO** (P01: objetivo, finalidade, atividade, bloco Tipo/Objetivo e regra da prioridade de atendimento; coluna de descrição de `TIPO_REQUERIMENTO`; P03: `REQUERIMENTO.NUMERO_PROCESSO` × `PROCESSO.NUMERO`):
  1. Verifique se o responsável deixou o mapeamento em `docs/04` (seção nova ou nota) ou num arquivo em `docs/` citado na conversa. Se sim, use-o.
  2. Se não, implemente o contrato completo (porta, DTO, tela com "—") **sem a consulta** desses campos e liste cada um como pendência no relatório. A estrutura deve permitir ligar o SQL depois mudando só `integration.siga`.
  3. Se `SIGA_DB_URL` estiver configurado e acessível no seu ambiente, você pode confirmar nomes de colunas por consultas **de leitura** ao dicionário (`ALL_TAB_COLUMNS`). Registre o que confirmou em `docs/04`.
- Para `PESO_PRIORIDADE`: só traduza para texto (ex.: "Atendimento normal") se a regra estiver confirmada; caso contrário não exiba o número bruto: mostre "—" e registre a pendência (a regra é do SIGA e não pode ser adivinhada).

## Escopo — back-end (`api/`)

### 1. Dependências e datasource `siga`

- Adicione `spring-boot-starter-jdbc` e `com.oracle.database.jdbc:ojdbc11` (versão do BOM do Spring Boot). Para testes: `org.testcontainers:oracle-free` e `junit-jupiter` do Testcontainers (versões do BOM). Sem Spring Data, JPA, Flyway.
- Como `ArchitectureTest` só permite `java.sql`, `javax.sql` e `org.springframework.jdbc` em `integration.siga`, a **configuração do datasource fica dentro de `integration.siga`** (ex.: `integration.siga.SigaDataSourceConfig`), não em `config/`. Exclua as autoconfigurações de `DataSource`/`JdbcTemplate` do Spring Boot (o projeto terá dois datasources no futuro) ou garanta que não conflitem.
- Propriedades (em `application.yml`, valores por variável de ambiente), conforme `connection-pooling.md`: `vistas.datasource.siga.url/username/password`, Hikari `read-only: true`, pool máximo `${SIGA_DB_POOL_MAX:2}`, `minimum-idle: 1`, `connection-timeout ${SIGA_DB_CONN_TIMEOUT_MS:5000}`, `max-lifetime ${SIGA_DB_MAX_LIFETIME_MS:1500000}`, `oracle.jdbc.ReadTimeout ${SIGA_DB_READ_TIMEOUT_MS:30000}`, `initialization-fail-timeout: -1`.
- Condicione os beans à existência de `SIGA_DB_URL`. Sem ela, registre um adapter de "SIGA não configurado" que lança a exceção de indisponibilidade (item 2), para que a API responda 503 de forma explícita.
- Regra de código: **nunca manter conexão aberta fora das leituras**. Cada consulta abre e libera a conexão (via template, sem transação longa).

### 2. Tratamento de falhas do SIGA

- Crie em `shared.application.exception` uma exceção para o `ErrorCode.SIGA_INDISPONIVEL` (503) com mensagem segura, sem SQL, nome de tabela ou URL.
- Em `integration.siga`, traduza `DataAccessException`/timeout/falha de conexão para ela, registrando o detalhe técnico só no log (com `correlationId`). Erros inesperados de SQL (coluna inexistente) viram `ERRO_INTERNO` 500, também sem detalhe na resposta.
- A tarefa atual é um caso especial (ver item 4): falha ao ler a tarefa **não** derruba o detalhe.

### 3. Módulo `consulta`

Estrutura (só o necessário; sem `infrastructure/`, as portas são implementadas por `integration.siga`):

- `domain`: `NumeroRequerimento` (valor `N/AAAA`, validação; número 1–6 dígitos e ano de 4), `SituacaoRequerimento` (enum com **todos** os valores do Apêndice A de `docs/04` e a descrição; valor desconhecido do SIGA vira `DESCONHECIDA` com o texto cru, sem quebrar), `Identificacao` (RN04: com processo, o número do processo; sem processo, o do requerimento e a marca "não protocolado"), `Interessado` (nome, CPF/CNPJ), `Requerimento` (todos os campos de RF04), `ResumoRequerimento` (para listas), `TarefaAtual` (estados `ATIVA`, `SEM_TAREFA`, `INDISPONIVEL`; nome, desde, responsável, fluxo), `CriterioBusca`.
- `application`: portas `RequerimentosDoSiga` (buscar por número de requerimento, por número de processo, por id, por interessado paginado) e `TarefaAtualDoRequerimento`; casos de uso `BuscarRequerimentos` e `ObterRequerimento`.
- `api` + `api.dto`: controller e DTOs HTTP.

Regras de negócio (no domínio/casos de uso, **não** no controller nem no adapter):

- **Critérios (RF01, RF02):** `tipo` (`REQUERIMENTO`|`PROCESSO`) + `numero` (formato `N/AAAA`), e/ou `cpfCnpj` (só dígitos; 11 ou 14), e/ou `nome` (mínimo 3 caracteres; busca parcial, sem diferenciar maiúsculas; escape de `%`, `_` e `\` no `LIKE`). Nenhum critério → `PARAMETRO_INVALIDO` 400. Formato inválido → 400 com o campo.
- **Busca só por número:** correspondência exata. Não achou → `NAO_ENCONTRADO` 404. Por processo, devolve o requerimento correspondente (1:1, RF03). Resposta: página com 1 item.
- **Demais combinações:** lista paginada (`pagina` a partir de 0, `tamanho` padrão 20, máximo 100), ordenada por data de abertura decrescente e id, com total.
- **RN07, antecipada da S4 (decisão registrada aqui):** como esta é a primeira sprint com dado real, o back-end já aplica o vínculo para perfis com `restritoAoInteressado()` (Operador): compara os dígitos do CPF/CNPJ do usuário atual (`UsuarioAtual`) com os do interessado. Busca por número de outro interessado → `NAO_INTERESSADO` 403 ("Você não é o interessado deste processo ou requerimento."); buscas por interessado e listas só trazem os vinculados; `GET /requerimentos/{id}` de outro interessado → 403 `NAO_INTERESSADO`. Usuário sem CPF/CNPJ cadastrado não vê nada. Gestor e Administrador não são restritos. Registre essa antecipação em `docs/14` (S1 e S4) e `docs/07`.
- **Identificação (RN04):** todo DTO devolve `identificacao` já resolvida (`rotulo` "Processo" ou "Requerimento", `numero`, `protocolado`), além dos números brutos.
- **Tradução da situação:** o DTO traz o código do enum e a descrição; descrições conforme o Apêndice A.

Endpoints (todos exigem login; documente com `@ApiResponse` e `ref` para os componentes comuns):

| Método | Rota | Resposta |
|---|---|---|
| `GET` | `/api/busca?tipo=&numero=&cpfCnpj=&nome=&pagina=&tamanho=` | `PageResponse<ResultadoBuscaResponse>` (id, identificacao, interessado e CPF/CNPJ, tipo de processo, situação, data de abertura) |
| `GET` | `/api/requerimentos/{id}` | `RequerimentoResponse` (campos de RF04 + `tarefaAtual`) |

Detalhe do `RequerimentoResponse`: prioridade de atendimento, tipo de requerimento, identificação, situação, abertura, última atualização, interessado, tipo de processo, objetivos, finalidades, atividades, bloco Tipo/Objetivo (listas; vazias quando não mapeadas) e `tarefaAtual` `{ estado, nome, desde, responsavel, fluxo }`. **A chave de arquivo e qualquer dado interno do SIGA nunca saem na API** (RNF09).

### 4. `integration.siga` (único pacote com SQL)

- Adapters que implementam `RequerimentosDoSiga` e `TarefaAtualDoRequerimento`, com SQL em `resources/sql/siga/` (um arquivo por consulta: `requerimento-por-numero.sql`, `requerimento-por-processo.sql`, `requerimento-por-id.sql`, `requerimentos-por-interessado.sql`, `tarefa-atual.sql`, mais os de objeto/prioridade **se** mapeados).
- Linhas e DTOs externos em `integration.siga.dto`; converta para os tipos do domínio dentro do adapter. Nenhum tipo de `integration.siga` aparece fora do pacote.
- Base: as consultas de referência de `docs/04` (valide os nomes). Para busca por CPF/CNPJ, compare só dígitos no banco (ex.: `REGEXP_REPLACE(p.CPF_CNPJ, '[^0-9]', '') = :digitos`) e anote como risco de desempenho em `docs/10`. Para processo, use a regra confirmada em P03; enquanto não confirmada, `REQUERIMENTO.NUMERO_PROCESSO` (como na tela atual) e registre.
- **Tarefa atual:** consulta em `SIGA2_CAMUNDA` conforme `docs/04`. Sem linhas → `SEM_TAREFA`. Qualquer falha ao ler → `INDISPONIVEL` (log com correlação), sem derrubar o detalhe. Responsável nulo → "Não atribuída" na tela.
- Se um requerimento sem `ID_PESSOA` (interessado nulo) aparecer, trate sem quebrar.

### 5. Testes do back-end

- `ArchitectureTest` continua verde e agora inspeciona `consulta` e `integration.siga`. Acrescente, se ainda não cobrir: módulos de negócio não dependem de `integration..`; `integration.siga.dto` só é usado dentro de `integration.siga`.
- **Unitários de domínio:** `NumeroRequerimento` (válidos e inválidos), `Identificacao` (RN04, com e sem processo), `SituacaoRequerimento` (todos os valores do Apêndice A + desconhecido), regra de vínculo (RN07: Operador com e sem CPF/CNPJ, formatado e só dígitos, Gestor, Administrador).
- **Casos de uso** com portas falsas (sem Spring): critérios inválidos, 404, 403, paginação, tarefa `INDISPONIVEL` não derruba o detalhe.
- **`MockMvc`** (`@SpringBootTest` sem `SIGA_DB_URL`, com portas substituídas por `@MockitoBean` ou implementações de teste): 200, 400 (formato e sem critério), 403 `NAO_INTERESSADO`, 404, 503 `SIGA_INDISPONIVEL`, sem token → 401; confira `ProblemDetail`, `errorCode` e `X-Correlation-Id`. Sem `SIGA_DB_URL`, `/actuator/health` = `UP` e uma busca = 503.
- **Integração do adapter** contra Oracle 23 Free via Testcontainers (imagem `gvenzl/oracle-free` com **tag fixa** existente), com `@Testcontainers(disabledWithoutDocker = true)` para que `./mvnw clean verify` passe (pulando esses testes) quando não houver Docker. O recorte do modelo do SIGA (schemas `SIGA2_LICENCIAMENTO` e `SIGA2_CAMUNDA` com só as tabelas e colunas usadas, DDL compatível com 19c) fica **só** em `src/test/resources/sql/siga/`. Cubra: busca por requerimento, por processo, por CPF/CNPJ formatado e só dígitos, por nome parcial (com `%` e `_` literais), paginação, requerimento sem processo, interessado nulo, situação desconhecida, tarefa ativa, sem tarefa e falha da tarefa. Reporte quantos testes foram executados e quantos pulados.
- **Teste de fumaça contra o SIGA real** (opcional, desligado por padrão, ativado só por variável de ambiente, somente leitura, que nunca roda no `verify` normal): verifica conexão e que cada coluna usada existe. Útil para o responsável validar quando tiver acesso.

## Escopo — front-end (`front/`)

Sem dependências novas. Reproduza as telas do protótipo com dados reais.

- **`features/consulta/`** (substitui o `EmConstrucaoComponent` da rota `/consulta`): quadro do topo (`QuadroTopoComponent`) com o cartão de vidro de busca (seletor Requerimento/Processo, número com máscara `N/AAAA`, CPF/CNPJ com máscara, nome), "Consultar" e "Limpar", aviso para perfil restrito ("Seu perfil consulta apenas processos e requerimentos em que você é o interessado"), alerta de erro inline. Folha sobreposta com:
  - **lista de resultados** (colunas: número com rótulo Processo/Requerimento e "não protocolado", interessado + CPF/CNPJ, tipo de processo, situação em tag, abertura, ação "Abrir"), paginação, estado vazio "Nenhum resultado encontrado";
  - **"Consultados recentemente"**: atalhos guardados no `localStorage` por login (só id e rótulo, máximo 6), exibidos somente se houver; **sem dados fictícios** na tela.
  - Busca só por número com 1 resultado abre o detalhe direto; 404 e 403 mostram a mensagem inline (ex.: "Processo 2825/2025 não tem você como interessado.").
- **`features/detalhe/`** (rota `/consulta/requerimentos/:id`, dentro do shell): voltar à consulta, quadro do topo com a identificação (RN04), blocos de dados do requerimento (prioridade, tipo, situação, abertura, atualização, interessado, CPF/CNPJ, tipo de processo), cartões de objeto (objetivos, finalidades, atividades, Tipo/Objetivo; "—" quando vazio), cartão **Tarefa atual** (nome, desde quando, responsável; estados "Sem tarefa ativa" e "Tarefa indisponível no momento"), e uma seção "Documentos" apenas com aviso "disponível na Sprint 2" (sem lista). Trilha: `Vistas do Processo / Consulta / <strong>Processo N/AAAA</strong>`. Datas em pt-BR.
- Cada feature tem seu serviço de API e seus modelos; nada de importar arquivos de outra feature (o comum vai para `shared/` ou `core/`: máscaras e formatação de data/CPF-CNPJ, por exemplo).
- Erros: o interceptor de erro já traduz o `errorCode`; para a busca, mostre a mensagem inline em vez de depender só do toast (evite mensagem duplicada). Acrescente os textos novos ao mapa de mensagens se necessário.
- Atualize `app.routes.ts`, o menu (item Consulta continua) e os títulos. `/dossies` e as demais continuam "em construção".
- **Testes (Vitest, headless):** máscaras (número, CPF/CNPJ), serviço de busca (parâmetros, normalização do critério), componente de consulta (validação, critério vazio, formato inválido, abrir direto com 1 resultado, mensagens de 403/404/503), componente de detalhe (RN04 com e sem processo, tarefa nos três estados, campos vazios com "—"), recentes (limite, por login, sem duplicar).

## Docker, ambiente e README

- `.env.local.example`: as variáveis `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD`, `SIGA_DB_POOL_MAX` e, se usar, `SIGA_DB_CONN_TIMEOUT_MS`, `SIGA_DB_READ_TIMEOUT_MS`, `SIGA_DB_MAX_LIFETIME_MS` passam a ser usadas: deixe-as documentadas (pode continuar comentadas, pois o sistema sobe sem elas) e atualize os comentários "a partir da S1".
- O container `api` já recebe o `.env.local` por `env_file`; confirme que as variáveis chegam e que a `api` alcança o Oracle externo a partir da máquina do Docker (nunca coloque o banco do SIGA no Compose).
- Atualize o `README.md`: nova seção "Conectar ao banco do SIGA" (como preencher o `.env.local`, o que esperar com e sem a conexão, o erro 503, como confirmar a conexão), passo no tutorial de uso da tela de consulta e a nota de que sem as variáveis a consulta responde "SIGA indisponível". Reexecute os dois tutoriais do README depois das mudanças e corrija o que divergir.

## Documentação (atualizar durante a sprint, não só no fim)

Atualize os arquivos abaixo **assim que cada parte for implementada**, sem acumular para o fim. Ao terminar, revise tudo com `git diff` para garantir que a documentação reflete o código.

| Arquivo | O que atualizar |
|---|---|
| `docs/04-modelo-de-dados.md` | Mapeamentos confirmados (P01, P03, `TIPO_REQUERIMENTO`), consultas finais usadas, o que continua **NÃO CONFIRMADO** |
| `docs/07-api.md` | Contrato final de `/busca` (página também para busca por número, 403/404/400/503) e de `/requerimentos/{id}` com o JSON de exemplo e `tarefaAtual`; também os endpoints de acesso (`/auth/login`, `/auth/me`, `/auth/logout`) com os três perfis e o campo `permissoes` |
| `docs/08-telas.md` | Login com os três perfis; telas 2 e 3 como implementadas (sem documentos); estados da tarefa atual; "recentes" |
| `docs/10-pendencias-e-riscos.md` | Status de P01, P03, P10, P11; novos riscos (busca por CPF/CNPJ sem índice, tarefa atual dependente das tabelas do motor de fluxo) |
| `docs/14-plano-de-sprints.md` | Marcar o que a S1 entregou, a antecipação da RN07 e o que mudou na S4; registrar a validação (abaixo) |
| `docs/13-padroes-tecnicos.md` e `docs/padroes/` **+ espelho em `.claude/skills/vistas-banco/references/`** | Variáveis novas (`ambientes-e-docker.md`), datasource configurado dentro de `integration.siga`, Testcontainers só em teste, health sem o banco do SIGA, mínimo de Docker Compose 2.24 (usa `env_file` opcional) |
| `docs/03-arquitetura.md` | Variáveis de ambiente e o datasource do SIGA, se mudarem |
| `docs/09-decisoes.md` | Decisão registrada: RN07 antecipada para a S1; busca sempre devolve página; aplicação sobe sem o SIGA |
| `CLAUDE.md` (raiz) | Seção "Estado do repositório" está desatualizada (diz que só há documentação): descreva o estado real (`api/` e `front/` existem; sprint atual). Mantenha o restante |
| `README.md` | Ver seção acima |

**Pendências de documentação herdadas da S0** (aplique agora): `docs/08` fala em dois usuários de demonstração e `docs/07` em dois perfis no login (são três: `OPERADOR`, `OPERADOR_SIGILO`, `ADMIN`); `docs/07` não lista o `405` e o `415` (hoje usam `PARAMETRO_INVALIDO` com o status 405/415: registre a decisão ou proponha códigos próprios no relatório); `docs/03` cita `.env.example` e o repositório usa `.env.local.example`; registrar Node mínimo (22.12; a imagem usa 22.23.3) e que o Angular está na linha 21.

## Verificação

Rode e reporte o resultado de:

```bash
cd api && ./mvnw -Dtest=ArchitectureTest test && ./mvnw clean verify && cd ..
npm --prefix front ci
npm --prefix front run build
npm --prefix front test -- --watch=false
docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml config --services
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
git diff --check
```

(`git diff --check` só funciona se a pasta for um repositório git; se não for, diga isso e faça a conferência manual de espaços finais.)

Com os containers no ar, **sem** as variáveis do SIGA no `.env.local`:

- `http://localhost:8081/actuator/health` → `UP`.
- `GET /api/busca?tipo=REQUERIMENTO&numero=1/2025` com token → **503 `SIGA_INDISPONIVEL`** em `ProblemDetail`, com `X-Correlation-Id`.
- No front (`http://localhost:8080`), uma consulta mostra a mensagem amigável de SIGA indisponível; os três perfis continuam entrando e o menu por perfil não mudou.

**Com acesso ao SIGA** (se o responsável preencheu o `.env.local`): busque por requerimento, processo e interessado; compare **3 processos** com a tela atual do SIGA (identificação, situação, datas, interessado, tipo de processo, tarefa atual) e registre o resultado (processo, campo, bate/não bate) em `docs/14-plano-de-sprints.md`, seção "Validação da S1". Sem acesso ao SIGA, registre explicitamente que essa validação ficou **pendente** e liste o que o responsável precisa fazer (preencher `.env.local`, rodar o teste de fumaça, comparar 3 processos).

Ao final, derrube os containers com `docker compose ... down` (nunca `down -v`).

## Relatório final

Responda com:

1. Versões e dependências acrescentadas, tamanho da imagem da `api` se mudou.
2. Árvore de pastas criada ou alterada (até 4 níveis).
3. Resultado de cada comando de verificação, incluindo quantos testes de integração (Testcontainers) rodaram e quantos foram pulados.
4. O que foi validado contra o SIGA real e o que ficou pendente (e por quê).
5. Campos **NÃO CONFIRMADO** que continuam sem mapeamento (P01, P03, `TIPO_REQUERIMENTO`, prioridade) e o que o responsável precisa fornecer.
6. Desvios em relação a este prompt ou à documentação, e o motivo; conflitos encontrados.
7. Lista dos arquivos de documentação atualizados e das pendências de documentação que sobraram.
8. Riscos para a S2 (documentos e dossiê em PDF).
9. Confirmação de que os tutoriais do README foram executados como escritos, com o resultado.
