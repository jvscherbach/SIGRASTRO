package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Limpeza (D22, RNF05): expira os dossiês vencidos (apagando o arquivo), esquece pedidos encerrados depois de
 * {@code DOSSIE_HISTORICO_HORAS} do vencimento (histórico de "Dossiês gerados", S4) e apaga pastas
 * temporárias sem pedido (por exemplo, depois de um reinício, já que a fila é em memória). Idempotente e sem SIGA.
 */
@Service
public class LimparDossies {

    private static final Logger log = LoggerFactory.getLogger(LimparDossies.class);

    private static final java.util.regex.Pattern NOME_DE_PEDIDO = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final RepositorioDePedidos pedidos;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public LimparDossies(RepositorioDePedidos pedidos, ParametrosDoDossie parametros, Clock relogio) {
        this.pedidos = pedidos;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** @return quantos pedidos foram expirados ou esquecidos nesta execução */
    public int expirarVencidos() {
        LocalDateTime agora = LocalDateTime.now(relogio);
        int tratados = 0;
        for (PedidoDeDossie pedido : pedidos.todos()) {
            if (pedido.venceu(agora) && pedido.expirar()) {
                // Concluído ou à espera de decisão, sem atividade dentro do TTL (S2.2).
                ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
                tratados++;
            }
            // Encerrado, o registro continua (sem arquivo) para "Dossiês gerados" mostrar a situação (S4).
            if (pedido.status().terminal() && foraDoHistorico(pedido, agora, parametros)) {
                ArquivosTemporarios.apagarPasta(parametros.diretorio().resolve(pedido.id()));
                pedidos.remover(pedido.id());
                tratados++;
            }
        }
        return tratados;
    }

    /** Encerrado e vencido há mais de {@code DOSSIE_HISTORICO_HORAS}: sai da lista e do repositório. */
    static boolean foraDoHistorico(PedidoDeDossie pedido, LocalDateTime agora, ParametrosDoDossie parametros) {
        return pedido.venceu(agora.minusHours(Math.max(0, parametros.historicoHoras())));
    }

    /**
     * Apaga as pastas de pedido (nome de UUID) do diretório temporário que não pertencem a um pedido ativo. Na subida a lista
     * de pedidos está vazia, então isso esvazia o diretório.
     */
    public int apagarOrfaos() {
        Path base = parametros.diretorio();
        if (!Files.isDirectory(base)) {
            return 0;
        }
        Set<String> ativos = pedidos.todos().stream()
                .filter(p -> !p.status().terminal())
                .map(PedidoDeDossie::id)
                .collect(Collectors.toSet());
        int apagados = 0;
        try (Stream<Path> filhos = Files.list(base)) {
            // Só mexe no que o próprio dossiê cria (pasta com nome de UUID): VISTAS_DOSSIE_DIRETORIO apontado para uma
            // pasta com outros arquivos não pode apagar nada que não seja temporário de pedido.
            List<Path> orfaos = filhos.filter(p -> NOME_DE_PEDIDO.matcher(p.getFileName().toString()).matches())
                    .filter(p -> !ativos.contains(p.getFileName().toString())).toList();
            for (Path orfao : orfaos) {
                if (Files.isDirectory(orfao)) {
                    ArquivosTemporarios.apagarPasta(orfao);
                } else {
                    ArquivosTemporarios.apagarArquivo(orfao);
                }
                apagados++;
            }
        } catch (IOException e) {
            log.warn("Não foi possível listar o diretório temporário dos dossiês: {}", e.getMessage());
        }
        return apagados;
    }
}
