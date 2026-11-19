package br.gov.mt.sema.rastro.configuracao.application;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import br.gov.mt.sema.rastro.configuracao.application.port.RepositorioDeSobreposicoes;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.configuracao.domain.ClasseDeAplicacao;
import br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Valor de cada parâmetro agora. As portas ({@code ParametrosDoDossie} e as demais, em {@code config}) leem daqui e
 * passam o valor do ambiente como padrão: o contrato delas não muda.
 *
 * <p>Um pedido de dossiê não muda no meio: na criação, {@link #retrato()} guarda os valores de classe "próxima geração"
 * e a geração roda dentro de {@link #comRetrato}, em que a thread lê do retrato. Assim vale para tudo o que a geração
 * consulta, inclusive a conversão e o download, sem levar parâmetro por parâmetro até os adapters.
 */
@Service
public class ParametrosEmVigor {

    private final RepositorioDeSobreposicoes sobreposicoes;
    private final Environment ambiente;
    private final ThreadLocal<Map<String, String>> congelados = new ThreadLocal<>();

    public ParametrosEmVigor(RepositorioDeSobreposicoes sobreposicoes, Environment ambiente) {
        this.sobreposicoes = sobreposicoes;
        this.ambiente = ambiente;
    }

    public int inteiro(String nome, int doAmbiente) {
        String valor = valor(nome);
        return valor == null ? doAmbiente : Integer.parseInt(valor);
    }

    public boolean booleano(String nome, boolean doAmbiente) {
        String valor = valor(nome);
        return valor == null ? doAmbiente : Boolean.parseBoolean(valor);
    }

    public String texto(String nome, String doAmbiente) {
        String valor = valor(nome);
        return valor == null ? doAmbiente : valor;
    }

    /** O padrão que vem do ambiente; é a fonte ao reiniciar. */
    String padrao(DefinicaoDeParametro definicao) {
        String doAmbiente = ambiente.getProperty(definicao.propriedade(), definicao.padrao());
        return definicao.aceitar(doAmbiente).orElse(definicao.tipo() == DefinicaoDeParametro.Tipo.OPCAO
                ? definicao.padrao() : doAmbiente);
    }

    /** Os valores de classe "próxima geração" neste instante. */
    public Map<String, String> retrato() {
        Map<String, String> atuais = sobreposicoes.todas();
        Map<String, String> retrato = new HashMap<>();
        for (DefinicaoDeParametro definicao : CatalogoDeParametros.todos()) {
            if (definicao.classe() == ClasseDeAplicacao.PROXIMA_GERACAO) {
                retrato.put(definicao.nome(), atuais.getOrDefault(definicao.nome(), padrao(definicao)));
            }
        }
        return Map.copyOf(retrato);
    }

    public <T> T comRetrato(Map<String, String> retrato, Supplier<T> tarefa) {
        Map<String, String> anterior = congelados.get();
        congelados.set(retrato);
        try {
            return tarefa.get();
        } finally {
            if (anterior == null) {
                congelados.remove();
            } else {
                congelados.set(anterior);
            }
        }
    }

    private String valor(String nome) {
        Map<String, String> retrato = congelados.get();
        if (retrato != null && retrato.containsKey(nome)) {
            return retrato.get(nome);
        }
        return sobreposicoes.todas().get(nome);
    }
}
