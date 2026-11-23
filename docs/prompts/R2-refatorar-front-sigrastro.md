# R2: refatoração do front para o SIGRASTRO

> **Forma de trabalho:** a sprint é dividida em **18 blocos**, cada um terminando em um **ponto de commit**. Sessões do Claude Code (**OPUS**, **SONNET** e **HAIKU**, esta só nos blocos pequenos e mecânicos) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

O front atual nasceu de um *starter* de terceiros (verde e limão, menu lateral, brasão do Estado como marca d'água). O responsável decidiu **trocar todo o padrão visual**, e a nova identidade foi aprovada em um protótipo funcional: o nome do produto passa a ser **SIGRASTRO** (**nome definitivo**), com marca própria (escudo árvore-vida), conceito visual **"Trilha"** (o caminho do processo como uma trilha pontilhada com um marcador) e paleta inspirada no **Cerrado** de Mato Grosso, como **tema único**. (O protótipo aprovado também tem Pantanal e Amazônia; **esses dois não entram** e não deve haver seletor de tema.)

**Escopo: somente o front** (`front/`) e a documentação. **Não altere** `api/`, contratos de API, rotas, regras de negócio, permissões, textos de erro de negócio, nem a lógica dos componentes: a refatoração é de **aparência, layout e marca**. Tudo o que a tela faz hoje continua funcionando igual.

### Material de referência (já está no repositório em `docs/design/sigrastro/`)

O responsável extraiu `referencias-front-sigrastro.zip` na raiz do repositório. Leia `docs/design/sigrastro/LEIA-ME.md`. **Se a pasta não existir, pare e avise**: sem ela não há como seguir o visual aprovado.

- `referencia/A3-trilha-funcional.html`: protótipo funcional, **fonte da verdade do visual e do comportamento**. Abra-o com o Playwright do projeto (`file://`) e tire capturas para comparar com o front real, em **1920, 1280 e 390 px de largura** e só no **Cerrado** (**ignore** o seletor de bioma, o painel de testes e os temas Pantanal e Amazônia do protótipo). As fontes do protótipo vêm do Google; sem rede elas caem em fonte padrão, o que não é defeito.
- `referencia/src/base.css`, `extra.css`, `app.js`: o mesmo protótipo em arquivos separados, para ler valores (espaçamento, raio, sombra) e a lógica das paisagens.
- `logo/sigrastro-escudo.svg`, `logo/sigrastro-horizontal.svg`, `cenas/cerrado.svg`.

O protótipo é **mais simples que o sistema real** (dados fictícios, JavaScript puro, painel de testes, atalhos de casos de teste). Ele mostra **como as telas devem parecer e se comportar**; o sistema real tem mais campos e estados. Regras: (1) **preserve toda funcionalidade, rótulo funcional e atributo de teste existentes** do front; (2) **não leve** para o front o que é só do protótipo (painel de testes, "Para testar", usuários e processos fictícios); (3) onde o sistema real tem algo que o protótipo não mostra, **aplique a mesma linguagem visual** (mesmos tokens, painéis, tags, tabelas) em vez de inventar um estilo novo.

## Decisões já tomadas (registre em `docs/09`, no próximo número livre)

- **Nome visível definitivo: SIGRASTRO** (tudo em maiúsculas, escrito como uma palavra). Substitui "Rastro" como nome visível; **"Rastro" continua nos identificadores técnicos** (pacote, artefatos, chaves `rastro.*`, nomes de container): **não os renomeie**. A decisão da R1 de não chamar o produto de "SIGA ..." continua valendo: o SIGRASTRO é um complemento de consulta, **não** um sistema oficial do SIGA.
- **Marca:** o **escudo árvore-vida** (`sigrastro-escudo.svg`) e o nome com "SIG" em peso 500 e "RASTRO" em peso 700. **Nenhum brasão do Estado de Mato Grosso e nenhuma logo da SEMA** em nenhum lugar do front. Também somem os textos "SEMA · Mato Grosso", "consulta ao SIGA Licenciamento" e "(protótipo)" como parte da marca. A frase de apoio da marca é **"Acompanhamento de processos de licenciamento ambiental"** e só aparece na tela de login.
- **Tema único: Cerrado.** Sem seletor de tema, sem preferência de bioma e sem aba Aparência. Pantanal e Amazônia ficam de fora (o protótipo os mostra só como exploração).
- **Layout:** barra superior escura com navegação horizontal (some o menu lateral); conteúdo em **largura total** (sem coluna central estreita).
- **Fontes locais:** o front **não chama serviço de terceiros em tempo de execução** (sem Google Fonts); as fontes entram pelo npm, com licença livre registrada em `docs/13`.
- **Cores só por token.** Depois do bloco 3, **nenhuma cor fixa** (hex, `rgb()`) em CSS ou template de componente, exceto: as constantes de status (`--ok`, `--warn`, `--bad` e tints), o fundo do login (`#12302B`) e as cores internas dos SVGs de marca e de paisagem.

## Tokens do novo padrão (use exatamente estes valores)

```css
:root{
  --ok:#1C7A4D; --ok-tint:#DDF1E6; --warn:#8A5A00; --bad:#B3261E; --bad-tint:#FBE4E2;
  --display:'Bricolage Grotesque','Segoe UI',system-ui,sans-serif; --body:'Instrument Sans','Segoe UI',system-ui,sans-serif; --r:10px;
  /* Cerrado: tema único */
  --paper:#EDECE5; --surface:#FFFFFF; --ink:#2A1C12; --slate:#665A4E; --line:#D6D1C6; --line-strong:#B9AFA0;
  --trail:#A8431A; --trail-deep:#7F300F; --trail-tint:#F6E3D8; --marker:#E8B712; --marker-tint:#FBF0C6;
  --sky1:#F4E3B8; --sky2:#FBF4DC; --l1:#D9B77A; --l2:#B9803F; --l3:#8A4A1F; --sun:#E8B712; --contour:#A8431A;
}
```

Observações: os tokens ficam todos em `:root` (um tema só). Fundo da página: `--paper` com o desenho de **curvas de nível**, fixo ao rolar: `--contour-img` é um SVG em *data URI* escrito **direto no CSS**, com traço na cor `--contour` (`%23A8431A`) e opacidade .08, como o SVG de 520×520 gerado por `aplicaTema()` em `referencia/src/app.js`.

## O que muda e o que não muda, em uma tabela

| Muda | Não muda |
|---|---|
| Tokens, tipografia, botões, campos, tags, painéis, tabelas, gaveta, diálogos | Rotas do Angular e da API, serviços, modelos, guardas, interceptores |
| Marca (escudo e nome), favicon, `<title>` | Permissões por perfil (RN08), mensagens de negócio e de erro |
| Layout: barra superior + navegação, páginas em largura total | Lógica dos componentes, *signals*, validações, ordem dos documentos, dossiê |
| Login (visual), faixa de paisagem do Cerrado | Atributos de teste e rótulos funcionais já usados pelos testes (se um rótulo precisar mudar, atualize o teste no mesmo bloco) |
| Nome visível "SIGRASTRO" nos textos do front e dos documentos vivos | Identificadores técnicos `rastro`, cabeçalhos `X-Vistas-*`, volume Docker, variáveis de ambiente |

## Regras desta refatoração

- **Somente front e documentação.** Se descobrir que algo exige mexer em `api/`, **pare o bloco, não mexa** e registre em `docs/10` (pendência) com o motivo.
- **Dados fictícios** em testes, exemplos e capturas: nenhum CPF, CNPJ, nome, número de processo ou de DAR reais. Nunca abra, imprima ou copie o `.env.local`. Nada é executado contra SIGA, SCORP, Camunda ou API de arquivos reais.
- **Nada pago**: dependências só gratuitas e de código aberto, versão fixa, licença registrada em `docs/13`, `npm audit` (produção) em 0. Prefira o que o projeto já tem; **a única dependência nova prevista são as fontes** (bloco 2).
- **Acessibilidade não regride:** foco visível, navegação por teclado, nomes acessíveis, `aria-current="page"` no item ativo (use `setAttribute`/`removeAttribute`; `toggleAttribute` deixa o valor vazio e quebra o seletor), contraste AA, `prefers-reduced-motion` respeitado, nada que dependa só de cor.
- **Responsivo sem rolagem horizontal** em 390, 768, 1280 e 1920 px, em todas as telas. Em tela estreita, as regras de ocultação do protótipo valem: rótulos da navegação somem (ficam só os ícones), o nome de usuário e o selo de perfil somem, tabelas largas rolam **dentro** do próprio contêiner.
- **Verificação com Docker** (quando o bloco pedir): sempre com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`; confirme dentro do container que as variáveis externas estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.
- Datas e horas continuam pela utilidade única do fuso de Cuiabá; nada novo de data nesta refatoração.
- **Código novo segue os padrões do projeto** (`docs/13`, `docs/padroes/`): componentes *standalone* e sem zone.js (*zoneless*), *signals*, testes com Vitest, e2e com Playwright e API simulada. Textos de interface em português, sem travessão como pontuação.
- **Compatibilidade durante a migração:** até o bloco 16, mantenha **apelidos** dos tokens antigos (`--forest`, `--lime`, `--sidebar`, `--bg`, `--green`, `--soft`, `--muted`, `--olive`... ) apontando para os novos, para que as telas ainda não migradas continuem legíveis. O bloco 16 remove os apelidos e confirma que nada os usa.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS`, `SESSÃO: SONNET` ou `SESSÃO: HAIKU`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/r2.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes);
   - atualize a documentação **da própria mudança**;
   - marque o bloco em `docs/progresso/r2.md` (crie no bloco 1, com a tabela dos 18 blocos); a marcação entra no mesmo commit;
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

