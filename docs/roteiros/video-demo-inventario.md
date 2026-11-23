# Vídeo de demonstração: inventário do ambiente e da cobertura da simulação (V1, bloco 1)

Só leitura. Nada foi alterado além deste relatório.

## Ambiente

| Item | Situação |
|---|---|
| Node | v22.19.0 |
| npm | 10.9.3 |
| Playwright | 1.63.0 (`@playwright/test` fixo no `front/package.json`) |
| Chromium do Playwright | instalado (`chromium-1255` e `chromium_headless_shell-1255` em `ms-playwright`; há também a 1243 antiga) |
| ffmpeg interno do Playwright | instalado (`ffmpeg-1013`), serve só ao `recordVideo` (WebM VP8); não codifica H.264 e não está no PATH |
| **FFmpeg / ffprobe do sistema** | **FALTAM** (`ffmpeg` e `ffprobe`: comando não encontrado) |

**O que falta e como instalar de graça:** FFmpeg (gratuito, código aberto). No Windows: `winget install Gyan.FFmpeg` (ou baixar o build "essentials" em gyan.dev/ffmpeg/builds e pôr a pasta `bin` no PATH); depois, reabrir o terminal e conferir `ffmpeg -version` e `ffprobe -version`. Só é necessário a partir do bloco 6 (montagem, H.264 sem áudio, `ffprobe`) e do bloco 7 (quadros). Não foi instalado: depende da confirmação do João.

## Cobertura da simulação por cena

Há três mecanismos: `front/e2e/api-simulada.ts` (`simularApi`, só login, `/api/busca` vazia e 404 para o resto), `front/e2e/simulacao-completa.ts` (`simularTudo`, usada pelas capturas) e simulações locais dentro de cada `*.e2e.ts`. A "cobertura" abaixo considera o que existe em qualquer um deles; só o que está em `simularTudo` serve direto ao gravador.

| Cena | Cobertura | Prova |
|---|---|---|
| Abertura, Gestor autenticado | **Sim** | `api-simulada.ts`: `GESTOR` (`verResponsavelTarefa`, `verPainel` verdadeiros); sessão por `sessionStorage 'rastro.token'` em `addInitScript` (linhas 96 a 98) e `/api/auth/me` (`simulacao-completa.ts` l. 262) |
| Painel do Gestor (3 gráficos) | **Parcial** | `simularTudo` **não** responde `/api/painel` (cairia em 404). Só `painel.e2e.ts` l. 40 simula com `page.route('**/api/painel', ...)`; falta o corpo da demonstração |
| Consulta por `9101/2026` | **Parcial** | `/api/busca` devolve sempre lista vazia (`simulacao-completa.ts` l. 225; `api-simulada.ts` l. 135); só `tramitacao.e2e.ts` l. 186 e `financeiro.e2e.ts` l. 104 devolvem resultado, cada um com os próprios dados |
| Detalhe do requerimento | **Parcial** | só o id `101` (`/api/requerimentos/101`, `REQUERIMENTO`); sem os ids dos processos 9101 a 9104 |
| "Onde está agora" | **Parcial** | `/api/requerimentos/101/posicao` com um responsável do tipo AREA (`POSICAO`); `multiInstancia: null`. O caso de vários analistas existe só em `onde-esta-agora.e2e.ts` l. 58 |
| Tramitação (passagens, fluxograma) | **Parcial** | `TRAMITACAO` do 101 tem 2 passagens, sem `final`, sem concluído. Fluxograma (`app-fluxograma-simplificado`) é coberto por `tramitacao.e2e.ts` com dados próprios |
| Taxas (vencida, marca e aviso) | **Parcial** | `FINANCEIRO` do 101 é `QUITADO`, `vencida: false`. Taxa vencida só em `financeiro.e2e.ts` e `financeiro-tramitacao.e2e.ts` (dados locais) |
| Dossiê: gerar e mostrar o PDF | **Parcial** | `/api/dossies` (GET) devolve a lista (`DOSSIES`), mas **POST, status e `/arquivo` não** estão em `simularTudo`. Existem em `documentos-dossie.e2e.ts` (l. 60 a 87) com PDF mínimo |
| Troca para o Interessado (Operador) | **Parcial** | `OPERADOR` existe (`restritoAoInteressado`, `verResponsavelTarefa: false`, `verPainel: false`) e o login troca de usuário (`POR_LOGIN`). Falta um processo cujo interessado seja o Operador e a busca filtrada |
| Caixa de entrada do analista | **Não existe** (por decisão; não simular) | |

## Processos simulados para cada caso

| Caso | Existe pronto? | O que falta criar |
|---|---|---|
| (a) concluído, com "Verificação da revisão" como "Concluído" | **Não** em `simularTudo`. `tramitacao.e2e.ts`/`financeiro-tramitacao.e2e.ts` têm passagens com `final`, mas com dados próprios | Processo `9101/2026` (id novo) com tramitação completa, `final` e `processoConcluido`, e o posicionamento coerente |
| (b) análise com vários analistas | Só `multiInstancia` em `onde-esta-agora.e2e.ts` (l. 58), local ao teste | `9102/2026` com `multiInstancia` de 3 analistas (2 concluíram, 1 pendente), nomes e papéis fictícios |
| (c) taxa vencida | Só em `financeiro.e2e.ts` e `financeiro-tramitacao.e2e.ts`, locais | `9103/2026` com lançamento `vencida: true`, aviso e marcas na tramitação |
| (d) processo do Operador (interessado) | **Não** | `9104/2026` com interessado igual ao `cpfCnpj` do `OPERADOR` da demonstração (hoje `<CPF_DE_TESTE>`; usar valor fictício evidente) e busca respeitando a RN07 |

Também faltam: corpo do painel (12 meses, fila por etapa, 5 a 8 servidores), `/api/painel` e `/api/painel/atualizacao` na simulação da demonstração, e as rotas de dossiê (POST, GET do pedido, `/arquivo`).

## PDF do dossiê na simulação

Hoje **não há** PDF no `simularTudo`: `/api/dossies/{id}/arquivo` cai em 404. Em `documentos-dossie.e2e.ts` (l. 10) o PDF é uma **string mínima escrita à mão** (`%PDF-1.4 ... 1 página`), devolvida com `Content-Type: application/pdf` (l. 81 a 87); serve para o teste, mas é quase vazio. Para o vídeo mostrar o dossiê abrindo, o bloco 2 deve gerar por script um **PDF pequeno e fictício** de algumas páginas (uma com tabela) e servi-lo nessas rotas.

## Riscos e observações para os próximos blocos

- Ao tocar `simularTudo`, não alterar o que os testes usam: criar o conjunto da demonstração em arquivo novo (`front/e2e/video/dados-demonstracao.ts`) que reaproveita as funções.
- A faixa `9101` a `9104/2026` está no formato `N/AAAA`; confirmar que é fictícia (regra 2 do prompt).
- `GESTOR.perfil` é `OPERADOR_SIGILO` com descrição "Gestor": é o perfil certo para a cena do Gestor.
- O `recordVideo` do Playwright grava WebM; o H.264 do trecho final depende do FFmpeg do sistema (bloco 6).
