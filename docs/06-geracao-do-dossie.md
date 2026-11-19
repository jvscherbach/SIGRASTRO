# 06 — Geração do dossiê

## Pipeline

```
Pedido (documentos selecionados)
  │
  ├─ 1. Validação: perfil x sigilo, documentos pertencem ao requerimento/processo
  ├─ 2. Ordenação cronológica (RN03)
  ├─ 3. Para cada documento (com limite de paralelismo):
  │      a. obter o binário → arquivo temporário
  │      b. identificar o tipo real (Content-Type + assinatura do arquivo)
  │      c. converter para PDF, se necessário
  │      d. validar o PDF (abre? tem senha? tem páginas?)
  │      e. em caso de falha → registra a falha do documento e segue (S2.2; ver abaixo)
  ├─ 4. Com falhas: PENDENTE_DECISAO (nada é entregue até a decisão); sem falhas: capa
  ├─ 5. Montagem: capa + sumário + "Documentos não incluídos" (S2.2) + documentos, na ordem
  ├─ 6. Marcadores (bookmarks) e numeração de páginas
  └─ 7. Arquivo final disponível para pré-visualização e download
```

A obtenção e a conversão podem ser paralelas. A montagem respeita sempre a ordem cronológica.

## Conversão na geração (S3, bloco 5)

Depois de obter cada arquivo, o pipeline (`PreparadorDePdf`, no `dossie`) decide pelo **tipo real** (conteúdo, `DetectorDeTipoReal`; a extensão do nome só desempata XLS e DOC antigos):

| Tipo real | O que acontece |
|---|---|
| PDF | segue como na S2 (validação: assinatura, senha, páginas) |
| planilha, texto, HTML | conversor de documentos (LibreOffice, [05](05-integracoes.md)) |
| imagem (PNG, JPEG) | conversor de imagens (PDFBox) |
| compactado (ZIP) | falha do documento: "arquivo compactado (ZIP): não entra no dossiê" (RN11) |
| não reconhecido | validação de PDF (aceita PDF com bytes antes da assinatura; senão, "o arquivo não é um PDF (tipo detectado: …)") |

- **Falha de conversão é falha do documento** (coletada, `PENDENTE_DECISAO`, decisão do usuário), com o motivo do conversor: protegido por senha, corrompido, tempo limite da conversão, planilha sem conteúdo imprimível, planilha que não coube inteira no PDF, páginas demais, conversor indisponível, imagem grande demais. As ações `TENTAR_NOVAMENTE` e `REMOVER_E_CONTINUAR` não mudaram.
- **Retomada:** o PDF convertido fica no diretório do pedido e **nunca é convertido de novo** (o item está resolvido). O arquivo original (`<posição>.original`) é apagado quando o PDF do item fica pronto e **guardado quando a conversão falha**: "Tentar novamente" converte de novo **sem baixar de novo** (as tentativas do item ficam 0). Os originais guardados de itens removidos saem ao concluir. Testado contando as chamadas ao repositório e ao conversor.
- **Concorrência:** cada thread de `DOSSIE_THREADS` obtém e converte um documento por vez (o total de downloads continua limitado por ela). As conversões de todos os pedidos (e da visualização) dividem o mesmo pool do LibreOffice (`CONVERSAO_PROCESSOS`), que enfileira: sem deadlock, porque a conversão não espera nenhum outro recurso do pedido. A espera por um processo livre tem o mesmo limite da tarefa (mínimo de 30 s).
- **Cancelamento:** cancelar um pedido em andamento interrompe a conversão em curso (a espera termina na hora, com "conversão interrompida"), e o executor apaga o diretório do pedido com os temporários (inclusive o diretório de trabalho do conversor, que fica dentro dele).
- **Paginação e ordem não mudam:** a página inicial de cada documento é conhecida depois da conversão e antes da montagem (as páginas vêm do resultado da conversão).
- Com **`CONVERSAO_HABILITADA=false`**, vale a S2.2: o que não é PDF falha com "o arquivo não é um PDF (tipo detectado: planilha XLSX) e a conversão está desligada" (a seleção já os marca como "não é PDF: aguardando conversão").

