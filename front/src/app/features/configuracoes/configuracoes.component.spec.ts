import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../shared/toast.service';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { ConfiguracoesComponent } from './configuracoes.component';
import { UsuarioConfigurado } from './configuracoes.models';

function usuario(sobre: Partial<UsuarioConfigurado> = {}): UsuarioConfigurado {
  return {
    id: 'operador.demo',
    nome: 'Operador Demo',
    login: 'operador.demo',
    perfil: 'OPERADOR',
    perfilRotulo: 'Operador',
    ativo: true,
    ultimoAcesso: '2026-10-21T14:00:00Z',
    cpfCnpj: '***.456.789-**',
    ...sobre,
  };
}

const ADMIN = usuario({ id: 'admin.demo', nome: 'Admin Demo', login: 'admin.demo', perfil: 'ADMIN', perfilRotulo: 'Administrador', cpfCnpj: null });

function problema(status: number, errorCode: string, detail?: string, campos?: { campo: string; mensagem: string }[]) {
  return new HttpErrorResponse({ status, error: { status, errorCode, detail, campos } });
}

describe('ConfiguracoesComponent', () => {
  let api: Record<'usuarios' | 'incluirUsuario' | 'alterarUsuario', ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<ConfiguracoesComponent>;
  let raiz: HTMLElement;
  let toast: ToastService;

  function abrir(): void {
    TestBed.configureTestingModule({
      imports: [ConfiguracoesComponent],
      providers: [
        { provide: ConfiguracoesApiService, useValue: api },
        { provide: AuthService, useValue: { usuario: signal({ login: 'admin.demo' }).asReadonly() } },
      ],
    });
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(ConfiguracoesComponent);
    fixture.detectChanges();
    raiz = fixture.nativeElement as HTMLElement;
  }

  const texto = () => raiz.textContent!.replace(/\s+/g, ' ');
  const linha = (login: string) => raiz.querySelector<HTMLElement>(`tr[data-login="${login}"]`)!;
  const botao = (rotulo: string, dentro: ParentNode = raiz) =>
    Array.from(dentro.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes(rotulo));
  const escolher = (select: HTMLSelectElement, valor: string) => {
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const gaveta = () => document.body.querySelector('app-novo-usuario') as HTMLElement | null;
  const digitar = (campo: HTMLInputElement, valor: string) => {
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(() => {
    api = {
      usuarios: vi.fn(() => of({ conteudo: [ADMIN, usuario()], pagina: 0, tamanho: 2, totalElementos: 2, totalPaginas: 1 })),
      incluirUsuario: vi.fn(),
      alterarUsuario: vi.fn(),
    };
  });

  afterEach(() => {
    fixture.destroy();
    vi.restoreAllMocks();
  });

  describe('abas', () => {
    it('seis abas, a de usuários ativa, com faixa de memória; as demais ficam em construção', () => {
      abrir();

      const abas = Array.from(raiz.querySelectorAll('[role="tab"]'));
      expect(abas.map((a) => a.textContent!.trim())).toEqual([
        'Usuários e perfis', 'Dossiê', 'Conversão', 'Visualização', 'Conexões', 'Auditoria',
      ]);
      expect(abas[0].getAttribute('aria-selected')).toBe('true');
      expect(texto()).toContain('Neste protótipo, as alterações ficam na memória e somem ao reiniciar o serviço.');
      (abas[1] as HTMLElement).click();
      fixture.detectChanges();
      expect(texto()).toContain('Dossiê: em construção');
    });

    it('setas, Home e End navegam e movem o foco; só a aba ativa está na ordem de tabulação', () => {
      abrir();
      const lista = raiz.querySelector('[role="tablist"]')!;
      const tecla = (key: string) => {
        lista.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
      };

      tecla('ArrowRight');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Dossiê');
      expect(raiz.querySelector('#aba-dossie')!.getAttribute('tabindex')).toBe('0');
      expect(raiz.querySelector('#aba-usuarios')!.getAttribute('tabindex')).toBe('-1');
      tecla('End');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Auditoria');
      tecla('ArrowRight');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Usuários e perfis');
      tecla('ArrowLeft');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Auditoria');
      tecla('Home');
      expect(raiz.querySelector('[role="tab"][aria-selected="true"]')!.textContent!.trim()).toBe('Usuários e perfis');
    });
  });

  describe('lista de usuários', () => {
    it('mostra nome, login, CPF/CNPJ só como a API mandou, perfil, situação e último acesso', () => {
      abrir();

      expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
      const op = linha('operador.demo');
      expect(op.textContent).toContain('***.456.789-**');
      expect(op.querySelector<HTMLSelectElement>('.cfg-perfil')!.value).toBe('OPERADOR');
      expect(op.querySelector<HTMLSelectElement>('.cfg-situacao')!.value).toBe('ativo');
      expect(op.textContent).toContain('21/10/2026');
      expect(linha('admin.demo').textContent).toContain('você');
      expect(linha('admin.demo').textContent).toContain('—');
    });

    it('inativo aparece esmaecido', () => {
      api.usuarios.mockReturnValue(of({ conteudo: [ADMIN, usuario({ ativo: false })], pagina: 0, tamanho: 2, totalElementos: 2, totalPaginas: 1 }));
      abrir();

      expect(linha('operador.demo').classList.contains('inativo')).toBe(true);
      expect(linha('admin.demo').classList.contains('inativo')).toBe(false);
    });

    it('erro de carga com "Tentar novamente"', () => {
      api.usuarios.mockReturnValueOnce(throwError(() => problema(503, 'ERRO_INTERNO')));
      abrir();
      expect(raiz.querySelector('.cfg-erro')).not.toBeNull();

      botao('Tentar novamente')!.click();
      fixture.detectChanges();

      expect(raiz.querySelectorAll('tbody tr')).toHaveLength(2);
    });

    it('mudar o perfil pede confirmação, grava com PATCH só do perfil e avisa', () => {
      api.alterarUsuario.mockReturnValue(of(usuario({ perfil: 'OPERADOR_SIGILO', perfilRotulo: 'Gestor' })));
      abrir();

      escolher(linha('operador.demo').querySelector('.cfg-perfil')!, 'OPERADOR_SIGILO');
      expect(api.alterarUsuario).not.toHaveBeenCalled();
      expect(texto()).toContain('Mudar o perfil de Operador Demo para Gestor?');
      botao('Confirmar')!.click();
      fixture.detectChanges();

      expect(api.alterarUsuario).toHaveBeenCalledWith('operador.demo', { perfil: 'OPERADOR_SIGILO' });
      expect(linha('operador.demo').querySelector<HTMLSelectElement>('.cfg-perfil')!.value).toBe('OPERADOR_SIGILO');
      expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
      expect(toast.toasts().some((t) => t.texto.includes('atualizado'))).toBe(true);
    });

    it('cancelar a confirmação volta o controle ao valor anterior e não grava', () => {
      abrir();
      const select = linha('operador.demo').querySelector<HTMLSelectElement>('.cfg-perfil')!;

      escolher(select, 'ADMIN');
      botao('Cancelar')!.click();
      fixture.detectChanges();

      expect(select.value).toBe('OPERADOR');
      expect(api.alterarUsuario).not.toHaveBeenCalled();
      expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
    });

    it('desativar grava só a situação, com a pergunta que avisa que o usuário deixa de entrar', () => {
      api.alterarUsuario.mockReturnValue(of(usuario({ ativo: false })));
      abrir();

      escolher(linha('operador.demo').querySelector('.cfg-situacao')!, 'inativo');
      expect(texto()).toContain('Desativar Operador Demo? Ele deixa de entrar no sistema.');
      botao('Confirmar')!.click();
      fixture.detectChanges();

      expect(api.alterarUsuario).toHaveBeenCalledWith('operador.demo', { ativo: false });
      expect(linha('operador.demo').classList.contains('inativo')).toBe(true);
    });

    for (const [codigo, mensagem] of [
      ['ULTIMO_ADMINISTRADOR', 'Não é possível desativar nem rebaixar o último Administrador ativo.'],
      ['PROPRIO_USUARIO', 'Não é possível desativar o próprio usuário.'],
    ]) {
      it(`${codigo}: mostra a mensagem do servidor na linha e volta ao valor anterior`, () => {
        api.alterarUsuario.mockReturnValue(throwError(() => problema(409, codigo, mensagem)));
        abrir();
        const select = linha('admin.demo').querySelector<HTMLSelectElement>('.cfg-situacao')!;

        escolher(select, 'inativo');
        botao('Confirmar')!.click();
        fixture.detectChanges();

        expect(raiz.querySelector('.cfg-erro-linha')!.textContent).toContain(mensagem);
        expect(select.value).toBe('ativo');
        expect(raiz.querySelector('.cfg-confirmacao')).toBeNull();
      });
    }

    it('a confirmação trava os controles enquanto grava e não duplica o PATCH', () => {
      const resposta = new Subject<UsuarioConfigurado>();
      api.alterarUsuario.mockReturnValue(resposta);
      abrir();

      escolher(linha('operador.demo').querySelector('.cfg-perfil')!, 'ADMIN');
      botao('Confirmar')!.click();
      fixture.detectChanges();
      botao('Gravando')!.click();

      expect(api.alterarUsuario).toHaveBeenCalledTimes(1);
      expect(linha('operador.demo').querySelector<HTMLSelectElement>('.cfg-perfil')!.disabled).toBe(true);
    });

    it('destruir a tela com a gravação em andamento não deixa inscrição pendurada', () => {
      const resposta = new Subject<UsuarioConfigurado>();
      api.alterarUsuario.mockReturnValue(resposta);
      abrir();
      escolher(linha('operador.demo').querySelector('.cfg-perfil')!, 'ADMIN');
      botao('Confirmar')!.click();

      fixture.destroy();

      expect(resposta.observed).toBe(false);
    });
  });

  describe('inclusão por gaveta', () => {
    const preencher = (nome: string, login: string, perfil: string, cpf: string) => {
      const g = gaveta()!;
      digitar(g.querySelector<HTMLInputElement>('#nu-nome')!, nome);
      digitar(g.querySelector<HTMLInputElement>('#nu-login')!, login);
      escolher(g.querySelector<HTMLSelectElement>('#nu-perfil')!, perfil);
      digitar(g.querySelector<HTMLInputElement>('#nu-cpf')!, cpf);
    };

    it('abre a gaveta e fecha com Esc e clique fora, sem chamar a API', () => {
      abrir();

      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      expect(gaveta()).not.toBeNull();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(gaveta()).toBeNull();

      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      gaveta()!.querySelector<HTMLElement>('.scrim')!.click();
      fixture.detectChanges();
      expect(gaveta()).toBeNull();
      expect(api.incluirUsuario).not.toHaveBeenCalled();
    });

    it('inclusão válida: envia o CPF só em dígitos, fecha, ordena a lista e avisa', () => {
      api.incluirUsuario.mockReturnValue(of(usuario({ id: 'ana.teste', nome: 'Ana de Teste', login: 'ana.teste', cpfCnpj: '***.444.777-**' })));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      preencher('Ana de Teste', 'Ana.Teste', 'OPERADOR', '111.444.777-35');
      gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!.value;
      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();

      expect(api.incluirUsuario).toHaveBeenCalledWith({ nome: 'Ana de Teste', login: 'ana.teste', cpfCnpj: '11144477735', perfil: 'OPERADOR' });
      expect(gaveta()).toBeNull();
      expect(Array.from(raiz.querySelectorAll('tbody tr td:first-child b')).map((b) => b.textContent)).toEqual([
        'Admin Demo', 'Ana de Teste', 'Operador Demo',
      ]);
      expect(raiz.textContent).not.toContain('11144477735');
      expect(toast.toasts().some((t) => t.texto.includes('incluído'))).toBe(true);
    });

    it('máscara de CPF/CNPJ na digitação', () => {
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      digitar(gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!, '11144477735');

      expect(gaveta()!.querySelector<HTMLInputElement>('#nu-cpf')!.value).toBe('111.444.777-35');
    });

    it('validações: nome, login (formato e único) e CPF/CNPJ obrigatório para Operador e com dígitos verificadores', () => {
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      let erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Informe o nome.', 'Informe o login.', 'O CPF/CNPJ é obrigatório para o Operador.']);

      preencher('Fulano', 'a b', 'OPERADOR', '111.444.777-36');
      erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Use de 3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado.', 'CPF/CNPJ inválido.']);

      preencher('Fulano', 'ADMIN.demo', 'ADMIN', '');
      erros = Array.from(gaveta()!.querySelectorAll('.cfg-campo-erro')).map((e) => e.textContent!.trim());
      expect(erros).toEqual(['Já existe um usuário com este login.']);
      expect(api.incluirUsuario).not.toHaveBeenCalled();
    });

    it('CPF/CNPJ é opcional fora do Operador', () => {
      api.incluirUsuario.mockReturnValue(of(usuario({ id: 'gestor.novo', login: 'gestor.novo' })));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();

      preencher('Gestor Novo', 'gestor.novo', 'OPERADOR_SIGILO', '');
      botao('Incluir usuário', gaveta()!)!.click();

      expect(api.incluirUsuario).toHaveBeenCalledWith(expect.objectContaining({ perfil: 'OPERADOR_SIGILO', cpfCnpj: '' }));
    });

    it('recusa do servidor: 409 de login repetido na própria gaveta; 400 por campo marca o campo', () => {
      api.incluirUsuario
        .mockReturnValueOnce(throwError(() => problema(409, 'LOGIN_JA_EXISTE', 'Já existe um usuário com este login.')))
        .mockReturnValueOnce(throwError(() => problema(400, 'PARAMETRO_INVALIDO', undefined, [{ campo: 'cpfCnpj', mensagem: 'CPF/CNPJ inválido.' }])));
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      preencher('Ana de Teste', 'ana.teste', 'ADMIN', '');

      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      expect(gaveta()!.querySelector('.cfg-erro-geral')!.textContent).toContain('Já existe um usuário com este login.');

      botao('Incluir usuário', gaveta()!)!.click();
      fixture.detectChanges();
      expect(gaveta()!.querySelector('.cfg-campo-erro')!.textContent).toContain('CPF/CNPJ inválido.');
      expect(gaveta()).not.toBeNull();
    });

    it('destruir a tela com a inclusão em andamento cancela o envio e remove a gaveta do corpo', () => {
      const envio = new Subject<UsuarioConfigurado>();
      api.incluirUsuario.mockReturnValue(envio);
      abrir();
      botao('Incluir usuário', raiz)!.click();
      fixture.detectChanges();
      preencher('Ana de Teste', 'ana.teste', 'ADMIN', '');
      botao('Incluir usuário', gaveta()!)!.click();
      expect(envio.observed).toBe(true);

      fixture.destroy();

      expect(envio.observed).toBe(false);
      expect(gaveta()).toBeNull();
    });
  });
});
