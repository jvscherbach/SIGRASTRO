# S3: conversão de planilhas, textos e imagens para PDF e visualização de documentos

> **Forma de trabalho:** a sprint é dividida em **10 blocos**, cada um terminando em um **ponto de commit**. Duas sessões do Claude Code (**OPUS** e **SONNET**) recebem **este mesmo prompt**; cada uma executa **somente os blocos atribuídos ao seu modelo**. O responsável alterna as sessões, revisa e commita cada bloco.

## Contexto

Na S2 só PDFs entram no dossiê. Os demais formatos aparecem na lista bloqueados ("conversão disponível em breve") ou, quando o tipo é desconhecido, falham ao baixar ("o arquivo não é um PDF"). A S3 faz **planilhas, textos e imagens entrarem no dossiê já convertidos para PDF** e cria o **modal de visualização** de documentos.

O dossiê responde a **processos jurídicos**: a conversão não pode cortar colunas, esconder conteúdo nem alterar a ordem; o que foi convertido deve ficar **identificado** como tal no PDF.

Pontos já decididos (leia `docs/02`, `03`, `06`, `07`, `08`, `13` e `14` antes de começar):

- Tipos (RF13): XLS, XLSX, ODS (planilhas); DOC, DOCX, ODT, RTF e HTML (textos); PNG, JPG e JPEG (imagens). Geometrias (SHP, KML, KMZ) e compactados (ZIP, RAR, 7Z) **não entram** (RN10 e RN11).
- Conversão por **LibreOffice headless via JODConverter** (`docs/03`); as imagens **não** passam pelo LibreOffice (PDFBox).
- A S2.2 definiu falhas coletadas, `PENDENTE_DECISAO`, continuação sem baixar de novo e seção "Documentos não incluídos". A conversão entra nesse modelo: **falha de conversão é falha do documento**, e o que já foi convertido não é refeito.
- O LibreOffice (writer e calc, sem interface) já está instalado no `api/Dockerfile`.

## Regras desta sprint

- **Você não executa nada contra o SIGA real nem contra a API de arquivos real.** Arquivos de teste: **somente sintéticos**, gerados por código (nada de arquivo real, nome real, CPF/CNPJ ou dado pessoal em teste, doc ou repositório).
- Nunca abra, imprima ou copie o `.env.local`.
- **Verificação com Docker:** suba **sempre** com `compose.yaml`, `compose.local.yaml` e `compose.verificacao.yaml`. Antes de qualquer teste manual, confirme **dentro do container** que `SIGA_DB_URL`, `SIGA_DB_USER`, `SIGA_DB_PASSWORD`, `LICENCIAMENTO_API_URL`, `LICENCIAMENTO_API_TOKEN`, `LICENCIAMENTO_API_TOKEN_FILE`, `LICENCIAMENTO_API_COOKIE_NAME` e `SCORP_API_URL` estão vazias (só "vazia" ou "preenchida"). Nunca `docker compose down -v`.
- **Nada pago** (regra permanente do `CLAUDE.md`): toda biblioteca e ferramenta nova precisa ser **gratuita e de código aberto com licença compatível** (registre nome, versão e licença em `docs/13`). Não use componente comercial nem de licença copyleft forte incompatível (por exemplo iText em AGPL); se tiver dúvida, não use e registre em `docs/10`.
- **Não execute commit, push, branch, stash nem nenhum comando git além de `git diff`** (e `git diff --stat`). Quem revisa e commita é o responsável.

## Protocolo das duas sessões

1. **Identifique a sessão:** a primeira mensagem do responsável traz `SESSÃO: OPUS` ou `SESSÃO: SONNET`. Sem essa linha, pergunte e espere; não deduza pelo seu nome de modelo.
2. **Descubra onde parou:** leia `docs/progresso/s3.md`. Se não existir, nenhum bloco foi feito (bloco atual = 1). O primeiro bloco **não concluído** é o atual.
3. **O bloco é seu?** Se sim, execute só ele. Se não, **não faça nada nem altere arquivo** e responda: `PRÓXIMO BLOCO: N (<título>), atribuído à sessão <X>. Volte para essa sessão e envie o mesmo prompt com "SESSÃO: <X>".`
4. **Ao concluir o bloco:**
   - deixe o repositório **íntegro** (compila, testes do bloco e os existentes verdes; um bloco não pode deixar o projeto quebrado);
   - atualize a documentação **da própria mudança** (só o trecho do bloco);
   - marque o bloco em `docs/progresso/s3.md` (crie no bloco 1, com a tabela dos 10 blocos); a marcação entra no mesmo commit;
   - rode `git diff --stat` e responda:

