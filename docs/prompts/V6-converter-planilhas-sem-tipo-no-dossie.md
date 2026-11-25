# V6: planilhas sem tipo informado falham no dossiê em vez de serem convertidas

**Tipo:** correção, em 3 blocos pequenos (o Bloco 0 é só manutenção). **Modelo: Haiku no Bloco 0, Sonnet nos demais.** Não use Opus.
**Ramo:** o que estiver ativo (eu confiro antes de commitar).

## Blocos e sessões

| Bloco | Título | Sessão |
|---|---|---|
| 0 | Espelho do padrão de módulos em `.claude/` | HAIKU |
| 1 | Converter por tipo detectado quando o tipo não foi informado | SONNET |
| 2 | Mensagem de documentos que não puderam ser obtidos | SONNET |
| 3 | Gráficos de planilha somem no PDF | SONNET |

## O problema (observado em produção, requerimento 70402)

Ao gerar o dossiê, 16 documentos falharam com a mensagem "o arquivo não é um PDF (tipo detectado: documento do Office antigo (doc, xls))". Todos são **planilhas do Office (xls)** sem extensão no nome e **sem tipo informado** na origem (por exemplo "Pivôs R1_Scheffer_V1", "Poços R2_Scheffer_V1", "Formulario_XX_Pivos_R1_Santa_Tereza"). O sistema já converte para PDF as planilhas cujo **nome termina em `.xls` ou cujo tipo é conhecido** (8 documentos desse requerimento foram marcados "será convertido para PDF"), mas **não converte** quando só descobre que é Office **depois de baixar**. Esse é exatamente o defeito que o SIGRASTRO existe para resolver (planilhas dentro do PDF final).

A tela já se comporta bem no resto: ela mostra os documentos que falharam, mantém os 126 que deram certo e **não entrega nada sem decisão do usuário**. Não mude esse comportamento.

Mensagem hoje (erros de formato, para o Bloco 2): `Pivôs R1_Scheffer_V1o arquivo não é um PDF (...)`. Falta separador entre o nome e o texto, e o mesmo nome aparece várias vezes sem nada que os diferencie.

## Regras

1. **Não rode comandos git**, exceto `git diff`. Não faça commit, push, checkout nem stash. Quem commita sou eu.
2. **Nenhum dado real.** Os testes usam **arquivos sintéticos** gerados no próprio teste ou em fixtures criadas por você (um `.xls` pequeno, um `.xlsx` pequeno, um `.doc` e um PDF válido). Não copie nada de `amostras-locais/`.
3. Não altere as regras de segurança: política de acesso, sigilo, ticket de uso único, restrições de conversão. A conversão continua só pelo caminho que já existe (LibreOffice com as restrições atuais).
4. Não suba containers. Não abra `.env.local`.
5. Cada bloco termina em **ponto de parada** com mensagem de commit sugerida. Pare e espere.

---

## Bloco 0 (HAIKU): espelho do padrão de módulos em `.claude/`

O arquivo `docs/padroes/modulos-e-dependencias.md` é a fonte. Existe uma cópia em `.claude/skills/vistas-banco/references/modulos-e-dependencias.md` que está **diferente** da fonte (ignore apenas diferença de fim de linha CRLF/LF).

- Compare os dois com `git diff --no-index --ignore-space-at-eol` (ou equivalente) e me diga em 2 linhas o que difere.
- Sobrescreva a cópia em `.claude/...` com o conteúdo da fonte em `docs/padroes/`. A fonte **não** muda.
- Confirme que não há outros pares fonte/espelho desalinhados entre `docs/padroes/` e `.claude/skills/*/references/` (só liste, não altere).

**Ponto de parada.** Commit sugerido: `docs(skills): espelha modulos-e-dependencias em .claude`

---

## Bloco 1 (SONNET): converter por tipo detectado quando o tipo não foi informado

**Antes de mexer**, ache e explique em poucas linhas onde o sistema decide "converte" ou "é PDF", e onde nasce o erro "o arquivo não é um PDF (tipo detectado: ...)". Diga quais tipos detectados existem hoje e quais são convertíveis.

