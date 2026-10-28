# Roteiro de homologação da S3 — conversão para PDF e visualização de documentos

**Quem executa:** o responsável pelo projeto, manualmente, com arquivos reais de uma base de **desenvolvimento ou homologação**. O desenvolvimento (Claude Code) **nunca** se conecta ao banco real do SIGA, à API de arquivos nem ao SCORP (D27, D34). Tudo o que foi provado até aqui usa arquivos **sintéticos gerados por código**, portas falsas e o LibreOffice 24.2 num container de teste; este roteiro é a primeira prova com planilhas, textos e imagens **reais**.

**O que a S3 entrega e este roteiro confere:** planilhas (XLS, XLSX, ODS), textos (DOC, DOCX, ODT, RTF, HTML) e imagens (PNG, JPG) entram no dossiê convertidos para PDF, em ordem cronológica, **sem cortar colunas nem linhas**; o convertido fica **identificado** no sumário, nos marcadores e na capa; a falha de conversão entra no fluxo de decisão da S2.2 sem reconverter o que já foi feito; o modal **Visualizar** mostra qualquer documento em PDF.

Marque cada passo: `[ ] OK` / `[ ] Falhou — observação:`. Registre o resultado em "Validação da S3" de [`docs/14`](../14-plano-de-sprints.md), preencha [`docs/casos-de-teste.md`](../casos-de-teste.md) (seção "S3") e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que não bater.

> **Dados pessoais e arquivos reais:** neste roteiro, CPF, CNPJ e números de processo ficam como marcadores (`<CPF_DE_TESTE>`, `<CNPJ_DE_TESTE>`, `<REQUERIMENTO_N>`). **Nenhum arquivo real, e nenhum PDF gerado a partir deles, vai para o repositório.** Se precisar guardar amostras (por exemplo, a pior planilha para comparar os modos), use a pasta local `amostras-locais/` na raiz do projeto: ela está no `.gitignore`. Antes de qualquer commit, rode `git status` e confira que nada dela aparece. Os PDFs baixados pelo navegador ficam na pasta de downloads, fora do repositório.

> **Uso do `.env.local`:** o roteiro pede para você **editar** o `.env.local` (variáveis `CONVERSAO_*`). Faça isso você mesmo; não cole o conteúdo do arquivo em lugar nenhum. Linhas do `.env.local` não podem começar com espaço.

## 1. Subir o Compose local e confirmar o conversor

Este roteiro **usa os sistemas reais** (SIGA e API de arquivos de desenvolvimento): suba **sem** o `compose.verificacao.yaml`, só com os dois arquivos abaixo, e com o `.env.local` preenchido como na S2 (passo 4 do roteiro da S2).

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d --build
```

Confirme que o LibreOffice existe na imagem e roda como usuário sem privilégios:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T api sh -c 'id -un; soffice --headless --version'
```

Resultado esperado: `vistas` e `LibreOffice 24.x` (Ubuntu 24.04). Confirme que a conversão está ligada, sem imprimir valores do `.env.local`:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T api sh -c '[ "${CONVERSAO_HABILITADA:-true}" = "true" ] && echo ligada || echo DESLIGADA'
```

Resultado esperado: `ligada`. O conversor **sobe na primeira conversão** (não na subida da aplicação): a linha `Conversor de documentos pronto: 2 processo(s) do LibreOffice` aparece nos logs depois do primeiro **Visualizar** de um documento não PDF (passo 3).

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs api | grep -i "Conversor de documentos"
```

**Memória do container (R42):** o `soffice` chegou a ~700 MB com 2 instâncias; o limite de memória do container da `api` precisa comportar o heap mais isso. Anote o limite em uso (`docker stats --no-stream`, coluna `MEM USAGE / LIMIT`) e confira de novo no passo 7.

- [ ] OK / [ ] Falhou — observação:

## 2. Selecionados ao abrir: planilhas, textos e imagens vêm marcados

Abra, na tela de detalhe, cada um dos 3 requerimentos de [`casos-de-teste.md`](../casos-de-teste.md) e confira na seção Documentos:

