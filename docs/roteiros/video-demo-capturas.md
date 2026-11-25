# Vídeo de demonstração: lista de conferência das capturas (V1, bloco 3)

Capturas 1920x1080 das cenas, com os dados fictícios do bloco 2. Nada foi corrigido: abaixo está só o que foi observado, para o João decidir.

Como regerar: `node tools/video-demo/capturar.mjs` (ou `npm --prefix front run video:capturar`). Saída: `front/test-results/video/capturas/` (fora do Git). Os arquivos `*-completa.png` são a página inteira; os outros são só o que cabe na tela de 1920x1080 (o que o vídeo mostrará).

## Lista por imagem

| Arquivo | Deve aparecer | Observado |
|---|---|---|
| `01-abertura.png` | Consulta autenticada como Gestor Demo | Não revisada em detalhe (tela inicial de consulta). A legenda "Dados fictícios, nenhum processo real" ainda não existe: entra no gravador (bloco 5). |
| `02-painel.png` | Os três gráficos | Na tela de 1080 px só o primeiro gráfico (protocolados x concluídos) aparece; a imagem de paisagem ocupa mais de um terço da altura e empurra "Fila por etapa" e "Carga por responsável" para baixo da dobra. A cena de 20 s precisará de rolagem. Gráfico 1 sem problema (barras, rótulos e legenda legíveis; dezembro com 0 e 0). `02-painel-completa.png` mostra os três (conferir a fila e a carga ali). |
| `03a-consulta-digitada.png`, `03b-consulta-resultado.png` | Busca por `9101/2026` e lista de resultados | `03b` mostra o **detalhe** do 9101/2026 já aberto, não a lista: a tela de consulta abre direto o processo quando só há um resultado. A cena "abrir o processo" ocorre sozinha; a lista de resultados não aparece no vídeo. |
| `04a-detalhe-concluido.png` (+ `-completa`) | Concluído, com "Verificação da revisão" como "Concluído" | "Onde está agora" mostra "Concluído / Processo concluído" e o aviso "Processo concluído; a verificação da revisão é só arquivamento". Porém, na Tramitação, a última passagem "Verificação da revisão do processo" aparece como **"Etapa atual"**, **"Em andamento"** e com a marca "Processo concluído", e não como "Concluído" (só o rodapé diz "Concluído em 18/10/2026"). Isso diverge do roteiro ("aparece como Concluído"). Também: o cartão "Tarefa atual" traz o texto "Sem tarefa ativa. A instância pode estar encerrada ou o requerimento ainda não foi enviado ao fluxo", que fica estranho num processo concluído; e o aviso do "Onde está agora" aparece em tom de alerta (rosado) apesar de ser informativo. A Tramitação do concluído não mostra o fluxograma simplificado (só a lista); o do 9102 mostra. A análise do 9101 lista os 3 analistas, todos "Concluiu". |
| `04b-detalhe-varios-analistas.png` (+ `-completa`) | Vários analistas, cada um concluiu ou pendente; responsável; fluxograma | Bom: "2 de 3 concluídos", Analista Exemplo 01 e 02 "Concluiu", 03 "Pendente"; fluxograma simplificado com "Etapa atual" em destaque. Reparos: o "Nº do requerimento" aparece como `9002/2026` (diferente do processo `9102/2026`; parece inconsistente na mesma tela); "Tarefa atual" repete "Sem tarefa ativa..." apesar de haver análise em andamento; "há 48 dias" depende do relógio fixo (23/11/2026 menos 06/10/2026); "Quitado" nas taxas. |
| `04d-detalhe-9104-visto-pelo-gestor.png` (+ `-completa`) e `07b-interessado-detalhe.png` (+ `-completa`) | Mesma tela, Gestor e Interessado, para comparar | Comparação correta: o Gestor vê "Conferente Exemplo - Conferente" e "Analista Exemplo 04"; o Interessado vê só o papel ("Conferente", "Analista"). Menu do Operador sem "Painel". Dados do cabeçalho do 9104 (Interessado Exemplo, CNPJ 98.765.432/0001-00) são fictícios. Mesmo ponto do `9004/2026` no número do requerimento. O fluxograma do 9104 diz "0 de 1 concluídos" na etapa atual. |
| `05-taxa-vencida.png` | Taxa vencida, com marca e aviso | Bom: marca "Taxa vencida em 05/11/2026" na tramitação, selo "Vencido", faixa amarela de aviso e linha "Aguardando pagamento / Taxa vencida". Reparo: o texto do aviso, "O SIGA ainda não marca a taxa como vencida", é explicação técnica interna; pode soar estranho numa demonstração. A captura mostra a passagem anterior cortada no topo da tela pelo menu fixo ("Conferência dos documentos" encostada na barra). |
| `06a-dossie-lista-gestor.png` | Lista de documentos e botão Gerar dossiê | Não revisada em detalhe. Na página inteira, a barra de seleção ("3 selecionado(s) / Gerar dossiê") **cobre o título "Documentos" e o campo de busca** (barra fixa sobreposta); só descola quando se rola até a lista. |
| `06b-dossie-pronto-gestor.png` | Dossiê pronto | Gaveta "Dossiê pronto" correta (3 itens "Obtido", 4 páginas). Reparo: a mensagem "O arquivo fica disponível até 01/01/2099 00:00 (restam 37924680 min)" é artefato da simulação (`expiraEm` em 2099) e fica absurda no vídeo; trocar por algo como 1 hora à frente do relógio fixo. |
| `06c-dossie-pdf-aberto.png` | PDF do dossiê aberto no visualizador | **Falha**: o modal "Pré-visualização do dossiê" abre, mas a área do PDF está **vazia (marrom)**. O Chromium em modo headless não traz o visualizador de PDF embutido, então o `iframe` com o Blob não desenha. O PDF em si é válido (teste do bloco 2). Para o vídeo mostrar o dossiê abrindo é preciso gravar com navegador com visualizador (Chromium com cabeça, `channel: 'chrome'` ou Edge, se instalado) ou usar outro recurso (renderizar as páginas do PDF para imagem e mostrá-las); decisão do João. |
| `07a-interessado-consulta.png` | Interessado na consulta | Não revisada em detalhe. |
| `07c-interessado-dossie-pronto.png` | Dossiê do Interessado pronto | Mesma gaveta de `06b`, com o mesmo problema da validade em 2099. |