```
BLOCO N CONCLUÍDO (sessão <OPUS|SONNET>): <título>
Testes: <rodaram / falharam / pulados>
Arquivos: <resumo do git diff --stat>
Mensagem de commit sugerida (Conventional Commits, português):
  <tipo>(<escopo>): <resumo de até 72 caracteres>

  <corpo curto: o que muda e por quê>
PRÓXIMO BLOCO: N+1 (<título>), sessão <OPUS|SONNET>.
```

5. **Pare.** Mesma sessão no próximo bloco: espere "continuar". Outra sessão: a atual termina ali.

Tipos de commit permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `chore`, `ci`.

## Blocos e sessões

| Bloco | Título | Sessão | Por quê |
|---|---|---|---|
| 1 | Domínio: tipo real do arquivo, seleção e parâmetros de conversão | **SONNET** | Regras claras, sem infraestrutura |
| 2 | Conversor LibreOffice: infraestrutura, segurança, textos e HTML | **OPUS** | Processo externo, documentos não confiáveis, isolamento |
| 3 | Planilhas: escala, orientação, abas ocultas, modos | **OPUS** | Parte mais frágil e de maior risco de perda de conteúdo |
| 4 | Imagens PNG e JPG para PDF (PDFBox) | **SONNET** | Escopo pequeno e conhecido |
| 5 | Conversão no pipeline do dossiê | **OPUS** | Estado, concorrência, retomada, identificação do convertido |
| 6 | Endpoint de conteúdo para o modal | **OPUS** | Acesso, sigilo, cache, vazamento |
| 7 | Front: "Visualizar" e modal | **SONNET** | Interface a partir de contrato pronto |
| 8 | Testes hostis e conversão no CI | **OPUS** | Segurança e qualidade da conversão |
| 9 | Medição de desempenho | **SONNET** | Medir e registrar |
| 10 | Roteiro de homologação e fechamento | **SONNET** | Documentação |

---

## Bloco 1 (SONNET): domínio, seleção e parâmetros

**Commit sugerido:** `feat(documentos): tipo real detectado pelo conteúdo e seleção dos formatos convertíveis`

- **Detecção do tipo real** (`documento.domain`, função pura sobre os primeiros bytes e a extensão): PDF (`%PDF-`), PNG, JPEG, RTF (`{\rtf`), HTML (texto com `<html`/`<!doctype`), OLE2 (cabeçalho `D0CF11E0A1B11AE1`: XLS ou DOC antigos), ZIP (`PK`): distinguir OOXML (`xl/`, `word/` na lista de entradas) e ODF (`mimetype` com `opendocument.spreadsheet`/`text`) de ZIP genérico (**compactado**). Resultado: `TipoReal` (`PDF`, `PLANILHA`, `TEXTO`, `IMAGEM`, `COMPACTADO`, `DESCONHECIDO`), com a extensão sugerida para a conversão. Não leia o arquivo inteiro.
- **Seleção (RN13 reescrita):** documento de **tipo convertível** (planilha, texto, imagem, por extensão conhecida) deixa de receber o bloqueio `NAO_PDF` e passa a ser **selecionável e pré-selecionado**. `DESCONHECIDO` continua selecionável; o tipo é conferido ao baixar. Geometrias e compactados continuam fora da lista.
- **Chave de segurança:** `CONVERSAO_HABILITADA` (padrão `true`). Com `false`, ou quando o conversor está indisponível, vale o comportamento da S2.2 (bloqueio `NAO_PDF`, motivo "conversão indisponível").
- **Porta `ParametrosDaConversao`** (aplicação), implementada em `config` por variáveis de ambiente, no mesmo padrão de `ParametrosDoDossie`: `CONVERSAO_HABILITADA`, `CONVERSAO_TIMEOUT_SEGUNDOS` (padrão 300), `CONVERSAO_PROCESSOS` (padrão 2), `CONVERSAO_TAREFAS_POR_PROCESSO` (padrão 50), `CONVERSAO_PLANILHA_MODO` (`LARGURA` ou `PAGINA_POR_ABA`, padrão `LARGURA`), `CONVERSAO_ABAS_OCULTAS` (padrão `false`, isto é, ocultas ficam fora), `CONVERSAO_IMAGEM_MAX_MEGAPIXELS` (padrão 100). Documente em `docs/12` e `.env.local.example` (comentadas).
- Atualize `docs/02` (RN13, RF13), `docs/07` (`bloqueio`, `tipoArquivo`), `docs/08` (avisos do topo: "N documento(s) serão convertidos para PDF"; "sem tipo informado: o tipo é conferido ao gerar o dossiê").
- Testes: detecção por assinatura (cada tipo, ZIP genérico, arquivo vazio, truncado, extensão enganosa como `.pdf` com conteúdo de planilha), seleção por tipo com conversão ligada e desligada, aviso do topo.