| Item | Esperado | OK? |
|---|---|---|
| Convertíveis marcados | planilhas, textos e imagens (extensões do RF13) vêm **selecionados**, junto com os PDFs; a linha traz a etiqueta "será convertido para PDF" | [ ] |
| Aviso no topo | "N documento(s) serão convertidos para PDF e identificados como convertidos no dossiê", com N = quantidade dos convertíveis que o seu perfil pode selecionar | [ ] |
| Sem a antiga promessa | nenhum "entram no dossiê a partir da próxima etapa" nem "conversão disponível em breve" | [ ] |
| Sem conversão | um `txt`, se houver, aparece bloqueado com "conversão indisponível" e no aviso "não têm conversão disponível: ficam fora do dossiê" | [ ] |
| Tipo não informado | documento sem extensão continua selecionável, com "tipo não informado" e o aviso de que o tipo é conferido ao gerar | [ ] |

Confira a contagem de convertíveis do aviso contra o SIGA (uma das seis origens, a `DOCUMENTO_PROCESSO`; some as demais origens pelo passo 5 do roteiro da S2). Substitua `<ID_DO_REQUERIMENTO>` pelo id do requerimento (definido só por você):

```sql
SELECT LOWER(REGEXP_SUBSTR(NOME_ARQUIVO, '[^.]+$')) AS EXTENSAO, COUNT(*) AS N
  FROM SIGA2_LICENCIAMENTO.DOCUMENTO_PROCESSO
 WHERE ID_REQUERIMENTO = <ID_DO_REQUERIMENTO>
 GROUP BY LOWER(REGEXP_SUBSTR(NOME_ARQUIVO, '[^.]+$'))
 ORDER BY N DESC;
```

Resultado esperado: a soma das extensões `xls`, `xlsx`, `ods`, `doc`, `docx`, `odt`, `rtf`, `html`, `png`, `jpg` e `jpeg` (somente documentos com chave válida, vigentes ou não, e que o seu perfil acessa) bate com N do aviso. Duplicados entre origens (mesma chave) contam uma vez (RN05).

- [ ] OK / [ ] Falhou — observação:

## 3. Visualizar: planilha larga, texto e imagem

Escolha **a planilha mais larga** dos casos de teste (mais colunas), **um DOC ou DOCX** e **uma imagem**. Para cada uma, abra o arquivo original no Excel/LibreOffice/Word (fora do Vistas, a partir do SIGA) para comparar, e clique em **Visualizar** na linha.

| Item | Esperado | OK? |
|---|---|---|
| Carregamento | enquanto converte, "Convertendo para PDF…" | [ ] |
| Faixa | "Versão convertida para PDF a partir de XLSX" (ou do formato do arquivo); o original não foi alterado | [ ] |
| **Planilha: colunas** | a **primeira e a última coluna** com conteúdo aparecem; nenhuma coluna cortada ou em folha à parte | [ ] |
| **Planilha: linhas** | a **última linha com conteúdo** aparece (role até o fim do PDF); a quantidade de páginas é compatível com a de linhas | [ ] |
| Planilha: abas | todas as abas **visíveis** aparecem, cada uma em página nova; a aba **oculta** não aparece | [ ] |
| Planilha: ocultos | linhas e colunas ocultas no original não aparecem; texto longo com quebra sai inteiro | [ ] |
| Planilha: legibilidade | o texto é legível ao ampliar; se alguma aba foi para "página única" (escala abaixo de 40%), anote qual | [ ] |
| Texto (DOC/DOCX) | tamanho e orientação do próprio documento (não forçado para A4), acentos corretos, sem texto cortado | [ ] |
| Imagem | uma página, sem distorção, na orientação certa (fotos de celular giradas aparecem em pé) | [ ] |
| Fechar | ao fechar o modal nada fica aberto; abrir de novo um documento já visto responde mais rápido (cache de 10 minutos) | [ ] |

Se algum conteúdo faltar, **anote** o documento (tipo, tamanho, número de colunas/linhas) sem copiar dado nenhum, e abra pendência: é exatamente o defeito que a S3 existe para evitar.

Outros estados do modal (use o que o seu requerimento de teste tiver):

| Item | Esperado | OK? |
|---|---|---|
| PDF | abre direto, sem faixa | [ ] |
| Incluir/Tirar do dossiê | o botão no cabeçalho do modal marca/desmarca a linha na tabela e o contador "N selecionados" acompanha | [ ] |
| Operador e sigiloso | com o perfil Operador, o sigiloso mostra "Este documento é sigiloso e não está disponível para o seu perfil" e **não** faz requisição ao conteúdo (DevTools > Rede: nenhum `/conteudo`) | [ ] |
| Token fora da URL | em DevTools > Rede, a URL de `/conteudo` tem só `requerimento` e `formato`; o token está no cabeçalho `Authorization` | [ ] |
| Erro | com a API de arquivos parada ou o token recusado, mensagem pelo código, sem texto técnico, com "Tentar novamente" | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 4. Gerar o dossiê completo e conferir o PDF

