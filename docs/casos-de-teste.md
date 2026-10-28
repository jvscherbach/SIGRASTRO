# Casos de teste

Lista dos requerimentos e processos usados para validar o SIGA Vistas (P09). **Preenchida pelo responsável**: o desenvolvimento não tem acesso aos dados reais. Use marcadores no lugar de dados pessoais: este arquivo é versionado e **não** pode conter CPF, CNPJ nem nome de pessoa real (veja `CLAUDE.md`).

Critérios de validação ([01](01-visao-geral.md)): (1) processos que **hoje não geram a vista** passam a gerar; (2) planilhas e outros formatos entram convertidos (S3); (3) todos os documentos relacionados são listados.

## Como preencher

1. Escolha requerimentos/processos na base de desenvolvimento ou de homologação: alguns que **falham hoje** na Vista do SIGA (anote o motivo) e alguns **com planilhas**.
2. Registre o número como aparece na tela (`N/AAAA`), sem dados do interessado.
3. Depois de gerar (passo 6 do [roteiro da S2](homologacao/S2-roteiro-homologacao.md)), anote resultado, tempo, páginas e tamanho.

## Casos

| # | Requerimento / processo | Motivo da falha atual na Vista do SIGA | Tem planilha? | Documentos (SIGA × Vistas) | Resultado do dossiê | Tempo | Páginas | Tamanho | Observação |
|---|---|---|---|---|---|---|---|---|---|
| 1 | | | | | | | | | |
| 2 | | | | | | | | | |
| 3 | | | | | | | | | |

**Resultado do dossiê:** `OK`, `FALHA (documento e motivo)` ou `NÃO GERA (bloqueio)`. **Documentos:** "SIGA N × Vistas N". Um caso vale como prova do critério (1) quando falha hoje na Vista do SIGA e gera no SIGA Vistas.

## Casos de falha provocada (S2)

| Cenário | Como | Esperado | Resultado |
|---|---|---|---|
| Token expirado ou vazio | token antigo ou `LICENCIAMENTO_API_TOKEN=` | mensagem de token; nenhum arquivo | |
| Tempo limite | `DOWNLOAD_TIMEOUT_SEGUNDOS=1` | falha com o documento e o motivo; nenhum arquivo; "Tentar novamente" funciona | |
| Remover documento e gerar | após a falha | gera sem o documento | |

## Dossiê grande (S2.2)

Passo 10.6 do [roteiro](homologacao/S2-roteiro-homologacao.md). Referência sem dados reais: na máquina de desenvolvimento, 20 PDFs sintéticos de ~48 MB montaram em 4,7 s, com PDF final de 960 MB e pico de heap de ~130 MB (docs/10, R26).

| Requerimento / processo | Documentos | Soma dos arquivos | Tempo até "Dossiê pronto" | Tamanho do PDF | Maior uso de `/var/tmp/vistas` | Download por ticket? | Observação |
|---|---|---|---|---|---|---|---|
| `<REQUERIMENTO_N>` | | | | | | | |

## Casos de falha provocada (S2.2)

| Cenário | Como | Esperado | Resultado |
|---|---|---|---|
| Falha coletada | um documento sem extensão que não é PDF | gera até o fim; "Documentos com problema" com a lista; nada entregue | |
| Tentar novamente | após a falha | só os com problema voltam a "Obtendo"; o progresso não volta a zero | |
| Remover e gerar | após a falha | PDF com "Documentos não incluídos" e capa "N incluídos de M listados" | |
| Token por arquivo | token expirado no arquivo e depois o novo, sem recriar o container | a obtenção para; com o token novo, "Tentar novamente" conclui sem baixar de novo o que já foi obtido | |

## Conversão e visualização (S3)

Passos 2 a 7 do [roteiro da S3](homologacao/S3-roteiro-homologacao.md). Por caso (use os mesmos 3 requerimentos da tabela de casos acima):

| # | Convertidos (planilhas / textos / imagens) | Nada cortado? (colunas e última linha) | "convertido de" no sumário, marcadores e capa? | Páginas | Tempo até "Dossiê pronto" | Tamanho do PDF | Observação |
|---|---|---|---|---|---|---|---|
| 1 | / / | | | | | | |
| 2 | / / | | | | | | |
| 3 | / / | | | | | | |

Para comparar com a referência sintética (docs/10): XLSX de 30 mil linhas x 30 colunas converte em ~16 s (177 páginas) e DOCX de 200 páginas em ~1 s; dossiê misto de 20 documentos (321 MB) em ~8 s.

### Planilha mais difícil: `LARGURA` × `PAGINA_POR_ABA` (passo 6)

| Critério | `LARGURA` | `PAGINA_POR_ABA` |
|---|---|---|
| Páginas | | |
| Todas as colunas e a última linha presentes? | | |
| Legível sem ampliar / ampliando? | | |
| Abas em página única | | |
| Tempo de conversão | | |

**Modo escolhido:** ________ . **Motivo:** ________ .

### Casos de falha provocada (S3)

| Cenário | Como | Esperado | Resultado |
|---|---|---|---|
| Falha de conversão | documento com senha, ou `CONVERSAO_PAGINAS_MAXIMAS=1` | "Documentos com problema" com o motivo da conversão; nada entregue | |
| Tentar novamente | após a falha | só o documento com problema volta; o contador de conversões nos logs sobe no máximo 1 | |
| Remover e gerar | após a falha | PDF sem o documento, com "Documentos não incluídos"; convertidos continuam identificados | |
| Conversão desligada | `CONVERSAO_HABILITADA=false` | convertíveis bloqueados ("conversão indisponível"); dossiê só com PDFs | |
| Sigiloso no modal (Operador) | Visualizar um sigiloso | motivo exibido; nenhuma requisição a `/conteudo` | |

### Memória e disco (passo 7)

| Maior `MEM USAGE` do container | Limite do container | Pico de `/var/tmp/vistas` | `soffice` vivos depois do `down` |
|---|---|---|---|
| | | | |