## Bloco 2 (OPUS): conversor LibreOffice, segurança, textos e HTML

**Commit sugerido:** `feat(conversao): conversor LibreOffice isolado para documentos de texto e HTML`

Porta `ConversorDeDocumentos` na aplicação (entrada: arquivo em disco e tipo real; saída: PDF em disco; falha tipada) e adapter em `integration.libreoffice` (**único** lugar que usa JODConverter; `ArchitectureTest` verificando). Dependência: JODConverter, versão estável compatível com Spring Boot 3.5 e Java 21 (fixe a versão e confirme a licença; se o starter do Spring não servir, use o módulo local sem starter).

- **Processos:** pool de `CONVERSAO_PROCESSOS` instâncias do `soffice`, cada uma com **diretório de perfil próprio e descartável**, reinício a cada `CONVERSAO_TAREFAS_POR_PROCESSO` tarefas, **timeout de tarefa** `CONVERSAO_TIMEOUT_SEGUNDOS` e **encerramento forçado** do processo travado (sem deixar `soffice` órfão, inclusive ao desligar a aplicação). Rodar como o usuário sem privilégios da imagem (`vistas`), com `HOME` gravável para o perfil.
- **Segurança (documentos de terceiros, tratar como hostis):**
  - macros **desligadas** (nível de segurança máximo) e nenhuma execução de script;
  - **sem atualizar vínculos externos** (links de planilhas, imagens vinculadas, OLE) e sem acesso à rede por conteúdo do documento;
  - o arquivo de entrada é copiado para um diretório de trabalho próprio do pedido; nunca converter direto do caminho de origem; nome do arquivo gerado, nunca o do usuário;
  - limite de tamanho da saída e limite de tempo; PDF de saída validado (abre, tem páginas).
- **Falhas tipadas:** protegido por senha (documento Office criptografado), corrompido, tempo limite, conversor indisponível, saída vazia. Mensagens em português para o item da falha, sem caminho interno.
- **HTML:** convertido pelo LibreOffice **sem carregar recursos externos** (imagens ou CSS remotos); referência remota vira conteúdo ausente, não requisição.
- **Textos:** DOC, DOCX, ODT, RTF para PDF mantendo tamanho e orientação definidos no próprio documento (RNF06; não forçar A4).
- **Docker (`api/Dockerfile`):** confirme `libreoffice-writer-nogui` e `libreoffice-calc-nogui`, e acrescente fontes livres para acentuação e compatibilidade métrica (por exemplo `fonts-liberation`, `fonts-dejavu-core`, `fonts-crosextra-carlito`, `fonts-crosextra-caladea`); sem root; relate o tamanho da imagem.
- **Disponibilidade:** `soffice` ausente ou sem permissão: o adapter responde "conversor indisponível" e a aplicação sobe normalmente.
- Docs: `docs/03`, `docs/05` (conversor), `docs/13` (dependência e licença), `docs/10` (riscos de processo externo).
- Testes: os que exigem `soffice` são **pulados com mensagem clara** quando ele não está instalado (`Assumptions`), e rodam quando está. Cubra DOCX, DOC, ODT, RTF e HTML simples; documento criptografado; arquivo corrompido; timeout (documento que não termina, simulado); processo morto no meio (a próxima tarefa funciona); **sem requisição de rede** (HTML com `<img src>` para um servidor HTTP local de teste: o servidor não pode receber nada).

