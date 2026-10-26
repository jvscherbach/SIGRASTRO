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
  ├─ 5. Montagem: capa + sumário + documentos, na ordem
  ├─ 6. Marcadores (bookmarks) e numeração de páginas
  └─ 7. Arquivo final disponível para pré-visualização e download
```

A obtenção e a conversão podem ser paralelas. A montagem respeita sempre a ordem cronológica.

## Como está na S2 (sem conversão)

- Só **PDFs** entram; os demais tipos aparecem na lista, bloqueados ("conversão disponível em breve"), até a S3.
- O `POST /dossies` relê a lista no servidor, valida a seleção (pertencimento, indisponível, não PDF, sigilo, RN07), **resolve as chaves uma única vez**, guarda o instantâneo no pedido e libera a conexão do SIGA **antes** de baixar qualquer arquivo; a geração não usa o banco (provado em `ConexaoDoSigaLiberadaTest`).
- Passo a passo: ordenar (RN03) → obter cada PDF (em sequência, um pedido por vez em cada thread de `DOSSIE_THREADS`) → validar (assinatura `%PDF-`, abre, sem senha de abertura, com páginas) → capa → sumário → montagem → marcadores e numeração → PDF final.
- **PDFBox 3 em disco:** `Loader.loadPDF` sobre `File` com cache em arquivos temporários; páginas importadas com `importPage` (tamanho, rotação, anotações e links preservados; **nada é forçado para A4**, RNF06). Formulários interativos são achatados com tratamento de erro (falha ao achatar não interrompe). Os documentos de origem ficam abertos até o PDF final ser salvo (o PDFBox compartilha objetos).
- **Capa** (RF12) e **sumário** são páginas A4 desenhadas com a fonte padrão do PDFBox (WinAnsi): o que a fonte não codifica vira `?` (acentos, `º`, `ç` funcionam).
- **Sumário determinístico:** uma linha por documento (data, título, origem, página inicial), com **38 linhas por página**; a página inicial de cada documento é conhecida antes de importar qualquer página (capa = 1, sumário = próximas, documentos em seguida). Cada linha é um link para a página do documento.
- **Marcadores** (`PDDocumentOutline`): "Capa", "Sumário" e um por documento. **Numeração** "Página X de Y" a partir da primeira página após a capa (X é o número físico), posicionada pela **caixa de recorte e pela rotação** de cada página (testado nas quatro rotações). Os três respeitam os parâmetros ligado/desligado.
- **Expiração:** `DOSSIE_TTL_MINUTOS` (30) desde a última atividade (S2.2; até a S2.1, a partir da conclusão); o diretório do pedido é apagado ao fim do download (`attachment`) ou ao vencer (limpeza a cada 5 min e verificação a cada leitura); na subida, as pastas de pedido sobrando são apagadas (a fila é em memória).

## Tipos de arquivo

| Tipo | Tratamento |
|---|---|
| PDF | Incluído como está |
| PDF com senha de abertura | Falha: interrompe a geração ("documento protegido por senha") |
| PDF corrompido | Falha: interrompe a geração |
| XLS, XLSX, ODS | Conversão pelo LibreOffice, com ajuste de escala (ver abaixo) |
| DOC, DOCX, ODT, RTF | Conversão pelo LibreOffice |
| PNG, JPG, JPEG | Uma página por imagem, ajustada à página sem distorção, com orientação definida pela proporção |
| HTML | Conversão pelo LibreOffice |
| SHP, KML, KMZ | Não entram no dossiê: download à parte em "Baixar geometrias" (RN10) |
| ZIP, RAR, 7Z | Desconsiderados (RN11) |

### Planilhas

Este é o ponto central da validação. Por padrão, o LibreOffice pagina planilhas largas em várias folhas e corta colunas. Antes da exportação:

- ajustar cada aba à largura de uma página (`ScaleToPagesX = 1`, `ScaleToPagesY = 0`);
- orientação paisagem quando a área usada for mais larga que alta;
- incluir todas as abas, cada uma iniciando em página nova;
- não imprimir abas ocultas.

Se o resultado ainda for ilegível (escala muito pequena), a alternativa a avaliar é exportar com a opção de uma página por aba (`SinglePageSheets`), que gera páginas de tamanho personalizado.

A validação visual será feita com as planilhas reais dos processos de teste.

## Capa

- Título: "Dossiê do Requerimento N/AAAA" ou "Dossiê do Processo N/AAAA".
- Dados: interessado, CPF/CNPJ, tipo de processo, situação, data de abertura, objetivos e atividades.
- No dossiê por processo: lista dos requerimentos incluídos.
- Data e hora de geração e usuário (simulado) que gerou.
- Quantidade de documentos incluídos.

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

1. o pipeline percorre **todos** os documentos e coleta as falhas (arquivo não encontrado, tempo limite, protegido por senha, corrompido, sem páginas, não é PDF, formato aguardando conversão, espaço em disco insuficiente); não para na primeira;
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
(cancelar: de qualquer situação não terminal → CANCELADO; sem atividade no TTL: CONCLUIDO ou PENDENTE_DECISAO → EXPIRADO)
```

**Validade:** o TTL (`DOSSIE_TTL_MINUTOS`) conta desde a **última atividade** — criação, cada documento processado, a entrada em `PENDENTE_DECISAO`, cada decisão e a conclusão —, inclusive enquanto o pedido espera a decisão. Cancelar e expirar apagam o diretório do pedido inteiro.

Geometrias e arquivos compactados não fazem parte da seleção do dossiê, portanto não geram falha.

Não há relatório de geração ao final do dossiê.

## Numeração de páginas

Numeração "Página X de Y" no rodapé, a partir da primeira página após a capa. A posição é calculada pelo tamanho e pela rotação de cada página, não por coordenada fixa.

## Preservação do conteúdo

- As páginas são importadas com tamanho, rotação, anotações e links preservados (importação de página do PDFBox, não desenho como imagem).
- Formulários interativos são achatados para evitar conflito de nomes de campo entre documentos.
- Assinaturas digitais de terceiros incorporadas aos PDFs perdem a validade criptográfica no documento mesclado. O carimbo visual de assinatura do SIGA é conteúdo da página e é preservado.

## Limites e expiração

- Timeout por download e por conversão (configuráveis).
- O arquivo final e os temporários são removidos após o download, ao cancelar ou após `DOSSIE_TTL_MINUTOS` sem atividade (inclusive à espera de decisão).
- A pré-visualização usa o mesmo arquivo e não gera novamente.
