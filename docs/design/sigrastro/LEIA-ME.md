# Padrão visual do SIGRASTRO

Guia do padrão visual do front (R2). O material de entrada da refatoração (`referencia/`, `logo/`, `cenas/`) continua nesta pasta como fonte da verdade do visual aprovado. Os valores vigentes estão em `front/src/styles/tokens.css` e são fiscalizados por teste (`tokens.spec.ts`, `contraste.spec.ts`, `cores-fixas.spec.ts`).

## Conceito "Trilha"

O caminho do processo é uma **trilha pontilhada com um marcador**: a Consulta lista os "Consultados por último" como trilha vertical, e o detalhe mostra a Tramitação como trilha (um nó por etapa, marcador `--marker` na etapa atual, marcador final quando concluído, tracejado para o caminho aproximado). A paleta é a do **Cerrado** de Mato Grosso, como **tema único**.

**Pantanal e Amazônia** foram explorados no protótipo e **ficaram de fora por decisão** (D90): não há seletor de tema, preferência de bioma nem aba Aparência.

## Arquivos de referência

| Arquivo | O que é |
|---|---|
| `referencia/A3-trilha-funcional.html` | Protótipo funcional e autossuficiente do novo visual, aprovado pelo responsável. Abre direto no navegador (`file://`). Dados fictícios. Só o Cerrado vale; ignore o painel de testes e os outros biomas. |
| `referencia/src/base.css`, `extra.css` | CSS do protótipo (a `extra.css` vence a `base.css`): espaçamento, raio, sombra e tamanho. |
| `referencia/src/app.js` | JavaScript do protótipo: telas, estados, paisagens e fundo de curvas de nível. |
| `logo/sigrastro-escudo.svg` | Escudo árvore-vida, `viewBox 0 0 240 240`, com contorno claro. |
| `logo/sigrastro-horizontal.svg` | Escudo, nome e frase, para documentos. O texto usa Bricolage Grotesque: depende da fonte instalada (R80 em `docs/10`). Não é usada no front. |
| `cenas/cerrado.svg` | Paisagem do Cerrado, `viewBox 0 0 1200 420`, animada de leve e com a própria regra de `prefers-reduced-motion`. Copiada para `front/src/assets/img/`. |

## Tokens do Cerrado

Todos em `:root` de `front/src/styles/tokens.css`.

| Grupo | Tokens |
|---|---|
| Status | `--ok` #1C7A4D, `--ok-tint` #DDF1E6, `--warn` #8A5A00, `--bad` #B3261E, `--bad-tint` #FBE4E2 |
| Superfície e texto | `--paper` #EDECE5 (página), `--surface` #FFFFFF (painel), `--ink` #2A1C12, `--slate` #665A4E, `--line` #D6D1C6, `--line-strong` #B9AFA0 |
| Identidade | `--trail` #A8431A, `--trail-deep` #7F300F, `--trail-tint` #F6E3D8, `--marker` #E8B712, `--marker-tint` #FBF0C6 |
| Paisagem | `--sky1`, `--sky2`, `--l1`, `--l2`, `--l3`, `--sun`, `--contour` |
| Forma | `--r` 10px (painéis 14px, faixa 18px) |
| Sobre a barra escura | `--on-ink`, `--on-ink-soft`, `--on-ink-line`, `--on-ink-track`, `--on-ink-hover`, `--on-ink-faint`; véus `--veil`, `--headline-bg`, `--glass-*` (R2, bloco 14) |

O fundo da página é `--paper` com curvas de nível (`--contour-img`, SVG em data URI no CSS, traço `--contour`, opacidade .08, fixo ao rolar).

**Regra: cor só por token.** Nenhum hex nem `rgb()` em CSS ou template, exceto as constantes de status, o fundo do login (`#12302B`), as sombras, a impressão em preto e branco e as cores internas dos SVGs de marca e paisagem. O `cores-fixas.spec.ts` falha se aparecer outra.

## Tipografia