Cada bloco termina com os comandos de verificação do front **que existem no `package.json`** (`npm run build`, `npm test` em modo de execução única, e `npm run e2e` quando o bloco mexer em tela). Se algum teste existente falhar por causa da mudança visual (seletor de texto, classe), **corrija o teste no mesmo bloco** sem enfraquecê-lo; nunca apague um teste para passar.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Ativos da marca, favicon e `<title>` | **HAIKU** | Copiar arquivos e editar o `<head>` |
| 2 | Fontes locais | **SONNET** | Dependência nova, licença e configuração do build |
| 3 | Tokens do Cerrado | **SONNET** | Base de tudo, fiel ao protótipo |
| 4 | Base visual: tipografia, botões, campos, tags, painéis, tabelas | **SONNET** | CSS global fiel ao protótipo |
| 5 | Componente da marca | **HAIKU** | Componente pequeno com marcação dada |
| 6 | Layout: barra superior e navegação | **SONNET** | Troca de estrutura do shell, menu e permissões |
| 7 | Paisagem do Cerrado e faixa do topo | **SONNET** | SVG como imagem e cartão de título |
| 8 | Tela de login | **SONNET** | Layout novo preservando o fluxo atual |
| 9 | Consulta | **SONNET** | Tela principal |
| 10 | Detalhe do requerimento e do processo (trilha) | **SONNET** | Trilha vertical e painéis |
| 11 | Documentos, gaveta do dossiê e visualizador | **SONNET** | Tela mais densa e com mais estados |
| 12 | Dossiês gerados e Auditoria (visual) | **HAIKU** | Aplicar classes e tokens já prontos |
| 13 | Configurações: abas, usuários, parâmetros e conexões (visual) | **HAIKU** | Aplicar classes e tokens já prontos |
| 14 | Responsividade, contraste e acessibilidade | **OPUS** | Verificação transversal, ajuste fino de tokens |
| 15 | Testes: reparar e ampliar Vitest e Playwright | **OPUS** | Suíte inteira, risco de enfraquecer teste sem notar |
| 16 | Limpeza: apelidos, ativos e CSS sem uso | **HAIKU** | Buscas e remoções mecânicas, com relatório |
| 17 | Documentação e roteiro de homologação | **SONNET** | Texto técnico e decisões |
| 18 | Nome visível nos documentos vivos | **HAIKU** | Troca de texto com varredura; ambíguo é só relatado |

