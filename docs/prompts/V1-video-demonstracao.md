# V1: vídeo de demonstração do SIGRASTRO (Demo Day)

Prompt para o Claude Code, em blocos. Cada bloco termina em ponto de parada; **você (João) confere e commita**. Este prompt **não** usa git além de `git diff`.

## Objetivo

Gravar, sobre **dados fictícios**, o vídeo da demonstração do SIGRASTRO:

- **Trecho principal:** 1min30 a 1min50, 16:9, **1920×1080**, **sem narração no arquivo** (o João fala ao vivo por cima). MP4 H.264, **sem faixa de áudio**.
- **Sem narração em nenhuma versão:** nada de voz, TTS ou Whisper. O João fala ao vivo por cima do vídeo.
- Tudo reproduzível por comando: o João pode regravar quando mudar uma tela.

**Fluxo:** blocos 1 a 3 preparam e fotografam as telas; o **João analisa as capturas** e libera; só então os blocos 4 a 7 gravam o vídeo.

## Regras que valem em todos os blocos

1. **Nada pago.** Só ferramenta gratuita e local. Se faltar uma (FFmpeg), **pare e diga qual falta e como instalar de graça**; não instale sem o João confirmar.
2. **Nenhum dado real:** nenhum CPF, CNPJ, nome de pessoa, número de processo ou razão social reais. Números de processo no **formato do SIGA, `N/AAAA`** (ex.: `9101/2026`), usando faixa alta e fora do que existe na produção, e sempre ligados a dados claramente fictícios; nomes genéricos ("Fazenda Modelo", "Analista Exemplo").
3. **Nunca abra `.env.local`, nunca envie `mapa-local.csv`**, nunca mostre senha, token ou variável de ambiente (nem no terminal gravado). **Senha nunca aparece digitada nem em texto claro:** a gravação começa com a sessão já autenticada.
4. **Não modifique código de produção** (`api/src/main`, `front/src/app`). Tudo novo vai em `tools/video-demo/` (roteiro, gravador, montagem) e, se for preciso estender a simulação, **somente** em arquivos de e2e/simulação ou em arquivo novo ao lado. Saídas (vídeo, quadros) em `front/test-results/video/` (já ignorada pelo Git); **não versione vídeo nem imagem**.
5. Sem push, sem commit: ao fim de cada bloco, mostre `git diff --stat` e a mensagem de commit sugerida.
6. Testes direcionados por bloco; suíte completa **uma vez**, no fim do bloco 7, só do que foi alterado.
7. Dados do vídeo vêm da **API simulada do front** (`front/e2e/api-simulada.ts`, `simulacao-completa.ts`), sem Oracle, sem Docker e sem back-end.

## Blocos e sessões

| Bloco | Título | Sessão |
|---|---|---|
| 1 | inventário do ambiente e da cobertura da simulação | SONNET |
| 2 | dados da demonstração | SONNET |
| 3 | capturas de tela para análise do João (ponto de parada obrigatório) | SONNET |
| 4 | roteiro executável | SONNET |
| 5 | gravador com Playwright | SONNET |
| 6 | montagem com FFmpeg | SONNET |
| 7 | conferência final e documentação | HAIKU |

## Roteiro do trecho principal (referência de todos os blocos)

| Tempo | Perfil | Tela | O que aparece |
|---|---|---|---|
| 0:00 a 0:10 | Gestor | Abertura | Tela inicial já autenticada; legenda "Dados fictícios, nenhum processo real" |
| 0:10 a 0:30 | Gestor | Painel do Gestor | Os três gráficos: protocolados × concluídos no ano, fila por etapa, carga por responsável |
| 0:30 a 0:45 | Gestor | Consulta | Busca pelo número `9101/2026` e abertura do processo |
| 0:45 a 1:10 | Gestor | Detalhe: "Onde está agora" e Tramitação | (a) um processo **concluído**, em que "Verificação da revisão" aparece como "Concluído"; (b) uma análise com **vários analistas**, cada um marcado como concluiu ou pendente; aparece o nome do responsável; fluxograma simplificado |
| 1:10 a 1:25 | Gestor | Taxas | Uma taxa **vencida**, com a marca e o aviso |
| 1:25 a 1:40 | Gestor | Dossiê | Gera o PDF do dossiê e mostra o resultado |
| 1:40 a 1:50 | Interessado (Operador) | Troca de perfil | Vê só o próprio processo, gera o dossiê dele; **não aparece o nome do responsável**, só o papel |

