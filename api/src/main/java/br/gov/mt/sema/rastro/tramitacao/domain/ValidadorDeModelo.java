package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de conteúdo do catálogo (docs/15). Devolve todos os problemas de uma vez, para quem edita o arquivo corrigir tudo
 * numa passada. Id técnico fora do catálogo não é problema: é "Etapa não mapeada" em tempo de execução.
 */
public final class ValidadorDeModelo {

    private static final Pattern ID_ESTAVEL = Pattern.compile("[a-z][a-z0-9_]*");

    private ValidadorDeModelo() {
    }

    public static List<String> problemas(String chave, String rotulo, List<Etapa> etapas, List<FinalDoFluxo> finais,
            List<IdIgnorado> ignorados) {
        List<String> problemas = new ArrayList<>();
        if (vazio(chave)) {
            problemas.add("modelo sem chave");
        }
        if (vazio(rotulo)) {
            problemas.add("modelo '" + chave + "' sem rótulo");
        }
        if (etapas == null || etapas.isEmpty()) {
            problemas.add("modelo '" + chave + "' sem etapa");
        }
        if (finais == null || finais.isEmpty()) {
            problemas.add("modelo '" + chave + "' sem final");
        }
        List<Etapa> listaDeEtapas = etapas == null ? List.of() : etapas;
        List<FinalDoFluxo> listaDeFinais = finais == null ? List.of() : finais;
        List<IdIgnorado> listaDeIgnorados = ignorados == null ? List.of() : ignorados;

        Set<String> idsEstaveis = new HashSet<>();
        Map<String, String> donoDoIdTecnico = new HashMap<>();
        Map<String, String> donoDoFilho = new HashMap<>();
        Map<Ramo, Map<Integer, String>> ordens = new HashMap<>();

        for (Etapa etapa : listaDeEtapas) {
            String nome = "etapa '" + etapa.id() + "'";
            idEstavel(etapa.id(), "etapa", idsEstaveis, problemas);
            if (vazio(etapa.rotulo())) {
                problemas.add(nome + " sem rótulo");
            }
            if (etapa.tipo() == null) {
                problemas.add(nome + " sem tipo");
            } else if (etapa.tipo() == TipoDeEtapa.FINAL) {
                problemas.add(nome + " com tipo FINAL (finais ficam na lista 'finais')");
            }
            if (etapa.responsavel() == null) {
                problemas.add(nome + " sem responsável");
            }
            if (etapa.ramo() == null) {
                problemas.add(nome + " sem ramo");
            } else if (etapa.ordem() < 1) {
                problemas.add(nome + " com ordem menor que 1");
            } else {
                String outra = ordens.computeIfAbsent(etapa.ramo(), r -> new HashMap<>()).putIfAbsent(etapa.ordem(), etapa.id());
                if (outra != null) {
                    problemas.add("ordem " + etapa.ordem() + " repetida no ramo " + etapa.ramo() + " ('" + outra + "' e '"
                            + etapa.id() + "')");
                }
            }
            if (etapa.multiInstancia() && etapa.tipo() != TipoDeEtapa.TAREFA_HUMANA) {
                problemas.add(nome + " multi-instância sem ser tarefa humana");
            }
            if (etapa.idsTecnicos().isEmpty() && etapa.processosFilhos().isEmpty()) {
                problemas.add(nome + " sem id técnico");
            }
            for (String id : etapa.idsTecnicos()) {
                registrar(id, nome, donoDoIdTecnico, problemas);
            }
            for (String filho : etapa.processosFilhos()) {
                if (vazio(filho)) {
                    problemas.add(nome + " com processo filho vazio");
                } else {
                    String outro = donoDoFilho.putIfAbsent(filho, nome);
                    if (outro != null) {
                        problemas.add("processo filho '" + filho + "' em mais de um lugar (" + outro + " e " + nome + ")");
                    }
                }
            }
        }
        for (FinalDoFluxo fim : listaDeFinais) {
            String nome = "final '" + fim.id() + "'";
            idEstavel(fim.id(), "final", idsEstaveis, problemas);
            if (vazio(fim.rotulo())) {
                problemas.add(nome + " sem rótulo");
            }
            registrar(fim.idTecnico(), nome, donoDoIdTecnico, problemas);
        }
        for (IdIgnorado ignorado : listaDeIgnorados) {
            String nome = "ignorado '" + ignorado.idTecnico() + "'";
            if (ignorado.motivo() == null) {
                problemas.add(nome + " sem motivo");
            }
            registrar(ignorado.idTecnico(), nome, donoDoIdTecnico, problemas);
        }
        for (Etapa etapa : listaDeEtapas) {
            for (String condicao : etapa.semPassagemPor()) {
                if (!donoDoIdTecnico.containsKey(condicao)) {
                    problemas.add("etapa '" + etapa.id() + "' com condição sobre id técnico desconhecido '" + condicao + "'");
                }
            }
        }
        return problemas;
    }

    private static void idEstavel(String id, String oQue, Set<String> vistos, List<String> problemas) {
        if (vazio(id)) {
            problemas.add(oQue + " sem id");
        } else if (!ID_ESTAVEL.matcher(id).matches()) {
            problemas.add(oQue + " com id '" + id + "' fora do padrão (minúsculas, dígitos e sublinhado)");
        } else if (!vistos.add(id)) {
            problemas.add("id '" + id + "' repetido no modelo");
        }
    }

    private static void registrar(String idTecnico, String dono, Map<String, String> donos, List<String> problemas) {
        if (vazio(idTecnico)) {
            problemas.add(dono + " com id técnico vazio");
            return;
        }
        String outro = donos.putIfAbsent(idTecnico, dono);
        if (outro != null) {
            problemas.add("id técnico '" + idTecnico + "' em mais de um lugar (" + outro + " e " + dono + ")");
        }
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
