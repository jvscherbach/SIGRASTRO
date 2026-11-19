# R1: renomeação do projeto para Rastro

> **Forma de trabalho:** a sprint é dividida em **7 blocos**, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**, esta só nos blocos mecânicos) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

O projeto, que nasceu como **SIGA Vistas** (geração do dossiê da Vista do Processo), passa a se chamar **Rastro**: um complemento de **consulta** ao SIGA Licenciamento (onde o processo está, por onde passou, histórico financeiro e, mais adiante, painel do Gestor). A Vista do Processo continua existindo como **módulo** do Rastro. O nome antigo **"SIGA Vistas"** fica só como histórico.

A renomeação tem **duas fases**, com um **ponto de parada** entre elas:

- **Fase 1 (blocos 1 a 4): nome visível e documentação.** Rápida e de baixo risco. Pode e deve ser feita **antes da apresentação**, para que a versão apresentada já mostre "Rastro".
- **Fase 2 (blocos 5 a 7): renomeação técnica** (pacote Java, artefatos, nomes de container, chaves de armazenamento, workflows). Tem risco real (quebra de importações, de volumes e de sessões). **Só execute depois da apresentação** e **depois que o responsável criar a marca de versão do estado apresentado** (tag Git `vistas-1.0`, que ele mesmo cria). Antes de começar o bloco 5, **pergunte** se a marca já foi criada e **espere a resposta**; sem confirmação, **não faça nada** da Fase 2.

Esta sprint **não muda comportamento**: só nomes. Nenhuma regra, rota (`/api/...`), tabela, consulta ou mensagem de negócio muda de sentido.

Leia antes de começar: `README.md`, `CLAUDE.md`, `docs/01`, `docs/03`, `docs/09`, `docs/13`, `docs/14` e `docs/progresso/` (o último arquivo).

## Decisões já tomadas (registre em `docs/09`)

- **Nome oficial: Rastro.** Descrição curta: "Rastro, complemento de consulta ao SIGA Licenciamento (protótipo)". **Não** use "SIGA Rastro", "Rastro SIGA" nem o brasão/logotipo da SEMA como se fosse marca do Rastro (o Rastro **não é** um sistema oficial do SIGA; a decisão sobre a capa do dossiê com a logo da SEMA é da S6, não desta sprint).
- **Rastro é o produto; "Vistas" é o módulo** do dossiê. Na interface: nome do produto "Rastro" no topo; o item de menu e a tela do dossiê continuam chamando **"Vistas"** (ou o rótulo atual) e os próximos módulos virão como "Tramitação", "Taxas" e "Painel".
- **Rotas da API, nomes de tabela, schemas do SIGA (`SIGA2_*`), variáveis de ambiente e nomes de arquivo de dados do usuário não mudam**, exceto o que for listado como mudança na Fase 2.
- **Variáveis de ambiente:** **não renomeie**. Se alguma contiver o nome antigo, **mantenha a antiga funcionando** e aceite a nova como alias (a antiga vence se as duas existirem), com aviso de depreciação no log **sem valor**. O `.env.local` do responsável **não pode quebrar**: você não o abre.
- **Documentos históricos** (`docs/prompts/`, `docs/progresso/` de sprints passadas, textos de PR, roteiros já homologados) **não são reescritos**: são registro da época. Só ganham, no índice de `docs/prompts/`, a nota "projeto então chamado SIGA Vistas".
- **O repositório e a pasta no GitHub/disco** (`siga-vistas`) são renomeados **pelo responsável**, à mão, depois da Fase 2. Você **não** mexe em remoto, nome de pasta nem histórico do Git.

## Regras desta sprint