Para cada um dos 3 requerimentos: **Gerar dossiê** com a seleção inicial, **Pré-visualizar** e conferir:

| Item | Esperado | OK? |
|---|---|---|
| Ordem cronológica | os documentos seguem a data da própria origem (sem data ao final), **inclusive os convertidos**, na mesma ordem da tabela | [ ] |
| Sumário | os convertidos têm a nota "(convertido de XLSX)" (ou do formato real) na mesma linha, e a página inicial confere com o documento | [ ] |
| Marcadores | o marcador do convertido traz "(convertido de …)" e leva à página certa | [ ] |
| Nota de rodapé | aba de planilha que foi para página única traz "*" no sumário e a nota explicando | [ ] |
| Capa | "N documentos incluídos de M listados; K convertidos para PDF", com K = convertidos de fato | [ ] |
| Numeração | **uma única** numeração por página, "Página X de Y" no **canto inferior direito**, a partir da 2ª página, inclusive nas convertidas em paisagem e nas giradas; nenhum "Página 1" do Calc nas planilhas; o fundo branco do número não esconde nada do documento | [ ] |
| Documentos não incluídos | a seção lista os desmarcados, indisponíveis e sigilosos, com o motivo; **nenhum** motivo "não é PDF: aguardando conversão" para os convertíveis | [ ] |
| Mesmo resultado do modal | o documento convertido no dossiê tem o mesmo número de páginas e o mesmo conteúdo da versão vista no modal | [ ] |
| Tamanho e rotação | os PDFs originais mantêm tamanho e rotação (nada forçado para A4) | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 5. Falha de conversão: decisão sem reconverter o resto

O SIGA é somente leitura, então use **um destes caminhos** para provocar uma falha de conversão:

- **(A) Documento real que falha:** se algum dos seus requerimentos de teste tem um documento Office **protegido por senha** ou corrompido, gere o dossiê com ele selecionado.
- **(B) Falha forçada por configuração:** no `.env.local`, ponha `CONVERSAO_PAGINAS_MAXIMAS=1` e suba de novo (`docker compose ... up -d`); qualquer documento convertido com mais de 1 página falha com "excede o número máximo de páginas". Ao terminar, **volte o valor** (apague a linha).

