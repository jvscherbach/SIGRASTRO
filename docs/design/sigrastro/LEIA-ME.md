# Referências de design do SIGRASTRO

Material de entrada da refatoração do front (`docs/prompts/R2-refatorar-front-sigrastro.md`). O Claude Code lê esta pasta; o responsável não precisa editá-la.

| Arquivo | O que é |
|---|---|
| `referencia/A3-trilha-funcional.html` | Protótipo funcional e autossuficiente (um arquivo) do novo visual, aprovado pelo responsável. Abre direto no navegador (`file://`). É a **referência visual e de comportamento**. Dados fictícios. |
| `referencia/src/base.css`, `extra.css` | CSS do protótipo (a `extra.css` vem depois e vence a `base.css`). Fonte dos valores de espaçamento, raio, sombra e tamanho. |
| `referencia/src/app.js` | JavaScript do protótipo: telas, estados, geração das paisagens (`function terra`) e do fundo de curvas de nível (`aplicaTema`). Tem o marcador `/*CERRADO*/` no lugar do SVG do Cerrado (que está em `cenas/cerrado.svg`). |
| `logo/sigrastro-escudo.svg` | Marca do SIGRASTRO (escudo árvore-vida), `viewBox 0 0 240 240`. Tem contorno claro, funciona sobre fundo escuro. |
| `logo/sigrastro-horizontal.svg` | Escudo + nome + frase, para documentos. O texto usa a fonte Bricolage Grotesque. |
| `cenas/cerrado.svg` | Paisagem do Cerrado (animada de leve, respeita `prefers-reduced-motion`). `viewBox 0 0 1200 420`. |

**Tema:** o protótipo traz três biomas (Cerrado, Pantanal e Amazônia) e um painel de testes. **Só o Cerrado é usado** no front; ignore o resto.

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
- **Diálogos** (visualizador, gaveta do dossiê, pré-visualizações, detalhe da auditoria, novo usuário) usam `FocoPresoDirective` (`shared/foco-preso.directive.ts`): o foco entra no diálogo ao abrir, o Tab circula só dentro dele, o Esc fecha (ou minimiza, na gaveta) e o foco volta ao botão que abriu. Diálogo novo: aplique `appFocoPreso` no elemento com `role="dialog"`.
- **Cor só por token.** `front/src/styles/cores-fixas.spec.ts` falha se aparecer hex ou `rgb()`/`rgba()` em CSS, template ou componente, fora das exceções: `tokens.css`, `login.css` (fundo `#12302B`), `impressao.css` e o bloco de impressão do fluxograma (preto e branco), sombras (`box-shadow`) e os SVGs de marca e paisagem. As camadas sobre a barra escura e os véus viraram tokens (`--on-ink*`, `--veil`, `--headline-bg`, `--glass-*`).
- **Movimento.** `prefers-reduced-motion: reduce` desliga toda transição e animação própria (`base.css`); `cerrado.svg` traz a própria regra.
- **Nada só por cor.** Tags e selos sempre com texto; o sigiloso tem cadeado e texto.
- **Sem rolagem horizontal** em 390, 768, 1280 e 1920 px; tabela larga rola dentro de `.table-wrap`.
- **Pendência:** a borda dos campos (`--line`) fica abaixo de 3:1 (R79 em `docs/10`), decisão do responsável.

A prova automática está em `front/e2e/acessibilidade.e2e.ts` (Tab com anel e nome acessível no login, barra, consulta, tabela de documentos e abas; foco preso e devolvido nos diálogos; movimento reduzido).