## Bloco 3 (OPUS): planilhas

**Commit sugerido:** `feat(conversao): planilhas ajustadas à largura da página sem cortar colunas`

O ponto central da sprint (`docs/06`, "Planilhas"): por padrão o LibreOffice divide planilhas largas em várias folhas e corta colunas.

- **Modo `LARGURA` (padrão):** cada aba ajustada a **1 página de largura** (`ScaleToPagesX = 1`, `ScaleToPagesY = 0`), **orientação paisagem** quando a área usada for mais larga que alta, **todas as abas visíveis**, cada uma começando em página nova, **abas ocultas fora** (`CONVERSAO_ABAS_OCULTAS=false`; com `true`, entram). Aplique pelos estilos de página (filtro do JODConverter ou UNO), **sem alterar o arquivo original**.
- **Modo `PAGINA_POR_ABA`:** uma página por aba, de tamanho personalizado (`SinglePageSheets`).
- **Proteção contra ilegível:** se o fator de escala do modo `LARGURA` ficar abaixo de um mínimo configurável (`CONVERSAO_PLANILHA_ESCALA_MINIMA`, padrão 40%), use `PAGINA_POR_ABA` **para aquela aba** e registre a escolha no item (para o sumário e o log, sem dado do documento). Relate como mediu a escala.
- **Conteúdo:** área de impressão definida no arquivo é respeitada; células com texto longo e quebras mantidas; fórmulas exportam o valor calculado; **linhas e colunas ocultas** não aparecem (como o usuário vê); planilha vazia vira falha clara ("planilha sem conteúdo imprimível"), não PDF em branco; limite de páginas de saída (`CONVERSAO_PAGINAS_MAXIMAS`, padrão 2000) com falha clara ao estourar.
- **XLS antigo** (OLE2) e **XLSX** com o mesmo resultado.
- Docs: `docs/06` (modo, escala mínima, decisão), `docs/12`, `.env.local.example`.
- Testes (arquivos **gerados por código**, por exemplo com Apache POI em escopo de teste, licença Apache 2.0; ou escritos como XLSX mínimo): planilha de 40 colunas, aba oculta, linha oculta, duas abas, área de impressão, aba vazia, planilha enorme (limite). **Qualidade objetiva:** extraia o texto do PDF com o PDFBox e confira que **o texto da última coluna e da última linha aparece**, que o número de páginas é o esperado e que a página tem orientação paisagem quando devido.

## Bloco 4 (SONNET): imagens

**Commit sugerido:** `feat(conversao): imagens PNG e JPG viram uma página de PDF`

- Porta própria (`ConversorDeImagens`) implementada com **PDFBox**, sem LibreOffice: **uma página por imagem**, ajustada à página **sem distorção**, orientação pela proporção da imagem (retrato ou paisagem), margens pequenas, tamanho A4 como base, **orientação EXIF** do JPEG aplicada, PNG com transparência sobre fundo **branco**.
- Limite `CONVERSAO_IMAGEM_MAX_MEGAPIXELS`: acima, falha clara, sem estourar memória (leia as dimensões antes de decodificar). Imagem corrompida: falha clara. Nada de arquivo inteiro em memória além do necessário.
- Testes: PNG e JPG pequenos, JPG com EXIF girado, PNG transparente, imagem muito larga, imagem acima do limite, arquivo corrompido, extensão enganosa.
- Docs: `docs/06`.

## Bloco 5 (OPUS): conversão no pipeline do dossiê

**Commit sugerido:** `feat(dossie): converte planilhas, textos e imagens na obtenção, com retomada`

