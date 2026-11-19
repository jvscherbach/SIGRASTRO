# Rastro

> Rastro, complemento de consulta ao SIGA Licenciamento (protótipo). Antes chamado "SIGA Vistas".

[![CI](https://github.com/jvscherbach/siga-vistas/actions/workflows/ci.yml/badge.svg)](https://github.com/jvscherbach/siga-vistas/actions/workflows/ci.yml)

Integração contínua: veja [Integração contínua](docs/13-padroes-tecnicos.md#integração-contínua) e o [passo a passo no GitHub](docs/13-padroes-tecnicos.md#configuração-no-github-passo-a-passo-feita-pelo-responsável).

O **Rastro** é um serviço externo ao SIGA Licenciamento. Seu primeiro módulo, o **Vistas**, consulta requerimentos e processos, lista os documentos de cada um e gera um **dossiê em PDF**, com os documentos escolhidos em ordem cronológica.

Hoje o projeto é um **protótipo de validação**: o objetivo é provar que os problemas da "Vista do Processo" atual têm solução (processos que não geram a vista e planilhas que ficam fora do PDF). Estão prontas as sprints **S0 a S4**:

- o login simulado com três perfis, cada um com o seu menu;
- a busca de requerimento ou processo, com os dados e a tarefa atual;
- a lista de todos os documentos, inclusive os pessoais do SCORP;
- o dossiê em PDF, com planilhas, textos e imagens convertidos;
- a visualização de cada documento;
- as geometrias em ZIP;
- a tela "Dossiês gerados";
- a geração do dossiê que continua em segundo plano quando a gaveta é fechada (indicador na barra do topo e aviso ao concluir);
- a auditoria de documentos e de acessos (consulta por perfil, detalhe e exportação em CSV);
- as configurações do Administrador (usuários e perfis, parâmetros, conexões com teste e retenção);
- as regras de acesso por perfil, validadas no back-end.

A leitura real do SIGA, dos arquivos e do SCORP depende da configuração descrita em "Conectar ao banco do SIGA", "Gerar um dossiê" e "Documentos pessoais, geometrias e Dossiês gerados". Auditoria e configurações ficam **em memória** (S5): somem ao reiniciar o serviço, e as telas avisam. Veja "Auditoria e configurações (S5)". Toda a documentação está em [docs/README.md](docs/README.md).

## Estrutura do repositório

| Pasta ou arquivo | O que é |
|---|---|
| `api/` | Back-end (Java 21 + Spring Boot). Tem o próprio `Dockerfile`. |
| `front/` | Front-end (Angular). Tem o próprio `Dockerfile` (nginx). |
| `docs/` | Documentação do projeto, padrões técnicos e o protótipo navegável (`docs/prototipo/`). |
| `.claude/` | Skill com os padrões técnicos usados no desenvolvimento assistido. |
| `compose.yaml` | Configuração comum dos serviços `api` e `front` no Docker. |
| `compose.local.yaml` | Configuração do seu computador: portas e volume dos dossiês. |
| `compose.verificacao.yaml` | Esvazia, na `api`, tudo que leva a sistemas externos (SIGA, API de arquivos, SCORP). **Obrigatório em toda verificação feita pelo desenvolvimento** (veja "Verificação sem sistemas externos"). |
| `.env.local.example` | Modelo do arquivo de variáveis `.env.local` (que não vai para o Git). |
| `docs/homologacao/` | Roteiros para conferir a conexão e os dados reais do SIGA e dos arquivos (feitos pelo responsável). |
| `docs/casos-de-teste.md` | Processos de teste (que falham hoje e com planilhas) e o resultado de cada dossiê. Preenchido pelo responsável. |

## Pré-requisitos

Para o **caminho recomendado (Docker)** você só precisa do Docker:

- **Docker Desktop 4.27 ou mais novo** (Windows e macOS), ou **Docker Engine 25 + Docker Compose v2.24 ou mais novo** (Linux). A versão mínima do Compose é 2.24 porque o arquivo `compose.local.yaml` usa `env_file` opcional.
- Para subir só com o login e a tela de consulta, **não é preciso** acesso ao SIGA. Para ler dados reais, veja "Conectar ao banco do SIGA".

Para **rodar sem Docker** (só para quem vai desenvolver) você também precisa de:

- **JDK 21** (o projeto é compilado para Java 21; a imagem Docker usa o Eclipse Temurin 21.0.12).
- **Node.js 22 LTS**, versão 22.12 ou mais nova (o projeto foi testado com a 22.19.0; a imagem Docker usa a 22.23.3). O `npm` já vem com o Node.

Para conferir o que você tem instalado, rode cada comando e compare com a versão mínima:

```bash
docker --version
docker compose version
java -version
node -v
```

Se algum comando responder "não é reconhecido" ou "command not found", aquele programa não está instalado.

## Verificação sem sistemas externos (leia antes de subir pelo desenvolvimento)

O `.env.local` do responsável pode ter acesso ao banco do SIGA, à API de arquivos e ao SCORP **reais**. Qualquer subida feita só para **verificar o código** (por uma pessoa ou por uma ferramenta de desenvolvimento assistido) usa **três** arquivos, o último esvazia no container tudo que leva a esses sistemas:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d --build
```

Antes de qualquer chamada, confirme **dentro do container** (sem imprimir valores) que cada variável está vazia; a saída tem de ser `vazia` em todas:

```bash
for v in SIGA_DB_URL SIGA_DB_USER SIGA_DB_PASSWORD LICENCIAMENTO_API_URL LICENCIAMENTO_API_TOKEN LICENCIAMENTO_API_TOKEN_FILE LICENCIAMENTO_API_COOKIE_NAME SCORP_API_URL SCORP_API_TOKEN_FILE SCORP_API_AUTH_MODE SCORP_API_COOKIE_NAME; do
  docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c "[ -z \"\$$v\" ] && echo $v: vazia || echo $v: PREENCHIDA"
done
```

Se alguma vier `PREENCHIDA`: pare, derrube os containers (**sem `-v`**) e não faça nenhuma chamada. Nunca abra nem imprima o `.env.local`. **Os usos reais** (o seu, com o SIGA e o token) seguem o Tutorial 1, sem o terceiro arquivo.

## Tutorial 1 — Subir tudo com Docker (recomendado)

Os comandos abaixo funcionam no **Bash** (Linux, macOS, Git Bash) e no **PowerShell** (Windows). Quando houver diferença, os dois aparecem.

### 1. Entre na pasta do projeto

```bash
cd siga-vistas
```

Se a pasta estiver em outro lugar, use o caminho dela. Confira com `ls` (Bash) ou `dir` (PowerShell): você deve ver `compose.yaml`, `api` e `front`.

### 2. Crie o arquivo `.env.local`

Bash:

```bash
cp .env.local.example .env.local
```

PowerShell:

```powershell
Copy-Item .env.local.example .env.local
```

O `.env.local` guarda as configurações do seu computador e **não é enviado para o Git**. **Nenhuma variável precisa ser preenchida** para o sistema subir e o login funcionar. Para a consulta mostrar dados reais é preciso preencher as variáveis do banco do SIGA (`SIGA_DB_*`); veja "Conectar ao banco do SIGA" mais abaixo.

### 3. Suba o sistema

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

O que esperar: o Docker baixa as imagens e constrói a `api` e o `front`. Na **primeira vez demora vários minutos** (download das imagens e do LibreOffice). No fim aparecem as linhas `Container ... Started` / `Healthy`.

### 4. Acompanhe até ficar pronto

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml ps
```

Quando a coluna de estado da `api` mostrar **`healthy`**, ela terminou de iniciar e está respondendo (leva de 30 a 90 segundos depois do passo 3; `starting` significa "ainda subindo, aguarde"). O `front` só inicia depois que a `api` fica `healthy`.

Para ver o log da `api` em tempo real (saia com `Ctrl + C`; isso não para o sistema):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs -f api
```

Procure a linha `Started VistasApplication`.

### 5. Abra no navegador

| O que | Endereço | O que você vê |
|---|---|---|
| Sistema (front) | http://localhost:8080 | A tela de login |
| Saúde da API | http://localhost:8081/actuator/health | `{"status":"UP", ...}` |
| Documentação da API (OpenAPI) | http://localhost:8081/swagger-ui.html | A página do Swagger com os endpoints `/api/auth/*` |

### 6. Entre com um usuário de demonstração

Na tela de login, clique em um dos perfis (entra com um clique) ou digite usuário e senha.

| Usuário | Perfil | O que vê no menu | Senha de demonstração |
|---|---|---|---|
| `operador.demo` | Operador | Consulta, Dossiês gerados, Auditoria › Documentos. Só acessa processos em que é o interessado. | `vistas-demo` |
| `gestor.demo` | Gestor (`OPERADOR_SIGILO`) | O mesmo menu do Operador, mas acessa todos os processos e os documentos sigilosos. | `vistas-demo` |
| `admin.demo` | Administrador | Tudo: inclui Auditoria › Acessos e Configurações. | `vistas-demo` |

Essa senha é só para demonstração e só vale no perfil `local`. Para trocar, defina `VISTAS_DEMO_SENHA` no `.env.local` e rode o passo 3 de novo (o clique rápido da tela de login deixa de funcionar; digite usuário e senha).

Depois de entrar, abra **Consulta**. Sem o banco do SIGA configurado, qualquer busca mostra "O banco do SIGA está indisponível no momento." (é o esperado, veja a próxima seção). Com a conexão configurada, a tela de consulta funciona assim:

- escolha **Requerimento** ou **Processo** e digite o número no formato `N/AAAA` (ex.: `3331/2025`); se houver um único resultado, o detalhe abre direto;
- ou informe o **CPF/CNPJ** e/ou parte do **nome** do interessado (mínimo 3 letras) para ver uma lista com paginação;
- o perfil **Operador** só enxerga processos em que ele é o interessado (o CPF/CNPJ do usuário de demonstração é fictício, então ele não verá dados reais);
- as consultas que você abriu aparecem em "Consultados recentemente".

Se você digitar na barra de endereço uma página que o seu perfil não usa (por exemplo `http://localhost:8080/configuracoes` como Operador), o sistema avisa e volta para a Consulta.

### 7. Pare o sistema

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml down
```

> **ATENÇÃO: nunca use `docker compose down -v`.** A opção `-v` apaga os volumes, e o projeto passará a guardar dados neles.

### 8. Atualize depois de mudanças no código

Repita o passo 3 (o `--build` reconstrói só o que mudou):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

## Conectar ao banco do SIGA

A consulta lê o banco Oracle do SIGA **somente para leitura**. O sistema funciona sem essa conexão (login, menu e telas abrem), mas toda busca responde **"O banco do SIGA está indisponível no momento"** (erro `503`, `SIGA_INDISPONIVEL`). Isso é proposital: a falta do SIGA não derruba o sistema nem o `health`.

1. Peça ao DBA do SIGA o usuário de leitura (`VISTAS_LEITURA_SIGA`), a URL do Oracle e a liberação de rede entre o seu computador e o banco. O pedido está detalhado no roteiro de homologação.
2. Abra o `.env.local` e preencha **só os valores**, mantendo os nomes:

```
SIGA_DB_URL=jdbc:oracle:thin:@//HOST:PORTA/SERVICO
SIGA_DB_USER=...
SIGA_DB_PASSWORD=...
```

3. Suba de novo (passo 3 do Tutorial 1) e confira `http://localhost:8081/actuator/health` (deve ficar `UP`).
4. Na tela de consulta, busque um requerimento que você conheça e compare com a tela atual do SIGA.

Se a senha estiver errada, o endereço não responder ou a rede estiver fechada, o sistema continua de pé e a consulta mostra o aviso de indisponibilidade em poucos segundos; corrija o `.env.local` e suba de novo. **Nunca** coloque a senha em outro arquivo nem a envie por e-mail ou chat.

Toda a conferência da conexão e dos dados reais (usuário de leitura, rede, tipos das colunas, comparação com 3 processos, teste de fumaça) está em [docs/homologacao/S1-roteiro-homologacao.md](docs/homologacao/S1-roteiro-homologacao.md). Ela é feita por quem tem acesso ao SIGA.

## Gerar um dossiê (S2)

Com o banco do SIGA configurado (seção anterior) **e** a API de arquivos configurada, abra um requerimento e use a seção **Documentos**.

### Configurar a API de arquivos e o token

1. No `.env.local`, preencha **só os valores** (cada linha começa na coluna 1, **sem espaço antes do nome**):

```
LICENCIAMENTO_API_URL=https://HOST_DA_API_DE_ARQUIVOS
LICENCIAMENTO_API_TOKEN=O_TOKEN_CAPTURADO
# Modo padrão: Authorization: Bearer. Se o SIGA usa cookie, troque:
# LICENCIAMENTO_API_AUTH_MODE=cookie
# LICENCIAMENTO_API_COOKIE_NAME=NOME_DO_COOKIE
```

2. Como capturar o token (procedimento completo no passo 4 de [docs/homologacao/S2-roteiro-homologacao.md](docs/homologacao/S2-roteiro-homologacao.md)): entre no SIGA Licenciamento, abra a Vista de um processo, abra o DevTools do navegador (aba **Network**), visualize um documento e copie o valor do cabeçalho `Authorization` (sem a palavra `Bearer`).
3. **Os acessos ficam registrados no nome de quem capturou o token.** Use só na sua máquina e nunca versione nem envie o token.
4. Suba de novo (passo 3 do Tutorial 1). O token **expira**; quando isso acontecer, a geração mostra "Token da API de arquivos expirado ou recusado. Capture um novo token": capture outro, troque no `.env.local` e suba de novo. Para trocar **sem recriar o container**, use o token por arquivo (abaixo).

### Token por arquivo (sem reiniciar o container)

Com `LICENCIAMENTO_API_TOKEN_FILE` definida, a `api` lê o token de um arquivo **a cada pedido e a cada nova tentativa**. Ela tem precedência sobre `LICENCIAMENTO_API_TOKEN`. Trocar o token é só reescrever o arquivo.

1. Crie a pasta `.secrets/` na raiz do projeto (já está no `.gitignore`) e, dentro dela, o arquivo `token-arquivos` contendo **só o token**, sem a palavra `Bearer`. Uma quebra de linha no final é ignorada.
2. Em `compose.local.yaml`, descomente a linha `- ./.secrets:/run/secrets-vistas:ro` do serviço `api`.
3. No `.env.local`, acrescente `LICENCIAMENTO_API_TOKEN_FILE=/run/secrets-vistas/token-arquivos`. O caminho é o de **dentro do container**.
4. Suba uma vez (passo 3 do Tutorial 1). Quando o token expirar, capture outro e grave por cima de `.secrets/token-arquivos`. Na gaveta, clique em **Tentar novamente**: a próxima chamada já usa o token novo.

Arquivo ausente ou vazio: a geração falha com "API de arquivos não configurada". O valor do token e o caminho do arquivo nunca aparecem em log, erro ou resposta.

Sem `LICENCIAMENTO_API_URL` a lista de documentos funciona normalmente e a geração falha com o motivo "API de arquivos não configurada".

### Usar a lista de documentos e gerar

1. Na **Consulta**, abra um requerimento ou processo. Abaixo do objeto aparece a seção **Documentos**, com todos os documentos de todas as origens, em ordem cronológica.
2. **Todos os documentos selecionáveis já vêm marcados** (vigentes e sem efeito, inclusive os de tipo não informado). Planilhas, textos e imagens (S3) também vêm marcados e entram no dossiê **convertidos para PDF**, com a etiqueta "será convertido para PDF"; só ficam bloqueados, com "conversão indisponível", os formatos sem conversão (por exemplo `txt`) ou todos os não PDF se `CONVERSAO_HABILITADA=false`; indisponíveis ficam esmaecidos; os sigilosos aparecem mascarados para o Operador. A linha da seção mostra "N documentos · V vigentes · S sem efeito · I indisponíveis".
3. Use a **busca**, o **filtro de origem** e a **ordenação** (clique em Documento, Origem ou Data). A ordenação da tabela não muda o dossiê, que segue sempre a ordem cronológica do servidor. "Seleção padrão" volta a marcar todos os selecionáveis; "Limpar seleção" desmarca tudo; a caixa do cabeçalho marca os documentos listados.
4. **Visualizar** (em cada linha) abre o documento em PDF num modal; os convertidos mostram "Versão convertida para PDF a partir de XLSX" e o modal também marca ou desmarca o documento. Clique em **Gerar dossiê**. A gaveta mostra o progresso e a situação de cada documento. Pode fechá-la: a geração continua no servidor.
5. Ao concluir, **Pré-visualizar** abre o PDF sem baixar e **Baixar PDF** salva o arquivo (`dossie-processo-N-AAAA.pdf`). Logo após o sumário, o PDF lista os "Documentos não incluídos" e o motivo, e a capa diz "N documentos incluídos de M listados; K convertidos para PDF". Os convertidos aparecem no sumário e nos marcadores como "(convertido de XLSX)". Acima de 150 MB (`DOSSIE_BLOB_MAX_MB`) a pré-visualização fica desligada e o navegador baixa o arquivo direto. O arquivo é apagado do servidor depois do download ou em 30 minutos sem atividade.
6. Se algum documento falhar, **nada é entregue sem a sua decisão**: a geração vai até o fim, a gaveta lista cada documento com problema e o motivo, e oferece **Tentar novamente os N com problema**, **Remover os N com problema e gerar** ou **Cancelar**. Nenhuma das opções baixa de novo o que já foi obtido.

## Documentos pessoais, geometrias e Dossiês gerados (S4)

**Documentos pessoais (SCORP).** Com `SCORP_API_URL` preenchida no `.env.local`, a seção Documentos traz também os documentos pessoais do interessado, dos representantes legais e dos responsáveis técnicos, com a origem "Pessoal · <papel>". Eles entram no dossiê como os demais. A autenticação usa, por padrão, o mesmo token da API de arquivos. Se o SCORP exigir outra credencial (**NÃO CONFIRMADO**, P06), use `SCORP_API_TOKEN_FILE`, `SCORP_API_AUTH_MODE` e `SCORP_API_COOKIE_NAME` (veja o `.env.local.example`). Com o SCORP fora do ar, a lista mostra os demais documentos e uma faixa de aviso com **Consultar novamente**. Sem `SCORP_API_URL`, nada é chamado. O sigilo padrão dos pessoais é `SCORP_SIGILO_PADRAO=false` (decisão pendente com a SEMA, P30).

**Geometrias.** SHP, KML e KMZ não entram no dossiê. Eles aparecem na seção **Geometrias**, abaixo de Documentos, e **Baixar geometrias** entrega um ZIP com as vigentes. Se algum arquivo falhar, nenhum ZIP é entregue e a tela lista os arquivos com problema. Para o Operador, as sigilosas não aparecem: a tela mostra só quantas são.

**Dossiês gerados.** No menu, a tela lista os dossiês desta sessão do serviço, com situação, expiração e as ações: pré-visualizar, baixar, decidir, cancelar e abrir o requerimento. O Operador vê só os seus; Gestor e Administrador veem todos e podem filtrar "Só os meus". O histórico fica **em memória**: some quando a `api` reinicia.

O roteiro de homologação com dados reais é o [docs/homologacao/S4-roteiro-homologacao.md](docs/homologacao/S4-roteiro-homologacao.md).

## Auditoria e configurações (S5)

**Geração em segundo plano.** Depois de clicar em **Gerar dossiê**, você pode fechar a gaveta (clique fora, Esc ou "Fechar"): a geração continua. Um indicador na barra do topo mostra o andamento em todas as telas, avisa quando o dossiê fica pronto ou precisa da sua decisão e reabre a gaveta com um clique. Só **Cancelar geração** cancela. A tela acompanha um pedido por vez.

**Auditoria.** No menu, **Auditoria › Documentos** lista consultas, visualizações, dossiês, falhas, downloads e geometrias, com período, indicadores, filtros, detalhe e **Exportar CSV** (abre direto no Excel). O Operador vê só os eventos dos **seus** processos, de qualquer usuário, e documento sigiloso aparece como "Documento sigiloso". **Auditoria › Acessos** (entradas, trocas de perfil e alterações de usuários e de configuração) é só do Administrador. A auditoria é somente inclusão: nenhuma tela altera ou apaga evento. Limites e retenção: `AUDITORIA_MAX_EVENTOS`, `AUDITORIA_RETENCAO_*` e `AUDITORIA_CSV_MAX_LINHAS` (`.env.local.example`).

**Configurações (só Administrador).** Usuários e perfis (com as proteções do último Administrador e do próprio usuário), parâmetros do dossiê, da conversão, da visualização e da auditoria, e conexões com teste. Cada parâmetro mostra se vale já, na próxima geração ou só depois de reiniciar. As alterações de parâmetros ficam pendentes e saem juntas em **Salvar alterações** (um único evento na auditoria). Credenciais não aparecem nem são editáveis.

> **Tudo em memória.** Eventos, usuários incluídos e parâmetros alterados pela tela **somem ao reiniciar o serviço**; volta o que está nas variáveis de ambiente e os três usuários de demonstração. O registro permanente é da versão definitiva (schema próprio).

O roteiro de homologação com dados reais é o [docs/homologacao/S5-roteiro-homologacao.md](docs/homologacao/S5-roteiro-homologacao.md).

## Tutorial 2 — Rodar sem Docker (desenvolvimento)

Use este caminho para desenvolver. **Pare o Tutorial 1 antes** (`down`), porque os dois usam a porta 8081.

### 1. Suba a API

Abra um terminal. Linux, macOS ou Git Bash:

```bash
cd api
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--server.port=8081"
```

Windows (PowerShell):

```powershell
cd api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--server.port=8081"
```

Mantenha as aspas. A primeira vez baixa o Maven e as dependências (alguns minutos). A API subiu quando aparece `Started VistasApplication` e `Tomcat started on port 8081`. Confira em outro terminal:

```bash
curl http://localhost:8081/actuator/health
```

Resposta esperada: `{"status":"UP", ...}`. Sem `curl`, abra o endereço no navegador.

> A API usa por padrão a porta 8080. O `--server.port=8081` faz ela ouvir na 8081, que é a porta para onde o front encaminha as chamadas (veja o passo 2).

### 2. Suba o front

Abra **outro** terminal, na pasta do projeto:

```bash
cd front
npm ci
npm start
```

O `npm ci` instala as dependências (1 a 2 minutos na primeira vez). O `npm start` roda `ng serve` e termina mostrando `Local: http://localhost:4200/`. Abra **http://localhost:4200**.

O arquivo `front/proxy.conf.json` faz o `ng serve` encaminhar tudo que começa com `/api` para a API em `http://localhost:8081`. Se você subir a API em outra porta, mude o `target` desse arquivo para a mesma porta e reinicie o `npm start`.

Entre com `operador.demo`, `gestor.demo` ou `admin.demo` (senha `vistas-demo`, igual ao Tutorial 1). Para parar, use `Ctrl + C` em cada terminal.

## Rodar os testes

Back-end (na pasta `api`):

```bash
./mvnw clean verify
```

Só os testes de arquitetura (rápido, não precisa de Docker):

```bash
./mvnw -Dtest=ArchitectureTest test
```

No Windows (PowerShell) use `.\mvnw.cmd` no lugar de `./mvnw`.

Front-end (na pasta `front`, depois do `npm ci`):

```bash
npm test -- --watch=false
```

Resultado esperado: tudo verde (`BUILD SUCCESS` no back-end e `passed` no front).

Os testes de integração do banco usam um Oracle de teste dentro do Docker (Testcontainers). **Sem Docker, eles são pulados** e o `./mvnw clean verify` ainda passa; com Docker, o primeiro teste leva alguns minutos para baixar a imagem e iniciar o Oracle.

Existem também testes de fumaça contra o SIGA real e contra a API de arquivos real, que **não rodam sozinhos**: só o responsável os usa na homologação (veja os roteiros, em `docs/homologacao/`).

Testes e2e do front (só desenvolvimento, **usam a API simulada**: nenhum sistema real é tocado). Medem a altura do quadro do topo em cada estado e o fluxo de documentos e dossiê num Chromium de verdade. Na primeira vez, instale o navegador:

```bash
cd front
npx playwright install chromium
npm run e2e
```

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| **Porta 8080 ou 8081 ocupada** (`port is already allocated`) | Descubra quem usa: Windows `netstat -ano \| findstr :8080`; Linux/macOS `lsof -i :8080`. Feche esse programa ou troque a porta da esquerda em `compose.local.yaml` (ex.: `"9080:8080"` para o front e `"9081:8080"` para a api) e repita o passo 3. |
| **Porta 4200 ocupada** (Tutorial 2) | Rode `npm start -- --port 4300` e abra `http://localhost:4300`. |
| **"Cannot connect to the Docker daemon"** / "docker: command not found" / `failed to connect to the docker API` | O Docker não está iniciado. Abra o Docker Desktop, espere ele indicar que está rodando e tente de novo. No Linux: `sudo systemctl start docker`. |
| **"Baixar PDF" falha com "Não foi possível conectar ao servidor"** num dossiê de dezenas de MB (o DevTools mostra 200 OK sem corpo; o servidor e o nginx estão bem) | O navegador não consegue receber o PDF por Blob (extensão, antivírus ou proteção do navegador). Reduza `DOSSIE_BLOB_MAX_MB` no `.env.local` (por exemplo, `20`) e reinicie a `api`: dossiês acima desse tamanho baixam direto pelo navegador, por ticket, sem **Pré-visualizar**. Para achar a causa, veja o `net::ERR_...` no Console do navegador e teste em janela anônima. |
| **Primeira construção muito demorada** | É normal: o Docker baixa as imagens e instala o LibreOffice (conversão de documentos, usada a partir da Sprint 3) dentro da imagem da `api`. As próximas vezes usam o cache e levam segundos. |
| **Erro de permissão em `/var/tmp/vistas`** (volume `dossies-tmp`) | O volume foi criado com dono diferente. Pare o sistema (`down`, **sem `-v`**), remova só esse volume com `docker volume rm siga-vistas_dossies-tmp` e suba de novo. Nesta sprint ele ainda não guarda nada importante. |
| **`env file ... .env.local not found`** | Você não fez o passo 2 do Tutorial 1. Copie o `.env.local.example` para `.env.local`. |
| **`unknown field "required"`** ou erro no `env_file` | Seu Docker Compose é anterior à 2.24. Atualize o Docker Desktop (veja os pré-requisitos). |
| **A tela volta para o login sozinha** | A sessão do protótipo vale 8 horas e fica só na aba do navegador. Se a API foi reiniciada ou a sessão expirou, o sistema avisa "sua sessão expirou" e volta ao login: entre de novo. |
| **A consulta diz "O banco do SIGA está indisponível no momento"** | O `.env.local` não tem as variáveis `SIGA_DB_*`, a senha/URL está errada ou a rede não alcança o Oracle. Veja "Conectar ao banco do SIGA". O resto do sistema continua funcionando. |
| **SIGA indisponível só dentro do container, mas o host do banco responde no seu computador** | O container não resolve o nome curto do host do banco (ex.: `oracle-siga`). Em `SIGA_DB_URL` no `.env.local`, use o nome completo (FQDN, ex.: `oracle-siga.dominio.interno`) ou o IP, e suba de novo. |
| **Os containers sobem, mas `localhost:8080` e `localhost:8081` não abrem** (`docker compose ps` não mostra portas) | Faltou o `-f compose.local.yaml`, que é quem publica as portas. Use sempre `docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml ...`. |
| **No Tutorial 2, o front mostra erro ao entrar / "não foi possível conectar"** | A API não está no ar na porta 8081. Confira o terminal da API e o `curl` do passo 1. |
| **Uma variável do `.env.local` "não vale" / o container parece ignorar o que você preencheu** | **Linhas do `.env.local` não podem começar com espaço** (nem ter espaço em volta do `=`). Uma linha com espaço no início não é lida como você espera. Confira abrindo o arquivo no seu editor, nunca imprimindo-o. |
| **A geração do dossiê falha com "API de arquivos não configurada"** | `LICENCIAMENTO_API_URL` está ausente ou vazia no `.env.local` (ou você subiu com o `compose.verificacao.yaml`, que a esvazia de propósito). Veja "Gerar um dossiê". |
| **"Token da API de arquivos expirado ou recusado"** | O token expirou ou foi capturado para outro ambiente. Capture outro (passo 4 do roteiro da S2), troque no `.env.local` e suba de novo. |
| **O dossiê "expirou" ao tentar baixar** | O arquivo vale 30 minutos (`DOSSIE_TTL_MINUTOS`) e é apagado depois do download. Gere de novo. |
| **`npm run e2e` diz que falta o navegador** | Rode `npx playwright install chromium` na pasta `front`. |
| **Windows: `mvnw.cmd` diz que `JAVA_HOME` não está definido** | Instale o JDK 21 e defina a variável `JAVA_HOME` com a pasta dele; abra um terminal novo e confira com `java -version`. |

## Links

- [Plano de sprints](docs/14-plano-de-sprints.md)
- [Roteiro de homologação da S1](docs/homologacao/S1-roteiro-homologacao.md)
- [Roteiro de homologação da S2](docs/homologacao/S2-roteiro-homologacao.md)
- [Roteiro de homologação da S3](docs/homologacao/S3-roteiro-homologacao.md)
- [Casos de teste](docs/casos-de-teste.md)
- [Padrões técnicos](docs/13-padroes-tecnicos.md)
- [Protótipo navegável](docs/prototipo/siga-vistas-prototipo.html) (abra no navegador; é a referência visual)