- **Títulos:** Bricolage Grotesque 700 (500 na marca), `letter-spacing:-.02em`, `line-height:1.1`.
- **Corpo:** Instrument Sans 400, 500, 600 e 700, `1rem/1.5`; `html{font-size:16px}`.
- Fontes **locais** (`@fontsource`, OFL-1.1, versão fixa; `docs/13`): nenhuma chamada a terceiros em tempo de execução. Pilha de reserva `'Segoe UI',system-ui,sans-serif`.

## Componentes

| Componente | Classes e regras |
|---|---|
| **Botão** | `.btn` é o secundário (fundo `--surface`, anel `--line`); `.btn.primary` preenchido em `--trail` com texto branco; `.btn.quiet` sem fundo; `.btn.sm`. Desabilitado com opacidade e `cursor:not-allowed`. |
| **Campo** | `input`, `select`, `label`, `.hint` (apoio) e mensagem de erro de campo, sempre com texto. |
| **Tag** | `.tag` (padrão `--trail-tint`), `.ok`, `.warn`, `.bad`, `.gray`. Sempre com texto, nunca só cor. Os nomes antigos `.lime`, `.amber`, `.red`, `.blue`, `.dark` continuam como alias. |
| **Painel** | `.panel`: fundo `--surface`, raio 14, anel `--line`. `.glass` (cartões do detalhe) é o mesmo papel com nome antigo. |
| **Tabela** | `.table-wrap` com rolagem interna; cabeçalho, linha, ordenação com `aria-sort`. Em tela estreita a tabela rola dentro do contêiner. |
| **Faixa do topo** | `quadro-topo`: 240px, gradiente `--sky1`/`--sky2`, `cerrado.svg` por `<img>` decorativo (`object-fit:cover; object-position:center bottom`, **nunca inline**) e cartão de título claro à esquerda (`--headline-bg`, raio 14). Sem etiqueta de tema. |
| **Trilha** | Linha tracejada e um nó por item; marcador `--marker` na etapa atual. Em tela estreita vai para coluna única. |
| **Controle segmentado** | `.seg` com `aria-pressed`. |
| **Barra superior** | Sticky, 56px, `--ink`; marca, navegação horizontal (item ativo com sublinhado `--marker` e `aria-current="page"`), indicador do dossiê, selo do perfil, nome, avatar (`--trail`) e "Sair". Em até 900px só ícones (o rótulo fica para leitor de tela); em até 520px somem selo e nome. |

## Regras de uso da marca

- A marca é o **escudo árvore-vida** mais o nome **SIGRASTRO**, uma palavra em maiúsculas, com "SIG" em peso 500 e "RASTRO" em 700 (`<app-marca>`).
- O escudo tem contorno claro: vale sobre **fundo claro ou escuro**. Sem recorte, sem borda, sem filtro.
- **Tamanho mínimo: 24 px** de largura. Não deformar (proporção 1:1), não girar, não recolorir.
- Escudo decorativo (`alt=""`) quando o nome está visível ao lado; sem nome visível, o conjunto tem nome acessível "SIGRASTRO".
- **Nenhum brasão do Estado de Mato Grosso e nenhuma logo da SEMA**, em lugar nenhum do front. Também não aparecem "SEMA · Mato Grosso", "consulta ao SIGA Licenciamento" nem "(protótipo)" como parte da marca.
- A frase "Acompanhamento de processos de licenciamento ambiental" só aparece no login.
- O SIGRASTRO é um complemento de consulta, **não** um sistema oficial do SIGA.

## Contraste medido (R2, bloco 14)

Razões WCAG 2.x calculadas por `front/src/styles/contraste.spec.ts` a partir dos valores reais de `tokens.css` (o teste quebra se um token mudar para abaixo do mínimo). Texto: mínimo 4,5:1 (AA). Elemento de interface: 3:1. **Nenhum token precisou de ajuste**: todos os pares passaram com os valores aprovados.

