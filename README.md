# SIGA Vistas

O **SIGA Vistas** (nome provisório) é um serviço externo ao SIGA Licenciamento. Ele consulta requerimentos e processos, lista os documentos de cada um e gera um **dossiê em PDF**, com os documentos escolhidos em ordem cronológica.

Hoje o projeto é um **protótipo de validação**: o objetivo é provar que os problemas da "Vista do Processo" atual têm solução (processos que não geram a vista e planilhas que ficam fora do PDF). Esta é a **Sprint 0 (Fundação)**: o esqueleto sobe e o login simulado funciona, mas as telas de consulta, dossiês, auditoria e configurações ainda mostram a página "em construção". Toda a documentação está em [docs/README.md](docs/README.md).

## Estrutura do repositório

| Pasta ou arquivo | O que é |
|---|---|
| `api/` | Back-end (Java 21 + Spring Boot). Tem o próprio `Dockerfile`. |
| `front/` | Front-end (Angular). Tem o próprio `Dockerfile` (nginx). |
| `docs/` | Documentação do projeto, padrões técnicos e o protótipo navegável (`docs/prototipo/`). |
| `.claude/` | Skill com os padrões técnicos usados no desenvolvimento assistido. |
| `compose.yaml` | Configuração comum dos serviços `api` e `front` no Docker. |
| `compose.local.yaml` | Configuração do seu computador: portas e volume dos dossiês. |
| `.env.local.example` | Modelo do arquivo de variáveis `.env.local` (que não vai para o Git). |

## Pré-requisitos

Para o **caminho recomendado (Docker)** você só precisa do Docker:

- **Docker Desktop 4.27 ou mais novo** (Windows e macOS), ou **Docker Engine 25 + Docker Compose v2.24 ou mais novo** (Linux). A versão mínima do Compose é 2.24 porque o arquivo `compose.local.yaml` usa `env_file` opcional.

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

O `.env.local` guarda as configurações do seu computador e **não é enviado para o Git**. **Nesta sprint nenhuma variável precisa ser preenchida** para o login funcionar. A partir da Sprint 1 será preciso preencher as variáveis do banco do SIGA (`SIGA_DB_*`).

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

## Problemas comuns

| Sintoma | O que fazer |
|---|---|
| **Porta 8080 ou 8081 ocupada** (`port is already allocated`) | Descubra quem usa: Windows `netstat -ano \| findstr :8080`; Linux/macOS `lsof -i :8080`. Feche esse programa ou troque a porta da esquerda em `compose.local.yaml` (ex.: `"9080:80"` para o front e `"9081:8080"` para a api) e repita o passo 3. |
| **Porta 4200 ocupada** (Tutorial 2) | Rode `npm start -- --port 4300` e abra `http://localhost:4300`. |
| **"Cannot connect to the Docker daemon"** / "docker: command not found" / `failed to connect to the docker API` | O Docker não está iniciado. Abra o Docker Desktop, espere ele indicar que está rodando e tente de novo. No Linux: `sudo systemctl start docker`. |
| **Primeira construção muito demorada** | É normal: o Docker baixa as imagens e instala o LibreOffice (conversão de documentos, usada a partir da Sprint 3) dentro da imagem da `api`. As próximas vezes usam o cache e levam segundos. |
| **Erro de permissão em `/var/tmp/vistas`** (volume `dossies-tmp`) | O volume foi criado com dono diferente. Pare o sistema (`down`, **sem `-v`**), remova só esse volume com `docker volume rm siga-vistas_dossies-tmp` e suba de novo. Nesta sprint ele ainda não guarda nada importante. |
| **`env file ... .env.local not found`** | Você não fez o passo 2 do Tutorial 1. Copie o `.env.local.example` para `.env.local`. |
| **`unknown field "required"`** ou erro no `env_file` | Seu Docker Compose é anterior à 2.24. Atualize o Docker Desktop (veja os pré-requisitos). |
| **A tela volta para o login sozinha** | A sessão do protótipo vale 8 horas e fica só na aba do navegador. Se a API foi reiniciada ou a sessão expirou, o sistema avisa "sua sessão expirou" e volta ao login: entre de novo. |
| **No Tutorial 2, o front mostra erro ao entrar / "não foi possível conectar"** | A API não está no ar na porta 8081. Confira o terminal da API e o `curl` do passo 1. |
| **Windows: `mvnw.cmd` diz que `JAVA_HOME` não está definido** | Instale o JDK 21 e defina a variável `JAVA_HOME` com a pasta dele; abra um terminal novo e confira com `java -version`. |

## Links

- [Plano de sprints](docs/14-plano-de-sprints.md)
- [Padrões técnicos](docs/13-padroes-tecnicos.md)
- [Protótipo navegável](docs/prototipo/siga-vistas-prototipo.html) (abra no navegador; é a referência visual)