**Identificação do convertido (transparência jurídica, D54):**

- **Sumário:** "Título (convertido de XLSX)" na linha do documento; o título é cortado antes da nota, que sempre aparece inteira, sem mudar a paginação. Planilha com aba em página única (escala mínima): "(convertido de XLSX*)" e a nota de rodapé "* Planilha com aba(s) em página única: no ajuste à largura da página, a escala ficaria abaixo da mínima.".
- **Marcador:** "Título (convertido de XLSX)".
- **Capa:** "N documentos incluídos de M listados; K convertidos para PDF" (sem conversões, a frase da S2.2).
- O formato é o do **tipo real** (XLSX, XLS, ODS, DOCX, DOC, ODT, RTF, HTML, PNG, JPG), não o do nome.

## Base da geração (S2; a conversão da S3 entra na etapa de obtenção)

- Na S2 só **PDFs** entravam. Desde a S3, com `CONVERSAO_HABILITADA=true` (padrão), planilhas, textos e imagens também entram, convertidos depois do download (descrito acima); com `false`, vale o comportamento da S2.2: os demais tipos ficam bloqueados na lista ("conversão indisponível").
- O `POST /dossies` relê a lista no servidor, valida a seleção (pertencimento, indisponível, não PDF, sigilo, RN07), **resolve as chaves uma única vez**, guarda o instantâneo no pedido e libera a conexão do SIGA **antes** de baixar qualquer arquivo; a geração não usa o banco (provado em `ConexaoDoSigaLiberadaTest`).
- Passo a passo: ordenar (RN03) → obter cada PDF (em sequência, um pedido por vez em cada thread de `DOSSIE_THREADS`) → validar (assinatura `%PDF-`, abre, sem senha de abertura, com páginas) → capa → sumário → montagem → marcadores e numeração → PDF final.
- **PDFBox 3 em disco:** `Loader.loadPDF` sobre `File` com cache em arquivos temporários; páginas importadas com `importPage` (tamanho, rotação, anotações e links preservados; **nada é forçado para A4**, RNF06). Formulários interativos são achatados com tratamento de erro (falha ao achatar não interrompe). Os documentos de origem ficam abertos até o PDF final ser salvo (o PDFBox compartilha objetos).
- **Capa** (RF12) e **sumário** são páginas A4 desenhadas com a fonte padrão do PDFBox (WinAnsi): o que a fonte não codifica vira `?` (acentos, `º`, `ç` funcionam).
- **Metadados do PDF (R1):** título = "Dossiê do Processo|Requerimento N/AAAA", assunto = dossiê do SIGA Licenciamento (módulo Vistas), criador e produtor = "Rastro". Nada do interessado, do CPF/CNPJ nem do usuário vai para o metadado, porque ele viaja com o arquivo. O nome do arquivo (`dossie-processo-N-AAAA.pdf`) não mudou.
- **Documentos pessoais (SCORP, S4):** entram como os demais, com origem "Pessoal" no sumário e nos marcadores; o arquivo vem do mesmo `/api/arquivo/{chave}` (NÃO CONFIRMADO que a chave do SCORP vale nele: se não valer, é falha do documento, com decisão na gaveta). A capa não menciona o SCORP: se ele estava fora do ar ao listar, o aviso é da tela.
- **Sumário determinístico:** uma linha por documento (data, título, origem, página inicial), com **38 linhas por página**; a página inicial de cada documento é conhecida antes de importar qualquer página (capa = 1, sumário = próximas, documentos em seguida). Cada linha é um link para a página do documento.
- **Marcadores** (`PDDocumentOutline`): "Capa", "Sumário" e um por documento. **Numeração** "Página X de Y" no canto inferior direito a partir da primeira página após a capa (X é o número físico), posicionada pela **caixa de recorte e pela rotação** de cada página (testado nas quatro rotações e em tamanhos de página diferentes). Os três respeitam os parâmetros ligado/desligado.
- **Expiração:** `DOSSIE_TTL_MINUTOS` (30) desde a última atividade (S2.2; até a S2.1, a partir da conclusão); o diretório do pedido é apagado ao fim do download (`attachment`) ou ao vencer (limpeza a cada 5 min e verificação a cada leitura); na subida, as pastas de pedido sobrando são apagadas (a fila é em memória).

