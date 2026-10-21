# 10 — Pendências e riscos

## Pendências para o protótipo

| # | Pendência | Como resolver |
|---|---|---|
| P01 | Tabelas de objetivo, finalidade, atividade, bloco Tipo/Objetivo, regra de prioridade de atendimento e coluna de descrição de `TIPO_REQUERIMENTO`. **Status (S1.1): resolvida** pelo mapeamento do responsável (21/10/2026), registrado na seção "[Mapeamento de objeto e prioridade (P01)](04-modelo-de-dados.md#mapeamento-de-objeto-e-prioridade-p01)" de 04 e ligado no SQL. Os itens "Ainda NÃO CONFIRMADO" daquela seção seguem como P19 | — |
| P03 | Diferença entre `REQUERIMENTO.NUMERO_PROCESSO` e `PROCESSO.NUMERO`. **Status (S1.1): resolvida.** O SIGA Vistas usa `REQUERIMENTO.NUMERO_PROCESSO` e **não consulta** a tabela `PROCESSO` (histórico do que foi protocolado, irrelevante para a solução), nem filtra por `PROCESSO.ORIGEM`. O número de processo é tratado como 1:N (D31) | — |
| P04 | Origens não exibidas hoje: confirmar se duplicam `DOCUMENTO_PROCESSO` e qual é o vínculo de `EXTRATO_PUBLICACAO` | Consulta no banco |
| P05 | Tipos reais das colunas de sigilo (`SIGILOSO`, `SIGILO`, `IND_SIGILOSO`) | Consulta ao dicionário de dados |
| P06 | Base, autenticação e validade das chaves do SCORP no `/api/arquivo` | Teste com o token e leitura do cliente SCORP no SIGA |
| P07 | Origem e forma de obtenção do DAR | Análise do cliente de Arrecadação no SIGA |
| P08 | Header exato do token (Bearer ou cookie) | Captura no navegador |
| P09 | Lista de processos de teste (que falham hoje e com planilhas) | Levantamento, registrado em `casos-de-teste.md` |
| P10 | Usuário de banco somente leitura com os privilégios de [04](04-modelo-de-dados.md). **Status (S1): aguardando homologação manual;** o script de referência para o DBA está em `homologacao/S1-roteiro-homologacao.md` | Criação pelo DBA |
| P11 | Rede entre a máquina do Docker e o Oracle/APIs. **Status (S1): aguardando homologação manual** (passo de rede no roteiro) | Verificação local |
| P12 | Operador é o interessado (RN07): definir como o CPF/CNPJ do usuário chega ao serviço (autenticação gov.br/Portal) e se representantes legais também acessam | Definição com a área |
| P13 | **Regra futura:** abertura a interessados externos (perfil Operador como interessado). Por ora o serviço é **interno**. Quando abrir, rever exposição e autenticação | Definição de arquitetura no momento da abertura |
| P14 | Instância Oracle 19c do schema próprio em homologação/produção (SGBD decidido: Oracle, D21) | Decisão com a infraestrutura |
| P15 | Infraestrutura de homologação/produção (alvo provável: Kubernetes do SIGA), registry e gestão de segredos | Decisão com a infraestrutura |
| P16 | Versão real do Oracle do SIGA e limite de sessões aceito para o pool de leitura | Consulta ao DBA do SIGA |
| P17 | Anomalia de dado no SIGA: o número de processo `445/2022` aparece em dois requerimentos (3391/2022, concluído; 3798/2023, cancelado pelo cadastrante), no mesmo `ID_PROCESSO`. O SIGA Vistas lista os dois (1:N) | Comunicar à equipe do SIGA |
| P18 | 591 tarefas com responsável (CPF de 11 dígitos) sem nome em `SERVIDOR` nem em `PESSOA` (21/10/2026): aparecem como "Responsável não identificado" | Levar à equipe do SIGA; avaliar consulta ao SCORP na versão definitiva |
| P19 | Itens NÃO CONFIRMADOS da S1.1: descrições oficiais dos critérios de prioridade; conferência na tela de um processo de licenciamento e de um com critério de prioridade; tarefas atribuídas a grupo (hoje "Não atribuída"); nomes das colunas de ligação `OBJETIVO_REQUERIMENTO_OUTORGA.ID_OBJETIVO_OUTORGA`, `REQUERIMENTO_FINALIDADE_ATIVIDADE.ID_FINALIDADE_ATIVIDADE` e `FINALIDADE_ATIVIDADE.ID_FINALIDADE` (assumidos pelo padrão do SIGA; não constam da seção P01). **Status: aguardando homologação** (`homologacao/S1.1-roteiro-homologacao.md`) | Roteiro de homologação da S1.1 |

## Riscos