## Resumo do que o João precisa decidir

1. Verificação da revisão como "Concluído" no concluído (hoje "Etapa atual / Em andamento"): ajustar os dados do 9101 ou aceitar o que a tela mostra.
2. Visualizador de PDF vazio na gravação headless (06c).
3. Número do requerimento `9002/2026` e `9004/2026` diferentes do processo.
4. Painel: três gráficos só com rolagem (cena de 20 s).
5. Validade do dossiê em 2099 e textos "Sem tarefa ativa..." e "O SIGA ainda não marca...".
6. Consulta pula a lista e abre o processo direto.

## Arquivos gerados

Pasta: `D:\Projeto\siga-vistas\front\test-results\video\capturas\`

`01-abertura`, `02-painel`, `02-painel-completa`, `03a-consulta-digitada`, `03b-consulta-resultado`, `04a-detalhe-concluido`, `04a-detalhe-concluido-completa`, `04b-detalhe-varios-analistas`, `04b-detalhe-varios-analistas-completa`, `04d-detalhe-9104-visto-pelo-gestor`, `04d-detalhe-9104-visto-pelo-gestor-completa`, `05-taxa-vencida`, `06a-dossie-lista-gestor`, `06b-dossie-pronto-gestor`, `06c-dossie-pdf-aberto`, `07a-interessado-consulta`, `07b-interessado-detalhe`, `07b-interessado-detalhe-completa`, `07c-interessado-dossie-pronto` (todos `.png`).