## Tipos de arquivo

| Tipo | Tratamento |
|---|---|
| PDF | Incluído como está |
| PDF com senha de abertura | Falha: interrompe a geração ("documento protegido por senha") |
| PDF corrompido | Falha: interrompe a geração |
| XLS, XLSX, ODS | Conversão pelo LibreOffice, com ajuste de escala (ver abaixo) |
| DOC, DOCX, ODT, RTF | Conversão pelo LibreOffice |
| PNG, JPG, JPEG | Só PDFBox, sem LibreOffice (S3, bloco 4): uma página por imagem, A4 como base, retrato ou paisagem pela proporção já com a orientação EXIF aplicada, margem de 18 pt, sem distorção. A imagem é reduzida para caber e nunca ampliada além de 150 dpi. PNG com transparência fica sobre fundo branco; o JPEG vai com os bytes originais, sem recompressão. O formato vale pelo conteúdo, não pela extensão. Acima de `CONVERSAO_IMAGEM_MAX_MEGAPIXELS` (100) a imagem é recusada pelas dimensões, antes de decodificar; arquivo ilegível ou truncado também é falha do documento |
| HTML | Conversão pelo LibreOffice |
| SHP, KML, KMZ | Não entram no dossiê: download à parte em "Baixar geometrias" (RN10) |
| ZIP, RAR, 7Z | Desconsiderados (RN11) |

### Planilhas

Este é o ponto central da validação. Por padrão, o LibreOffice pagina planilhas largas em várias folhas e corta colunas. Desde a S3 (bloco 3), antes da exportação, um filtro do conversor (`FiltroDePlanilha`, em `integration.libreoffice`) prepara a planilha carregada em memória; **o arquivo original nunca é alterado** (a cópia de trabalho só é exportada para PDF). XLS (OLE2), XLSX e ODS passam pelo mesmo caminho e dão o mesmo resultado.

Cada aba que entra no PDF recebe um estilo de página próprio (cópia do estilo dela: margens, cabeçalho e rodapé ficam como no arquivo), conforme `CONVERSAO_PLANILHA_MODO`:

- **`LARGURA` (padrão):** a aba ocupa **1 página de largura** com altura livre (`ScaleToPagesX = 1`, `ScaleToPagesY = 0`), em **paisagem** quando a área impressa é mais larga que alta (senão, retrato), no tamanho de papel do próprio arquivo;
- **`PAGINA_POR_ABA`:** uma página por aba, do tamanho da área impressa mais margens, cabeçalho e rodapé (a aba inteira numa página). O efeito é o do `SinglePageSheets` da exportação, mas aplicado aba a aba pelo estilo de página, o que permite usá-lo também numa aba só (abaixo). Lado máximo de 5 m (abaixo do limite de 200 polegadas do PDF); acima, a aba é reduzida para caber.

**Escala mínima (proteção contra ilegível):** no modo `LARGURA`, se a escala estimada da aba ficar abaixo de `CONVERSAO_PLANILHA_ESCALA_MINIMA` (padrão 40%), **aquela aba** vai para uma página única (como em `PAGINA_POR_ABA`). **Só se a página única couber no limite do LibreOffice** (~5 m, 500.000 centésimos de milímetro em cada lado; R41): acima disso o LibreOffice cortava as linhas de baixo sem erro (30 mil linhas x 30 colunas saíam com só as ~1.930 primeiras). Nesse caso, e também no modo `PAGINA_POR_ABA`, a aba é **paginada na largura mesmo abaixo da escala mínima**: menos legível, nada perdido (a mesma planilha sai em 177 páginas, com todas as linhas). Não conta como "página única" e é registrada no log só pelo número da aba. O conversor devolve quantas abas caíram nesse caso (`ResultadoDaConversao.abasEmPaginaUnica`, com a observação para o sumário) e registra no log só o número da aba, sem nome nem conteúdo.

