# L1: consolidar o login (sem usuários de demonstração; três usuários padrão)

> **Forma de trabalho:** 7 blocos, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**SONNET** e **HAIKU**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. **Não use Opus** (decisão do responsável até a apresentação). Manual (o responsável alterna as sessões, revisa e commita) ou pelo `/orquestrar` em branch `auto/*`.

## Contexto (o que existe hoje, já conferido no código)

- `RepositorioUsuariosEmMemoria` cria 3 usuários fixos (`operador.demo`, `gestor.demo`, `admin.demo`) e **todos usam a mesma senha**, vinda de `rastro.demo.senha` (`VISTAS_DEMO_SENHA`); a comparação é da senha em claro. Não há hash por usuário.
- O **front** traz a senha de demonstração **escrita no código** (`SENHA_DEMO` em `login.component.ts`) e botões "perfis de demonstração" que entram com um clique; o texto da tela diz "Nesta versão não há autenticação real". O README documenta o login e a senha de demonstração.
- Usuários incluídos ou alterados pela tela de Configurações somem ao reiniciar (D09, R50, R57). **A persistência em banco continua para a H1; não faça banco nesta tarefa.**
- Não há `spring-security` no `pom.xml` e não encontrei limite de tentativas de login.
- Perfis existentes: **Operador** (restrito ao interessado), **Gestor** (enum `OPERADOR_SIGILO`), **Analista** (S11, só `verCaixa`) e **Administrador** (inclui `verPainel`). O perfil Analista já existe; **não crie perfil novo**.

## Objetivo

1. **Remover** os usuários, a senha, os botões e os textos de demonstração, no back-end, no front, nos testes, na infraestrutura e na documentação.
2. **Senha por usuário, guardada só como hash**, comparada de forma segura, e **bloqueio depois de 3 tentativas erradas**.
3. **Três usuários padrão**, criados na subida a partir de variáveis de ambiente: **`operador`** (perfil Operador), **`analista`** (perfil Analista) e **`admin`** (perfil Administrador). **Nenhuma senha padrão no repositório.** O perfil Gestor continua existindo e é criado pelo Administrador na tela de Configurações (não é usuário padrão).
4. Administrador **define e redefine senha** de qualquer usuário; todo usuário **troca a própria senha**.

Fora desta tarefa (registre no Bloco 7): destrancar um login pelo Administrador antes dos 15 minutos; persistência em banco (H1), login institucional (Portal do SIGA/gov.br), troca obrigatória no primeiro acesso, recuperação de senha por e-mail, MFA e expiração de sessão por inatividade (se já existir, não altere).

## Regras

- **Somente o necessário para o login.** Não altere consulta, dossiê, tramitação, painel nem caixa, exceto onde um teste ou texto citar os usuários de demonstração.
- **Nenhum dado real.** CPF e CNPJ de teste são **inválidos de propósito** (ex.: `000.000.000-01`). Senhas de teste são valores óbvios de teste (`senha-de-teste-123`) e **só em código de teste**, nunca em arquivo de produção, exemplo, README ou compose de uso real.
- **Nunca abra, imprima ou copie `.env.local`.** O `.env.local.example` só ganha **nomes** de variáveis, com valor vazio.
- **Segurança:** a senha em claro **nunca** aparece em log, erro, auditoria, resposta, URL, DOM ou arquivo. O hash **nunca** sai em nenhuma resposta da API. Mensagem de erro de login é **a mesma** para usuário inexistente, usuário desativado e senha errada.
- **Nada pago** e, se possível, **nenhuma dependência nova**: prefira os recursos do JDK (por exemplo `PBKDF2WithHmacSHA256`, `SecureRandom`, `MessageDigest.isEqual`). Se achar que uma biblioteca é indispensável, **pare e descreva** (nome, licença, motivo) antes de adicionar.
- **Módulos e arquitetura:** respeite o `ArchitectureTest` e `docs/padroes/modulos-e-dependencias.md`. Se mudar esse arquivo, copie-o para `.claude/skills/vistas-banco/references/modulos-e-dependencias.md`.
- **Docker:** quando precisar, use `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml` e confirme que as variáveis externas estão vazias. Nunca `docker compose down -v`.
- **Testes por bloco:** só os ligados ao bloco. A suíte completa roda **uma vez no fim**, por quem comanda.
- **Git:** no modo manual, nenhum comando além de `git diff`. No modo automático, só o `commit-bloco`. **Nunca push.**