**O que fazer**

- Quando o documento não tem tipo informado (ou o tipo declarado não é conclusivo) e o **conteúdo baixado** é detectado como Office convertível, **converter para PDF** pelo mesmo caminho dos documentos conhecidos, em vez de falhar. Tipos a cobrir: `xls` e `doc` (Office antigo), `xlsx`, `docx`. Se já existe suporte a mais tipos convertíveis para documentos com tipo conhecido, reutilize **a mesma lista**, sem criar uma segunda.
- O documento convertido deve ser **identificado como convertido** no dossiê e na lista de itens do dossiê, igual aos já convertidos hoje (nome com `.pdf`, marca de convertido, e a ordem cronológica de sempre).
- Se a conversão **falhar** (arquivo corrompido, conversão com erro, tempo esgotado), cair no fluxo atual: o documento aparece em "não puderam ser obtidos", com o motivo, e **a entrega continua dependendo da decisão do usuário**.
- Tipos não convertíveis (imagem, `zip`, desconhecido) continuam falhando com a mensagem atual. Não converta nada que não esteja na lista.
- Não rebaixe a segurança: a detecção continua sendo pelo **conteúdo**, nunca só pela extensão; o arquivo continua sendo tratado como não confiável.
- Não rebaixar o limite de tamanho, o tempo máximo nem a concorrência de conversão.

**Testes (obrigatórios, todos com arquivos sintéticos)**
1. `xls` sem extensão e sem tipo informado → converte, sai como convertido no dossiê.
2. `doc`, `xlsx`, `docx` sem tipo informado → convertem.
3. PDF válido sem tipo informado → continua passando sem conversão.
4. Arquivo desconhecido ou `zip` sem tipo informado → continua falhando, com a mensagem atual.
5. `xls` corrompido → vai para "não puderam ser obtidos", e o dossiê **não é entregue** sem decisão.
6. Um documento com tipo informado como PDF mas conteúdo Office → comportamento atual preservado (diga qual é e mostre o teste).
7. Os documentos já convertidos antes (nome `.xls`) continuam saindo iguais.

Rode os testes direcionados (da conversão e do dossiê). **Rode também a suíte do back-end completa**, porque este código é compartilhado, e informe quantos passaram, falharam e foram pulados, **explicando cada pulado** (por exemplo, testes de LibreOffice que só rodam onde há `soffice`).

**Ponto de parada.** Commit sugerido: `fix(dossie): converte Office detectado pelo conteúdo quando o tipo não foi informado`

---

## Bloco 2 (SONNET): mensagem de documentos que não puderam ser obtidos

- Na lista "Estes documentos não puderam ser obtidos", **separar o nome do motivo** (por exemplo `Pivôs R1_Scheffer_V1: o arquivo não é um PDF (...)`) e **mostrar o ID do documento** ao lado do nome, para diferenciar documentos com o mesmo nome (esta lista repete vários "Poços R1_Scheffer_V1"). O ID já aparece na lista de documentos da tela, então não é dado novo.
- Se a mesma falha, com o mesmo motivo, ocorre em vários documentos, **não omita nenhum**: cada documento continua em uma linha própria.
- Não mostre CPF, token, caminho interno nem nome de arquivo do servidor.
- Manter acessibilidade (a lista continua legível por leitor de tela) e a regra de não entregar sem decisão.

**Testes:** spec do componente (separador e ID presentes, duas linhas com o mesmo nome ficam distintas) e, se já existe e2e dessa tela, ajuste-o.

**Ponto de parada.** Commit sugerido: `fix(front): lista de documentos não obtidos com separador e ID`

---

## Bloco 3 (SONNET): gráficos de planilha somem no PDF

Causa já confirmada em teste (não refaça a investigação, só confira): o perfil-modelo do LibreOffice criado em `PerfilSeguro` grava `DisableActiveContent = true`. Com essa opção, o LibreOffice não desenha os gráficos das planilhas XLSX (o Excel guarda gráficos como objetos embutidos). Os dados e o resto da aba saem normais; só os gráficos desaparecem. Sem essa opção, na mesma planilha, os 13 gráficos saem. Todas as outras proteções (macro nunca executada, vínculo nunca atualizado, `UpdateDocMode.NO_UPDATE`, proxy morto) não têm esse efeito.

