# MER abstrato e mapeado

## Abstrato (conceitual)

Entidades do Rastro: Usuário, Perfil, Parâmetro, Pedido de dossiê, Documento do pedido, Evento de auditoria, Documento do evento, Alteração do evento. Entidades **externas** (do SIGA): Requerimento, Processo, Interessado, Documento, Tarefa. Notação de Chen; sem tipos nem constraints.

Usar o abstrato para decidir com a área se algo é entidade ou atributo (ex.: "perfil" é atributo do usuário enquanto os perfis forem fixos).

## Mapeado (físico)

Nomes de `nomenclatura.md`, tipos de `tipos-dados.md`. Agrupar visualmente por módulo (`acesso`, `configuracao`, `auditoria`, `dossie`) e desenhar as entidades do SIGA num bloco separado, marcado como externo.

- N:N vira associativa (`DOSSIE_PEDIDO_DOCUMENTO`).
- Relação com o SIGA é **lógica** (coluna com ID, sem FK), sempre tracejada no diagrama.
- Relação entre módulos do Rastro também é lógica quando cruza módulos (ex.: `AUDITORIA_EVENTO.LOGIN_USUARIO` não tem FK para `USUARIO`: o evento sobrevive à exclusão/alteração do usuário).

## Como desenhar

O MER mapeado fica em `docs/database/mer.mmd` (Mermaid `erDiagram`), renderizado em `docs/database/mer.html`; o abstrato, em `docs/database/mer-conceitual.mmd`. Todos são gerados como código, versionados e atualizados na mesma entrega de cada migration (ver `documentacao.md`).

## Checklist

- Toda entidade do abstrato tem tabela ou justificativa?
- Cardinalidades corretas?
- Nomes conforme o padrão?
- Módulo de cada tabela visível?
- Referências ao SIGA e entre módulos marcadas como lógicas?