---

## Bloco 1 (HAIKU): ativos da marca, favicon e título

**Commit sugerido:** `feat(front): ativos da marca SIGRASTRO e favicon`

- Copie de `docs/design/sigrastro/` para `front/src/assets/img/`: `logo/sigrastro-escudo.svg` → `sigrastro-escudo.svg` e `cenas/cerrado.svg` → `cerrado.svg`. **Não** copie ainda o que não vai ser usado (a logo horizontal fica só na documentação).
- `front/src/index.html`: `<title>SIGRASTRO</title>`, favicon `<link rel="icon" type="image/svg+xml" href="assets/img/sigrastro-escudo.svg">` (remova a referência a `logo-sema.png`). Não mexa em mais nada do arquivo.
- Confirme que o `angular.json` já publica `src/assets` (sem alterar o que já funciona).
- Teste: `npm run build` passa e os dois SVG aparecem em `dist/`.
- Docs: `docs/progresso/r2.md` (crie com a tabela dos 18 blocos).

## Bloco 2 (SONNET): fontes locais

**Commit sugerido:** `feat(front): fontes Bricolage Grotesque e Instrument Sans locais`

- Fontes do padrão: **Bricolage Grotesque** (títulos; pesos 500 e 700) e **Instrument Sans** (corpo; 400, 500, 600 e 700). Instale pelas **distribuições `@fontsource`** correspondentes (licença OFL, gratuitas), **só os pesos usados**, subconjunto latino, **versão fixa** no `package.json`. Importe em `styles/index.css` (ou no ponto equivalente do projeto), com `font-display: swap`.
- Pilha de reserva nos tokens: `'Bricolage Grotesque','Segoe UI',system-ui,sans-serif` e `'Instrument Sans','Segoe UI',system-ui,sans-serif`. **Nenhuma requisição externa** em tempo de execução: confirme no build que não há `fonts.googleapis.com` em nenhum arquivo de `dist/`.
- `npm audit` (produção) em 0; se alguma transitiva trouxer alerta, relate e use a versão corrigida.
- Teste: `npm run build` e `npm test` verdes; um teste simples que lê `package.json` e garante que as duas fontes têm versão fixa (sem `^` nem `~`).
- Docs: `docs/13` (as duas dependências, licença OFL-1.1, versões, motivo: sem chamada a terceiros), `docs/progresso/r2.md`.