**Como a escala é medida:** escala = largura útil da página (largura do papel na orientação escolhida, menos as margens esquerda e direita) ÷ largura da área impressa, limitada a 100%. A largura da área impressa é a propriedade `Size` do intervalo de células no Calc (soma das larguras das colunas, sem as ocultas). A área impressa é a área de impressão definida no arquivo (a maior, se houver várias) ou, sem ela, de A1 até o fim da área usada (o Calc imprime a partir de A1). É uma estimativa feita antes da exportação, porque o Calc não expõe a escala que calcula para "ajustar à largura". Exemplos medidos nos testes: 40 colunas de 6 caracteres em A4 paisagem ficam em 1 página; 120 colunas na largura padrão ficam bem abaixo de 40% e vão para página única.

**Cabeçalho, rodapé e centralização (S3.1, D59):** em cada estilo de página de aba (nos dois modos e em XLS, XLSX e ODS):

- **Cabeçalho = nome do documento** como o dossiê o conhece (o mesmo texto do sumário e do marcador, sem "convertido de"); na visualização, o título da lista. Quando **mais de uma aba entra no PDF** (visíveis, ou ocultas exibidas por `CONVERSAO_ABAS_OCULTAS=true`, com conteúdo), `<documento> — <aba>`; com uma só, só o documento. Nunca o "Planilha1" nem a data do cabeçalho padrão do Calc: as três partes do cabeçalho são reescritas, só a central com texto, fonte de 9 pt.
- O título e o nome da aba são **dados não confiáveis**: entram como texto puro (sem campo, fórmula nem marcação; `<b>`, `&`, `{` e `$(TABLE)` saem como estão), sem caractere de controle nem invisível, espaços em sequência viram um, até 200 caracteres (aba, 40) e **cortados com reticências** para caber numa linha na largura útil da página (conta conservadora de 1,9 mm por caractere). O nome da aba é preservado; o título é que encolhe. Sem título (nulo ou vazio depois da limpeza), o cabeçalho fica desligado.
- **Rodapé do Calc desligado** ("Página 1" some): a numeração oficial é a do dossiê.
- **Centralizada na horizontal**, não na vertical.
- O cabeçalho tem altura fixa (7 mm com o espaço até o corpo) e é ligado **antes** da decisão de layout: a página única e a área útil já o descontam; a escala (largura) não muda. A conferência da última linha (R41) continua igual.
- Documentos de texto (DOC, DOCX, ODT, RTF, HTML) não mudam.

**Conteúdo**