A caixa de entrada do analista **não existe**: não gravar nem simular.

---

## Bloco 1 (SONNET): inventário do ambiente e da cobertura da simulação

Só leitura e relatório. **Não altere nada.**

- Confira e liste, com a versão: Node, `npm`, navegadores do Playwright (`npx playwright --version` e se o Chromium está instalado), **FFmpeg** (`ffmpeg -version`). Diga o que falta e como instalar de graça.
- Em `front/e2e/api-simulada.ts` e `simulacao-completa.ts`, descubra o que a simulação já responde: login e perfis (Gestor, Operador), consulta, detalhe, "Onde está agora", tramitação (passagens e fluxograma), taxas, **painel**, dossiê (geração e download). Para cada cena do roteiro, diga **sim, parcial ou não**, com o arquivo e o trecho que prova.
- Diga quais processos simulados já servem para: (a) concluído, com a verificação da revisão; (b) vários analistas; (c) taxa vencida; (d) processo do Operador (interessado). Se faltar algum, liste o que seria preciso criar.
- Confirme como a simulação trata o PDF do dossiê (arquivo real pequeno, ou resposta vazia?). O vídeo precisa **mostrar** o dossiê abrindo.
- Entregue o relatório em `docs/roteiros/video-demo-inventario.md` (curto, com a tabela cena × cobertura).

Ponto de parada. Commit sugerido: `docs(video): inventário do ambiente e da cobertura da simulação`

---

## Bloco 2 (SONNET): dados da demonstração

Com base no bloco 1, complete o que faltar **sem mexer no que os testes já usam**.

- Crie um conjunto próprio da demonstração (arquivo novo, por exemplo `front/e2e/video/dados-demonstracao.ts`, que **reaproveita** as funções da simulação existente), com:
  - `9101/2026`: processo **concluído** (caminho completo até a verificação da revisão, final "Concluído");
  - `9102/2026`: **análise em andamento com 3 analistas** (2 concluíram, 1 pendente), com nome e papel;
  - `9103/2026`: **taxa vencida**, com o aviso e as marcas;
  - `9104/2026`: processo cujo interessado é o usuário Operador da demonstração.
- Painel com números **verossímeis e redondos** (fila com várias etapas, carga de 5 a 8 servidores com nomes genéricos, série de 12 meses com protocolados maiores que concluídos).
- Dossiê: devolva um **PDF pequeno e fictício** (algumas páginas com texto de exemplo, uma delas com tabela vinda de planilha) para o gerador e o visualizador. Gere o PDF por script, **nunca use documento real**.
- Teste de varredura (Vitest ou script Node): nenhum CPF, CNPJ, e-mail ou nome fora da lista fictícia no conjunto da demonstração.

Testes: a varredura nova e os e2e que já usam a simulação (direcionados).

Ponto de parada. Commit sugerido: `test(video): dados fictícios da demonstração com os quatro processos e o painel`

---

## Bloco 3 (SONNET): capturas de tela para análise do João (ponto de parada obrigatório)

**Antes de gravar qualquer vídeo.** Gere imagens das telas com os dados do bloco 2, para o João analisar e decidir se a demonstração está boa.

- Script `tools/video-demo/capturar.mjs` (reaproveite o mecanismo de `front/playwright.capturas.config.ts`, com a simulação da demonstração): uma captura **1920×1080** por momento do roteiro, na ordem das cenas, em `front/test-results/video/capturas/`, com nome `NN-cena.png` (por exemplo `02-painel.png`, `04a-detalhe-concluido.png`, `04b-detalhe-varios-analistas.png`, `05-taxa-vencida.png`, `06-dossie-gestor.png`, `07-interessado.png`).
- Capture também o que o Gestor vê **e o que o Interessado vê** na mesma tela de detalhe, lado a lado em arquivos separados, para comparar (nome do responsável aparece só para o Gestor).
- Capture o PDF do dossiê aberto no visualizador.
- Faça uma **lista de conferência por imagem** em `docs/roteiros/video-demo-capturas.md`: arquivo, o que deve aparecer, o que você observou de errado (texto cortado, rolagem horizontal, dado que pareça real, gráfico vazio, rótulo "Etapa não mapeada" indevido, contraste ruim). **Não corrija nada ainda**: só descreva.
- Liste os caminhos dos arquivos no final.