- **Você não executa nada contra o SIGA real, o SCORP real, o Camunda real nem a API de arquivos real.** Testes e demonstração com dados **fictícios e sintéticos** (nenhum CPF, CNPJ, nome, número de processo ou número de DAR real em teste, doc, exemplo, log ou repositório). CPF fictício é sempre **inválido de propósito** (ex.: `000.000.000-01`) e nomes são evidentes (`Servidor Exemplo 01`).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas (`SIGA_DB_*`, `LICENCIAMENTO_API_*`, `SCORP_*`) estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): bibliotecas novas só gratuitas e de código aberto, com licença registrada em `docs/13`. Prefira o que o projeto já tem; para diagramas, **sem biblioteca**: HTML, CSS e SVG simples.
- **Somente leitura no banco.** O usuário do banco só tem `SELECT`. Todo SQL novo é `SELECT`/`WITH`, com **parâmetros nomeados** (nunca concatenar valor), `FETCH FIRST n ROWS ONLY`, tempo limite de consulta e concentrado **num único componente de acesso** (risco R04 de `docs/10`). Nada é gravado no Oracle.
- **Dados pessoais:** `ASSIGNEE_`, vínculos de identidade e variáveis do Camunda guardam **CPF e nome**. O CPF **nunca** sai em resposta, log, erro, evento de auditoria, CSV ou tela, **nem parcialmente mascarado**. Variáveis do Camunda só podem ser lidas de uma **lista fixa** (lista branca) sem dado pessoal. A chave do arquivo, tokens e cookies também nunca saem (RNF09).
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Datas e horas sempre no fuso de **Cuiabá (`America/Cuiaba`)**, pela utilidade única já existente (S5.1). Data pura (sem hora) nunca muda de dia na conversão.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/r1.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/r1.md` (crie no bloco 1, com a tabela dos 7 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET|HAIKU>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET|HAIKU>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`. A mensagem **não** começa com espaço.

## Blocos e sessões

| Bloco | Título | Sessão | Fase | Por quê |
|---|---|---|---|---|
| 1 | Inventário das ocorrências do nome antigo | **SONNET** | 1 | Levantamento e classificação, sem alterar código |
| 2 | Front: nome visível do produto | **SONNET** | 1 | Interface e testes |
| 3 | Back-end e PDF: nome visível | **SONNET** | 1 | Textos, metadados e títulos |
| 4 | Documentação e `CLAUDE.md` | **HAIKU** | 1 | Troca de texto em Markdown, mecânica; a varredura do bloco 7 confere |
| 5 | Renomeação técnica do back-end | **OPUS** | 2 | Pacote, artefatos, ArchUnit: risco de quebra |
| 6 | Renomeação técnica do front e da infraestrutura | **OPUS** | 2 | Containers, volumes, chaves de armazenamento, CI |
| 7 | Varredura final e fechamento | **HAIKU** | 2 | Buscas e execução de comandos; o que for ambíguo é só relatado |

---

## Bloco 1 (SONNET): inventário

**Commit sugerido:** `docs(rastro): inventário das ocorrências do nome antigo`

- Procure, **sem alterar nada além do documento novo**, todas as ocorrências (sem diferenciar caixa) de `siga-vistas`, `siga vistas`, `sigavistas`, `siga_vistas`, `vistas` (ocorrências relevantes: nome de produto, pacote, artefato, container, volume, rede, chave de armazenamento, cookie, título), `Vistas do Processo` e `br.gov.mt.sema.vistas`, em **todo** o repositório (código, testes, configuração, Docker, CI, docs, README, `CLAUDE.md`, `package.json`, `angular.json`, `pom.xml`/`build.gradle`, nginx, `.env.example`, `.env.local.example`; **não** abra `.env.local`).
- Crie `docs/rastro/inventario-renomeacao.md` com uma tabela: arquivo, linha, trecho, **classe** e **ação**:
  - **A, visível:** vê o usuário (títulos, cabeçalhos, login, mensagens, PDF, metadados, favicon, `<title>`).
  - **B, técnica:** pacote, artefato, nome de container/imagem/volume/rede, chave de armazenamento do navegador, cookie, nome de workflow, nome de projeto Angular/npm.
  - **C, histórico:** documento de sprint passada, prompt, PR, roteiro homologado (**não muda**).
  - **D, não nosso:** nome que pertence ao SIGA (`Vista do Processo` do SIGA Licenciamento, rotas e telas do SIGA, `SIGA2_*`) ou a terceiros (**não muda**).
  - **E, "vistas" com outro sentido** (palavra comum em texto): julgue pelo contexto.
- Resumo no topo: total por classe e **riscos** da Fase 2 (volume do Docker, chave de armazenamento que desloga usuários, importações, regras ArchUnit por nome de pacote, nomes em workflows e em segredos do CI, imagens em cache).
- Sem alterações em código.
- Docs: `docs/09` (decisão do nome e das duas fases), `docs/progresso/r1.md` (crie com a tabela dos 7 blocos).

## Bloco 2 (SONNET): front, nome visível

**Commit sugerido:** `feat(front): nome do produto Rastro na interface`