- Abas **ocultas** ficam fora (o Calc não as imprime). Com `CONVERSAO_ABAS_OCULTAS=true`, tornam-se visíveis e entram; se a estrutura da pasta estiver protegida no arquivo, a aba continua fora (registrado no log). Para isso a planilha é carregada sem "só leitura" (o Calc recusa mostrar aba oculta em documento só leitura); macro e vínculo continuam bloqueados.
- **Linhas e colunas ocultas** não aparecem, como o usuário as vê.
- Cada aba começa em **página nova**; aba sem célula com conteúdo não gera página.
- **Área de impressão** definida no arquivo é respeitada.
- Texto longo com quebra automática sai inteiro; **fórmulas** saem com o valor calculado guardado no arquivo (o Calc não recalcula na abertura de XLSX por padrão).
- Planilha sem nenhuma célula com conteúdo nas abas que entram: falha do documento **"planilha sem conteúdo imprimível"**, nunca PDF em branco.
- **Conferência da última linha (R41):** depois de exportar, de cada aba é conferido que o texto da última linha visível (não oculta nem filtrada) da área impressa aparece no PDF (comparação sem espaços). Senão, falha do documento **"a planilha não coube inteira no PDF: as últimas linhas não aparecem"**. Só servem de marca células de texto que o Calc nunca corta: com quebra automática, ou alinhadas à esquerda com a próxima coluna visível vazia. Texto com a vizinha preenchida sai cortado na borda (por exemplo, "Linha 30000" vira "Linha 3000" na largura padrão) e número pode sair "###", então a aba sem marca assim fica sem essa conferência: a proteção principal é a página única condicional. Conferido que a conferência barra o PDF truncado quando a página única condicional é desligada.
- Mais de `CONVERSAO_PAGINAS_MAXIMAS` páginas (padrão 2000, vale para qualquer documento convertido): falha do documento **"o PDF convertido excede o número máximo de páginas permitido"**. O limite é conferido no PDF gerado; o tempo da exportação é limitado por `CONVERSAO_TIMEOUT_SEGUNDOS`.

Testes objetivos (`ConversaoDePlanilhasTest`, planilhas geradas pelo Apache POI): o texto extraído do PDF contém a última coluna e a última linha, o número de páginas é o esperado e a página está em paisagem quando devido. A escolha entre `LARGURA` e `PAGINA_POR_ABA` é feita na homologação com as planilhas reais dos processos de teste (critério de aceite da S3).

## Capa

- Título: "Dossiê do Requerimento N/AAAA" ou "Dossiê do Processo N/AAAA".
- Dados: interessado, CPF/CNPJ, tipo de processo, situação, data de abertura, objetivos e atividades.
- No dossiê por processo: lista dos requerimentos incluídos.
- Data e hora de geração e usuário (simulado) que gerou.
- Quantidade de documentos: "N documentos incluídos de M listados" (S2.2). M é o total da lista que o usuário viu ao gerar (depois da consolidação, sem compactados nem geometrias; para o Operador, inclui os sigilosos mascarados). M − N é exatamente o que aparece em "Documentos não incluídos".

## Documentos não incluídos (S2.2)

Logo após o sumário (ou após a capa, com o sumário desligado), a seção "Documentos não incluídos" lista cada documento da lista que não entrou no PDF, na mesma ordem cronológica do sumário, com data (ou "sem data"), título, origem, id e motivo:

| Motivo no PDF | Quando |
|---|---|
| removido pelo usuário após falha: *motivo* | o usuário escolheu "Remover e continuar" (docs/07); sem falha registrada (não chegou a ser obtido): "removido pelo usuário (não obtido)" |
| não selecionado pelo usuário | selecionável, mas desmarcado na tabela |
| indisponível (chave do arquivo inválida) | chave nula, vazia ou `"0"` (RN01) |
| não é PDF: aguardando conversão | só com `CONVERSAO_HABILITADA=false`: extensão convertível que não é PDF. Com a conversão ligada (padrão, S3) esses documentos entram convertidos |
| sigiloso: o perfil de quem gerou não tem acesso | sigiloso para o Operador; o título sai mascarado ("Documento sigiloso") |

- A seção é **omitida** quando não há nada fora; tem **marcador** próprio ("Documentos não incluídos") e é **obrigatória** (não depende de `DOSSIE_SUMARIO`).
- **Paginação determinística:** 22 documentos por página (duas linhas cada: dados e motivo); o número de páginas é conhecido antes de importar qualquer documento, então a página inicial de cada documento no sumário e os marcadores continuam exatos (capa = 1, sumário, seção, documentos).
- O que fica fora é decidido no servidor: o `POST /dossies` guarda no pedido o instantâneo dos não selecionados (com o motivo) e o total listado; os removidos na decisão entram na montagem. Nenhuma chave de arquivo vai para a seção.

