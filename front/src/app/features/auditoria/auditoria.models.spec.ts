import { TIPOS_DO_GRUPO } from './auditoria.models';

describe('tipos de evento da auditoria', () => {
  it('a posição na tramitação (S7) é do grupo documentos, com o rótulo do servidor', () => {
    expect(TIPOS_DO_GRUPO.documentos).toContainEqual({ codigo: 'TRAMITACAO', rotulo: 'Posição na tramitação' });
    expect(TIPOS_DO_GRUPO.acessos.map((t) => t.codigo)).not.toContain('TRAMITACAO');
  });

  it('o histórico financeiro (S9) é do grupo documentos, com o rótulo do servidor', () => {
    expect(TIPOS_DO_GRUPO.documentos).toContainEqual({ codigo: 'FINANCEIRO', rotulo: 'Histórico financeiro' });
    expect(TIPOS_DO_GRUPO.acessos.map((t) => t.codigo)).not.toContain('FINANCEIRO');
  });
});
