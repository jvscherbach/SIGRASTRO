import { Routes } from '@angular/router';

import { autenticadoGuard, permissaoGuard, visitanteGuard } from './core/guards';
import { AuditoriaComponent } from './features/auditoria/auditoria.component';
import { ConfiguracoesComponent } from './features/configuracoes/configuracoes.component';
import { ConsultaComponent } from './features/consulta/consulta.component';
import { DetalheComponent } from './features/detalhe/detalhe.component';
import { DossiesGeradosComponent } from './features/dossies/dossies-gerados.component';
import { LoginComponent } from './features/login/login.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [visitanteGuard], title: 'Entrar · SIGA Vistas' },
  {
    path: '',
    component: ShellComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'consulta' },
      {
        path: 'consulta',
        component: ConsultaComponent,
        title: 'Consulta · SIGA Vistas',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'consulta/requerimentos/:id',
        component: DetalheComponent,
        title: 'Requerimento · SIGA Vistas',
        data: { crumb: ['Consulta'] },
      },
      {
        path: 'dossies',
        component: DossiesGeradosComponent,
        title: 'Dossiês gerados · SIGA Vistas',
        data: { crumb: ['Dossiês gerados'] },
      },
      {
        path: 'auditoria/documentos',
        component: AuditoriaComponent,
        title: 'Auditoria de documentos · SIGA Vistas',
        data: { crumb: ['Auditoria', 'Documentos'], grupo: 'documentos' },
      },
      {
        path: 'auditoria/acessos',
        component: AuditoriaComponent,
        canActivate: [permissaoGuard],
        title: 'Auditoria de acessos · SIGA Vistas',
        data: { permissao: 'verAuditoriaAcessos', crumb: ['Auditoria', 'Acessos'], grupo: 'acessos' },
      },
      {
        path: 'configuracoes',
        component: ConfiguracoesComponent,
        canActivate: [permissaoGuard],
        title: 'Configurações · SIGA Vistas',
        data: { permissao: 'acessarConfiguracoes', crumb: ['Configurações'] },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