## Sumário

Página(s) após a capa com uma linha por documento: data (dd/MM/aaaa), título, origem e página inicial. Cada documento recebe também um marcador (bookmark) no PDF. Documento sem data mostra "sem data" e fica no fim; havendo algum, o rodapé da última página do sumário traz a nota "Documentos "sem data" não têm data registrada na origem e aparecem ao final, por origem e id." (a nota não ocupa linha, então a paginação do sumário não muda).

## Ordem do dossiê (S2.2)

A ordem é **sempre a do servidor**, decidida no `POST /dossies` a partir do instantâneo da lista relida do SIGA (`SelecionarDocumentosParaDossie`, `Documento.ORDEM_CRONOLOGICA`):

1. `DATA_DOC` crescente, pelo timestamp completo (dia, hora, minuto e segundo);
2. sem data, ao final;
3. empate de data (e entre os sem data): ordem das origens na tabela do docs/04 (documento do processo, documento do requerimento, complementação, imóvel, representante legal, responsável técnico);
4. depois, `ID` numérico (9 antes de 10).

A ordem em que o usuário clica, filtra ou ordena a tabela não altera o dossiê: o cliente manda só `origem + id`, e o servidor reordena. A gaveta avisa "O dossiê segue sempre a ordem cronológica.". A tabela abre por data crescente com o mesmo critério de desempate.

**Fuso e dia.** `DATA_DOC` é `TIMESTAMP` (ou `DATE`) sem fuso, no relógio de Cuiabá. O adapter lê a coluna como `LocalDateTime` direto do driver (`DataHoraDoSiga`), sem passar por `java.sql.Timestamp`, que usaria o fuso da JVM. Um documento das 23:30 continua no mesmo dia na tela e no sumário, mesmo com a JVM em UTC. O front lê o texto `aaaa-mm-ddThh:mm` campo a campo, sem converter pelo fuso do navegador.

## Falha na geração (S2.2: coleta as falhas e continua de onde parou)

O dossiê continua **sem ser entregue incompleto sem decisão explícita** (RN09), mas a decisão não reinicia nada:

1. o pipeline percorre **todos** os documentos e coleta as falhas (arquivo não encontrado, tempo limite, protegido por senha, corrompido, sem páginas, não é PDF, falha de conversão (S3), compactado, espaço em disco insuficiente); não para na primeira;
2. terminada a obtenção: sem falha, monta e conclui. Com falhas, o pedido fica em **`PENDENTE_DECISAO`** com a lista completa (`falhas` no andamento, cada uma com documento e motivo) e **nada é entregue**;
3. exceção: **401/403** (credencial recusada) interrompe a obtenção na hora (nada mais funcionaria) e também deixa o pedido em `PENDENTE_DECISAO`, com `erro.codigo = CREDENCIAL_ARQUIVOS_RECUSADA`; os itens ainda não tentados ficam `AGUARDANDO`;
4. os arquivos já obtidos e validados ficam no **diretório do pedido** (`VISTAS_DOSSIE_DIRETORIO/<id>/`, um arquivo por posição no pedido) até concluir, cancelar ou expirar; o arquivo de um documento que falhou é apagado na hora;
5. a decisão é `POST /dossies/{id}/continuacao` (docs/07): **`TENTAR_NOVAMENTE`** devolve à fila só os itens não resolvidos (`FALHA` e `AGUARDANDO`); **`REMOVER_E_CONTINUAR`** os marca `REMOVIDO` e vai para a montagem com os já obtidos. Nenhuma das duas baixa de novo o que já foi obtido (testado contando as chamadas ao repositório). `REMOVER_E_CONTINUAR` sem nenhum documento obtido é recusado (400);
6. falha na **montagem** também leva a `PENDENTE_DECISAO` (motivo geral em `erro`); `TENTAR_NOVAMENTE` refaz só a montagem;
7. a tentativa com falhas será registrada na auditoria de documentos (evento `FALHA`, S5; a auditoria não existe na S2).