Caso real: planilha XLSX com uma aba só de gráficos de linha (13 gráficos) sobre dados de outra aba, sem valores em cache. No PDF, a aba aparece com o título e uma tabelinha, mas sem nenhum gráfico.

**O que fazer**

1. Confirme com um teste que reproduz (veja abaixo): com o perfil atual o gráfico some; sem `DisableActiveContent`, aparece.
2. Não remova a proteção para todos os arquivos. Crie um segundo perfil-modelo sem `DisableActiveContent` (as demais opções iguais) e use-o só para planilha XLSX/ODS cujo pacote não contém conteúdo ativo. Antes de escolher o perfil, confira a lista de entradas do pacote ZIP (já há `ZipLimitado`; reuse a leitura) e use o perfil estrito (o atual) se existir qualquer uma destas entradas: `vbaProject.bin`, `*/embeddings/*`, `*oleObject*`, `externalLinks/*`, `activeX/*`, ou relações com `TargetMode="External"`. Gráficos (`xl/charts/*`, `xl/drawings/*`) não contam como conteúdo ativo. Arquivos XLS binário, DOC, DOCX, ODT, RTF e HTML continuam sempre no perfil estrito.
3. Sem gerar dois pools à toa: o segundo pool só é criado na primeira planilha que precisar, e respeita o mesmo limite de processos, tempo limite e encerramento do pool atual. Se isso exigir mudança grande, pare e descreva as opções (um pool por perfil, ou um pool que troca de perfil) com custo e risco, sem implementar.
4. Quando o perfil estrito for usado numa planilha e ela tiver gráficos ou desenhos, o resultado deve dizer isso no aviso de conversão da tela ("os gráficos desta planilha não foram incluídos"), em vez de sair em silêncio. Se detectar os gráficos exigir leitura que hoje não existe, use a mesma lista do pacote (entradas `xl/charts/*` ou `xl/drawings/*`).
5. Registre em `docs/09` (decisão), `docs/06` (planilhas e gráficos) e `docs/10` (risco novo: conversão sem `DisableActiveContent` para pacote sem conteúdo ativo; item para revisão de segurança na primeira sprint pós-apresentação).

**Testes (arquivos sintéticos criados no teste ou em fixtures suas; nada de `amostras-locais/`)**

- Uma planilha XLSX mínima com uma aba de gráfico de linha apontando para dados de outra aba, com cache vazio (como no caso real). Gere com a biblioteca que o projeto já usa em testes; se não houver, monte o pacote OOXML mínimo como recurso de teste. Verifique que o PDF contém o título do gráfico como texto.
- Perfil estrito: planilha sintética com `vbaProject.bin` (arquivo qualquer, sem macro real), outra com `embeddings/oleObject1.bin` e outra com link externo, cada uma usa o perfil estrito e nada de macro ou OLE é executado. A conversão continua funcionando para o texto das células.
- Os testes de `PerfilSeguro` existentes continuam passando, e um novo prova que o perfil estrito mantém `DisableActiveContent` e o novo perfil não a tem.
- Regressão: planilha comum sem gráficos converte igual a antes (mesmo número de páginas).
- Se testes de conversão dependem do LibreOffice instalado, siga o padrão de pulo que já existe.

Fora deste bloco (registre em `docs/10`): gráficos grandes são partidos entre páginas no modo `LARGURA` (altura livre, o Calc quebra a página no meio do gráfico); isso é limitação do modo e não perda de dados.

**Ponto de parada.** Commit sugerido: `fix(conversao): desenha gráficos de planilhas sem conteúdo ativo`

---

## Ao final

Responda com: onde estava a decisão que causava o erro; o que mudou; os números da suíte completa; e **o que eu devo conferir manualmente** no requerimento 70402 (que os 16 documentos aparecem convertidos no dossiê e nenhum some).
