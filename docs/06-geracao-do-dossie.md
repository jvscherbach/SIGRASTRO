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
  │      e. em caso de falha → INTERROMPE a geração (ver abaixo)
  ├─ 4. Capa
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
- **Expiração:** `DOSSIE_TTL_MINUTOS` (30) a partir da conclusão; o arquivo é apagado ao fim do download (`attachment`) ou ao vencer (limpeza a cada 5 min e verificação a cada leitura); na subida, as pastas de pedido sobrando são apagadas (a fila é em memória).

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

Página(s) após a capa com uma linha por documento: data, título, origem e página inicial. Cada documento recebe também um marcador (bookmark) no PDF.

## Falha na geração

O objetivo é entregar o dossiê completo com o que foi pedido. Por isso, se qualquer documento selecionado não puder ser obtido, convertido ou validado:

1. a geração é interrompida no documento que falhou; os seguintes não são processados;
2. os arquivos temporários são descartados e **nenhum PDF é entregue**;
3. o pedido fica com status `FALHA`, com o documento e o motivo (ex.: "tempo limite excedido ao obter o arquivo", "arquivo não encontrado no repositório", "o documento está protegido por senha", "o arquivo está corrompido", "o arquivo não tem páginas", "o arquivo não é um PDF válido"); token recusado interrompe tudo com o código `CREDENCIAL_ARQUIVOS_RECUSADA`;
4. o usuário escolhe entre **Tentar novamente** (mesma seleção) ou **Remover documento e gerar** (nova geração sem o documento, por decisão explícita);
5. a tentativa interrompida será registrada na auditoria de documentos (evento `FALHA`, S5; a auditoria não existe na S2).

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
- O arquivo final e os temporários são removidos após o download ou após `DOSSIE_TTL_MINUTOS`.
- A pré-visualização usa o mesmo arquivo e não gera novamente.