## Bloco 3 (SONNET): tokens do Cerrado

**Commit sugerido:** `feat(front): tokens do novo padrão (Cerrado)`

- Reescreva `styles/tokens.css` com **os tokens exatos** da seção acima, mais os **apelidos de compatibilidade** dos tokens antigos (comentados como temporários, removidos no bloco 16). Escala tipográfica base: `html{font-size:16px}`, corpo `400 1rem/1.5`, títulos em Bricolage Grotesque 700, `letter-spacing:-.02em`, `line-height:1.1`.
- `--contour-img` (curvas de nível) escrito direto no CSS como *data URI*, e aplicado ao `body` com `background-attachment:fixed`. **Sem serviço de tema, sem JavaScript e sem atributo `data-bioma`.**
- No **relatório do bloco**, inclua a **tabela de mapeamento** token antigo → token novo (usada no bloco 16).
- Teste (Vitest): um teste que lê `styles/tokens.css` como texto e confirma que todos os tokens obrigatórios da seção estão definidos, com os valores exatos.
- Docs: `docs/09` (decisões deste início: nome, marca, tema único Cerrado, fontes locais), `docs/progresso/r2.md`.

## Bloco 4 (SONNET): base visual

**Commit sugerido:** `feat(front): base visual do SIGRASTRO (botões, campos, tags, painéis, tabelas)`

- Refaça `styles/base.css`, `styles/quadros.css` e o que for global em `styles/*.css` a partir de `referencia/src/base.css` e `extra.css`: `body` (cor, fundo com curvas de nível, `--paper`), `h1..h3`, links, **foco visível** (`outline:3px solid var(--marker)`), botões (`.btn`, `.btn.quiet`, tamanhos), campos (`input`, `select`, `label`, `.hint`, mensagens de erro de campo), **tags** (`.tag`, `.ok`, `.warn`, `.bad`, `.gray`), **painéis** (`.panel`, raio 14, anel `--line`), tabelas (cabeçalho, linha, ordenação, ordenada com `aria-sort`), interruptor (`.switch`), alertas, *toasts*, diálogo/folha sobreposta.
- **Mantenha os nomes de classe que os componentes atuais já usam**; quando o protótipo usa outro nome para o mesmo papel, **mapeie** (alias de seletor) em vez de renomear em todos os templates agora. A renomeação de classes só acontece nos blocos de cada tela.
- `main` passa a **largura total**: `padding:28px clamp(16px,2.4vw,40px) 48px`, sem `max-width`.
- Teste: build, testes e e2e existentes (corrija seletores que dependam de aparência).
- Docs: `docs/progresso/r2.md`.

## Bloco 5 (HAIKU): componente da marca

**Commit sugerido:** `feat(front): componente da marca SIGRASTRO`

- Crie `app/shared/marca.component.ts` (*standalone*, *OnPush*): `<app-marca [tamanho]="..." [mostrarNome]="true">`.
  - Escudo: `<img src="assets/img/sigrastro-escudo.svg" alt="">` (decorativo) com largura do `tamanho` (padrão 36px, **sem recorte nem borda**).
  - Nome: `<span class="wm"><span class="l">SIG</span>RASTRO</span>`; `.wm` em Bricolage Grotesque 700 (`1.2rem`, `letter-spacing:.01em`), `.l` em peso 500.
  - O conjunto tem **nome acessível "SIGRASTRO"** (`aria-label` no contêiner quando `mostrarNome` for falso; texto visível quando verdadeiro).
- Teste (Vitest): renderiza o nome, o escudo e o rótulo acessível nos dois modos.
- Docs: `docs/progresso/r2.md`.

## Bloco 6 (SONNET): layout, barra superior e navegação

**Commit sugerido:** `feat(front): barra superior e navegação horizontal`

- Reescreva `layout/shell.component.html` e `styles/layout.css` a partir de `cabecalho()` e do CSS `.top`/`.nav`/`.who` do protótipo:
  - Barra **sticky**, 56px, fundo `--ink`, `z-index` acima do conteúdo; à esquerda `<app-marca>` (link para `/consulta`, rótulo "SIGRASTRO, início"); navegação horizontal com ícone + rótulo; item ativo com **sublinhado `--marker`** e `aria-current="page"`; à direita o selo do perfil, o nome do usuário, o avatar com iniciais (fundo `--trail`) e "Sair".
  - **Remova** o menu lateral, o botão de recolher, o véu mobile, a **marca d'água do brasão** (`brasao-mt.png`) e o rodapé "SEMA · Mato Grosso, v0.1.0, protótipo".
