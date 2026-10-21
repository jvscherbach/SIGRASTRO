# 10 — Pendências e riscos

## Pendências para o protótipo

| # | Pendência | Como resolver |
|---|---|---|
| P01 | Tabelas de objetivo, finalidade, atividade, bloco Tipo/Objetivo e regra de prioridade de atendimento | Mapear no código do SIGA (tela atual) ou no banco |
| P03 | Diferença entre `REQUERIMENTO.NUMERO_PROCESSO` e `PROCESSO.NUMERO` | Consulta no banco |
| P04 | Origens não exibidas hoje: confirmar se duplicam `DOCUMENTO_PROCESSO` e qual é o vínculo de `EXTRATO_PUBLICACAO` | Consulta no banco |
| P05 | Tipos reais das colunas de sigilo (`SIGILOSO`, `SIGILO`, `IND_SIGILOSO`) | Consulta ao dicionário de dados |
| P06 | Base, autenticação e validade das chaves do SCORP no `/api/arquivo` | Teste com o token e leitura do cliente SCORP no SIGA |
| P07 | Origem e forma de obtenção do DAR | Análise do cliente de Arrecadação no SIGA |
| P08 | Header exato do token (Bearer ou cookie) | Captura no navegador |
| P09 | Lista de processos de teste (que falham hoje e com planilhas) | Levantamento, registrado em `casos-de-teste.md` |
| P10 | Usuário de banco somente leitura com os privilégios de [04](04-modelo-de-dados.md) | Criação pelo DBA |
| P11 | Rede entre a máquina do Docker e o Oracle/APIs | Verificação local |
| P12 | Operador é o interessado (RN07): definir como o CPF/CNPJ do usuário chega ao serviço (autenticação gov.br/Portal) e se representantes legais também acessam | Definição com a área |
| P13 | **Regra futura:** abertura a interessados externos (perfil Operador como interessado). Por ora o serviço é **interno**. Quando abrir, rever exposição e autenticação | Definição de arquitetura no momento da abertura |
| P14 | Instância Oracle 19c do schema próprio em homologação/produção (SGBD decidido: Oracle, D21) | Decisão com a infraestrutura |
| P15 | Infraestrutura de homologação/produção (alvo provável: Kubernetes do SIGA), registry e gestão de segredos | Decisão com a infraestrutura |
| P16 | Versão real do Oracle do SIGA e limite de sessões aceito para o pool de leitura | Consulta ao DBA do SIGA |

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
