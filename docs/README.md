# Rastro — Protótipo

Serviço externo ao SIGA Licenciamento que gera o **dossiê em PDF** de um requerimento ou processo, reunindo os documentos selecionados em ordem cronológica. O módulo do dossiê se chama **Vistas** (Vista do Processo).

> Produto: **Rastro** (antes "SIGA Vistas"). Ver [01 — Visão geral](01-visao-geral.md) e a decisão D73.

## Status

**Protótipo de validação.** O objetivo é comprovar que os problemas atuais da funcionalidade "Vista do Processo" do SIGA têm solução. Com essa comprovação em mãos, a ideia será apresentada e serão solicitados os acessos necessários para a versão definitiva.

## Índice

| Documento | Conteúdo |
|---|---|
| [01 — Visão geral](01-visao-geral.md) | Contexto, problema, objetivo, escopo e critérios de validação |
| [02 — Requisitos](02-requisitos.md) | Requisitos funcionais, não funcionais e regras de negócio |
| [03 — Arquitetura](03-arquitetura.md) | Componentes, stack, execução em Docker e configuração |
| [04 — Modelo de dados](04-modelo-de-dados.md) | Tabelas lidas no Oracle, regras de vigência e consultas de referência |
| [05 — Integrações](05-integracoes.md) | API do Licenciamento, Camunda, SCORP e Arrecadação |
| [06 — Geração do dossiê](06-geracao-do-dossie.md) | Pipeline de montagem do PDF, conversões e tratamento de falhas |
| [07 — API do protótipo](07-api.md) | Endpoints expostos pelo back-end |
| [08 — Telas](08-telas.md) | Informações exibidas e comportamento das telas |
| [09 — Decisões](09-decisoes.md) | Registro das decisões técnicas e seus motivos |
| [10 — Pendências e riscos](10-pendencias-e-riscos.md) | Pontos em aberto, riscos e itens da versão definitiva |
| [11 — Auditoria](11-auditoria.md) | Auditoria de documentos e de acessos, eventos e modelo lógico |
| [12 — Configurações](12-configuracoes.md) | Perfis, usuários e parâmetros administráveis do serviço |
| [13 — Padrões técnicos](13-padroes-tecnicos.md) | Arquitetura de código, módulos, persistência, banco, erros HTTP e verificação (detalhes em [`padroes/`](padroes/)) |
| [14 — Plano de sprints](14-plano-de-sprints.md) | Sprints do protótipo, entregas, aceite e validações |
| [Ferramentas do Claude Code](claude-code/README.md) | Guardas (hooks), servidor MCP e subagentes versionados no repositório (S6) |
| [Homologação da S1](homologacao/S1-roteiro-homologacao.md) | Roteiro manual de conexão e leitura reais do SIGA |
| [Homologação da S1.1](homologacao/S1.1-roteiro-homologacao.md) | Roteiro manual de objeto, prioridade, responsável, processo 1:N e quadro do topo |
| [Homologação da S2](homologacao/S2-roteiro-homologacao.md) | Roteiro manual de documentos, token, dossiê real, falhas, acesso e quadro do topo |
| [Homologação da S3](homologacao/S3-roteiro-homologacao.md) | Roteiro manual da conversão (planilhas, textos, imagens), do modal Visualizar, da falha de conversão e da escolha do modo de planilha |
| [Homologação da S4](homologacao/S4-roteiro-homologacao.md) | Roteiro manual das regras de acesso por perfil, geometrias, documentos pessoais (SCORP) e "Dossiês gerados" |
| [Homologação da S6](roteiros/s6-homologacao.md) | Roteiro manual dos defeitos corrigidos (nome × tipo, layout, disco cheio, teste de conexão), do CI e da demonstração das ferramentas do Claude Code |
| [Homologação da S5](homologacao/S5-roteiro-homologacao.md) | Roteiro manual da geração em segundo plano, auditoria, usuários, parâmetros, conexões e reinício do serviço |
| [Casos de teste](casos-de-teste.md) | Processos de teste (que falham hoje e com planilhas) e o resultado de cada dossiê |
| [Prompts das sprints](prompts/) | Prompt de cada sprint para o Claude Code |

## Fontes

Os documentos se baseiam em duas análises somente leitura feitas no código:

- repositório do SIGA Licenciamento (implementação atual da Vista do Processo, modelo de dados, Camunda e arquivos);
- repositório `sema-web-core` (autenticação e cliente Pandora).

Itens marcados **NÃO CONFIRMADO** dependem de verificação no banco ou em repositórios não analisados.
