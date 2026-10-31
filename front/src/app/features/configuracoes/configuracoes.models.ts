/** Usuário da aba "Usuários e perfis" (GET /configuracoes/usuarios). O CPF/CNPJ só chega mascarado. */
export interface UsuarioConfigurado {
  id: string;
  nome: string;
  login: string;
  perfil: string;
  perfilRotulo: string;
  ativo: boolean;
  ultimoAcesso: string | null;
  cpfCnpj: string | null;
}

export interface NovoUsuario {
  nome: string;
  login: string;
  cpfCnpj: string;
  perfil: string;
}

export interface AlteracaoDeUsuario {
  perfil?: string;
  ativo?: boolean;
}

export const PERFIS_ASSINAVEIS: { codigo: string; rotulo: string }[] = [
  { codigo: 'OPERADOR', rotulo: 'Operador' },
  { codigo: 'OPERADOR_SIGILO', rotulo: 'Gestor' },
  { codigo: 'ADMIN', rotulo: 'Administrador' },
];

export const LOGIN_VALIDO = /^[a-z0-9._-]{3,60}$/;

// --- parâmetros em tempo de execução (GET/PUT/DELETE /configuracoes/parametros)

export type GrupoDeParametro = 'DOSSIE' | 'CONVERSAO' | 'VISUALIZACAO' | 'CONEXOES' | 'AUDITORIA';
export type ClasseDeAplicacao = 'IMEDIATO' | 'PROXIMA_GERACAO' | 'EXIGE_REINICIO';

export interface Parametro {
  nome: string;
  rotulo: string;
  grupo: GrupoDeParametro;
  tipo: 'INTEIRO' | 'BOOLEANO' | 'OPCAO';
  /** Sempre texto: "30", "true", "LARGURA". */
  valor: string;
  padrao: string;
  minimo: number | null;
  maximo: number | null;
  opcoes: string[] | null;
  classeDeAplicacao: ClasseDeAplicacao;
  sobreposto: boolean;
  pendenteDeReinicio: boolean;
}

export interface ParametrosResposta {
  parametros: Parametro[];
}

export type ValorDeParametro = string | number | boolean;

// --- conexões (GET /configuracoes/conexoes, POST /configuracoes/conexoes/{nome}/teste)

export interface Conexao {
  nome: string;
  rotulo: string;
  estado: 'CONFIGURADA' | 'NAO_CONFIGURADA' | 'DESATIVADA';
  horasAteExpirar: number | null;
  tokenVencido: boolean;
}

export interface TesteDeConexao {
  nome: string;
  sucesso: boolean;
  tempoMs: number;
  mensagem: string;
  horasAteExpirar: number | null;
  tokenVencido: boolean;
}

/** Texto das etiquetas de classe de aplicação (docs/12). */
export const ROTULO_DA_CLASSE: Record<ClasseDeAplicacao, string> = {
  IMEDIATO: 'Vale já',
  PROXIMA_GERACAO: 'Vale na próxima geração',
  EXIGE_REINICIO: 'Exige reinício',
};

export const EXPLICACAO_DA_CLASSE: Record<ClasseDeAplicacao, string> = {
  IMEDIATO: 'Vale já na próxima leitura.',
  PROXIMA_GERACAO: 'Vale para os pedidos criados depois de salvar; os que estão em andamento não mudam.',
  EXIGE_REINICIO:
    'O valor é lido só quando o serviço sobe. A alteração fica registrada, mas só vale de fato pela variável de ambiente do servidor: as alterações desta tela somem ao reiniciar.',
};

/** Rótulo amigável de uma opção ou de um valor; o que não está no mapa aparece como veio. */
export const ROTULO_DA_OPCAO: Record<string, string> = {
  LARGURA: 'Ajustar à largura',
  PAGINA_POR_ABA: 'Uma página por aba',
};
