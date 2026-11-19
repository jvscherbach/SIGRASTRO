package br.gov.mt.sema.rastro.configuracao.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.configuracao.application.port.RepositorioDeSobreposicoes;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.configuracao.domain.ClasseDeAplicacao;
import br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Abas de parâmetros (RF26 a RF28): só Administrador. Salvar é tudo ou nada: valida tudo antes de aplicar, troca o conjunto
 * de uma vez e gera um único evento {@code CONFIG} com o antes e o depois. Sem mudança real, sem evento.
 */
@Service
public class GerirParametros {

    private final RepositorioDeSobreposicoes sobreposicoes;
    private final ParametrosEmVigor vigentes;
    private final PoliticaDeAcesso politica;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    public GerirParametros(RepositorioDeSobreposicoes sobreposicoes, ParametrosEmVigor vigentes, PoliticaDeAcesso politica,
            RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.sobreposicoes = sobreposicoes;
        this.vigentes = vigentes;
        this.politica = politica;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    public List<ParametroVigente> listar() {
        politica.exigirConfiguracoes();
        return situacao();
    }

    public synchronized List<ParametroVigente> salvar(Map<String, String> alteracoes) {
        politica.exigirConfiguracoes();
        Map<String, String> novas = new HashMap<>(sobreposicoes.todas());
        List<AlteracaoDoEvento> mudancas = new ArrayList<>();
        for (Map.Entry<String, String> alteracao : alteracoes.entrySet()) {
            DefinicaoDeParametro definicao = CatalogoDeParametros.buscar(alteracao.getKey())
                    .orElseThrow(() -> new ParametroInvalidoException(alteracao.getKey(),
                            "Parâmetro desconhecido ou não editável: " + nomeSeguro(alteracao.getKey()) + "."));
            String valor = definicao.aceitar(alteracao.getValue())
                    .orElseThrow(() -> new ParametroInvalidoException(definicao.nome(),
                            definicao.rotulo() + ": " + definicao.faixa()));
            String padrao = vigentes.padrao(definicao);
            String atual = novas.getOrDefault(definicao.nome(), padrao);
            if (valor.equals(atual)) {
                continue;
            }
            if (valor.equals(padrao)) {
                novas.remove(definicao.nome());
            } else {
                novas.put(definicao.nome(), valor);
            }
            mudancas.add(linha(definicao, atual, valor));
        }
        aplicar(novas, mudancas);
        return situacao();
    }

    /** Volta ao padrão do ambiente. Sem sobreposição, nada muda e não há evento. */
    public synchronized List<ParametroVigente> restaurar(String nome) {
        politica.exigirConfiguracoes();
        DefinicaoDeParametro definicao = CatalogoDeParametros.buscar(nome)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Parâmetro não encontrado."));
        Map<String, String> novas = new HashMap<>(sobreposicoes.todas());
        String anterior = novas.remove(nome);
        if (anterior != null) {
            aplicar(novas, List.of(linha(definicao, anterior, vigentes.padrao(definicao))));
        }
        return situacao();
    }

    private void aplicar(Map<String, String> novas, List<AlteracaoDoEvento> mudancas) {
        if (mudancas.isEmpty()) {
            return;
        }
        sobreposicoes.substituir(novas);
        registrar.registrar(contexto.novo(TipoDeEvento.CONFIG).detalhe(mudancas.size() + " parâmetro(s) alterado(s)")
                .alteracoes(mudancas).criar());
    }

    private static AlteracaoDoEvento linha(DefinicaoDeParametro definicao, String antes, String depois) {
        String item = definicao.classe() == ClasseDeAplicacao.EXIGE_REINICIO
                ? definicao.rotulo() + " (exige reinício)" : definicao.rotulo();
        return new AlteracaoDoEvento(item, antes, depois);
    }

    private List<ParametroVigente> situacao() {
        Map<String, String> atuais = sobreposicoes.todas();
        return CatalogoDeParametros.todos().stream().map(definicao -> {
            boolean sobreposto = atuais.containsKey(definicao.nome());
            String padrao = vigentes.padrao(definicao);
            return new ParametroVigente(definicao, atuais.getOrDefault(definicao.nome(), padrao), padrao, sobreposto,
                    sobreposto && definicao.classe() == ClasseDeAplicacao.EXIGE_REINICIO);
        }).toList();
    }

    /** O nome veio do cliente e volta na mensagem: só caracteres de identificador, com tamanho limitado. */
    private static String nomeSeguro(String nome) {
        String limpo = nome == null ? "" : nome.replaceAll("[^A-Za-z0-9._-]", "");
        return limpo.length() > 60 ? limpo.substring(0, 60) : limpo;
    }
}
