# Agendamento de tarefas

## Decisão para a fase atual

Usar o agendador do próprio Spring (`@EnableScheduling` + `@Scheduled`), dentro da aplicação. **Sem Quartz, sem cron externo, sem ferramenta adicional.**

Justificativa: o protótipo já precisa de uma tarefa periódica (apagar dossiês expirados, RNF05) e o escopo é pequeno. Ferramentas maiores só se justificam com várias réplicas ou tarefas que precisem sobreviver a reinícios com garantia.

## Tarefas

| Tarefa | Fase | Módulo dono | Frequência | O que faz |
|---|---|---|---|---|
| Limpeza de dossiês expirados | **agora** | `dossie` | a cada 5 min (`fixedDelay`) | apaga PDF e temporários de pedidos expirados ou interrompidos; marca o pedido como `EXPIRADO` |
| Limpeza de temporários órfãos | **agora** | `dossie` | na inicialização e a cada 1 h | apaga diretórios de trabalho sem pedido correspondente (ex.: após reinício do container) |
| Expurgo da auditoria por retenção | definitivo | `auditoria` | diária, fora do horário de uso | remove eventos mais antigos que a retenção de cada grupo, com usuário de banco próprio (`VISTAS_EXPURGO`) |
| Retomada da fila durável | definitivo | `dossie` | na inicialização | reprocessa ou marca como falha os pedidos `PROCESSANDO` deixados por um reinício |
| Aviso de credencial de arquivos perto de expirar | opcional | `configuracao` | a cada 1 h | registra alerta quando o token tiver menos de 6 h |

Não criar tarefas especulativas além destas.

## Regras de implementação

- A classe agendada fica em `<modulo>.infrastructure.agendamento` e **só chama um caso de uso** de `application`; nenhuma regra dentro do método agendado.
- Intervalos e horários vêm de configuração (ex.: `vistas.agendamento.limpeza-dossie=PT5M`), nunca fixos no código.
- `AGENDAMENTO_ENABLED=false` desliga todas as tarefas (útil em testes e, no futuro, em réplicas que não devem agendar).
- Toda execução registra no log início, fim, quantidade processada e duração, com um identificador de execução no MDC no lugar do `correlationId`.
- Tarefas idempotentes: rodar duas vezes seguidas não pode causar erro nem efeito duplicado.
- Exceção numa execução não pode derrubar o agendador: capturar, registrar e seguir para a próxima execução.
- Tarefas não usam conexão do SIGA.
- Pool de execução do agendador separado do pool de geração de dossiês (`spring.task.scheduling.pool.size=1` na fase inicial).

## Quando evoluir

| Situação | Ação |
|---|---|
| Mais de uma réplica da API | adicionar **ShedLock** com tabela de trava no schema próprio (`AGENDAMENTO_TRAVA`), para cada tarefa rodar em uma réplica só |
| Expurgo pesado demais para a aplicação | mover para job do banco (`DBMS_SCHEDULER`) ou do orquestrador, com decisão registrada |
| Necessidade de agendar por usuário/regra de negócio | reavaliar Quartz; não antes |
