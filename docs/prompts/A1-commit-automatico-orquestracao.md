# A1: commit automático por bloco e orquestração com quatro agentes

> **Forma de trabalho:** a sprint é dividida em **6 blocos**, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

> **Esta sprint ainda é feita no modo manual** (o responsável revisa e commita cada bloco). Ela **constrói** o modo automático; só depois de pronta é que os próximos prompts (como o `R2-refatorar-front-sigrastro.md`) rodam sem parar entre os blocos.

## Contexto e decisão do responsável

Até aqui, cada bloco de cada prompt termina numa parada para o responsável revisar e commitar. Para prompts longos (a R2 tem 18 blocos), ele decidiu **automatizar o commit** e deixar **uma sessão só orquestrando** os modelos:

- **Quatro agentes:** `executor-haiku`, `executor-sonnet`, `executor-opus` (cada um executa **um bloco** no seu modelo e **não commita**) e `commitador` (só faz o `add` e o `commit`).
- **Uma sessão orquestradora** (a sessão principal, em Sonnet) lê o prompt, chama o executor certo para cada bloco, chama o `commitador` quando o bloco fecha com testes verdes e passa ao próximo. Para na primeira falha.
- **Os commits saem no nome do responsável** (a identidade do Git já configurada na máquina dele), **sem** a linha `Co-Authored-By: Claude`, **sem** `Claude-Session` e **sem** "Generated with Claude Code". Isso é decisão dele, num projeto pessoal.
- **Nunca há `push`.** O responsável revisa a branch e faz o `push` e o PR à mão.

Isto é uma **exceção deliberada e estreita** à regra permanente "só o responsável commita": o único caminho de commit passa por **um script do projeto**, que é a trava. Registre em `docs/09` e no `CLAUDE.md`.

## Desenho do fluxo

```
/orquestrar docs/prompts/<prompt>.md
  sessão principal (Sonnet)
    verifica: branch auto/*, árvore limpa, identidade do Git, guardas ativas
    lê a tabela "Blocos e sessões" e docs/progresso/<id>.md → próximo bloco
    ┌─ para cada bloco ─────────────────────────────────────────────┐
    │ executor-<modelo>  executa o bloco, roda os testes,           │
    │                    grava .claude/tmp/commit-msg.txt e          │
    │                    .claude/tmp/bloco-N.json, devolve o relatório│
    │ (falhou ou precisa de decisão? PARA e avisa)                   │
    │ commitador         node .claude/scripts/commit-bloco.mjs ...   │
    │                    (confere tudo e commita; sem push)          │
    └───────────────────────────────────────────────────────────────┘
    ao final: tabela bloco → hash do commit
```

## Regras desta sprint

