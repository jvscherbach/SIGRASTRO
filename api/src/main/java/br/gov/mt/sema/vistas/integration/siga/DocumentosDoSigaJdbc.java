package br.gov.mt.sema.vistas.integration.siga;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.vistas.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
* Documentos do requerimento (docs/04), as seis origens da Vista do SIGA. Uma consulta por origem, em arquivo próprio, para isolar falhas e permitir
 * desligar uma origem (propriedade {@code vistas.documentos.origens.<origem>}). Uma origem desligada não é consultada.
 * Falha numa origem ligada derruba a listagem inteira (503 ou 500, tabela da S1), sem estado parcial. Cada consulta
 * termina e devolve a conexão ao pool antes do retorno: nada fica aberto durante o download dos arquivos.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class DocumentosDoSigaJdbc implements DocumentosDoRequerimento {

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<OrigemDocumento, String> consultas = new EnumMap<>(OrigemDocumento.class);
    private final OrigensLigadas ligadas;

    @Autowired
    DocumentosDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, OrigensLigadas ligadas) {
        this(sigaJdbcTemplate, EnumSet.allOf(OrigemDocumento.class), ligadas);
    }

    DocumentosDoSigaJdbc(NamedParameterJdbcTemplate jdbc, Set<OrigemDocumento> ligadas) {
        this(jdbc, ligadas, origem -> true);
    }

    private DocumentosDoSigaJdbc(NamedParameterJdbcTemplate jdbc, Set<OrigemDocumento> conhecidas, OrigensLigadas ligadas) {
        this.jdbc = jdbc;
        this.ligadas = ligadas;
        for (OrigemDocumento origem : conhecidas) {
            if (!origem.doSiga()) {
                continue;
            }
            consultas.put(origem, SigaSql.carregar(nomeDaConsulta(origem)));
        }
    }

    private static String nomeDaConsulta(OrigemDocumento origem) {
        return "documentos/" + origem.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    @Override
    public List<Documento> listar(long idRequerimento) {
        MapSqlParameterSource id = new MapSqlParameterSource("idRequerimento", idRequerimento);
        List<Documento> documentos = new ArrayList<>();
        for (Map.Entry<OrigemDocumento, String> consulta : consultas.entrySet()) {
            OrigemDocumento origem = consulta.getKey();
            if (!ligadas.ligada(origem)) {
                continue;
            }
            documentos.addAll(SigaFalhas.traduzir(() -> jdbc.query(consulta.getValue(), id, (rs, n) -> Documento.deLinha(
                    origem, rs.getString("ID"), rs.getLong("ID_REQUERIMENTO"), rs.getString("CHAVE"),
                    rs.getString("TITULO"), rs.getString("NOME_ARQUIVO"), DataHoraDoSiga.ler(rs, "DATA_DOC"),
                    DataHoraDoSiga.ler(rs, "DATA_FIM"), rs.getObject("SIGILO")))));
        }
        return documentos;
    }
}