- `layout/menu.ts`: **mantenha a estrutura de dados e as permissões** (`permissao`, RN08). A navegação renderiza as seções em sequência (sem rótulos de seção). **Auditoria** passa a ser **um item único** que leva a `/auditoria/documentos`; as duas visões ("Documentos" e "Acessos", esta só com a permissão de acessos) viram **abas dentro da tela** (bloco 12). Atualize `menu.spec.ts`.
- Em ≤ 900px: só ícones na navegação; ≤ 520px: some o selo do perfil e o nome de usuário. Barra **sem rolagem horizontal**.
- Teste (Vitest): `shell.component.spec.ts` e `menu.spec.ts` (itens por perfil, ativo, Auditoria única).
- Docs: `docs/08-telas.md` (estrutura do shell novo), `docs/progresso/r2.md`.

## Bloco 7 (SONNET): paisagem do Cerrado e faixa do topo

**Commit sugerido:** `feat(front): paisagem do Cerrado e faixa do topo`

- A paisagem é `assets/img/cerrado.svg`, exibida por `<img>` com `object-fit:cover; object-position:center bottom` (o SVG tem animação leve própria e já respeita `prefers-reduced-motion`). **Não** coloque esse SVG inline (os identificadores e as classes de animação colidiriam).
- `shared/quadro-topo.component.ts` (a faixa de 240px): fundo `linear-gradient(var(--sky1), var(--sky2))`, a paisagem, e um **cartão de título** claro e semitransparente à esquerda (`rgba(251,246,228,.92)`, raio 14, sombra leve) com o título e o subtítulo da tela. O cartão é necessário: o desenho do Cerrado é cheio e o título ficaria ilegível sem ele. **Sem etiqueta de tema** na faixa.
- Remova `cena-cachoeira.ts` (e a cena do login, no bloco 8) **só quando nada mais a usar**.
- Testes (Vitest): a faixa renderiza o `<img>` da paisagem com texto alternativo vazio (decorativo) e o cartão com o título e o subtítulo informados.
- Docs: `docs/progresso/r2.md`.

## Bloco 8 (SONNET): tela de login

**Commit sugerido:** `feat(front): nova tela de login do SIGRASTRO`

- Layout (veja a captura do protótipo): **fundo de uma cor só `#12302B`** (sem as curvas de nível e sem imagem), tudo **centralizado** em coluna: `<app-marca>` grande (escudo `clamp(96px,16vh,140px)`; nome `clamp(2.2rem,6vw,3.4rem)`, cor `#F1F4EA`), a frase **"Acompanhamento de processos de licenciamento ambiental"** (cor `rgba(241,244,234,.78)`) e, abaixo, **a caixa de login** branca de `min(440px,100%)` com sombra `0 20px 60px rgba(0,0,0,.35)`.
- **Preserve todo o fluxo atual** do `login.component.ts`: formulário reativo, validações, mensagens de erro, botão de entrar e os **atalhos dos usuários de demonstração**, "Acesso simulado" e demais textos funcionais. Só muda a aparência. Retire o texto "(protótipo)" **da marca**; o aviso de acesso simulado dentro da caixa **permanece**.
- Remova a coluna da cena, o brasão e `cena-login.ts`.
- Em 390px: margens de 20px, sem rolagem horizontal; a caixa ocupa a largura.
- Testes: `login.component.spec.ts` e o e2e de login atualizados; e2e novo: a caixa fica **centralizada** (centro da caixa dentro de 2px do centro da janela) em 1920 e 1280.
- Docs: `docs/08-telas.md` (login), `docs/progresso/r2.md`.

## Bloco 9 (SONNET): consulta

**Commit sugerido:** `feat(front): visual da tela de Consulta`

- Aplique `telaConsulta` do protótipo: faixa do topo com título "Consulta", **caixa de busca sobreposta à base da faixa** (`margin-top:-26px`), alternância **Processo | Requerimento** em controle segmentado (`aria-pressed`), botão **Consultar**, e a lista **"Consultados por último"** como uma **trilha vertical** (linha tracejada com um círculo por item) em painéis.
- **Não leve** os atalhos "Para testar" do protótipo. Preserve a validação do formato N/AAAA, as mensagens de "não encontrado" e de erro, a lista de recentes e o comportamento de teclado.
- Testes: `consulta.component.spec.ts` e e2e da consulta (corrigidos para o novo visual, sem enfraquecer).
- Docs: `docs/08-telas.md`, `docs/progresso/r2.md`.

## Bloco 10 (SONNET): detalhe do requerimento e do processo (trilha)

**Commit sugerido:** `feat(front): visual do detalhe com a trilha do processo`