- **Nenhum dado real** em teste, exemplo ou log (CPF, CNPJ, nomes, números de processo ou de DAR). O `.env.local` nunca é aberto nem impresso.
- **Nada pago.** Só Node (já presente) e o que o projeto já usa; **nenhuma dependência nova**.
- **Os testes do script nunca tocam o repositório real:** criam repositórios Git **temporários** em `os.tmpdir()`, com identidade de teste, e os apagam no fim. O `git` só é chamado por **código de teste em Node** (`node --test`), nunca por comando de shell seu.
- **Nesta sprint, você (sessão do Claude Code) não executa commit, push, branch, stash nem comando git além de `git diff`.** O script que você escreve executa `git commit`; você não o roda contra o repositório real.
- As guardas do projeto (`.claude/`) podem **bloquear a sua edição** de arquivos em `.claude/`. Se bloquearem, **pare e peça ao responsável a liberação temporária**; **não contorne** nem desative guarda.
- Nunca altere `user.name`, `user.email` nem qualquer configuração do Git.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/a1.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/a1.md` (crie no bloco 1, com a tabela dos 6 blocos); a marcação entra no mesmo commit;
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

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Script `commit-bloco` (a trava do commit) | **OPUS** | Segurança: é o único caminho de commit |
| 2 | Guardas: exceção estreita, proteção do `.claude/` e sem coautoria | **OPUS** | Mexe nas guardas de segurança (S6, S6.1) |
| 3 | Os quatro agentes | **SONNET** | Definições de agente com regras precisas |
| 4 | Comando `/orquestrar` | **SONNET** | Laço, retomada e parada em falha |
| 5 | Documentação | **SONNET** | Texto técnico, decisão e roteiro |
| 6 | Prompt de exemplo e teste de ponta a ponta (preparação) | **HAIKU** | Arquivos pequenos e mecânicos; quem roda é o responsável |

---

## Bloco 1 (OPUS): script `commit-bloco`

**Commit sugerido:** `feat(claude-code): script commit-bloco, único caminho de commit automático`

Arquivo: `.claude/scripts/commit-bloco.mjs` (Node, sem dependência). Chamada **exata**:

```
node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt
```

O script usa `execFile` com **vetor de argumentos** (nunca shell, nunca interpolar texto em comando) e **aborta com código diferente de zero** e mensagem clara na primeira verificação que falhar, **sem alterar nada** (nem `add`). Verificações, nesta ordem:

1. **Argumentos:** exatamente `--bloco` (inteiro de 1 a 3 dígitos) e `--mensagem` com o caminho `.claude/tmp/commit-msg.txt`; **qualquer outro argumento ou caminho é recusado**.
2. **Repositório e branch:** está na raiz do repositório; a branch atual **começa com `auto/`** (a branch é criada pelo responsável antes de tudo); **recusa** `main`, `master`, qualquer outra e HEAD solto.
3. **Identidade:** `git config user.name` e `user.email` **preenchidos**; senão aborta e diz para o responsável configurar. **Nunca** define identidade, nunca usa `--author`, `-c user.*` nem variáveis `GIT_AUTHOR_*`/`GIT_COMMITTER_*`.
4. **Marca de testes:** existe `.claude/tmp/bloco-<N>.json` com `"bloco": N` e `"testes"` igual a **`"direcionados"`** (só os testes ligados ao bloco passaram), **`"completa"`** (a suíte completa passou) ou **`"nao-se-aplica"`** (bloco só de documentação). Qualquer outro valor (inclusive `"ok"`), arquivo ausente ou de outro bloco → recusa.
5. **Mensagem:** lê `.claude/tmp/commit-msg.txt`, **remove** linhas de coautoria e de ferramenta (`Co-Authored-By:`, `Claude-Session:`, `Signed-off-by:` com Claude, "Generated with [Claude Code]", URLs `claude.ai/code`, `noreply@anthropic.com`; sem diferenciar caixa) e **valida** o que sobrou: primeira linha no formato Conventional Commits (`feat|fix|refactor|test|docs|perf|chore|ci`, escopo opcional em minúsculas, `: ` e resumo), **até 100 caracteres**, sem começar com espaço, corpo opcional. Mensagem vazia depois da limpeza → recusa.
6. **Arquivos proibidos:** lê `git status --porcelain`; se aparecer **não rastreado ou alterado** algum caminho de segredo, de dado ou de artefato (`.env*`, `mapa-local.csv`, `amostras-locais/`, `*.pem`, `*.key`, `node_modules/`, `dist/`, `test-results/`, `playwright-report/`, `.claude/tmp/`), **aborta** listando os caminhos. Árvore sem nenhuma mudança → recusa ("nada para commitar").
7. **Varredura de dados:** passa o **diff** (`git diff` do que será incluído, sem `add` prévio: use `git diff HEAD` e os arquivos novos) pelo **mesmo detector** que a guarda `varrer-dados` já usa (reaproveite a função; **não** duplique as expressões). Segredo ou dado real detectado → aborta.
8. **Só então:** `git add -A` e `git commit -F <mensagem limpa>` (grave a mensagem limpa em arquivo temporário do sistema, nunca por `-m`). **Sem** `--no-verify`, **sem** `--amend`, **sem** push. Imprime o hash curto e `git show --stat --oneline HEAD`.
9. **Depois do commit:** apaga `.claude/tmp/commit-msg.txt` e `.claude/tmp/bloco-<N>.json`.

Também:
- `.claude/tmp/` entra no `.gitignore` (confira se já existe).
- **Testes** `.claude/scripts/commit-bloco.test.mjs` (`node --test`), cada caso em **repositório temporário**: commit feliz com a identidade de teste; **autor do commit = identidade configurada**; mensagem com `Co-Authored-By` e `Claude-Session` sai **sem** elas; branch `main` recusada; branch sem `auto/` recusada; identidade vazia recusada; marca de testes ausente, de outro bloco, `falharam` ou `ok` recusada; `direcionados`, `completa` e `nao-se-aplica` aceitas; mensagem fora do padrão recusada; `.env.local` e `node_modules/` na árvore recusados; segredo falso (valor claramente inventado, ex.: `chave-falsa-0000`) no diff recusado; árvore limpa recusada; argumento extra recusado; **nenhum caso chama `git push`**, e um teste confirma que o código do script não contém `push`.
- **CI:** o job `mudancas` do `ci.yml` (que já roda os testes das guardas) passa a rodar também `.claude/scripts/*.test.mjs`, sem instalar nada.
- Docs: `docs/claude-code/hooks.md` (seção do script e das 9 verificações), `docs/progresso/a1.md` (crie com a tabela dos 6 blocos).

## Bloco 2 (OPUS): guardas

**Commit sugerido:** `feat(claude-code): exceção estreita para o commit-bloco e proteção do .claude/`

- **`guarda-bash`:** hoje bloqueia qualquer `git` que escreva. Acrescente **uma única exceção**: o comando completo (depois de `trim`) deve casar, do início ao fim, com
  `node .claude/scripts/commit-bloco.mjs --bloco <1 a 3 dígitos> --mensagem .claude/tmp/commit-msg.txt`
  **sem nenhum outro caractere**: sem `;`, `&`, `|`, `>`, `<`, `` ` ``, `$`, `(`, `)`, quebra de linha, aspas ou argumento extra. **Todo o resto continua como está:** `git commit` direto, `git add`, `git push` (sempre bloqueado, inclusive dentro da exceção, que nem chega a ele), `git reset`, `--force`, `-c`, redirecionamentos e variações. **Na dúvida, bloqueia.**
- **`guarda-arquivos`:** os agentes **não podem escrever** (`Write`, `Edit`, `sed -i`, redirecionamento) em `.claude/scripts/`, `.claude/hooks/`, `.claude/agents/`, `.claude/commands/` nem em `.claude/settings.json`, **exceto** quando a sessão é a de **construção** desta sprint e o responsável liberou (se a guarda atual já protege `.claude/`, só **confirme com teste** e registre; se a liberação para construir for necessária, **pare e peça**). Gravar em `.claude/tmp/` é permitido (é onde os executores deixam a mensagem e a marca).
- **Permissão sem pergunta:** o `.claude/settings.json` do projeto só tem `deny` (não há `allow`). Acrescente em `permissions.allow` **somente** a regra do comando exato (`Bash(node .claude/scripts/commit-bloco.mjs --bloco * --mensagem .claude/tmp/commit-msg.txt)`, na sintaxe da versão instalada; confirme na documentação), para o `commitador` não parar pedindo aprovação a cada bloco. O `deny` de `git commit`, `git push` e `gh` **continua** (o script chama o Git por dentro, não por comando de shell). Registre em `docs/claude-code/hooks.md` e teste que as regras de `deny` seguem intactas.
- **Sem coautoria:** em `.claude/settings.json`, configure o atributo de commit do Claude Code como **vazio** (chave `attribution.commit` como string vazia; em versões antigas, `includeCoAuthoredBy: false`; **confirme na documentação da versão instalada** e use a que existir). Teste que, ainda assim, o script limpa o que aparecer (bloco 1).
- **Testes** (`.claude/hooks/*.test.mjs`): o comando exato é permitido; **pelo menos 40 variações** são bloqueadas (argumento extra, caminho diferente, `./` e `..`, `--mensagem /etc/passwd`, outro script, encadeamentos com `&&`, `;`, `|`, `$(...)`, crase, quebra de linha, `node -e`, `git commit -m x`, `git push`, `git add -A`, `git -c user.name=x commit`...); o corpus de vazamento da S6.1 (120 bloqueados, 40 permitidos) **continua verde**; escrita nos caminhos protegidos é bloqueada para qualquer ferramenta.
- Docs: `docs/claude-code/hooks.md` (exceção, motivo e limite: **qualquer chamada fora do comando exato continua bloqueada**), `docs/10` (risco: o script é a única trava; se for alterado, a trava some; ele fica protegido), `docs/09` (decisão: exceção estreita à regra "só o responsável commita"), `docs/progresso/a1.md`.

## Bloco 3 (SONNET): os quatro agentes

**Commit sugerido:** `feat(claude-code): agentes executor-haiku, executor-sonnet, executor-opus e commitador`

Em `.claude/agents/` (o projeto já tem `revisor-seguranca`, `executor-de-testes` e `atualizador-de-docs`: **não os altere**).

- **`executor-haiku.md`, `executor-sonnet.md`, `executor-opus.md`:** cabeçalho com `name`, `description` ("Executa UM bloco de um prompt de desenvolvimento no modelo X. Não commita."), `model: haiku` | `sonnet` | `opus` e `tools: Read, Write, Edit, Glob, Grep, Bash`. **O corpo é idêntico nos três**; só o nome e o modelo mudam (gere os três do mesmo texto). Corpo:
  1. Entrada: caminho do prompt, **número do bloco**, e a linha `SESSÃO: <HAIKU|SONNET|OPUS>` com `MODO: AUTOMÁTICO`.
  2. Leia o prompt inteiro (contexto, decisões, regras) e **somente o bloco pedido**; leia `docs/progresso/<id>.md`. Se o bloco **não for do seu modelo** ou já estiver concluído, devolva `PARADA: bloco N não é meu` e **não faça nada**.
  3. Execute o bloco **exatamente como o prompt manda**, com as regras do prompt. **Nunca** execute comando git que escreva (as guardas bloqueiam; não tente contornar) e **nunca** edite `.claude/` (exceto gravar em `.claude/tmp/`).
  4. **Testes (regra do modo automático, vale mesmo que o bloco diga outra coisa):** rode **só os testes ligados ao bloco** (os do módulo, da classe ou do arquivo que você alterou; no front, só os specs afetados), **sem** a suíte completa. A **suíte completa do back-end (leva ~9 min) só roda se o próprio bloco pedir expressamente**, e então **uma única vez**: **nunca repita a suíte completa** no mesmo bloco; se ela falhar, corrija e rode **apenas os testes que falharam**. Depois a verificação que o bloco pede. **Testes falharam, bloco ambíguo, decisão necessária, guarda bloqueou algo legítimo ou o bloco manda parar e avisar?** Devolva `PARADA: <motivo, arquivo e linha>` e **não grave** a marca de testes.
  5. Terminou com tudo verde: atualize o `docs/progresso/<id>.md` (como o protocolo do prompt manda), grave **`.claude/tmp/commit-msg.txt`** (só a mensagem Conventional Commits, no formato do bloco, **sem nenhuma linha de coautoria ou de ferramenta**) e **`.claude/tmp/bloco-<N>.json`** (`{"bloco": N, "testes": "direcionados", "resumo": "<uma frase>"}`; `"completa"` só se você rodou a suíte completa e ela passou; `"nao-se-aplica"` só para bloco de documentação).
  6. Devolva o relatório no formato do protocolo do prompt (`BLOCO N CONCLUÍDO ...`, com `git diff --stat`). **Não** espere "continuar": devolva o relatório e termine.
- **`commitador.md`:** `model: haiku`, `tools: Read, Bash`. Corpo: receba o **número do bloco**; rode **somente** `node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt`; devolva a saída (hash e `--stat`); se sair com erro, devolva `FALHA DO COMMIT:` e o texto do erro **sem alterar**, **sem tentar variações** e **sem** usar `git` diretamente; **não edite arquivo, não adicione linha à mensagem, não faça push**.
- Teste manual descrito em `docs/claude-code/agentes.md` (o responsável roda no bloco 6); aqui, um teste automatizado simples (`node --test`) que lê os quatro arquivos e confere: nomes, `model`, `tools`, **corpo dos três executores idêntico**, ausência de qualquer ferramenta de escrita no `commitador`, e que nenhum dos quatro cita `push`.
- Docs: `docs/claude-code/agentes.md`, `docs/progresso/a1.md`.

## Bloco 4 (SONNET): comando `/orquestrar`

**Commit sugerido:** `feat(claude-code): comando /orquestrar para rodar um prompt bloco a bloco`

Em `.claude/commands/orquestrar.md`, uso: `/orquestrar <caminho-do-prompt> [bloco-inicial] [bloco-final]`.

1. **Pré-checagens (pare e explique se falhar):** a branch começa com `auto/`; a árvore de trabalho está **limpa** (só `.claude/tmp/` ignorado); `user.name` e `user.email` estão configurados; as guardas estão ativas (`.claude/settings.json` com os hooks); o arquivo do prompt existe e tem a tabela **"Blocos e sessões"**. **Se a branch for `main` ou outra, não faça nada** e diga como criá-la (`git switch -c auto/<nome>`, comando do responsável).
2. Leia a tabela (colunas Bloco, Título, Sessão) e o `docs/progresso/<id>.md` para achar o **primeiro bloco não concluído** (ou o `bloco-inicial`).
3. **Para cada bloco, em ordem:** chame o agente `executor-<modelo da tabela>` com o caminho do prompt, o número do bloco, `SESSÃO: <X>` e `MODO: AUTOMÁTICO`. Se devolver **`PARADA`**, ou se faltar `.claude/tmp/bloco-N.json`, **pare tudo** e mostre ao responsável o motivo e o bloco. Se concluiu, chame o `commitador` com o número do bloco. Se devolver **`FALHA DO COMMIT`**, **pare** e mostre o erro. Se deu certo, registre `bloco N → hash` e siga.
   **Suíte completa só no fim:** depois do **último** bloco commitado, rode a suíte completa **uma única vez** chamando o agente `executor-de-testes` (já existente; ele só executa e relata), **a menos que** a marca do último bloco já tenha sido `"completa"`. Se passar, registre. Se falhar, **não tente corrigir nem repetir**: pare e mostre quais testes falharam, junto com `git log --oneline` e os arquivos de cada bloco, para o responsável ver de qual bloco veio a falha. Os commits já feitos ficam como estão.
4. **Você (a sessão principal) não edita arquivo, não roda git e não commita:** só orquestra. Guarde **apenas o resumo** de cada bloco (uma linha), não o conteúdo.
5. **Ao final** (ou ao parar): tabela `bloco | título | modelo | hash | situação`, onde parou e **o que fazer a seguir** (`git log --oneline`, revisar a branch, `git push` e abrir o PR, **tudo pelo responsável**; você **nunca** faz push).
6. **Retomada:** rodar de novo o mesmo comando continua do primeiro bloco não concluído; nada se repete.
7. **Limites:** a suíte completa roda **no máximo uma vez** por orquestração; no máximo **uma tentativa** por bloco; **nunca** repetir um bloco que falhou sem o responsável pedir; nunca pular bloco; nunca alterar o prompt.

- Docs: `docs/claude-code/README.md` (como usar, com o exemplo da R2), `docs/progresso/a1.md`.

## Bloco 5 (SONNET): documentação

**Commit sugerido:** `docs(claude-code): commit automático e orquestração de blocos`

- Crie `docs/claude-code/orquestracao.md`: o fluxo (diagrama do início desta sprint), como preparar (criar a branch `auto/<nome>`, abrir o Claude Code **em Sonnet**), como rodar, o que cada agente faz e **não** faz, **o que o commit contém** (autor = identidade do Git do responsável, sem coautoria), o que fazer quando para (`PARADA`, `FALHA DO COMMIT`), como retomar, como desfazer um bloco (revisar com `git log` e usar o histórico; **só o responsável** faz isso) e a **lista de limites** (sem push, só branch `auto/*`, uma tentativa por bloco).
- `docs/10`: riscos e limites (o script é a única trava e fica protegido; o commit automático **tira a revisão bloco a bloco**: ela passa a ser no PR; a mensagem não identifica ferramenta por decisão do responsável; **cada executor relê o prompt inteiro**, o que consome tokens: prompts longos custam mais no modo automático).
- `CLAUDE.md`: regra curta: "Commit só pelo `commitador`, via `commit-bloco`, em branch `auto/*`; nunca push; nenhuma coautoria de ferramenta."
- `docs/prompts/INDICE.md`: nota na seção de regras permanentes: "a regra 'só o responsável commita' tem **uma exceção**: o modo automático (A1), em branch `auto/*`, sem push".
- `docs/14` e `docs/progresso/a1.md`.

## Bloco 6 (HAIKU): prompt de exemplo e teste ponta a ponta (preparação)

**Commit sugerido:** `docs(claude-code): prompt de exemplo e roteiro de teste do modo automático`

- Crie `docs/prompts/exemplo-orquestracao.md` com **3 blocos triviais e inofensivos**, no mesmo formato dos prompts reais (tabela "Blocos e sessões", protocolo, `docs/progresso/ex.md`): bloco 1 **HAIKU** cria `tools/exemplo/ola-haiku.txt` com uma frase; bloco 2 **SONNET** cria `tools/exemplo/ola-sonnet.txt`; bloco 3 **OPUS** cria `tools/exemplo/resumo.md` listando os dois arquivos. Cada um com mensagem de commit sugerida (`chore(exemplo): ...`) e testes "não se aplica".
- Crie `docs/roteiros/a1-homologacao.md` para o **responsável** rodar:
  1. `git switch -c auto/teste-orquestracao` (comando dele).
  2. Abrir o Claude Code em **Sonnet** e rodar `/orquestrar docs/prompts/exemplo-orquestracao.md`.
  3. Conferir: **3 commits** na branch, cada um com os arquivos do seu bloco; `git log --format='%an <%ae>%n%B'` mostra **a identidade dele** e **nenhuma** linha de coautoria nem menção ao Claude; os modelos usados foram Haiku, Sonnet e Opus (o resumo da orquestração informa); nenhum push foi feito.
  4. **Testes de recusa:** (a) tentar `/orquestrar` estando em `main` deve recusar; (b) com um arquivo `.env.local` falso na árvore, o commit deve ser recusado; (c) forçar uma falha de teste num bloco deve **parar** a orquestração sem commitar.
  5. Limpeza: `git switch main` e apagar a branch de teste (comandos dele).
- **Limite do bloco (HAIKU):** só criar os dois arquivos acima; **não execute** o fluxo, **não** rode git nem crie branch.
- Docs: `docs/progresso/a1.md` fechado.

---

## Critérios de aceite

1. `commit-bloco.mjs` cumpre as 9 verificações, com testes em repositórios temporários; **nenhum teste** toca o repositório real; o código **não contém `push`**.
2. A guarda permite **somente** o comando exato do script; as 40 variações e o corpus da S6.1 continuam bloqueados; `.claude/scripts|hooks|agents|commands` e `settings.json` protegidos contra os agentes.
3. Quatro agentes definidos, **três executores com corpo idêntico**, `commitador` sem ferramenta de escrita e sem `push`.
4. Cada bloco roda só testes direcionados; a suíte completa roda **uma vez**, no fim da orquestração (ou no bloco que a pedir); `commit-bloco` aceita as marcas `direcionados`, `completa` e `nao-se-aplica`. `/orquestrar` roda bloco a bloco, para na primeira falha, retoma de onde parou, só em branch `auto/*`, e **nunca faz push**.
5. Os commits saem **na identidade do Git do responsável**, **sem** coautoria, `Claude-Session` ou "Generated with".
6. Documentação, roteiro de homologação e prompt de exemplo entregues; CI roda os testes dos scripts.
7. **Nenhum comando git além de `git diff`** foi executado por você nesta sprint; nenhum dado real em teste, exemplo ou log.