| # | Risco | Impacto | Mitigação |
|---|---|---|---|
| R01 | Qualidade da conversão de planilhas largas | Critério central de validação | Ajuste de escala por aba. Alternativa: uma página por aba. |
| R02 | Token pessoal expira durante testes | Interrupção da geração | Erro explícito e orientação para recaptura |
| R03 | Acessos registrados no CPF de quem capturou o token | Rastreabilidade | Uso local e restrito, declarado na apresentação |
| R04 | Mudança no modelo de dados do SIGA | Consultas quebram | Concentrar SQL num único componente; em produção, usar views |
| R05 | Migração do SIGA para Camunda 8 | Tarefa atual indisponível | Isolar a consulta do Camunda num componente substituível |
| R06 | Dados incompletos de data | Ordem cronológica aproximada | Regra de preferência (RN03) declarada na capa |
| R07 | Processos com centenas de documentos | Tempo de geração | Paralelismo na obtenção e acompanhamento de progresso |
| R08 | Falha transitória num documento impede o dossiê inteiro (D12) | Retrabalho do usuário | Nova tentativa com um clique; avaliar retentativa automática por documento antes de interromper |
| R09 | Busca por nome usa `UPPER(...) LIKE '%...%'` e não usa índice no SIGA. **S1.1:** a busca por CPF/CNPJ deixou de usar `REGEXP_REPLACE` e compara direto (`p.CPF_CNPJ = :digitos`), então pode usar o índice de `PESSOA.CPF_CNPJ`, se houver (conferido no roteiro da S1.1); o risco ficou só para o nome | Consulta lenta por nome em tabela grande de pessoas | Medir na homologação; se necessário, pedir ao DBA uma view ou índice funcional, ou exigir mais critérios; o pool pequeno e o `ReadTimeout` limitam o dano |
| R10 | Os testes de integração rodam em Oracle 23 Free, não em 19c | SQL que passa no teste mas falha no ambiente real | Lista de recursos proibidos, SQL simples (`OFFSET/FETCH`, `REGEXP_LIKE`, `KEEP (DENSE_RANK FIRST ...)`, `LIKE`), roteiro de homologação e teste de fumaça contra o SIGA real |
| R11 | Tipos reais das colunas do SIGA (data, número, texto) diferentes das hipóteses do recorte de teste | Erro de leitura só visto no SIGA real | Teste de fumaça e passo de conferência do dicionário no roteiro de homologação |
| R12 | Instância com mais de uma tarefa ativa (fluxos paralelos) | A tela mostra só a mais recente | Aceitável no protótipo; reavaliar se a área precisar de todas |
| R13 | Tarefa atual depende das tabelas do motor de fluxo do SIGA (risco R05 ampliado) | Estado "Tarefa indisponível" se as tabelas mudarem ou faltar grant | Falha isolada: não derruba o detalhe |
| R14 | Busca por número de processo sem índice em `REQUERIMENTO.NUMERO_PROCESSO`: lê a tabela inteira (cerca de 63 mil requerimentos em 21/10/2026) | Baixo: consulta mais lenta, não quebra | Medir na homologação; se crescer, sugerir índice à equipe do SIGA (o SIGA Vistas não cria objeto no SIGA) |
| R15 | `front/package.json` usa `overrides` para `vite` (`^7.3.7`) e `@modelcontextprotocol/sdk` (`^1.31.1`) para zerar o `npm audit` | Versão forçada pode divergir do que o Angular espera e quebrar o build numa atualização | Revisar a cada atualização do Angular e remover os `overrides` quando as dependências do Angular já trouxerem versões corrigidas |
| R16 | Objeto e prioridade fazem parte do detalhe (sem estado parcial): falha de leitura em uma dessas tabelas derruba o detalhe inteiro | Detalhe com 503/500 por falta de grant ou mudança numa tabela de outorga | Grants conferidos no roteiro da S1.1 e no teste de fumaça; a tarefa atual continua isolada (R13) |

## Itens da versão definitiva

- Credencial de serviço: API key emitida pelo serviço de identidade, ou acesso direto ao Pandora.
- Autenticação real e perfil de Gestor para sigilosos.
- Schema próprio para usuários, perfis, auditoria (modelo lógico em [11](11-auditoria.md)) e fila durável.
- Prazos definitivos de retenção da auditoria.
- Implantação no Kubernetes do SIGA.
- Inclusão definitiva de SCORP e DAR.

## Achados de segurança no SIGA (a reportar separadamente)

Identificados durante a análise e independentes deste projeto:

1. `GET /api/documento-processo/{chave}/pdf` devolve arquivos sem autenticação.
2. Nenhum endpoint de arquivo valida dono do processo ou sigilo no back-end. O controle existe só na interface.
3. O `sema-web-core` desabilita a validação de certificados TLS em toda a JVM.
4. Uma única chave simétrica assina tokens de usuário e de cliente e é distribuída a todos os serviços.
5. Cookie de autorização com `secure` desligado por padrão e validade longa dos tokens.