- Depois de obter o arquivo, o pipeline usa o **tipo real** (bloco 1) e escolhe: PDF segue como hoje; planilha, texto e HTML vão ao conversor (blocos 2 e 3); imagem ao conversor de imagens (bloco 4); compactado e tipo irreconhecível são falha clara do documento.
- **Falha de conversão é falha do documento** (coletada, `PENDENTE_DECISAO`), com motivo próprio (senha, corrompido, tempo limite, planilha vazia, conversor indisponível). As ações da S2.2 (`TENTAR_NOVAMENTE`, `REMOVER_E_CONTINUAR`) continuam iguais.
- **Retomada:** o PDF convertido fica no diretório do pedido e **não é convertido de novo** em "Tentar novamente" nem em "Remover e continuar" (teste contando chamadas ao conversor).
- **Concorrência:** a conversão respeita `CONVERSAO_PROCESSOS`; o total de downloads continua limitado por `DOSSIE_THREADS`; documentos de pedidos diferentes dividem o mesmo pool do conversor sem deadlock. Cancelar o pedido interrompe a conversão em andamento e apaga os temporários.
- **Transparência jurídica (decisão a registrar em `docs/09`):** documento convertido aparece no **sumário** e no **marcador** com a indicação "convertido de XLSX" (ou o formato de origem), e a capa informa "N documentos incluídos, M convertidos". Os motivos de `Documentos não incluídos` deixam de ter "não é PDF: aguardando conversão" quando a conversão está ligada; com `CONVERSAO_HABILITADA=false`, voltam.
- A paginação determinística do sumário e a ordem cronológica **não podem mudar**: a página inicial de cada documento é conhecida depois da conversão e antes da montagem.
- Docs: `docs/02`, `docs/06`, `docs/07`, `docs/08`, `docs/09`.
- Testes: dossiê com PDF, XLSX, DOCX e imagem em ordem cronológica; conversão que falha e continuação; segunda tentativa sem reconverter; cancelamento no meio; sumário e capa com "convertido de"; `CONVERSAO_HABILITADA=false`; conversor indisponível.

## Bloco 6 (OPUS): endpoint de conteúdo para o modal

**Commit sugerido:** `feat(documentos): endpoint de conteúdo com conversão sob demanda e regras de acesso`

`GET /api/documentos/{origem}/{id}/conteudo?formato=original|pdf` (`docs/07`), usado pelo modal.

- **Acesso antes de tudo:** requerimento do documento resolvido no servidor; **RN07** (Operador de outro interessado: 403 `NAO_INTERESSADO`); **sigiloso sem perfil: 403 `DOCUMENTO_SIGILOSO`**; documento inexistente ou de outro requerimento: 404. A chave do arquivo **nunca** sai; o contrato recebe `origem + id`, e como os `id` repetem entre origens, o servidor revalida o pertencimento.
- `formato=original`: o arquivo como veio, com `Content-Type` pelo tipo real e `Content-Disposition` com nome seguro (sem caminho, sem caractere de controle). Para tipos que o navegador pode executar (HTML), **entregar como download**, nunca renderizar inline (`Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`).
- `formato=pdf`: PDF se já for PDF; senão converte (planilha, texto, HTML, imagem), com o **mesmo conversor e os mesmos parâmetros** do dossiê. Tipo sem conversão: 415 `FORMATO_NAO_SUPORTADO` com mensagem em português.
- **Cache temporário** da versão em PDF em subdiretório do `VISTAS_DOSSIE_DIRETORIO`, chave por `(origem, id)`, com **TTL curto** (`VISUALIZACAO_TTL_MINUTOS`, padrão 10) e **teto de espaço** (`VISUALIZACAO_CACHE_MAX_MB`, padrão 500, descartando os mais antigos). **A verificação de acesso roda a cada requisição, mesmo com cache.** Limpeza agendada junto da do dossiê; esvaziar na subida.
- Streaming para o cliente, sem carregar o arquivo em memória; limite de conversões simultâneas do modal (fila curta, 429 `MUITAS_CONVERSOES` quando cheia); falhas de obtenção e conversão traduzidas em `ProblemDetail` com `errorCode` (reuse `CREDENCIAL_ARQUIVOS_RECUSADA`, tempo limite, não encontrado, senha, corrompido).
- Docs: `docs/07`, `docs/12`, `docs/10`.
- Testes: `MockMvc` com matriz de perfis (Operador do interessado, Operador de outro, Gestor, Admin; documento sigiloso e não sigiloso), 404 de origem e id que não pertencem ao requerimento, `formato=pdf` de PDF, XLSX, DOCX e imagem, `formato=original` de HTML como download, 415, cache (segunda chamada sem reconverter, mas **com checagem de acesso**), TTL, teto de espaço, 429, e **teste que percorre as respostas e garante que a chave do arquivo e o token não aparecem**.

