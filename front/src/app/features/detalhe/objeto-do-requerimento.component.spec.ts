import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Objeto } from './detalhe.models';
import { ObjetoDoRequerimentoComponent } from './objeto-do-requerimento.component';

const VAZIO: Objeto = { objetivos: [], finalidadesAtividades: [], tiposObjetivo: [], atividadeLicenciamento: null };

describe('ObjetoDoRequerimentoComponent', () => {
  function criar(objeto: Partial<Objeto>): { fixture: ComponentFixture<ObjetoDoRequerimentoComponent>; raiz: HTMLElement } {
    TestBed.configureTestingModule({ imports: [ObjetoDoRequerimentoComponent] });
    const fixture = TestBed.createComponent(ObjetoDoRequerimentoComponent);
    fixture.componentRef.setInput('objeto', { ...VAZIO, ...objeto });
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  const cartao = (raiz: HTMLElement, nome: string) => raiz.querySelector<HTMLElement>(`section[aria-label="${nome}"]`);
  const objetivos = (raiz: HTMLElement) =>
    Array.from(cartao(raiz, 'Objetivos')!.querySelectorAll('li')).map((li) => ({
      tipo: li.querySelector('.obj-tipo')?.textContent ?? null,
      texto: li.querySelector('.obj-texto')!.textContent,
    }));
  const lista = (n: number) => Array.from({ length: n }, (_, i) => `Objetivo ${i + 1}`);

  describe('objetivos', () => {
    it('um objetivo: sem contador e sem botão', () => {
      const { raiz } = criar({ objetivos: lista(1) });

      expect(objetivos(raiz)).toEqual([{ tipo: null, texto: 'Objetivo 1' }]);
      expect(raiz.querySelector('.contador')).toBeNull();
      expect(raiz.querySelector('.mostrar-todos')).toBeNull();
    });

    it('dois objetivos: uma linha para cada um e o contador "2" no cabeçalho', () => {
      const { raiz } = criar({ objetivos: lista(2) });

      expect(objetivos(raiz).map((o) => o.texto)).toEqual(['Objetivo 1', 'Objetivo 2']);
      expect(cartao(raiz, 'Objetivos')!.querySelector('header .contador')!.textContent).toBe('2');
      expect(raiz.querySelector('.mostrar-todos')).toBeNull();
    });

    it('cinco objetivos ainda cabem sem "Mostrar todos"', () => {
      const { raiz } = criar({ objetivos: lista(5) });

      expect(objetivos(raiz).length).toBe(5);
      expect(raiz.querySelector('.mostrar-todos')).toBeNull();
    });

    it('seis objetivos mostram cinco e o botão "Mostrar todos (6)", que expande e recolhe', () => {
      const { fixture, raiz } = criar({ objetivos: lista(6) });

      expect(objetivos(raiz).length).toBe(5);
      const botao = raiz.querySelector<HTMLButtonElement>('.mostrar-todos')!;
      expect(botao.textContent!.trim()).toBe('Mostrar todos (6)');
      expect(cartao(raiz, 'Objetivos')!.querySelector('.contador')!.textContent).toBe('6');

      botao.click();
      fixture.detectChanges();
      expect(objetivos(raiz).length).toBe(6);
      expect(raiz.querySelector('.mostrar-todos')!.textContent!.trim()).toBe('Mostrar menos');

      raiz.querySelector<HTMLButtonElement>('.mostrar-todos')!.click();
      fixture.detectChanges();
      expect(objetivos(raiz).length).toBe(5);
    });

    it('outorga: "Objetivos" e "Tipo | Objetivo" viram uma lista só, com a etiqueta do tipo em cada linha', () => {
      const { raiz } = criar({
        objetivos: ['Consumo humano', 'Irrigação'],
        tiposObjetivo: [
          { tipo: 'SUBTERRANEA', tipoDescricao: 'Subterrânea', objetivo: 'Consumo humano' },
          { tipo: 'SUPERFICIAL', tipoDescricao: 'Superficial', objetivo: 'Irrigação' },
        ],
      });

      expect(objetivos(raiz)).toEqual([
        { tipo: 'Subterrânea', texto: 'Consumo humano' },
        { tipo: 'Superficial', texto: 'Irrigação' },
      ]);
      expect(raiz.textContent).not.toContain('Tipo | Objetivo');
      expect(raiz.querySelectorAll('section[aria-label="Objetivos"]').length).toBe(1);
    });

    it('tipo desconhecido aparece cru e tipo ausente fica sem etiqueta', () => {
      const { raiz } = criar({
        tiposObjetivo: [
          { tipo: 'NOVO', tipoDescricao: 'NOVO', objetivo: 'A' },
          { tipo: null, tipoDescricao: null, objetivo: 'B' },
        ],
      });

      expect(objetivos(raiz)).toEqual([
        { tipo: 'NOVO', texto: 'A' },
        { tipo: null, texto: 'B' },
      ]);
    });

    it('o texto não é negrito: nenhum <b> nem <strong> no cartão', () => {
      const { raiz } = criar({ objetivos: lista(2) });

      expect(cartao(raiz, 'Objetivos')!.querySelector('b, strong')).toBeNull();
    });
  });

  describe('finalidade e atividade', () => {
    it('agrupa por finalidade: a finalidade aparece uma vez com as atividades dela', () => {
      const { raiz } = criar({
        finalidadesAtividades: [
          { finalidade: 'Abastecimento público', atividade: 'Captação' },
          { finalidade: 'Agricultura', atividade: 'Irrigação por aspersão' },
          { finalidade: 'Abastecimento público', atividade: 'Tratamento' },
          { finalidade: 'Abastecimento público', atividade: 'Captação' },
        ],
      });

      const grupos = Array.from(cartao(raiz, 'Finalidade e atividade')!.querySelectorAll('.obj-grupo')).map((g) => ({
        finalidade: g.querySelector('.obj-finalidade')!.textContent,
        atividades: Array.from(g.querySelectorAll('li')).map((li) => li.textContent),
      }));
      expect(grupos).toEqual([
        { finalidade: 'Abastecimento público', atividades: ['Captação', 'Tratamento'] },
        { finalidade: 'Agricultura', atividades: ['Irrigação por aspersão'] },
      ]);
    });

    it('finalidade sem atividade mostra só a finalidade', () => {
      const { raiz } = criar({ finalidadesAtividades: [{ finalidade: 'Agricultura', atividade: null }] });

      const grupo = cartao(raiz, 'Finalidade e atividade')!.querySelector('.obj-grupo')!;
      expect(grupo.querySelector('.obj-finalidade')!.textContent).toBe('Agricultura');
      expect(grupo.querySelector('li')).toBeNull();
    });

    it('atividade sem finalidade cai num grupo "Finalidade não informada"', () => {
      const { raiz } = criar({ finalidadesAtividades: [{ finalidade: null, atividade: 'Captação' }] });

      expect(cartao(raiz, 'Finalidade e atividade')!.querySelector('.obj-finalidade')!.textContent).toBe('Finalidade não informada');
    });
  });

  describe('atividade do licenciamento', () => {
    const atividade = (grupo: string | null, texto: string | null) => ({ atividadeLicenciamento: { grupo, atividade: texto } });

    it('grupo diferente da atividade vira legenda "Grupo: ..."', () => {
      const { raiz } = criar(atividade('Mineração', 'Extração de areia'));

      const c = cartao(raiz, 'Atividade')!;
      expect(c.querySelector('.obj-atividade-principal')!.textContent).toBe('Extração de areia');
      expect(c.querySelector('.obj-grupo-legenda')!.textContent).toBe('Grupo: Mineração');
    });

    it('grupo igual à atividade, sem acento e sem diferenciar maiúsculas, não aparece', () => {
      const { raiz } = criar(atividade('FABRICAÇÃO DE ARTEFATOS DE CIMENTO', 'Fabricação de artefatos de cimento'));

      const c = cartao(raiz, 'Atividade')!;
      expect(c.querySelector('.obj-atividade-principal')!.textContent).toBe('Fabricação de artefatos de cimento');
      expect(c.querySelector('.obj-grupo-legenda')).toBeNull();
    });

    it('só o grupo, sem atividade: o grupo é o texto principal', () => {
      const { raiz } = criar(atividade('Agropecuária', null));

      expect(cartao(raiz, 'Atividade')!.querySelector('.obj-atividade-principal')!.textContent).toBe('Agropecuária');
      expect(raiz.querySelector('.obj-grupo-legenda')).toBeNull();
    });

    it('atividade e grupo vazios: nenhum cartão', () => {
      const { raiz } = criar(atividade('  ', null));

      expect(cartao(raiz, 'Atividade')).toBeNull();
      expect(raiz.textContent).toContain('Sem objeto cadastrado para este requerimento');
    });
  });

  describe('só renderiza cartões com dados', () => {
    it('sem nenhum dado: um aviso único e nenhum cartão com traço', () => {
      const { raiz } = criar({});

      expect(raiz.querySelectorAll('.objeto-card').length).toBe(0);
      expect(raiz.textContent!.trim()).toBe('Sem objeto cadastrado para este requerimento');
      expect(raiz.textContent).not.toContain('—');
    });

    it('licenciamento com objetivos e atividade: dois cartões, sem o de finalidade', () => {
      const { raiz } = criar({ objetivos: ['Licença prévia'], atividadeLicenciamento: { grupo: 'Mineração', atividade: 'Extração de areia' } });

      expect(Array.from(raiz.querySelectorAll('.objeto-card')).map((c) => c.getAttribute('aria-label'))).toEqual(['Objetivos', 'Atividade']);
    });

    it('outorga com tudo: objetivos e finalidade/atividade, sem o cartão de atividade do licenciamento', () => {
      const { raiz } = criar({
        objetivos: ['Consumo humano'],
        finalidadesAtividades: [{ finalidade: 'Abastecimento público', atividade: 'Captação' }],
      });

      expect(Array.from(raiz.querySelectorAll('.objeto-card')).map((c) => c.getAttribute('aria-label'))).toEqual([
        'Objetivos',
        'Finalidade e atividade',
      ]);
    });

    it('apenas objetivos: um cartão só', () => {
      const { raiz } = criar({ objetivos: lista(2) });

      expect(raiz.querySelectorAll('.objeto-card').length).toBe(1);
    });
  });
});
