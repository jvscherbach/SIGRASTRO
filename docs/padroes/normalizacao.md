# Normalização (3FN) e desnormalizações

Toda tabela do schema próprio nasce em 3FN. Desnormalização só deliberada e documentada.

## Checklist

1. **1FN**: nada de listas numa coluna. Documentos de um dossiê → `DOSSIE_PEDIDO_DOCUMENTO`; alterações de configuração → `AUDITORIA_EVENTO_ALTERACAO`.
2. **2FN**: com PK composta, nenhum atributo depende de parte da chave.
3. **3FN**: sem dependência transitiva. Ex.: perfil e nome do usuário pertencem a `USUARIO`, não a cada pedido de dossiê.

## Desnormalização deliberada do Rastro: o "retrato" na auditoria

A auditoria grava **cópias** de dados que pertencem a outros lugares: nome e perfil do usuário, número do processo/requerimento, título, nome de arquivo e sigilo dos documentos.

- Justificativa: a auditoria registra **o que era verdade no momento do evento**. Se o título de um documento mudar no SIGA, ou o perfil do usuário mudar, o evento antigo não pode mudar.
- Não há sincronização: é um retrato, não uma cópia mantida em dia. Isso deve constar no `COMMENT ON` das colunas.
- Não aplicar o mesmo raciocínio fora da auditoria. `DOSSIE_PEDIDO` referencia o usuário por `ID_USUARIO`, sem copiar nome.

## Fronteira com o SIGA

- Nenhuma tabela do schema próprio replica dados do SIGA para consulta (ex.: cópia de `REQUERIMENTO` ou `DOCUMENTO_PROCESSO`). A fonte de verdade é o SIGA, lido por `integration.siga`.
- Cache, se um dia for necessário, deve ser medido e documentado, com expiração explícita.

## A cada tabela nova

Verificar se ela cria nova fonte de verdade concorrente, mais um ponto de sincronização ou revela entidade compartilhada. Registrar a resposta no dicionário de dados.