- Troque as ocorrências **A** do front: `<title>`, cabeçalho/topo (o produto aparece como **Rastro**, com o rótulo curto "consulta ao SIGA Licenciamento (protótipo)" quando houver espaço), tela de login, rodapé, mensagens, texto do aviso de protótipo, favicon (se tiver texto), `manifest`, textos de ajuda e rótulos de acessibilidade.
- O **módulo do dossiê** mantém o rótulo "Vistas" (ou o atual) no menu; só o **produto** muda.
- Não altere rotas do front, nomes de componentes nem chaves de armazenamento (Fase 2).
- Testes: ajuste os testes (Vitest e Playwright) que dependem do texto antigo; novo teste confirmando que **nenhum texto visível** contém "SIGA Vistas" (varredura do DOM nas telas principais) e que o `<title>` é "Rastro".
- Docs: `docs/08` (nome do produto e do módulo).

## Bloco 3 (SONNET): back-end e PDF, nome visível

**Commit sugerido:** `feat(back): nome do produto Rastro em respostas, PDF e metadados`

- Troque as ocorrências **A** do back-end: título e descrição do **OpenAPI**, mensagens de erro e de saúde visíveis, banner de inicialização, **metadados do PDF** (título, criador/produtor, assunto) e o texto de rodapé/capa **que cita o nome do serviço** (a **capa** do dossiê em si não muda de layout: é assunto da S6), nome do arquivo gerado se contiver o nome antigo (mantenha compatível: arquivos já baixados não são afetados).
- **Não** altere pacote, artefato, rotas, nomes de propriedades de configuração nem de variáveis de ambiente (Fase 2).
- Testes: ajuste os que verificam o texto antigo; novo teste dos metadados do PDF (produtor "Rastro") e do título do OpenAPI.
- Docs: `docs/07` (título da API).

## Bloco 4 (HAIKU): documentação e `CLAUDE.md`

**Commit sugerido:** `docs(rastro): documentação e instruções do projeto passam a usar o nome Rastro`

- Atualize `README.md` (nome, descrição, índice), `CLAUDE.md` (nome do projeto, **nota**: "antes SIGA Vistas"; as regras permanentes não mudam), `docs/01` (visão geral: o Rastro e o módulo Vistas, o escopo em fases), `docs/03` (nome do produto), `docs/14` (plano de sprints: R1, S6 a S11 como no índice `docs/prompts/INDICE.md`, se existir), e os comentários de `.env.example`/`.env.local.example` **sem trocar nomes de variável**.
- **Não reescreva** documentos de classe **C** (histórico). No `docs/prompts/` (se o índice existir), acrescente só a nota "projeto então chamado SIGA Vistas".
- Remova "Nome do projeto provisório" do `README.md`.
- Varredura: `docs/` sem "SIGA Vistas" fora de classe C e fora da nota histórica.
- Docs: `docs/progresso/r1.md`.
- **Limite do bloco (HAIKU):** só substituições de texto nos arquivos listados. Se um trecho for ambíguo (ex.: "SIGA Vistas" dentro de uma explicação histórica), **não altere**: liste no resumo e deixe para o responsável.

> **PONTO DE PARADA DA FASE 1.** Depois do bloco 4, **encerre** e avise: "Fase 1 concluída. Fase 2 só após a apresentação e a criação da marca de versão `vistas-1.0`."

## Bloco 5 (OPUS): renomeação técnica do back-end

**Commit sugerido:** `refactor(back): renomeia pacote e artefatos de vistas para rastro`

**Antes de começar:** pergunte ao responsável se a **marca de versão `vistas-1.0` já foi criada** e se a apresentação já aconteceu. Sem a confirmação, **pare**.

- **Pacote raiz** `br.gov.mt.sema.vistas` → `br.gov.mt.sema.rastro` em **todos** os módulos (código, testes, `module-info` se houver, `META-INF/spring`, `@ComponentScan`, `@ConfigurationProperties` e **regras do ArchUnit** que citam o pacote), preservando a estrutura interna e o histórico (mova os arquivos, não recrie).
- **Artefato e nome da aplicação:** `groupId`/`artifactId`/`name` no build, `spring.application.name`, classe principal (`...VistasApplication` → `...RastroApplication`), nome do JAR, nome da imagem no `Dockerfile`, `finalName`.
- **Propriedades e chaves de configuração** com prefixo antigo (`vistas.*`): renomeie para `rastro.*` **mantendo o prefixo antigo como alias** (leitura dos dois; o antigo vence se ambos existirem; log de depreciação sem valor). Variáveis de ambiente **não** mudam (decisão).
- Logs: nome do *logger*/categoria ajustado; formato e níveis intactos.
- **Não** toque em SQL, schemas do SIGA, rotas `/api`, nem no `.env.local`.
- Verificação: compila, **todos os testes** e o ArchUnit verdes; `docker compose` com os 3 arquivos sobe e responde na saúde; confirme dentro do container que as variáveis externas continuam **vazias** (só "vazia"/"preenchida").
- Testes: um teste novo de **alias de propriedade** (a antiga ainda funciona e a nova também; a antiga vence).
- Docs: `docs/03`, `docs/13` (`padroes/modulos-e-dependencias.md` com o novo pacote), `docs/09` (D de alias), `docs/progresso/r1.md`.