- Aplique `telaProcesso` e `telaCaminho` do protótipo ao `features/detalhe/`: cabeçalho do processo com **tag de situação** (`situacao-tag.component.ts` na nova paleta), painéis de dados, e a **trilha vertical do caminho** (linha pontilhada, nós, o **marcador `--marker`** na etapa atual e o **marcador final** quando concluído; o "caminho aproximado" com o estilo tracejado do protótipo). Use os componentes e dados que já existem (`trilha.service.ts`); **não mude** a lógica do caminho. O diretório `features/detalhe/` já traz, da S8 e da S9, a **Tramitação** (linha do tempo, fluxograma simplificado, plano B), o bloco **Taxas e pagamentos** (`financeiro.component.ts`), os **selos das marcas financeiras** ("Taxa vencida" em alerta) e a linha de aviso em "Onde está agora": aplique a nova paleta a todos, **sem mudar** HTML semântico, textos, ordem, regra de impressão em preto e branco nem lógica. O selo de alerta usa `--warn`/`--marker-tint` ou `--bad`/`--bad-tint`, sempre com **texto**, nunca só cor.
- Em tela estreita a trilha vai para cima do conteúdo (coluna única).
- Testes: `detalhe.component.spec.ts` e `objeto-do-requerimento.component.spec.ts` verdes; e2e do detalhe.
- Docs: `docs/08-telas.md`, `docs/progresso/r2.md`.

## Bloco 11 (SONNET): documentos, gaveta do dossiê e visualizador

**Commit sugerido:** `feat(front): visual de documentos, gaveta do dossiê e visualizador`

- Aplique o visual do protótipo à tabela de documentos (ordenável, filtros, contadores de documentos e de MB, **bloqueio de documento sigiloso** com ícone de cadeado e texto), à **gaveta do dossiê** (progresso por etapa, mensagens de erro reais, "Tentar novamente"), ao **visualizador** (diálogo, Esc fecha) e às **geometrias**. `indicador-dossie`, `gaveta-dossie`, `documentos-secao`, `geometrias-secao` e `visualizador-documento`.
- **Nada de lógica nova:** ordem, seleção, geração em segundo plano, 507/disco cheio, erros e acompanhamento permanecem. Só classes, tokens e estrutura visual.
- Testes: todos os `*.spec.ts` de `documentos/` e os e2e (`documentos-dossie`, `geometrias`, `geracao-segundo-plano`) verdes.
- Docs: `docs/08-telas.md`, `docs/progresso/r2.md`.

## Bloco 12 (HAIKU): dossiês gerados e auditoria (visual)

**Commit sugerido:** `feat(front): visual de Dossiês gerados e Auditoria`

- Aplique as classes e tokens já prontos (blocos 4 e 11) às telas `dossies/` e `auditoria/`: tabelas, filtros, tags, botões de baixar e descartar, estado vazio, paginação.
- **Auditoria** ganha **abas** "Documentos" e "Acessos" no topo da tela (a aba **Acessos só aparece** com a permissão `verAuditoriaAcessos`; sem ela, a rota continua bloqueada como hoje). As rotas `/auditoria/documentos` e `/auditoria/acessos` **não mudam**.
- **Limite do bloco (HAIKU):** não altere lógica, serviços nem modelos; só templates e CSS. Se algo exigir lógica, **pare e relate**.
- Testes: specs e e2e (`dossies-gerados`, `auditoria-documentos`, `auditoria-acessos`) verdes.
- Docs: `docs/progresso/r2.md`.

## Bloco 13 (HAIKU): configurações, abas, usuários, parâmetros e conexões (visual)

**Commit sugerido:** `feat(front): visual de Configurações`

- Aplique classes e tokens às abas de `configuracoes.component.*` (**Usuários, Parâmetros e Conexões**, `role="tablist"`, teclado), `usuarios-config`, `novo-usuario`, `parametros-aba`/`parametro-campo`, `conexoes-config`, `barra-pendencias` e `diagnostico-financeiro.component.ts` (painel do Administrador na aba Parâmetros): tabelas, interruptores, campos com `.hint` e erro de campo, cartões de conexão com o resultado do teste (tags `ok`/`warn`/`bad`), barra de alterações não salvas. **Não crie aba Aparência.**
- **Limite do bloco (HAIKU):** só templates e CSS; nenhuma lógica, validação ou serviço muda. Se algo exigir lógica, **pare e relate**.
- Testes: specs e e2e de configurações verdes.
- Docs: `docs/08-telas.md`, `docs/progresso/r2.md`.

## Bloco 14 (OPUS): responsividade, contraste e acessibilidade

**Commit sugerido:** `test(front): verificação transversal de responsividade, contraste e acessibilidade`

