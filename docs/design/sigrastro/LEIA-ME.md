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