Antes de gerar, anote quantas conversões o servidor já fez:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs api | grep -c "Executing local conversion task"
```

| Item | Esperado | OK? |
|---|---|---|
| Falha coletada | a geração vai até o fim; a gaveta mostra "Documentos com problema" com o título e o **motivo da conversão** (por exemplo "documento protegido por senha"); nada é entregue | [ ] |
| Resto intacto | os demais documentos aparecem como obtidos (inclusive os convertidos com sucesso) | [ ] |
| Tentar novamente | só o documento com problema volta a "Obtendo…"; o progresso não volta a zero; **a contagem de conversões acima aumenta no máximo em 1** (o que já foi convertido não é refeito) | [ ] |
| Remover e gerar | o PDF sai sem o documento, com "Documentos não incluídos" e a capa "N incluídos de M listados"; os já convertidos continuam identificados como convertidos | [ ] |
| Sem dados nos logs | os logs de conversão não trazem título, nome de arquivo nem caminho do documento | [ ] |

Com a conversão **desligada** (`CONVERSAO_HABILITADA=false` no `.env.local` e `up -d`): os convertíveis aparecem bloqueados com "conversão indisponível", sem seleção, e o dossiê só leva PDFs, como na S2.2. **Volte para `true`** (ou apague a linha) ao terminar.

- [ ] OK / [ ] Falhou — observação:

## 6. Escolher o modo das planilhas: `LARGURA` × `PAGINA_POR_ABA`

Este é o critério de aceite da S3: **comparar os dois modos na pior planilha** dos casos de teste (a mais larga e/ou a mais comprida) e **escolher** um.

1. Com o modo padrão (`LARGURA`, sem a variável no `.env.local`), gere o dossiê só com essa planilha (desmarque os demais) ou use **Visualizar**. Anote páginas, se alguma aba foi para "página única" e a legibilidade.
2. No `.env.local`, ponha `CONVERSAO_PLANILHA_MODO=PAGINA_POR_ABA`, suba de novo (`docker compose ... up -d`) e repita com a **mesma** planilha.
3. Preencha:

| Critério | `LARGURA` | `PAGINA_POR_ABA` |
|---|---|---|
| Páginas do PDF da planilha | | |
| Todas as colunas e a última linha presentes? | | |
| Legível em tela cheia, sem ampliar? | | |
| Legível ao ampliar? | | |
| Abas em página única (nota do sumário) | | |
| Tamanho de página (cabe em impressora comum?) | | |
| Tempo de conversão | | |

4. **Modo escolhido:** ____________ . **Motivo (uma frase):** ______________________________ .
5. Deixe o `.env.local` com o modo escolhido (ou sem a variável, se for `LARGURA`) e registre a escolha em [`docs/09`](../09-decisoes.md) (nova decisão) e em [`docs/12`](../12-configuracoes.md).

Nota: no modo `LARGURA`, uma aba cuja escala cairia abaixo de `CONVERSAO_PLANILHA_ESCALA_MINIMA` (40%) vai para página única **quando ela cabe** no limite do LibreOffice (~5 m); se não cabe, é paginada na largura, mesmo abaixo da escala mínima. Em qualquer caso o conversor confere que a última linha aparece no PDF e, se não aparecer, falha o documento ("a planilha não coube inteira no PDF"). Se você vir essa falha numa planilha real, anote o documento e as dimensões.

- [ ] OK / [ ] Falhou — observação:

## 7. Medir tempo, tamanho do PDF e uso de disco

Para cada um dos 3 casos, no dossiê completo do passo 4, anote em [`casos-de-teste.md`](../casos-de-teste.md): tempo até "Dossiê pronto", páginas, tamanho do PDF, quantos documentos foram convertidos e o tempo da conversão mais lenta (pelos logs, se precisar).

Durante e logo depois de uma geração grande:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T api du -sh /var/tmp/vistas
docker stats --no-stream
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml top api
```

- **Disco:** na medição sintética o pico foi ~2 vezes a soma dos arquivos (originais + convertidos + PDF final); compare com o seu.
- **Memória:** `docker stats` deve ficar abaixo do limite do container (R42); anote o maior valor de `MEM USAGE`.
- **Processos:** o `top` mostra o pool do LibreOffice vivo (até `CONVERSAO_PROCESSOS`, padrão 2) enquanto a `api` roda; depois de `docker compose ... down` (sem `-v`) não pode sobrar nenhum `soffice` no host.
- **Referência sintética (sem dado real, docs/10):** XLSX de 30 mil linhas x 30 colunas em ~16 s (177 páginas); DOCX de 200 páginas em ~1 s; JPG de 20 MP em 0,3 s; dossiê misto de 20 documentos (321 MB) em ~8 s; heap até 34% de 768 MB; `soffice` até ~700 MB. Valores muito diferentes dos seus indicam um arquivo real mais pesado que o sintético: anote o tipo e abra pendência.

- [ ] OK / [ ] Falhou — observação:

## 8. Conferências finais

| Item | Esperado | OK? |
|---|---|---|
| Quadro do topo | a altura do quadro do topo do detalhe não muda ao abrir/fechar o modal e a gaveta (A1, roteiro da S2 §9) | [ ] |
| Logs | `docker compose ... logs api` sem token, URL da API de arquivos, chave de arquivo nem nome de documento | [ ] |
| Repositório | `git status` sem arquivo de amostra, PDF ou dado real | [ ] |
| `.env.local` | sem sobras de `CONVERSAO_PAGINAS_MAXIMAS=1`, `CONVERSAO_HABILITADA=false` ou `CONVERSAO_TIMEOUT_SEGUNDOS` de teste | [ ] |

- [ ] OK / [ ] Falhou — observação:

## 9. Registro do resultado

Preencha a seção **"Validação da S3"** de [`docs/14`](../14-plano-de-sprints.md) (requerimento, tipos convertidos, nada cortado?, "convertido de" presente?, dossiê, páginas, tempo, bate/não bate), anote os casos em [`casos-de-teste.md`](../casos-de-teste.md) e abra pendência em [`docs/10`](../10-pendencias-e-riscos.md) para o que não bater (R41 e R42 são os dois pontos que esta homologação confirma ou encerra). Registre o modo de planilha escolhido em [`docs/09`](../09-decisoes.md) e [`docs/12`](../12-configuracoes.md).
