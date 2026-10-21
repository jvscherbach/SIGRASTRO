# MER abstrato e mapeado

## Abstrato (conceitual)

Entidades do Vistas: Usuário, Perfil, Parâmetro, Pedido de dossiê, Documento do pedido, Evento de auditoria, Documento do evento, Alteração do evento. Entidades **externas** (do SIGA): Requerimento, Processo, Interessado, Documento, Tarefa. Notação de Chen; sem tipos nem constraints.

Usar o abstrato para decidir com a área se algo é entidade ou atributo (ex.: "perfil" é atributo do usuário enquanto os perfis forem fixos).

## Mapeado (físico)

Nomes de `nomenclatura.md`, tipos de `tipos-dados.md`. Agrupar visualmente por módulo (`acesso`, `configuracao`, `auditoria`, `dossie`) e desenhar as entidades do SIGA num bloco separado, marcado como externo.

- N:N vira associativa (`DOSSIE_PEDIDO_DOCUMENTO`).
- Relação com o SIGA é **lógica** (coluna com ID, sem FK), sempre tracejada no diagrama.
- Relação entre módulos do Vistas também é lógica quando cruza módulos (ex.: `AUDITORIA_EVENTO.LOGIN_USUARIO` não tem FK para `USUARIO`: o evento sobrevive à exclusão/alteração do usuário).

## Como desenhar

Mermaid `erDiagram` para o mapeado; Graphviz/DOT para o abstrato. Gerar como código, versionado em `docs/`, atualizado a cada migration.

## Checklist

- Toda entidade do abstrato tem tabela ou justificativa?
- Cardinalidades corretas?
- Nomes conforme o padrão?
- Módulo de cada tabela visível?
- Referências ao SIGA e entre módulos marcadas como lógicas?
