# 01 — Visão geral

## Contexto

O SIGA Licenciamento tem a funcionalidade **Vista do Processo**: o usuário localiza um requerimento ou processo, seleciona documentos e recebe um pacote com eles ("Fazer Carga do Processo").

Hoje a funcionalidade apresenta dois problemas em produção:

1. **Alguns processos não geram vista.**
2. **Planilhas (XLS/XLSX) não são juntadas ao PDF final.** Arquivos que não são PDF saem soltos dentro de um ZIP.

A correção oficial dentro do SIGA tem previsão longa.

## Causas prováveis identificadas no código atual

| Problema | Causa provável |
|---|---|
| PDF final corrompido ou duplicado | A rotina de paginação grava o resultado do PDFBox no mesmo buffer que já contém o PDF do iText, gerando dois PDFs concatenados num só arquivo. |
| Falha em processos grandes | As chaves dos documentos seguem na *query string* de um GET, o que estoura o limite de URL. A geração também é síncrona e roda inteira em memória. |
| Documentos somem sem aviso | Falhas ao obter um arquivo são apenas registradas no log, e o documento é descartado. |
| Erro na listagem | A ordenação por data quebra quando a data é nula. |
| Itens que não abrem | Documentos invalidados (chave `"0"`) e substituídos aparecem na lista sem distinção. |
| XLS fora do PDF | Não existe conversão: só PDFs são mesclados. |

São hipóteses fundamentadas no código, ainda não reproduzidas em ambiente controlado.

## Objetivo do protótipo

Comprovar, com processos reais que hoje falham:

1. que é possível gerar o dossiê desses processos;
2. que planilhas e outros formatos podem ser convertidos e incluídos no PDF final;
3. que todos os documentos relacionados ao requerimento ou processo podem ser listados para seleção.

O protótipo serve de base para apresentar a solução e solicitar acesso ao Pandora (repositório de arquivos) e ao ambiente Kubernetes do SIGA.

## Escopo do protótipo

**Dentro do escopo**

- Consulta por número de requerimento, número de processo, CPF/CNPJ ou nome do interessado.
- Exibição dos dados do requerimento ou processo, da situação e da tarefa atual do fluxo.
- Lista de **todos** os documentos relacionados, indicando origem, data e sigilo.
- Visualização de cada documento em modal.
- Seleção dos documentos que entram no dossiê.
- Geração do dossiê em PDF único, em ordem cronológica, com capa e sumário.
- Conversão de planilhas, documentos de texto e imagens para PDF.
- Pré-visualização do dossiê antes do download.
- Dossiê de requerimento não protocolado e de processo. A consulta pode ser feita por qualquer um dos dois números. O número do requerimento é único; o número de processo pode se repetir em mais de um requerimento (1:N, dado real do SIGA), e nesse caso a consulta lista todos para o usuário escolher.
- Tarefa atual do fluxo com o nome do responsável, visível só para Gestor e Administrador (nunca o CPF do servidor).
- Documentos pessoais vindos do SCORP. DAR do Arrecadação, se a forma de obtenção for confirmada.
- Login simulado com três perfis: Operador (só processos em que é o interessado), Gestor (todos os processos e sigilosos) e Administrador (irrestrito).
- Auditoria dividida em documentos e acessos, com eventos mantidos apenas em memória.
- Tela de configurações (Administrador): usuários e perfis, regras do dossiê, conversão, conexões e retenção da auditoria.
- Sem integração de escrita com o SIGA: o serviço apenas lê o banco e obtém arquivos.
- Público atual: **interno**. O acesso de interessados externos (perfil Operador como interessado, RN07) é regra futura; no protótipo o Operador é simulado como interessado para demonstrar a regra.

**Fora do escopo do protótipo**

- Autenticação real (Portal ou outro provedor).
- Acesso direto ao Pandora.
- Implantação no Kubernetes.
- Persistência de dossiês, gravação da auditoria em banco e fila durável.

## Critérios de validação

O protótipo é considerado bem-sucedido se, para um conjunto de processos escolhidos (os que falham hoje e os que têm planilhas):

1. o dossiê é gerado sem erro;
2. as planilhas aparecem no PDF legíveis, sem cortes de colunas que impeçam a leitura;
3. se algum documento selecionado não puder ser incluído, nenhum dossiê incompleto é entregue sem decisão explícita do usuário; quando ele decide remover, o PDF registra o documento e o motivo em "Documentos não incluídos" (S2.2);
4. a lista de documentos inclui os itens que a Vista atual não exibe (ver [04 — Modelo de dados](04-modelo-de-dados.md)).

A lista de processos de teste será mantida em `docs/casos-de-teste.md`, a ser criado.