## Protocolo das sessões

1. **Identifique a sessão:** a primeira mensagem traz `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem a linha, pergunte e espere. `SESSÃO: OPUS` não existe nesta tarefa.
2. **Descubra onde parou:** leia `docs/progresso/l1.md` (se não existir, nenhum bloco foi feito). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não altere nada** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir:** repositório íntegro (compila, testes do bloco verdes), documentação da própria mudança atualizada, bloco marcado em `docs/progresso/l1.md` (crie no bloco 1 com a tabela dos 7 blocos), `git diff --stat` e a resposta:

```
BLOCO N CONCLUÍDO (sessão <SONNET|HAIKU>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto>
PRÓXIMO BLOCO: N+1 (<título>), sessão <SONNET|HAIKU>.
```

5. **Pare.** Manual, mesma sessão: espere "continuar". Outra sessão: a atual termina ali.

**Blocos HAIKU (mecânicos):** só executam o que já está decidido aqui; não inventam regra, número nem nome; o que não achar vira **NÃO CONFIRMADO**. Se o bloco exigir decisão de código, pare e responda `Este bloco precisa da sessão SONNET.`

## Blocos e sessões

| Bloco | Título | Sessão |
|---|---|---|
| 1 | Senha por usuário com hash e limite de tentativas | SONNET |
| 2 | Usuários padrão por variável de ambiente; remover os de demonstração no back-end | SONNET |
| 3 | Administrador define e redefine senha; usuário troca a própria | SONNET |
| 4 | Front: login sem demonstração e tela "Minha conta" | SONNET |
| 5 | Testes, infraestrutura e MCP sem usuários de demonstração | SONNET |
| 6 | Segurança do login: varreduras e matriz de acesso | SONNET |
| 7 | Documentação, roteiro e registro do que fica para a H1 | HAIKU |

---

## Bloco 1 (SONNET): senha por usuário, hash e limite de tentativas

- `Usuario` passa a ter a **senha em hash** (campo próprio, nunca exposto em DTO nem em `toString`). Uma porta/serviço `HashDeSenha` no módulo `acesso`: **PBKDF2WithHmacSHA256** do JDK, **sal aleatório por senha** (16 bytes ou mais), **600.000 iterações** (parâmetro com padrão e faixa, em Configurações como classe "reinício"), formato do hash autodescritivo (algoritmo, iterações, sal, hash em Base64) para permitir trocar o custo depois. Comparação em **tempo constante** (`MessageDigest.isEqual`).
- `autenticar(login, senha)`: usuário inexistente ou desativado executa **o mesmo trabalho** de hash (hash "falso" fixo) para o tempo de resposta não revelar se o login existe. Mensagem única: "Usuário ou senha inválidos."
- **Política de senha** (para definição e troca, não para entrar): mínimo **12 caracteres**, máximo 200, sem espaços só, não igual ao login. Mensagens claras, sem revelar regras internas além disso.
- **Limite de tentativas: 3.** Depois de **3 senhas erradas seguidas** para o mesmo par **login + origem** (IP já tratado pelo projeto; **não confie em `X-Forwarded-For` sem a configuração de proxy que o projeto já tiver**), a entrada fica **bloqueada por 15 minutos** e responde **429** com mensagem genérica ("Muitas tentativas. Tente de novo em N minutos."), mais o tempo restante em segundos, **sem dizer se o login existe**. Durante o bloqueio, **até a senha certa é recusada** (429). Acerto antes do bloqueio zera o contador desse par. O contador é por **par login + origem** (e não só por login) para que quem digita errado de outro lugar **não consiga trancar** o administrador. Some a isso um limite **por origem**, de **10 falhas em 15 minutos** somando todos os logins (mesmo 429 e mesma mensagem). Contagem em memória, relógio injetável (`Clock`), limpeza de entradas velhas. Parâmetros (`tentativas` = 3, `janela` = 15 min, `bloqueio` = 15 min, `limitePorOrigem` = 10) em Configurações, classe "reinício", com faixas (tentativas 3 a 10).
- Auditoria: falha de login e bloqueio são eventos **sem a senha** e sem dizer se o login existe (reuse o evento de acesso; se for criar tipo novo, atualize `docs/11`).

**Testes:** hash (formato, sal diferente a cada vez, verificação, custo mudado ainda verifica), comparação, mesmo trabalho para usuário inexistente (sem medir tempo: prove que o mesmo caminho é percorrido), política de senha, **3 erros bloqueiam a 4ª tentativa por 15 minutos (inclusive com a senha certa)**, liberação depois de 15 minutos com relógio de teste, acerto antes do bloqueio zera o contador, outra origem não é bloqueada pelo mesmo login, limite por origem, auditoria sem senha. **Ponto de parada.** Commit sugerido: `feat(acesso): senha por usuário com hash e limite de tentativas`

## Bloco 2 (SONNET): usuários padrão por variável de ambiente

- **Apague** os três usuários fixos de demonstração, `rastro.demo.senha`, `VISTAS_DEMO_SENHA` e o alias antigo dela (confira `AliasDasPropriedadesAntigas`, `application.yml`, `docs/12`). Nada de senha-padrão no código.
- Na subida, um **inicializador** cria os usuários padrão **se a variável da senha estiver preenchida e válida pela política** (mínimo 12 caracteres):
  - `operador` (perfil **Operador**, nome "Operador"), senha `RASTRO_SENHA_OPERADOR`, CPF/CNPJ do interessado em `RASTRO_OPERADOR_CPF_CNPJ`;
  - `analista` (perfil **Analista**, nome "Analista"), senha `RASTRO_SENHA_ANALISTA`, CPF em `RASTRO_ANALISTA_CPF` (é o vínculo com o `ASSIGNEE_` do Camunda; **sem CPF, a caixa fica vazia com a mensagem já existente**);
  - `admin` (perfil **Administrador**, nome "Administrador"), senha `RASTRO_SENHA_ADMIN`.
- **Cada usuário padrão tem a sua própria senha: duas senhas iguais nunca valem.** Se `RASTRO_SENHA_OPERADOR`, `RASTRO_SENHA_ANALISTA` e `RASTRO_SENHA_ADMIN` tiverem **valores repetidos** (comparação exata e também sem diferença de maiúscula/minúscula), **nenhum dos usuários envolvidos é criado** e o aviso diz só os **nomes das variáveis repetidas**. A senha também não pode ser igual ao login do próprio usuário nem conter o login. A mesma regra vale quando o Administrador define ou redefine uma senha: recusar senha **igual à de outro usuário ativo** é desnecessário (o hash tem sal), mas recusar **senha igual à atual** na troca é obrigatório.
- Variável **ausente ou fraca**: o usuário **não é criado** e a API registra **um aviso por usuário** (só o nome da variável, **nunca** o valor). Se **nenhum administrador** puder existir (sem `RASTRO_SENHA_ADMIN` válida), o aviso é destacado e `GET /api/estado` informa "sem administrador cadastrado" (sem expor mais nada).
- O Administrador **não pode desativar nem rebaixar o último administrador ativo** (se a regra ainda não existir, crie e teste).
- Usuário padrão pode ser alterado e redefinido como qualquer outro; **ao reiniciar volta ao que as variáveis dizem** (persistência é da H1; registre isso).
- Variáveis entram no `.env.local.example` (**só nomes, vazias**), em `docs/12` (tabela de variáveis, serviço `api`, sem padrão) e no `docs/03` se lá houver lista.

**Testes:** com e sem cada variável, senha fraca, **duas ou três senhas iguais (nenhum dos repetidos é criado)**, senha igual ao login, perfil de cada usuário, CPF do analista, último administrador, nenhum valor de senha em log (capture o log do teste e varra). **Ponto de parada.** Commit sugerido: `feat(acesso): usuários padrão por variável de ambiente, sem demonstração`

## Bloco 3 (SONNET): definir, redefinir e trocar senha

- **Administrador** (tela de usuários e API já existentes): ao **incluir** usuário, informa a senha inicial (mesma política); ao **editar**, pode **redefinir** a senha. A resposta nunca devolve senha nem hash. Auditoria do ato (quem, quem foi afetado, quando), **sem a senha**.
- **Qualquer usuário autenticado:** `POST /api/auth/senha` com senha atual, nova e confirmação; confere a senha atual (com o mesmo limite de tentativas), aplica a política, grava o hash novo, **encerra as outras sessões** desse usuário e mantém a atual. Erros sem vazar detalhes.
- Redefinir ou trocar senha de um usuário **encerra todas as sessões abertas dele** (no caso da redefinição pelo Administrador).
- Contratos no OpenAPI, `docs/07` e `docs/12` (matriz: todos os perfis trocam a **própria** senha; só o Administrador redefine a de outros). 403/401 corretos e **antes** de qualquer leitura.

**Testes:** back (política, senha atual errada, sessões encerradas, auditoria sem senha, Operador/Analista/Gestor não redefinem senha alheia, último administrador). **Ponto de parada.** Commit sugerido: `feat(acesso): definição, redefinição e troca de senha`

## Bloco 4 (SONNET): front

- **Login:** remova `SENHA_DEMO`, `USUARIOS_DEMO`, `entrarComoDemo`, a seção "perfis de demonstração" e o texto "Nesta versão não há autenticação real". Fica o formulário usuário e senha, com mensagem para credencial inválida e para **bloqueio temporário** (429, com o tempo restante). Sem dica de usuários existentes e sem preencher campo automaticamente.
- **Minha conta** (item de menu, todos os perfis): troca da própria senha (atual, nova, confirmação), com a política mostrada, mensagens claras, foco e leitor de tela.
- **Configurações › Usuários:** campo de senha inicial ao incluir e ação "Redefinir senha" ao editar (só Administrador), sem exibir senha salva.
- **Apague a exposição por completo:** nada de `vistas-demo`, de usuário `*.demo` nem de "perfis de demonstração" pode sobrar no código-fonte do front, no bundle gerado (`front/dist`, se existir), na API simulada, nos dados de `front/e2e/`, em `docs/prototipo/` e `docs/rastro/` (se houver protótipo navegável com esses logins), nos prints descritos em documentos e em qualquer texto de ajuda. Faça a busca no repositório inteiro (`vistas-demo`, `.demo`, `perfis de demonstração`, `Demo`) e resolva cada ocorrência, **exceto** `RASTRO_MODO=demonstracao` (modo declarado em `/api/estado`, que não é credencial).
- **Garantia de build:** nenhum texto de senha de demonstração no bundle. Acrescente uma verificação (teste ou script do projeto, no padrão de `contraste`/`cores-fixas`) que **falha** se encontrar `vistas-demo`, `.demo` ou `perfis de demonstração` em `front/src`.
- Atualize a imagem/protótipo de referência do login em `docs/design/sigrastro/` **se existir** (descreva, não gere imagem nova).

**Testes:** componentes do login e da Minha conta, e2e (Playwright, API simulada) do login com credencial errada, bloqueio e troca de senha; a API simulada (`front/e2e/api-simulada.ts`) passa a usar os logins `operador`, `analista` e `admin` (mais um `gestor` **só na simulação**). **Ponto de parada.** Commit sugerido: `feat(front): login sem demonstração e tela Minha conta`

## Bloco 5 (SONNET): testes, infraestrutura e MCP

- Substitua `operador.demo`, `gestor.demo`, `admin.demo` e a senha de demonstração em **todos** os testes do back-end (há dezenas, em `acesso`, `configuracao`, `auditoria`, `consulta`, `dossie`, `tramitacao`, `painel`, `caixa`, `testsupport` e `ContratoHttpTest`). Crie **uma configuração de teste única** (em `testsupport`) que define os três usuários padrão com senhas de teste via as mesmas propriedades da produção e **cria um Gestor de teste** no setup; os testes reusam essa configuração em vez de repetir logins e senhas.
- `compose.verificacao.yaml`, `compose.local.yaml`, `compose.yaml`, `.env.local.example`, servidor MCP (`RASTRO_MCP_USUARIO` e `RASTRO_MCP_SENHA`: passa a usar um usuário **somente leitura de verificação** definido por variável, nunca lido do `.env.local`; descreva a decisão), `tools/`, scripts e hooks que citem os usuários antigos.
- **Não** mude o comportamento dos testes: só nomes, senhas e a configuração compartilhada. Rode a suíte do back-end que for afetada por pacote (acesso, configuracao, auditoria, ContratoHttpTest, MatrizDeAcessoTest, PoliticaDeAcessoTest) e o `ArchitectureTest`.

**Ponto de parada.** Commit sugerido: `test(acesso): usuários padrão e senha de teste no lugar dos de demonstração`

## Bloco 6 (SONNET): segurança do login

- **Varredura de vazamento** (back e front): a senha de teste e o hash **nunca** aparecem em respostas de nenhum endpoint (incluindo a lista de usuários e `/api/auth/me`), mensagens de erro, log capturado do teste, auditoria e DOM do front nos fluxos de login, troca e redefinição.
- **Enumeração:** mesmo corpo e mesmo status para usuário inexistente, desativado e senha errada; no bloqueio (429), mesma resposta para login existente e inexistente.
- **Matriz de acesso:** os três usuários padrão mais o Gestor de teste, em todos os endpoints de `/api` (a `MatrizDeAcessoTest` atualizada), incluindo os novos (`/api/auth/senha`, redefinição, usuários).
- **Sessão:** token anterior deixa de valer depois de redefinição e de troca; logout continua funcionando; sessão de usuário desativado deixa de valer.
- **Bundle do front sem senha de demonstração** (a verificação do Bloco 4 roda no teste e prova).
- Corrija o que a varredura achar **neste bloco**, sem reduzir cobertura.

**Ponto de parada.** Commit sugerido: `test(acesso): varredura de vazamento, enumeração e matriz do login`

## Bloco 7 (HAIKU): documentação, roteiro e H1

- **README:** apague as menções a `operador.demo`, `gestor.demo`, `admin.demo`, à senha `vistas-demo` e a `VISTAS_DEMO_SENHA`. Os Tutoriais 1 e o de execução local passam a dizer: defina `RASTRO_SENHA_OPERADOR`, `RASTRO_SENHA_ANALISTA` e `RASTRO_SENHA_ADMIN` no `.env.local` (mínimo 12 caracteres, **sem valor de exemplo**) e entre com `operador`, `analista` ou `admin`. Explique que o Gestor é criado pelo `admin` em Configurações.
- **`docs/09`:** decisão (sem usuários de demonstração; três usuários padrão por variável; PBKDF2 do JDK; limite de tentativas; administrador define e redefine senha; troca da própria). **`docs/10`:** riscos novos (usuários e senhas alteradas ainda em memória, **voltam ao que as variáveis dizem ao reiniciar**; senha dos padrões vem do ambiente; sem troca obrigatória no primeiro acesso; limite de tentativas em memória zera ao reiniciar). **`docs/12`:** variáveis e matriz. **`docs/03`**, **`docs/08`** (telas) e **`docs/14`** (L1 concluída; H1 mantém persistência, login institucional, troca obrigatória e recuperação de senha).
- **`docs/roteiros/l1-homologacao.md`:** cenários (entrar com cada usuário padrão, senha errada, bloqueio, trocar a própria senha, Administrador redefine a de outro e a sessão antiga cai, criar um Gestor, ver o painel, sem variável não cria usuário) com a coluna "Resultado" `Não executado`.
- `docs/progresso/l1.md` completo e `docs/prompts/INDICE.md` (linha da L1).
- Rode a **suíte completa uma vez** (back-end, front, e2e e hooks), anote os números reais em `docs/progresso/l1.md` e rode a varredura de dados pessoais e de segredos do projeto (`docs/13`). Confirme que **nenhum** arquivo do repositório contém `vistas-demo`, `operador.demo`, `gestor.demo` ou `admin.demo` (a busca inclui `docs/`, `front/`, `api/`, `tools/`, `.github/` e arquivos de exemplo). Registre em `docs/10` que a senha antiga **continua no histórico do Git** (README e código do front de antes desta tarefa) e que isso é aceito porque o usuário e a senha **deixaram de existir**; **nunca reutilize `vistas-demo`** em ambiente algum, e quem tiver `VISTAS_DEMO_SENHA` em `.env.local` deve apagar a linha.

**Ponto de parada.** Commit sugerido: `docs(l1): documentação, roteiro e registro do que fica para a H1`