- **Contraste automático:** um teste (Vitest, lendo `styles/tokens.css` como texto) que calcula a razão de contraste WCAG dos pares: `--ink`/`--paper`, `--ink`/`--surface`, `--slate`/`--paper`, `--slate`/`--surface`, `#fff`/`--trail` (botão), `--trail-deep`/`--trail-tint` (tag), `--ok`/`--ok-tint`, `--warn`/`--marker-tint`, `--bad`/`--bad-tint`, `#F1F4EA`/`#12302B` (login), `rgba(255,255,255,.78)` composto sobre `--ink` (navegação) e `--marker`/`--ink` (sublinhado ativo, **3:1**). Texto: **≥ 4,5:1**; elementos de interface: **≥ 3:1**. Se um par falhar, **ajuste o token mínimo necessário** (escurecer ou clarear), registre antes e depois no relatório do bloco e no `docs/design/sigrastro/LEIA-ME.md`.
- **Varredura de cor fixa:** busque `#[0-9a-fA-F]{3,8}` e `rgb(` em `front/src` fora de `tokens.css`, dos SVGs de marca/paisagem e do login; o que sobrar vira token ou é justificado.
- **Teclado e foco:** percorrer com Tab o login, o shell, a consulta, a tabela de documentos, a gaveta, o diálogo (foco preso e devolvido, Esc fecha) e as abas; nada fica sem foco visível ou sem nome acessível.
- **Movimento:** com `prefers-reduced-motion: reduce`, nenhuma animação própria roda (inclusive a do Cerrado, que já traz a regra).
- **Responsividade:** em 390, 768, 1280 e 1920 px, **sem rolagem horizontal da página** em todas as rotas (use o e2e do bloco 15). Corrija o que estourar.
- Docs: `docs/design/sigrastro/LEIA-ME.md` (contrastes medidos, regras de acessibilidade), `docs/progresso/r2.md`.

## Bloco 15 (OPUS): testes, reparar e ampliar

**Commit sugerido:** `test(front): suíte de testes alinhada ao novo visual e e2e de layout e tema`

- Rode a suíte inteira (Vitest e Playwright). **Corrija** os testes que quebraram por aparência (texto, classe, estrutura) **mantendo a intenção de cada um**. É **proibido** apagar, pular ou afrouxar uma verificação para passar; se um teste deixou de fazer sentido (ex.: menu lateral recolhido), **substitua** por um equivalente do novo layout e liste no relatório.
- Novos e2e (API simulada, sem dado real), `e2e/layout-e-tema.e2e.ts`:
  1. **Sem rolagem horizontal** em cada rota principal (login, consulta, detalhe, dossiês, auditoria, configurações) em 390, 768, 1280 e 1920 px.
  2. **Login centralizado** (centro da caixa dentro de 2px do centro da janela) em 1920 e 1280; fundo `#12302B`; presença do escudo e do nome.
  3. **Navegação:** o item ativo tem `aria-current="page"`; **Auditoria** é item único; **Configurações** só aparece com a permissão; Operador não vê **Acessos** nem **Configurações**.
  4. **Tema único:** não existe aba Aparência nem seletor de tema; a faixa mostra a paisagem do Cerrado.
  5. **Sem erros de console** em nenhuma das rotas acima.
  6. **Sem marca antiga:** nenhuma página contém "SEMA · Mato Grosso", "brasão", "consulta ao SIGA Licenciamento" nem requisição a `fonts.googleapis.com`.
- **Capturas** de cada tela em 1920 e 390 px, geradas por um script de Playwright em `front/test-results/capturas/` (confirme que a pasta está no `.gitignore`; **não** versione imagens), para o responsável comparar com o protótipo.
- Docs: `docs/13` (como rodar os e2e de layout e gerar as capturas), `docs/progresso/r2.md`.

## Bloco 16 (HAIKU): limpeza

**Commit sugerido:** `chore(front): remover apelidos de tokens, ativos e CSS sem uso`

- Remova os **apelidos de compatibilidade** do bloco 3, **depois** de buscar cada um em `front/src` (CSS e templates): se ainda houver uso, **troque pelo token novo equivalente** (ver a tabela de mapeamento no relatório do bloco 3) ou, se não houver equivalente óbvio, **deixe o apelido e liste**.
- Remova os arquivos **sem nenhuma referência**: `assets/img/brasao-mt.png`, `assets/img/logo-sema.png`, `cena-cachoeira.ts`, `cena-login.ts`, regras de CSS mortas (`.sidebar`, `.watermark`, `.login-art`, `.brand`...). **Antes de remover cada um, mostre a busca que prova que não há uso.**
- **Limite do bloco (HAIKU):** só remoção do que é comprovadamente sem uso; qualquer dúvida vai **listada** no resumo, com arquivo e linha, para o responsável decidir.
- Teste: `npm run build`, `npm test` e `npm run e2e` verdes; `git diff --stat` conferido.
- Docs: `docs/progresso/r2.md`.

## Bloco 17 (SONNET): documentação e roteiro de homologação

**Commit sugerido:** `docs(front): padrão visual do SIGRASTRO e roteiro de homologação da R2`

