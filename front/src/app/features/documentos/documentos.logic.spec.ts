import {
  ORDEM_PADRAO,
  chaveDe,
  classeDaExtensao,
  filtrar,
  normalizar,
  ordenar,
  referenciaDe,
  rotuloDoBloqueio,
  selecaoPadrao,
  selecionavel,
} from './documentos.logic';
import { documento } from './documentos.fixtures';
import { DocumentoItem } from './documentos.models';

describe('documentos.logic', () => {
  const lista: DocumentoItem[] = [
    documento({ id: '10', titulo: 'Relatório técnico', nomeArquivo: 'relatorio.pdf', data: '2025-04-03T10:00:00' }),
    documento({ id: '9', titulo: 'Ofício', nomeArquivo: 'oficio.pdf', origem: 'JUNTADA', origemDescricao: 'Juntada', data: '2025-04-01T10:00:00' }),
    documento({ id: '3', titulo: 'Sem data', nomeArquivo: 'sd.pdf', data: null }),
    documento({
      id: '4',
      titulo: 'Cálculo',
      nomeArquivo: 'calculo.xlsx',
      tipoArquivo: 'PLANILHA',
      extensao: 'xlsx',
      bloqueio: 'NAO_PDF',
      selecionavelPorPadrao: false,
      data: '2025-04-02T10:00:00',
    }),
  ];

  describe('chaves', () => {
    it('chave e referência usam origem e id (texto), nunca a chave do arquivo', () => {
      const d = documento({ origem: 'JUNTADA', id: '900118' });

      expect(chaveDe(d)).toBe('JUNTADA:900118');
      expect(referenciaDe(d)).toEqual({ origem: 'JUNTADA', id: '900118' });
    });
  });

  describe('busca', () => {
    it('ignora acento e maiúsculas e olha título, id e nome do arquivo', () => {
      expect(filtrar(lista, 'RELATORIO', '').map((d) => d.id)).toEqual(['10']);
      expect(filtrar(lista, 'oficio', '').map((d) => d.id)).toEqual(['9']);
      expect(filtrar(lista, 'calculo.xlsx', '').map((d) => d.id)).toEqual(['4']);
      expect(filtrar(lista, '10', '').map((d) => d.id)).toEqual(['10']);
    });

    it('busca vazia ou só com espaços devolve tudo', () => {
      expect(filtrar(lista, '   ', '').length).toBe(4);
    });

    it('sem resultado devolve lista vazia', () => {
      expect(filtrar(lista, 'inexistente', '')).toEqual([]);
    });

    it('documento sigiloso mascarado não casa pelo título real', () => {
      const mascarado = documento({ id: '77', titulo: 'Documento sigiloso', nomeArquivo: null, sigiloso: true, acessivel: false });

      expect(filtrar([mascarado], 'laudo', '')).toEqual([]);
      expect(filtrar([mascarado], '77', '').length).toBe(1);
    });
  });

  describe('filtro de origem', () => {
    it('mostra só a origem escolhida e combina com a busca', () => {
      expect(filtrar(lista, '', 'JUNTADA').map((d) => d.id)).toEqual(['9']);
      expect(filtrar(lista, 'relatorio', 'JUNTADA')).toEqual([]);
    });
  });

  describe('ordenação', () => {
    it('padrão: data crescente, sem data ao final', () => {
      expect(ordenar(lista, ORDEM_PADRAO).map((d) => d.id)).toEqual(['9', '4', '10', '3']);
    });

    it('data decrescente também deixa o sem data ao final', () => {
      expect(ordenar(lista, { campo: 'data', direcao: -1 }).map((d) => d.id)).toEqual(['10', '4', '9', '3']);
    });

    it('por documento, sem considerar acento (Cálculo antes de Ofício)', () => {
      expect(ordenar(lista, { campo: 'titulo', direcao: 1 }).map((d) => d.titulo)).toEqual([
        'Cálculo',
        'Ofício',
        'Relatório técnico',
        'Sem data',
      ]);
    });

    it('por origem, com desempate pelo id numérico (9 antes de 10)', () => {
      const ids = ordenar(lista, { campo: 'origem', direcao: 1 }).map((d) => d.id);

      // "Documento do processo" (3, 4, 10) vem antes de "Juntada" (9); dentro da origem, o id numérico: 3 < 4 < 10
      expect(ids).toEqual(['3', '4', '10', '9']);
    });

    it('não altera a lista original', () => {
      const copia = [...lista];

      ordenar(lista, { campo: 'titulo', direcao: -1 });

      expect(lista).toEqual(copia);
    });
  });

  describe('seleção', () => {
    it('só o que a API marca como selecionável por padrão entra na seleção inicial', () => {
      const sel = selecaoPadrao([
        ...lista,
        documento({ id: '5', situacao: 'SUBSTITUIDO', }),
        documento({ id: '6', situacao: 'INDISPONIVEL', bloqueio: 'INDISPONIVEL', selecionavelPorPadrao: false }),
        documento({ id: '7', sigiloso: true, acessivel: false, bloqueio: 'SIGILOSO', selecionavelPorPadrao: false }),
      ]);

      expect(Array.from(sel).sort()).toEqual(['DOCUMENTO_PROCESSO:10', 'DOCUMENTO_PROCESSO:3', 'DOCUMENTO_PROCESSO:5', 'JUNTADA:9']);
    });

    it('bloqueado não é selecionável; substituído em PDF é (manualmente)', () => {
      expect(selecionavel(documento({ bloqueio: 'NAO_PDF' }))).toBe(false);
      expect(selecionavel(documento({ bloqueio: 'INDISPONIVEL' }))).toBe(false);
      expect(selecionavel(documento({ bloqueio: 'SIGILOSO' }))).toBe(false);
      expect(selecionavel(documento({ situacao: 'SUBSTITUIDO', selecionavelPorPadrao: false }))).toBe(true);
    });
  });

  describe('apresentação', () => {
    it('rótulos dos bloqueios', () => {
      expect(rotuloDoBloqueio(documento({ bloqueio: 'INDISPONIVEL' }))).toBe('arquivo indisponível');
      expect(rotuloDoBloqueio(documento({ bloqueio: 'NAO_PDF' }))).toBe('conversão disponível em breve');
      expect(rotuloDoBloqueio(documento({ bloqueio: 'SIGILOSO' }))).toBe('documento sigiloso');
      expect(rotuloDoBloqueio(documento())).toBeNull();
    });

    it('classe do selo pela categoria do arquivo; mascarado fica neutro', () => {
      expect(classeDaExtensao(documento())).toBe('pdf');
      expect(classeDaExtensao(documento({ tipoArquivo: 'PLANILHA' }))).toBe('xls');
      expect(classeDaExtensao(documento({ tipoArquivo: 'TEXTO' }))).toBe('doc');
      expect(classeDaExtensao(documento({ tipoArquivo: 'IMAGEM' }))).toBe('img');
      expect(classeDaExtensao(documento({ tipoArquivo: null }))).toBe('off');
    });

    it('normalizar tira acento, maiúsculas e espaços das pontas', () => {
      expect(normalizar('  AÇÃO Média ')).toBe('acao media');
      expect(normalizar(null)).toBe('');
    });
  });
});