## Bloco 6 (OPUS): renomeação técnica do front e da infraestrutura

**Commit sugerido:** `refactor(infra): renomeia projeto, containers, volumes e chaves de armazenamento para rastro`

- **Front:** `name` do `package.json`, nome do projeto em `angular.json` (cuidado com `outputPath`, `architect` e caminhos de teste), nomes em `playwright`/`vitest` configs, `base href` se contiver o nome.
- **Docker/Compose:** nome do projeto Compose (`name:`), nomes de serviço, imagem, container e rede com o nome antigo → `rastro`. **Volumes:** **não perca dados do responsável.** Mantenha o nome de volume antigo **ou** documente a migração (copiar para o novo volume) e **nunca** rode `docker compose down -v`; se o volume só guarda arquivos temporários de dossiê, registre que o descarte é seguro, mas **não decida por conta própria**: deixe o nome do volume como está e explique no `docs/13` como renomear manualmente.
- **Chaves de armazenamento do navegador e cookies** com o nome antigo: leia a **antiga como fallback** por uma versão (migra o valor para a nova chave e apaga a antiga) para **não deslogar nem perder preferências**; teste do fallback.
- **nginx:** nomes de *upstream*, de log e de arquivo de configuração; **rotas `/api` não mudam**.
- **CI (`.github/workflows`):** nomes de workflow, de job e de artefato; **não** renomeie segredos nem variáveis do repositório sem listar (liste em `docs/13` o que o responsável deve ajustar à mão); sem passos pagos.
- **`compose.verificacao.yaml`:** ajuste e rode a verificação completa com os 3 arquivos; confirme as variáveis externas **vazias** dentro dos containers.
- Testes: suíte completa do front, Playwright, e a do back; teste do fallback de armazenamento.
- Docs: `docs/13` (lista **manual** para o responsável: renomear o repositório no GitHub e a pasta local, atualizar `git remote set-url`, segredos do CI, `.env.local` se tiver prefixo antigo; **você não executa nada disso**), `docs/09`, `docs/progresso/r1.md`.

## Bloco 7 (HAIKU): varredura final e fechamento

**Commit sugerido:** `docs(rastro): varredura final da renomeação e fechamento da R1`

- **Varredura** repetindo o inventário do bloco 1: não pode sobrar o nome antigo **fora** das classes C (histórico), D (não nosso) e dos **aliases documentados** (propriedades, variáveis de ambiente, chaves de armazenamento). Liste o que sobrou e o motivo em `docs/rastro/inventario-renomeacao.md` (seção "Resultado final").
- `docs/roteiros/r1-homologacao.md`: roteiro curto: abrir a aplicação (nome e `<title>`), login, consulta, geração do dossiê, PDF (metadados), reinício dos containers (sessão e preferências preservadas), CI verde no PR, e a **lista manual** do bloco 6.
- `docs/14` e `docs/progresso/r1.md` completos; `docs/10` revisado (risco do alias e da remoção futura dos aliases).
- Varredura de dados reais e `git diff --stat`.
- **Limite do bloco (HAIKU):** não renomeie nada de código nem decida exceções. O que sobrar e parecer inesperado vai **listado** no resumo, com arquivo e linha, para o responsável decidir.

---

## Critérios de aceite

1. **Fase 1:** nenhum texto visível, metadado de PDF, título de API, README, `CLAUDE.md` ou documento vivo cita "SIGA Vistas" como nome atual; o módulo do dossiê mantém o rótulo "Vistas".
2. **Fase 2:** compila; testes (back, front, e2e, ArchUnit) verdes; `docker compose` com os 3 arquivos sobe; **nenhum dado, sessão ou preferência do responsável se perde**; aliases de propriedades e de chaves funcionam e estão documentados.
3. Nada mudou de comportamento: rotas `/api`, SQL, schemas, regras e mensagens de negócio idênticos.
4. Nenhum comando git além de `git diff`; repositório e pasta **não** foram renomeados por você.
5. Testes verdes, ArchUnit verde, `docs/` e `docs/progresso/r1.md` atualizados.
