import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NEVER, Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { Perfil, Usuario } from '../../core/usuario.model';
import { PageResponse } from '../../shared/api-tipos';
import { DetalheApiService } from '../detalhe/detalhe-api.service';
import { MAXIMO_DE_POSICOES_SIMULTANEAS } from '../detalhe/fila-de-posicoes.service';
import { PosicaoDoRequerimento } from '../detalhe/posicao.models';
import { ConsultaApiService } from './consulta-api.service';
import { ConsultaComponent } from './consulta.component';
import { ResultadoBusca } from './consulta.models';

function usuario(perfil: Perfil): Usuario {
  return {
    login: 'u.demo',
    nome: 'U',
    perfil,
    perfilDescricao: perfil,
    cpfCnpj: null,
    permissoes: {
      verSigilosos: perfil !== 'OPERADOR',
      verAuditoriaAcessos: perfil === 'ADMIN',
      acessarConfiguracoes: perfil === 'ADMIN',
      restritoAoInteressado: perfil === 'OPERADOR',
      verResponsavelTarefa: perfil !== 'OPERADOR',
    },
  };
}

function resultado(id: number, numero: string, protocolado = true): ResultadoBusca {
  return {
    id,
    identificacao: { rotulo: protocolado ? 'Processo' : 'Requerimento', numero, protocolado },
    numeroRequerimento: '3331/2025',
    numeroProcesso: protocolado ? numero : null,
    interessado: { nome: 'Associação Vale Verde', cpfCnpj: '12345678000190' },
    tipoProcesso: 'Uso de Recursos Hídricos',
    situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    abertura: '2025-03-31T10:12:00',
  };
}

function pagina(itens: ResultadoBusca[], total = itens.length, numeroDaPagina = 0, totalPaginas = 1): PageResponse<ResultadoBusca> {
  return { conteudo: itens, pagina: numeroDaPagina, tamanho: 20, totalElementos: total, totalPaginas };
}

function erroHttp(status: number, corpo: object) {
  return throwError(() => new HttpErrorResponse({ status, error: corpo }));
}