Situações do **item**: `AGUARDANDO`, `OBTENDO`, `OK`, `FALHA`, `REMOVIDO`. Situações do **pedido**: `AGUARDANDO`, `OBTENDO`, `PENDENTE_DECISAO`, `MONTANDO`, `CONCLUIDO`, `CANCELADO`, `EXPIRADO`.

```
AGUARDANDO → OBTENDO ─┬─ sem falhas ─→ MONTANDO → CONCLUIDO
     ↑                └─ com falhas ─→ PENDENTE_DECISAO
     └──────── TENTAR_NOVAMENTE / REMOVER_E_CONTINUAR ─┘
(download completo: CONCLUIDO → EXPIRADO com a marca "baixado", que "Dossiês gerados" mostra como BAIXADO, S4)
(cancelar: de qualquer situação não terminal → CANCELADO; sem atividade no TTL: CONCLUIDO ou PENDENTE_DECISAO → EXPIRADO)
```

**Validade:** o TTL (`DOSSIE_TTL_MINUTOS`) conta desde a **última atividade** — criação, cada documento processado, a entrada em `PENDENTE_DECISAO`, cada decisão e a conclusão —, inclusive enquanto o pedido espera a decisão. Cancelar e expirar apagam o diretório do pedido inteiro.

Geometrias e arquivos compactados não fazem parte da seleção do dossiê, portanto não geram falha. As geometrias têm download próprio em ZIP (S4, docs/07 "Geometrias"), com a mesma regra de tudo ou nada: obtém todas com o mesmo cliente, tempos, novas tentativas e checagem de espaço do dossiê, numa pasta temporária própria (`VISTAS_DOSSIE_DIRETORIO/geometrias`), e só monta o ZIP se nenhuma falhar.

Não há relatório de geração ao final do dossiê; o que ficou de fora está na seção "Documentos não incluídos", no início (S2.2).

## Numeração de páginas

Numeração **"Página X de Y" no canto inferior direito** (S3.1, D59), a partir da primeira página após a capa (a capa é a página 1, sem número; X é o número físico). O texto é **alinhado à direita**: o fim dele fica a 28 pt da borda direita e a base a 20 pt da borda inferior, **como a página é exibida**. A posição é calculada pela caixa de recorte e pela rotação de cada página (0, 90, 180 e 270), não por coordenada fixa, e o texto acompanha o sentido da página. Vale para todas as páginas numeradas: PDFs originais, convertidos e seções geradas (sumário, "Documentos não incluídos").

O texto é em **negrito** (Helvetica-Bold, 9 pt, preto) e **sem fundo** (S3.2): é desenhado direto sobre a página, sem cobrir nem deslocar nada. O retângulo branco da S3.1 foi removido a pedido do responsável. Efeito: se um PDF original desenha algo exatamente nesse cantinho (por exemplo, a numeração própria dele), o número **pode se sobrepor** ao conteúdo; margens inferiores comuns (acima de 36 pt) não são afetadas. O conteúdo do documento fica intacto e extraível. Cada página numerada tem uma única numeração: a das planilhas convertidas, do Calc, foi removida (D59).

## Preservação do conteúdo

- As páginas são importadas com tamanho, rotação, anotações e links preservados (importação de página do PDFBox, não desenho como imagem).
- Formulários interativos são achatados para evitar conflito de nomes de campo entre documentos.
- Assinaturas digitais de terceiros incorporadas aos PDFs perdem a validade criptográfica no documento mesclado. O carimbo visual de assinatura do SIGA é conteúdo da página e é preservado.

## Limites e expiração

- Timeout por download e por conversão (configuráveis).
- O arquivo final e os temporários são removidos após o download, ao cancelar ou após `DOSSIE_TTL_MINUTOS` sem atividade (inclusive à espera de decisão).
- A pré-visualização usa o mesmo arquivo e não gera novamente.