**PARE AQUI.** Não passe ao bloco 4 enquanto o João não liberar depois de analisar as imagens. Se ele pedir ajustes, eles entram neste bloco ou no bloco 2.

Ponto de parada. Commit sugerido: `feat(video): capturas de tela das cenas para análise`

---

## Bloco 4 (SONNET): roteiro executável

Crie `tools/video-demo/roteiro.json` com **uma entrada por cena** da tabela acima:

- `id`, `perfil`, `inicio`, `fim` (segundos), `rota` (URL do front), `acoes` (clicar, digitar, rolar, esperar por seletor visível) e `legenda` (texto curto na tela, ou vazio).
- Validação do roteiro por teste simples: soma dos tempos entre 90 e 110 s, cenas em ordem, nenhum campo vazio obrigatório.

Ponto de parada. Commit sugerido: `feat(video): roteiro executável da demonstração`

---

## Bloco 5 (SONNET): gravador com Playwright

Crie `tools/video-demo/gravar.mjs` (ou `.ts`, o que o projeto já usa) que lê o roteiro e **grava** o trecho principal:

- Viewport e vídeo em **1920×1080**; `recordVideo` do Playwright; Chromium; API simulada interceptando as chamadas do front (reaproveite o mecanismo do e2e). Idioma `pt-BR`, fuso `America/Cuiaba`, relógio fixo do conjunto da demonstração.
- **Cursor visível** (círculo ou seta injetada na página) e movimento suave até cada elemento; pausas de leitura entre as ações (0,8 a 1,5 s); rolagem suave.
- Legenda na tela (faixa discreta, fonte legível, contraste AA) quando a cena tiver `legenda`.
- A sessão **começa autenticada** (estado de armazenamento do Gestor); para a cena do Interessado, **segundo contexto** autenticado como Operador, gravado à parte e depois emendado. Nenhuma senha digitada.
- Saídas em `front/test-results/video/bruto/` (um arquivo por perfil). Imprima a duração de cada cena e falhe se uma cena estourar o tempo do roteiro em mais de 20%.
- Script npm novo: `npm --prefix front run video:gravar` (ou equivalente; **não** inclua no `npm run e2e`).

Ponto de parada: grave uma vez e mostre os caminhos e as durações. Commit sugerido: `feat(video): gravador Playwright da demonstração`

---

## Bloco 6 (SONNET): montagem com FFmpeg

Crie `tools/video-demo/montar.mjs` (ou shell simples) que gera o **trecho principal**:

- Junta os brutos na ordem do roteiro, corta sobras no início e no fim de cada cena, aplica transição curta (fade de 0,3 s) e codifica em **MP4 H.264, 1920×1080, 30 fps, `yuv420p`**, sem áudio.
- Duração final entre **1min30 e 1min50**; se passar, ajuste a velocidade das esperas, não das ações. Falhe com mensagem clara se ficar fora.
- Saída: `front/test-results/video/sigrastro-demonstracao.mp4`. Imprima duração, resolução e tamanho (`ffprobe`).
- Script npm: `video:montar`.

Ponto de parada. Commit sugerido: `feat(video): montagem do trecho principal com FFmpeg`

---

## Bloco 7 (HAIKU): conferência final e documentação

- Extraia um quadro a cada 2 s do MP4 (`ffmpeg -vf fps=1/2`) em `front/test-results/video/quadros/` e liste os arquivos para o João **olhar um a um**. Procure no código dos dados e nos textos do roteiro: CPF, CNPJ, e-mail, token, senha, nome fora da lista fictícia; reporte o resultado.
- Escreva `docs/roteiros/video-demo.md`: pré-requisitos, comandos (`video:gravar`, `video:montar`), onde saem os arquivos, como regravar uma cena, lista de conferência manual (nenhuma senha, nenhum dado real, 1920×1080, duração, áudio mudo no trecho principal) e o plano B (capturas de `npm --prefix front run capturas`).
- Rode a suíte direcionada do que foi alterado (varredura do bloco 2 e e2e que usam a simulação).

Ponto de parada. Commit sugerido: `docs(video): roteiro de regravação e conferência final`

---

## Ao final

Liste: arquivos criados, tempo e tamanho de cada vídeo, o que falhou ou foi pulado, o que o João precisa conferir à mão antes de enviar.