describe('ConsultaComponent', () => {
  let fixture: ComponentFixture<ConsultaComponent>;
  let api: { buscar: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  const usuarioAtual = signal<Usuario | null>(usuario('OPERADOR_SIGILO'));

  function criar(): HTMLElement {
    fixture = TestBed.createComponent(ConsultaComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function digitar(raiz: HTMLElement, seletor: string, valor: string): HTMLInputElement {
    const campo = raiz.querySelector<HTMLInputElement>(seletor)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    return campo;
  }

  function consultar(raiz: HTMLElement): void {
    raiz.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  const texto = (raiz: HTMLElement) => raiz.textContent!.replace(/\s+/g, ' ');

  beforeEach(() => {
    localStorage.clear();
    usuarioAtual.set(usuario('OPERADOR_SIGILO'));
    api = { buscar: vi.fn() };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [ConsultaComponent],
      providers: [
        { provide: ConsultaApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { usuario: usuarioAtual } },
        { provide: DetalheApiService, useValue: detalheApi },
      ],
    });
  });

  // S7, bloco 9: coluna opcional "Etapa atual" (as posições pendentes ficam abertas para medir a fila).
  const pedidosDePosicao = new Map<number, Subject<PosicaoDoRequerimento>>();
  const detalheApi = {
    parametrosDeExibicao: vi.fn(() => of({ posicoesSimultaneas: MAXIMO_DE_POSICOES_SIMULTANEAS })),
    posicao: vi.fn((id: number) => {
      const s = new Subject<PosicaoDoRequerimento>();
      pedidosDePosicao.set(id, s);
      return s;
    }),
  };

  describe('coluna "Etapa atual" (S7)', () => {
    beforeEach(() => {
      pedidosDePosicao.clear();
      detalheApi.posicao.mockClear();
    });

    it('desligada por padrão: nenhuma posição pedida', () => {
      api.buscar.mockReturnValue(of(pagina([resultado(1, '1/2026'), resultado(2, '2/2026')])));
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '1/2026');
      consultar(raiz);

      expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
      expect(raiz.querySelector('thead')!.textContent).not.toContain('Etapa atual');
      expect(detalheApi.posicao).not.toHaveBeenCalled();
    });

    it(`ligada: só as linhas da página e no máximo ${MAXIMO_DE_POSICOES_SIMULTANEAS} de cada vez`, () => {
      const itens = Array.from({ length: 12 }, (_, i) => resultado(100 + i, `${100 + i}/2026`));
      api.buscar.mockReturnValue(of(pagina(itens, 12)));
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '1/2026');
      consultar(raiz);

      const botao = [...raiz.querySelectorAll('button')].find((b) => b.textContent!.includes('Mostrar etapa atual'))!;
      expect(botao.getAttribute('aria-pressed')).toBe('false');
      botao.click();
      fixture.detectChanges();

      expect(botao.getAttribute('aria-pressed')).toBe('true');
      expect(texto(raiz)).toContain('Etapa atual');
      expect(raiz.querySelectorAll('td.etapa-atual')).toHaveLength(12);
      expect(detalheApi.posicao).toHaveBeenCalledTimes(MAXIMO_DE_POSICOES_SIMULTANEAS);

      const primeiro = pedidosDePosicao.get(100)!;
      primeiro.next({
        idRequerimento: 100, numeroRequerimento: '100/2026', numeroProcesso: null, situacaoLicenciamento: null,
        modelo: 'Outorga de recursos hídricos', estado: 'ENCERRADO', etapaAtual: null, emParalelo: [], complementacao: null,
        multiInstancia: null, avisos: [], final: { rotulo: 'Concluído', encerradoEm: null },
      });
      primeiro.complete();
      fixture.detectChanges();

      expect(detalheApi.posicao).toHaveBeenCalledTimes(MAXIMO_DE_POSICOES_SIMULTANEAS + 1);
      expect(raiz.querySelector('td.etapa-atual')!.textContent).toContain('Concluído');
    });
  });

  afterEach(() => localStorage.clear());

  describe('validação antes de consultar', () => {
    it('sem nenhum critério mostra a mensagem e não chama a API', () => {
      const raiz = criar();

      consultar(raiz);

      expect(texto(raiz)).toContain('Informe ao menos um critério de pesquisa.');
      expect(api.buscar).not.toHaveBeenCalled();
    });

    it('número fora do formato N/AAAA', () => {
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '3331');

      consultar(raiz);

      expect(texto(raiz)).toContain('Informe o número no formato N/AAAA.');
      expect(api.buscar).not.toHaveBeenCalled();
    });

    it('CPF/CNPJ com quantidade de dígitos errada', () => {
      const raiz = criar();
      digitar(raiz, '#consulta-cpf-cnpj', '123456');

      consultar(raiz);

      expect(texto(raiz)).toContain('Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).');
      expect(api.buscar).not.toHaveBeenCalled();
    });

    it('nome com menos de 3 caracteres', () => {
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'ab');

      consultar(raiz);

      expect(texto(raiz)).toContain('Informe ao menos 3 caracteres do nome.');
      expect(api.buscar).not.toHaveBeenCalled();
    });
  });

  describe('máscaras', () => {
    it('aplicam a máscara durante a digitação', () => {
      const raiz = criar();

      expect(digitar(raiz, '#consulta-numero', '33312025').value).toBe('3331/2025');
      expect(digitar(raiz, '#consulta-cpf-cnpj', '12345678000190').value).toBe('12.345.678/0001-90');
    });
  });

  describe('busca por número', () => {
    it('com um único resultado abre o detalhe direto', () => {
      api.buscar.mockReturnValue(of(pagina([resultado(101, '2825/2025')])));
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '28252025');

      consultar(raiz);

      expect(api.buscar).toHaveBeenCalledWith({
        tipo: 'REQUERIMENTO',
        numero: '2825/2025',
        cpfCnpj: undefined,
        nome: undefined,
        pagina: 0,
      });
      expect(router.navigate).toHaveBeenCalledWith(['/consulta/requerimentos', 101]);
    });

    it('envia PROCESSO quando o seletor está em Processo', () => {
      api.buscar.mockReturnValue(of(pagina([resultado(101, '2825/2025')])));
      const raiz = criar();
      raiz.querySelectorAll<HTMLButtonElement>('.seg button')[1].click();
      fixture.detectChanges();
      digitar(raiz, '#consulta-numero', '28252025');

      consultar(raiz);

      expect(api.buscar.mock.calls[0][0].tipo).toBe('PROCESSO');
      expect(raiz.querySelector<HTMLInputElement>('#consulta-numero')!.placeholder).toContain('2825/2025');
    });

    it('403 NAO_INTERESSADO mostra que o usuário não é o interessado, com o número', () => {
      api.buscar.mockReturnValue(erroHttp(403, { errorCode: 'NAO_INTERESSADO' }));
      const raiz = criar();
      raiz.querySelectorAll<HTMLButtonElement>('.seg button')[1].click();
      digitar(raiz, '#consulta-numero', '28252025');

      consultar(raiz);

      expect(texto(raiz)).toContain('Processo 2825/2025 não tem você como interessado.');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('404 NAO_ENCONTRADO mostra o rótulo e o número', () => {
      api.buscar.mockReturnValue(erroHttp(404, { errorCode: 'NAO_ENCONTRADO', detail: 'texto do servidor' }));
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '99992020');

      consultar(raiz);

      expect(texto(raiz)).toContain('Requerimento 9999/2020 não encontrado.');
      expect(texto(raiz)).not.toContain('texto do servidor');
    });

    function buscarProcesso(raiz: HTMLElement, numero: string): void {
      raiz.querySelectorAll<HTMLButtonElement>('.seg button')[1].click();
      fixture.detectChanges();
      digitar(raiz, '#consulta-numero', numero);
      consultar(raiz);
    }

    it('processo com 1 resultado abre o detalhe direto, sem aviso', () => {
      api.buscar.mockReturnValue(of(pagina([resultado(101, '2825/2025')])));
      const raiz = criar();

      buscarProcesso(raiz, '28252025');

      expect(router.navigate).toHaveBeenCalledWith(['/consulta/requerimentos', 101]);
      expect(raiz.querySelector('.processo-repetido')).toBeNull();
    });

    it('processo com 2 resultados (1:N) lista os dois com situação e abertura e mostra o aviso', () => {
      const a = { ...resultado(402, '445/2022'), abertura: '2023-03-01T08:00:00' };
      const b = { ...resultado(401, '445/2022'), situacao: { codigo: 'CONCLUIDO', descricao: 'Concluído' }, abertura: '2022-05-10T08:00:00' };
      api.buscar.mockReturnValue(of(pagina([a, b])));
      const raiz = criar();

      buscarProcesso(raiz, '4452022');

      expect(router.navigate).not.toHaveBeenCalled();
      expect(raiz.querySelectorAll('tbody tr').length).toBe(2);
      expect(texto(raiz)).toContain('Este número de processo está associado a mais de um requerimento.');
      expect(texto(raiz)).toContain('Concluído');
      expect(texto(raiz)).toContain('01/03/2023');
      expect(texto(raiz)).toContain('10/05/2022');
    });
  });

  describe('quadro do topo com altura estável (docs/08)', () => {
    const estrutura = (raiz: HTMLElement) => {
      const quadro = raiz.querySelector('app-quadro-topo .dash-hero')!;
      const reserva = quadro.querySelector('.search-card .search-feedback');
      return { quadro, reserva, alerta: raiz.querySelector('.alert') };
    };

    it('o espaço do alerta existe desde o estado inicial', () => {
      const { quadro, reserva, alerta } = estrutura(criar());

      expect(quadro.classList).toContain('tall');
      expect(reserva).not.toBeNull();
      expect(alerta).toBeNull();
    });

    const estados: [string, () => unknown][] = [
      ['400', () => erroHttp(400, { errorCode: 'PARAMETRO_INVALIDO', campos: [{ campo: 'numero', mensagem: 'Formato inválido.' }] })],
      ['403', () => erroHttp(403, { errorCode: 'NAO_INTERESSADO' })],
      ['404', () => erroHttp(404, { errorCode: 'NAO_ENCONTRADO' })],
      ['503', () => erroHttp(503, { errorCode: 'SIGA_INDISPONIVEL' })],
      ['erro de rede', () => erroHttp(0, {})],
    ];

    for (const [nome, resposta] of estados) {
      it(`${nome}: o alerta fica dentro do espaço reservado, sem elemento novo no quadro`, () => {
        api.buscar.mockReturnValue(resposta());
        const raiz = criar();
        const filhosAntes = raiz.querySelector('.search-card')!.children.length;
        digitar(raiz, '#consulta-numero', '28252025');

        consultar(raiz);

        const { quadro, reserva, alerta } = estrutura(raiz);
        expect(alerta).not.toBeNull();
        expect(alerta!.parentElement).toBe(reserva);
        expect(raiz.querySelector('.search-card')!.children.length).toBe(filhosAntes);
        expect(quadro.classList).toContain('tall');
      });
    }

    it('resultado vazio aparece na folha sobreposta, fora do quadro', () => {
      api.buscar.mockReturnValue(of(pagina([])));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'inexistente');

      consultar(raiz);

      const vazio = Array.from(raiz.querySelectorAll('.empty')).find((e) => e.textContent!.includes('Nenhum resultado'))!;
      expect(vazio).toBeDefined();
      expect(estrutura(raiz).quadro.contains(vazio)).toBe(false);
      expect(estrutura(raiz).alerta).toBeNull();
    });
  });

  describe('lista de resultados', () => {
    const duas = pagina([resultado(1, '2825/2025'), resultado(2, '1187/2026', false)], 41, 0, 3);

    it('mostra a tabela com número (RN04), interessado, situação e total', () => {
      api.buscar.mockReturnValue(of(duas));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');

      consultar(raiz);

      const linhas = raiz.querySelectorAll('tbody tr');
      expect(linhas.length).toBe(2);
      expect(texto(raiz)).toContain('41 resultado(s)');
      expect(linhas[0].textContent).toContain('2825/2025');
      expect(linhas[0].textContent).toContain('12.345.678/0001-90');
      expect(linhas[0].textContent).toContain('Em análise');
      expect(linhas[0].textContent).toContain('31/03/2025');
      expect(linhas[1].textContent).toContain('Requerimento · não protocolado');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('busca por número junto com outro critério também lista (não abre direto)', () => {
      api.buscar.mockReturnValue(of(pagina([resultado(1, '2825/2025')])));
      const raiz = criar();
      digitar(raiz, '#consulta-numero', '28252025');
      digitar(raiz, '#consulta-nome', 'vale');

      consultar(raiz);

      expect(router.navigate).not.toHaveBeenCalled();
      expect(raiz.querySelectorAll('tbody tr').length).toBe(1);
    });

    it('"Abrir" navega para o detalhe do item', () => {
      api.buscar.mockReturnValue(of(duas));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');
      consultar(raiz);

      raiz.querySelectorAll<HTMLButtonElement>('tbody .btn')[1].click();

      expect(router.navigate).toHaveBeenCalledWith(['/consulta/requerimentos', 2]);
    });

    it('pagina para a próxima e para a anterior mantendo os critérios', () => {
      api.buscar.mockReturnValueOnce(of(duas)).mockReturnValueOnce(of({ ...duas, pagina: 1 })).mockReturnValue(of(duas));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');
      consultar(raiz);
      expect(texto(raiz)).toContain('Página 1 de 3');

      const [anterior, proxima] = Array.from(raiz.querySelectorAll<HTMLButtonElement>('.pager button'));
      expect(anterior.disabled).toBe(true);
      proxima.click();
      fixture.detectChanges();

      expect(api.buscar.mock.calls[1][0]).toMatchObject({ nome: 'vale', pagina: 1 });
      expect(texto(raiz)).toContain('Página 2 de 3');
      raiz.querySelectorAll<HTMLButtonElement>('.pager button')[0].click();
      expect(api.buscar.mock.calls[2][0]).toMatchObject({ nome: 'vale', pagina: 0 });
    });

    it('sem resultados mostra o estado vazio', () => {
      api.buscar.mockReturnValue(of(pagina([], 0, 0, 0)));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'zzz');

      consultar(raiz);

      expect(texto(raiz)).toContain('Nenhum resultado encontrado');
    });

    it('"Limpar" zera campos, erro e resultados', () => {
      api.buscar.mockReturnValue(of(duas));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');
      consultar(raiz);

      raiz.querySelectorAll<HTMLButtonElement>('.form-actions button')[1].click();
      fixture.detectChanges();

      expect(raiz.querySelector('tbody')).toBeNull();
      expect(raiz.querySelector<HTMLInputElement>('#consulta-nome')!.value).toBe('');
    });
  });

  describe('erros da API', () => {
    it('503 SIGA_INDISPONIVEL mostra a mensagem amigável', () => {
      api.buscar.mockReturnValue(erroHttp(503, { errorCode: 'SIGA_INDISPONIVEL' }));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');

      consultar(raiz);

      expect(texto(raiz)).toContain('O banco do SIGA está indisponível no momento.');
      expect(raiz.querySelector('.alert')).not.toBeNull();
    });

    it('400 mostra a mensagem do campo apontado pela API', () => {
      api.buscar.mockReturnValue(erroHttp(400, { errorCode: 'PARAMETRO_INVALIDO', campos: [{ campo: 'nome', mensagem: 'Nome curto demais.' }] }));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');

      consultar(raiz);

      expect(texto(raiz)).toContain('Nome curto demais.');
    });

    it('falha sem resposta (rede) mostra mensagem de conexão', () => {
      api.buscar.mockReturnValue(erroHttp(0, {}));
      const raiz = criar();
      digitar(raiz, '#consulta-nome', 'vale');

      consultar(raiz);

      expect(texto(raiz)).toContain('Não foi possível conectar ao servidor.');
    });
  });

  describe('perfil e recentes', () => {
    it('o Operador vê o aviso de que só consulta o que é dele', () => {
      usuarioAtual.set(usuario('OPERADOR'));

      expect(texto(criar())).toContain('Seu perfil consulta apenas processos e requerimentos em que você é o interessado.');
    });

    it('Gestor e Administrador não veem o aviso', () => {
      expect(texto(criar())).not.toContain('Seu perfil consulta apenas');
    });

    it('sem recentes não mostra atalhos nem dados fictícios', () => {
      const raiz = criar();

      expect(raiz.querySelectorAll('.tile').length).toBe(0);
      expect(texto(raiz)).toContain('Informe um critério para começar');
    });

    it('mostra os recentes do usuário e abre o detalhe ao clicar', () => {
      localStorage.setItem('rastro.recentes.u.demo', JSON.stringify([{ id: 7, rotulo: 'Processo 2825/2025' }]));
      const raiz = criar();

      const atalhos = raiz.querySelectorAll<HTMLButtonElement>('.tile');
      expect(atalhos.length).toBe(1);
      expect(atalhos[0].textContent).toContain('Processo 2825/2025');
      atalhos[0].click();
      expect(router.navigate).toHaveBeenCalledWith(['/consulta/requerimentos', 7]);
    });

    it('os recentes formam a trilha vertical "Consultados por último"', () => {
      localStorage.setItem('rastro.recentes.u.demo', JSON.stringify([{ id: 7, rotulo: 'Processo 2825/2025' }]));
      const raiz = criar();

      expect(texto(raiz)).toContain('Consultados por último');
      expect(raiz.querySelectorAll('ul.rec-list > li > button.tile').length).toBe(1);
    });

    it('o tipo de pesquisa é um controle segmentado com aria-pressed', () => {
      const raiz = criar();
      const botoes = raiz.querySelectorAll<HTMLButtonElement>('.seg button');

      expect(botoes[0].getAttribute('aria-pressed')).not.toBeNull();
      expect(Array.from(botoes).filter((b) => b.getAttribute('aria-pressed') === 'true').length).toBe(1);
    });

    it('dois requerimentos do mesmo processo aparecem desempatados, com a situação em texto pequeno', () => {
      localStorage.setItem(
        'rastro.recentes.u.demo',
        JSON.stringify([
          { id: 2, rotulo: 'Processo 445/2022', numeroRequerimento: '3798/2023', situacao: 'Cancelado' },
          { id: 1, rotulo: 'Processo 445/2022', numeroRequerimento: '3391/2022', situacao: 'Concluído' },
          { id: 3, rotulo: 'Processo 2825/2025', numeroRequerimento: '3331/2025', situacao: 'Em análise' },
        ]),
      );
      const raiz = criar();

      const atalhos = Array.from(raiz.querySelectorAll('.tile')).map((t) => ({
        titulo: t.querySelector('b')!.textContent,
        situacao: t.querySelector('small')?.textContent ?? null,
      }));
      expect(atalhos).toEqual([
        { titulo: 'Processo 445/2022 · Req. 3798/2023', situacao: 'Cancelado' },
        { titulo: 'Processo 445/2022 · Req. 3391/2022', situacao: 'Concluído' },
        { titulo: 'Processo 2825/2025', situacao: null },
      ]);
    });
  });
});
