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
