# Connection pooling (HikariCP)

Dois pools independentes em `application.yml`, valores por variável de ambiente.

**Fase inicial (validação do protótipo):** pools propositalmente pequenos — 2 conexões para o SIGA e 3 para o schema próprio. Só aumentar com medição e com o limite aceito pelo DBA.

```yaml
vistas:
  datasource:
    siga:
      url: ${SIGA_DB_URL}
      username: ${SIGA_DB_USER}
      password: ${SIGA_DB_PASSWORD}
      hikari:
        pool-name: siga-leitura
        read-only: true
        maximum-pool-size: ${SIGA_DB_POOL_MAX:2}
        minimum-idle: 0
        connection-timeout: ${SIGA_DB_CONN_TIMEOUT_MS:5000}
        initialization-fail-timeout: -1   # a api sobe mesmo com o SIGA fora do ar
        max-lifetime: ${SIGA_DB_MAX_LIFETIME_MS:1500000}
        data-source-properties:
          oracle.jdbc.ReadTimeout: ${SIGA_DB_READ_TIMEOUT_MS:30000}
    vistas:   # somente na versão definitiva
      url: ${VISTAS_DB_URL}
      username: ${VISTAS_DB_USER}
      password: ${VISTAS_DB_PASSWORD}
      hikari:
        pool-name: vistas
        maximum-pool-size: ${VISTAS_DB_POOL_MAX:3}
```

## Pool de leitura do SIGA: pequeno de propósito

- O banco do SIGA atende o próprio SIGA em produção. O Rastro é um consumidor secundário e não pode competir por sessões.
- As consultas são curtas (lista de documentos, tarefa atual). A parte demorada da geração do dossiê é download e conversão, que **não** seguram conexão.
- Regra de código: nunca manter conexão do SIGA aberta durante download, conversão ou montagem do PDF. Ler os metadados, liberar a conexão, depois processar.
- `maximum-pool-size × réplicas` é o que chega ao SIGA. Pedir ao DBA do SIGA o limite aceitável antes de fixar.
- `oracle.jdbc.ReadTimeout` limita consulta travada; o valor deve ser menor que o timeout HTTP da requisição.

## `minimum-idle: 0` e inicialização sem o SIGA

- Na fase inicial o pool **não mantém sessão ociosa** no SIGA (`minimum-idle: 0`): conexões abrem sob demanda e fecham após `idleTimeout`. Isso evita ocupar sessões do banco do SIGA sem uso e evita tentativas de reconexão em segundo plano quando ele está inacessível. O custo é abrir uma conexão na primeira consulta depois de um período ocioso (aceitável no protótipo; reavaliar com medição).
- `initialization-fail-timeout: -1`: o Hikari não impede a inicialização da `api` se o SIGA estiver fora do ar; as consultas respondem `503 SIGA_INDISPONIVEL`.
- O banco do SIGA **não entra** no `/actuator/health` (`management.health.db.enabled=false`): a indisponibilidade dele não pode deixar a `api` `unhealthy` nem impedir o `front` de subir (ele depende do health da `api`).
- Sem `SIGA_DB_URL` (ausente **ou vazia**), nenhum datasource é criado; a leitura responde 503 pelo adapter "SIGA não configurado".

## Relação com `DOSSIE_THREADS`

`DOSSIE_THREADS` (gerações simultâneas) não precisa de conexões do SIGA além das leituras iniciais. Aumentar threads aumenta uso de disco temporário, memória do LibreOffice e chamadas à API de arquivos, não o pool. Dimensionar threads pela memória do container.

## `max-lifetime`

Menor que qualquer timeout de firewall/proxy entre o container e o Oracle, para evitar conexões mortas silenciosamente.

## Ao propor números

Partir de: limite de sessões aceito pelo DBA, número de réplicas e tráfego esperado. Nunca fixar números "que costumam funcionar" sem essa conta.