| Par (frente / fundo) | Uso | Razão | Mínimo |
|---|---|---|---|
| `--ink` / `--paper` | texto sobre a página | 13,93 | 4,5 |
| `--ink` / `--surface` | texto sobre painel | 16,49 | 4,5 |
| `--slate` / `--paper` | texto secundário | 5,65 | 4,5 |
| `--slate` / `--surface` | texto secundário em painel | 6,69 | 4,5 |
| `#FFFFFF` / `--trail` | botão principal | 6,03 | 4,5 |
| `--trail-deep` / `--trail-tint` | tag padrão, botão em foco | 7,25 | 4,5 |
| `--ok` / `--ok-tint` | tag ok | 4,52 | 4,5 |
| `--warn` / `--marker-tint` | tag de alerta | 5,19 | 4,5 |
| `--bad` / `--bad-tint` | tag de erro | 5,38 | 4,5 |
| `#F1F4EA` / `#12302B` | marca no login | 12,72 | 4,5 |
| `rgba(255,255,255,.78)` sobre `--ink` | navegação (`--on-ink-soft`) | 10,43 | 4,5 |
| `--marker` / `--ink` | sublinhado ativo, foco na barra | 8,81 | 3 |
| `--on-ink` / `--warn`, `--bad`, `--ok` | indicador do dossiê | 5,93 / 6,54 / 5,33 | 4,5 |
| `--warn` / `--surface` | cadeado de sigiloso | 5,93 | 4,5 |

Atenção: `--ok`/`--ok-tint` passa por pouco (4,52). Qualquer ajuste em um desses dois tokens precisa rodar o teste.

## Regras de acessibilidade do front

- **Foco visível em duas cores.** O `--marker` sozinho tem 1,9:1 sobre `--surface` e 1,6:1 sobre `--paper` (falha o 3:1). Por isso o anel é `outline:3px solid var(--marker)` com um anel interno `box-shadow:0 0 0 2px var(--ink)` (13,9:1 sobre a página); na barra escura vale o `--marker` (8,8:1). Não remova o anel interno nem troque o `outline` por `none`.
- **Diálogos** (visualizador, gaveta do dossiê, pré-visualizações, detalhe da auditoria, novo usuário) usam `FocoPresoDirective` (`shared/foco-preso.directive.ts`): o foco entra no diálogo ao abrir, o Tab circula só dentro dele, o Esc fecha (ou minimiza, na gaveta) e o foco volta ao botão que abriu. Se o diálogo se atualiza sozinho e o elemento com foco some ou fica desabilitado, ou se o foco vai parar fora dele, o foco volta ao primeiro elemento focável do diálogo do topo (R2, bloco 15). Diálogo novo: aplique `appFocoPreso` no elemento com `role="dialog"`.
- **Cor só por token.** `front/src/styles/cores-fixas.spec.ts` falha se aparecer hex ou `rgb()`/`rgba()` em CSS, template ou componente, fora das exceções: `tokens.css`, `login.css` (fundo `#12302B`), `impressao.css` e o bloco de impressão do fluxograma (preto e branco), sombras (`box-shadow`) e os SVGs de marca e paisagem. As camadas sobre a barra escura e os véus viraram tokens (`--on-ink*`, `--veil`, `--headline-bg`, `--glass-*`).
- **Movimento.** `prefers-reduced-motion: reduce` desliga toda transição e animação própria (`base.css`); `cerrado.svg` traz a própria regra.
- **Nada só por cor.** Tags e selos sempre com texto; o sigiloso tem cadeado e texto.
- **Sem rolagem horizontal** em 390, 768, 1280 e 1920 px; tabela larga rola dentro de `.table-wrap`.
- **Pendência:** a borda dos campos (`--line`) fica abaixo de 3:1 (R79 em `docs/10`), decisão do responsável.

A prova automática está em `front/e2e/acessibilidade.e2e.ts` (Tab com anel e nome acessível no login, barra, consulta, tabela de documentos e abas; foco preso e devolvido nos diálogos; movimento reduzido) e em `front/e2e/layout-e-tema.e2e.ts` (sem rolagem horizontal, login centralizado, navegação, tema único, console limpo, sem marca antiga).