## Bloco 7 (SONNET): front, "Visualizar" e modal

**Commit sugerido:** `feat(front): ação Visualizar e modal de visualização de documentos`

- Ação **Visualizar** em cada linha da lista (exceto indisponíveis; sigiloso sem perfil mostra o motivo, sem chamar o back).
- **Modal** (`docs/08`): o PDF é buscado pelo `HttpClient` (token no cabeçalho, **nunca na URL**), vira `Blob` e é exibido por URL de objeto, **revogada ao fechar**; indicação de carregamento ("convertendo…" para os convertíveis); documento **convertido** mostra a faixa "Versão convertida para PDF a partir de <formato>"; erro por `errorCode`, com "Tentar novamente"; formato sem conversão: nome, tipo e **Baixar original**.
- **Selecionar ou desmarcar o documento a partir do modal**, refletindo na tabela.
- Documento grande (acima de `DOSSIE_BLOB_MAX_MB`): sem exibição por `Blob`; ofereça só **Baixar original** com aviso (use o mesmo critério da gaveta, sem ticket novo se o contrato do bloco 6 não o previr).
- Avisos do topo da seção atualizados (bloco 1): convertíveis selecionados por padrão, sem o "entram no dossiê a partir da próxima etapa".
- Sem vazamento de subscription e sem URL de objeto sobrando; o quadro do topo continua com diferença de 0 px.
- Docs: `docs/08`.
- Testes (Vitest e e2e com API simulada): abrir e fechar o modal, revogação da URL, convertido (faixa), erro e nova tentativa, selecionar pelo modal, perfil Operador com sigiloso, documento grande, indisponível.

## Bloco 8 (OPUS): testes hostis e conversão no CI

**Commit sugerido:** `test(conversao): arquivos hostis e instalação do LibreOffice no CI`

- **Arquivos hostis sintéticos**, gerados por código, que a conversão não pode deixar causar dano nem vazar: XLSX/DOCX com **macro** (assinatura de VBA fictícia), planilha com **link externo** e fórmula que referencia arquivo ou URL, DOCX com **imagem remota**, HTML com `<img>`, `<link>`, `<iframe>` e `<script>` para servidor local de teste (o servidor **não pode receber requisição**), ZIP com **entradas gigantes** ("bomba") rotulado como DOCX, arquivo com extensão trocada, arquivo de **0 bytes**, planilha com **milhares de abas** ou **milhões de linhas** (limites e tempo), nome de arquivo com `../` e caracteres de controle. Para cada um, o resultado esperado é **falha clara do documento** ou conversão sem efeito colateral, dentro do tempo limite, e **nenhum `soffice` órfão** ao final.
- **CI (gratuito):** os testes que exigem LibreOffice precisam rodar no CI e não ficar pulados em silêncio. No job `api` do `ci.yml`, instale o mínimo (`libreoffice-writer-nogui`, `libreoffice-calc-nogui`, fontes) **só quando o job roda**; meça e registre o **tempo e os minutos acrescentados** (o repositório é privado no plano gratuito, 2.000 min/mês: `docs/13`, D53). Se o custo em minutos for alto, proponha e aplique alternativa gratuita (por exemplo, marcar os testes de conversão para rodar somente em PR que toca `api/` e em `main`), sem remover o check obrigatório. Mantenha o pino por hash e as permissões mínimas dos workflows.
- Os testes que dependem do `soffice` **falham** no CI se ele não estiver instalado (variável `CI` definida), e **pulam com aviso** na máquina do desenvolvedor sem LibreOffice.
- Docs: `docs/13` (tabela de CI), `docs/10` (riscos e resultados).

## Bloco 9 (SONNET): medição de desempenho

**Commit sugerido:** `perf(conversao): medição do tempo e da memória da conversão`