- `docs/design/sigrastro/LEIA-ME.md` → vire o **guia do padrão visual**: conceito "Trilha"; tokens do Cerrado; tipografia; componentes (botão, campo, tag, painel, tabela, faixa, trilha); **regras de uso da marca** (escudo sobre fundo claro ou escuro, tamanho mínimo 24px, não deformar, **sem brasão nem logo do Estado ou da SEMA**); acessibilidade e contraste medidos. Registre que **Pantanal e Amazônia foram explorados no protótipo e ficaram de fora** por decisão.
- `docs/08-telas.md` completo para o novo layout; `docs/09` (decisões, no próximo número livre); `docs/10` (pendências e riscos: **textos visíveis do back-end que ainda dizem "Rastro"** ou o nome antigo, como capa do PDF, metadados e título da API, ficam **fora desta refatoração** e viram pendência para outra sprint; logo horizontal depende da fonte instalada); `CLAUDE.md` (regras curtas do front: tokens, sem cor fixa, sem brasão, tema único); `README` (nome e imagem da marca).
- `docs/prototipo/siga-vistas-prototipo.html`: **não apague**; adicione ao topo do arquivo um comentário "protótipo histórico do visual antigo; o vigente está em `docs/design/sigrastro/`".
- `docs/roteiros/r2-homologacao.md`: roteiro para o responsável, **telas × 3 larguras (1920, 1280, 390)**: login centralizado, nome e escudo; navegação e item ativo; Consulta; detalhe e trilha; documentos e gaveta; dossiês gerados; auditoria em abas; configurações (abas); teclado; `prefers-reduced-motion`; e a conferência final de que **nada do comportamento mudou** (gerar um dossiê de ponta a ponta com dados fictícios).
- `docs/14`, `docs/progresso/r2.md` completos.

## Bloco 18 (HAIKU): nome visível nos documentos vivos

**Commit sugerido:** `docs: SIGRASTRO como nome visível do produto nos documentos vivos`

- Nos **documentos vivos** (`README.md`, `CLAUDE.md`, `docs/01` a `docs/14`, `docs/padroes/`, `docs/claude-code/`, `docs/roteiros/` ainda em uso), troque o **nome visível do produto** "Rastro" por **SIGRASTRO**. **Não altere:** identificadores técnicos (`rastro`, `br.gov.mt.sema.rastro`, `rastro-api`, chaves `rastro.*`, `tools/mcp-rastro`, nome de servidor MCP), nomes de arquivo, `docs/prompts/`, `docs/progresso/` de sprints passadas, textos de PR, nem "Vistas" (módulo do dossiê).
- A palavra comum "rastro" no sentido de **trilha do processo** ("rastro do processo") **fica como está**.
- **Limite do bloco (HAIKU):** só troca de texto. O que for ambíguo **não** é trocado: liste arquivo, linha e trecho no resumo para o responsável decidir.
- Varredura final: liste as ocorrências de "Rastro" que **sobraram** e o motivo de cada uma; confirme que nenhum documento vivo ainda chama o produto de "SIGA Vistas".
- Varredura de dados reais (CPF, CNPJ, nomes, números de processo) e `git diff --stat`.
- Docs: `docs/progresso/r2.md` fechado.

---

## Critérios de aceite

1. O front mostra **SIGRASTRO** com o escudo árvore-vida na barra, no login e no favicon; **nenhum brasão** do Estado, logo da SEMA ou texto "consulta ao SIGA Licenciamento" em lugar algum.
2. **Login:** fundo de uma cor, marca e frase no fundo, caixa central; fluxo e atalhos de demonstração idênticos ao anterior.
3. **Layout:** barra superior com navegação horizontal e item ativo; conteúdo em largura total; **sem rolagem horizontal** em 390, 768, 1280 e 1920 px em todas as telas.
4. **Tema único Cerrado:** tokens, paisagem aprovada (`cerrado.svg`) na faixa, sem seletor de tema, sem aba Aparência, sem Pantanal nem Amazônia.
5. **Fontes locais**; nenhuma requisição a terceiros em tempo de execução.
6. **Nada de comportamento mudou:** rotas, permissões (RN08), validações, geração e acompanhamento do dossiê, mensagens de negócio idênticos; a API não foi tocada.
7. **Cor só por token** (exceções listadas); contraste AA **medido por teste**; foco visível; `prefers-reduced-motion` respeitado.
8. Suíte do front (Vitest) e e2e (Playwright) **verdes**, sem teste apagado ou enfraquecido; build sem aviso novo; `npm audit` (produção) em 0.
9. Apelidos de token e ativos antigos **removidos** (ou listados com motivo).
10. Documentação e roteiro de homologação entregues; **nenhum comando git além de `git diff`** executado; nenhum dado real em teste, exemplo ou captura.