- Teste `@Tag("desempenho")`, **desligado por padrão**, com arquivos sintéticos: XLSX de 30 mil linhas × 30 colunas, XLS equivalente, DOCX de 200 páginas, JPG de 20 MP, e um dossiê misto de 20 documentos (PDFs de ~50 MB, planilhas, textos e imagens). Registre **tempo por tipo, pico de heap da aplicação, memória do `soffice`, uso de disco e tamanho do PDF final**. Rode **uma vez**, relate os números e apague os temporários.
- **Não redesenhe**: se algum valor passar dos limites (heap acima de 70% do configurado, conversão perto do timeout), **relate** com os números e proponha o ajuste em `docs/10` (por exemplo reduzir `CONVERSAO_PROCESSOS`, limite de memória do container), sem aplicá-lo sem aprovação.
- Docs: `docs/10` (R de tempo de conversão), `docs/12` (valores padrão justificados).

## Bloco 10 (SONNET): roteiro de homologação e fechamento

**Commit sugerido:** `docs(homologacao): roteiro da S3 e registro das decisões`

- `docs/homologacao/S3-roteiro-homologacao.md`, para o **responsável** executar, no formato dos anteriores (checklist, comando ou SQL exato, resultado esperado, `[ ] OK / [ ] Falhou: observação`):
  1. subir o Compose local (sem o override de verificação) e confirmar que o conversor está disponível;
  2. abrir os 3 requerimentos do `docs/casos-de-teste.md` e conferir que **planilhas, textos e imagens vêm selecionados**;
  3. **Visualizar** uma planilha real larga, um DOC e uma imagem, e conferir que **nenhuma coluna ou linha com conteúdo foi cortada** e que a faixa "Versão convertida" aparece;
  4. gerar o dossiê completo e conferir, no PDF, a **ordem cronológica**, a indicação "convertido de" no sumário e nos marcadores, a numeração e a seção "Documentos não incluídos";
  5. forçar uma falha de conversão (documento com senha) e conferir a decisão sem reconverter o resto;
  6. comparar `CONVERSAO_PLANILHA_MODO=LARGURA` e `PAGINA_POR_ABA` na pior planilha dos casos de teste, **escolher** o modo e anotar a escolha e o motivo (critério de aceite da S3);
  7. medir tempo de geração, tamanho do PDF e uso de disco e anotar em `docs/casos-de-teste.md`.
- Instrução de segurança no roteiro: **arquivos reais usados na homologação não vão para o repositório** (acrescente a pasta de amostras locais ao `.gitignore`).
- `docs/14`: registre a S3 e o que cada bloco entregou; `docs/09`: decisões (conversão pelo LibreOffice isolado, imagens por PDFBox, "convertido de" no sumário, escala mínima, flag de segurança); `docs/10`: riscos novos e pendências; coerência final entre `docs/02`, `06`, `07`, `08`, `12` e `13`.
- **Verificação final completa:** `./mvnw -B clean verify`, front (build, Vitest, e2e), `ArchitectureTest`, Compose com os três arquivos e variáveis vazias dentro do container, YAMLs válidos.

---

## Critérios de aceite (ao final do bloco 10)

1. Planilhas (XLS, XLSX, ODS), textos (DOC, DOCX, ODT, RTF, HTML) e imagens (PNG, JPG) entram no dossiê convertidos, em ordem cronológica, **sem cortar colunas ou linhas** (verificado por texto extraído nos testes e pela homologação com arquivos reais).
2. Documento convertido fica **identificado** no sumário, nos marcadores e na capa.
3. Falha de conversão entra no fluxo da S2.2 (coletada, decisão, sem reconverter o que já foi feito).
4. O modal visualiza qualquer documento em PDF, com acesso e sigilo aplicados no back-end a cada requisição e sem a chave do arquivo em nenhuma resposta.
5. Arquivos hostis (macro, vínculo externo, recurso remoto, ZIP bomba, arquivo vazio) não causam requisição de rede, processo órfão nem dossiê corrompido.
6. Tudo gratuito e de código aberto, com licenças registradas em `docs/13`; os testes de conversão rodam no CI sem estourar a cota gratuita de minutos.
7. Medições de tempo e memória registradas; modo de planilha escolhido na homologação.
8. Cada bloco foi executado pela sessão atribuída, terminou íntegro e foi marcado em `docs/progresso/s3.md`.
9. Nenhum comando git além de `git diff`; nenhum dado real no repositório.
