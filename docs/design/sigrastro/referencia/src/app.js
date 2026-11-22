'use strict';
/* ===== utilidades ===== */
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];
const pref={get(k,d){try{const v=localStorage.getItem('rastro.'+k);return v===null?d:JSON.parse(v)}catch{return d}},set(k,v){try{localStorage.setItem('rastro.'+k,JSON.stringify(v))}catch{}}};
const fmtDH=d=>new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Cuiaba',dateStyle:'short',timeStyle:'medium'}).format(d);
const fmtH=d=>new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Cuiaba',hour:'2-digit',minute:'2-digit'}).format(d);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const NOMES={cerrado:'Cerrado',pantanal:'Pantanal',amazonia:'Amazônia'};
const PERFIS={OPERADOR:'Operador',OPERADOR_SIGILO:'Operador com sigilo',ADMINISTRADOR:'Administrador'};
const can={sigilo:p=>p!=='OPERADOR',acessos:p=>p==='ADMINISTRADOR',config:p=>p==='ADMINISTRADOR'};

/* ===== dados fictícios ===== */
const P=(o)=>o;
const PROCS=[
 P({id:'1530/2026',tipo:'Processo',req:'4821/2026',interessado:'Empresa Exemplo 01 Ltda',cnpj:'00.000.000/0001-00',objeto:'Licença Ambiental Simplificada',sit:['Em análise técnica','warn'],prior:true,abertura:'03/09/2026',atual:'17/11/2026',taxaVencida:'14/11/2026',
  onde:{etapa:'Análise técnica',desde:'17/11/2026',grupo:'Gerência de Análise Exemplo'},
  caminho:[{n:'Protocolo',d:'03/09/2026'},{n:'Conferência documental',d:'08/09/2026'},{n:'Emissão da taxa',d:'09/09/2026'},{n:'Aguardando pagamento',d:'09/09 a 17/11/2026'},{n:'Conferência documental',d:'17/11/2026',p:'2ª passagem'},{n:'Análise técnica',d:'desde 17/11/2026',atual:true}],docs:7}),
 P({id:'0942/2026',tipo:'Processo',req:'3310/2026',interessado:'Servidor Exemplo 02',cnpj:'000.000.000-01',objeto:'Outorga de uso de água',sit:['Concluído','ok'],prior:false,abertura:'12/03/2026',atual:'10/05/2026',
  onde:{etapa:'Concluído',desde:'10/05/2026',grupo:'Arquivo (Verificação da revisão)'},
  caminho:[{n:'Protocolo',d:'12/03/2026'},{n:'Análise técnica',d:'20/03/2026'},{n:'Emissão do ato',d:'Outorga · 02/05/2026'},{n:'Verificação da revisão',d:'10/05/2026'},{fim:'Concluído'}],docs:5}),
 P({id:'2207/2025',tipo:'Processo',req:'1204/2025',interessado:'Empresa Exemplo 04 Ltda',cnpj:'00.000.000/0001-01',objeto:'Renovação de licença',sit:['Concluído','ok'],prior:false,abertura:'20/02/2025',atual:'30/06/2025',isento:true,
  onde:{etapa:'Concluído',desde:'30/06/2025',grupo:'Arquivo (Verificação da revisão)'},
  caminho:[{n:'Protocolo',d:'20/02/2025'},{n:'Conferência documental',d:'25/02/2025'},{n:'Isenção confirmada',d:'Isento de Taxa · 26/02/2025'},{n:'Análise técnica',d:'27/02/2025'},{n:'Emissão do ato',d:'Licença · 20/06/2025'},{n:'Verificação da revisão',d:'30/06/2025'},{fim:'Concluído'}],docs:4}),
 P({id:'5177/2026',tipo:'Requerimento',req:'5177/2026',proc:null,interessado:'Empresa Exemplo 03 S.A.',cnpj:'00.000.000/0001-02',objeto:'Renovação de licença',sit:['Aguardando pagamento','gray'],prior:false,abertura:'10/11/2026',atual:'10/11/2026',
  onde:{etapa:'Aguardando pagamento',desde:'10/11/2026',grupo:'Com o interessado (taxa emitida)'},
  caminho:[{n:'Protocolo',d:'10/11/2026'},{n:'Emissão da taxa',d:'10/11/2026'},{n:'Aguardando pagamento',d:'desde 10/11/2026',atual:true}],docs:3}),
 P({id:'3310/2026',tipo:'Processo',req:'4044/2026',interessado:'Empresa Exemplo 05 Ltda',cnpj:'00.000.000/0001-03',objeto:'Licença Ambiental Simplificada',sit:['Em análise técnica','warn'],prior:false,abertura:'01/10/2026',atual:'05/11/2026',aproximado:true,
  onde:{etapa:'Análise técnica',desde:'05/11/2026',grupo:'Não informado neste processo'},
  caminho:[{n:'Requerimento protocolado',d:'01/10/2026'},{n:'Conferência dos documentos',d:'09/10/2026'},{n:'Encaminhado para análise',d:'05/11/2026'}],docs:5}),
];
const MODELO_DOCS=[
 ['projeto-tecnico-v3.pdf','Projeto técnico','05/09/2026','vigente',0,4.2,0,'SIGA'],
 ['teste-de-bombeamento.xlsx','Teste de bombeamento','05/09/2026','vigente',0,0.8,1,'SIGA'],
 ['comprovante-de-endereco.pdf','Comprovante de endereço','03/09/2026','vigente',0,0.3,0,'SIGA'],
 ['projeto-tecnico-v2.pdf','Projeto técnico','04/09/2026','substituido',0,3.9,0,'SIGA'],
 ['parecer-juridico.pdf','Parecer jurídico','12/09/2026','vigente',1,0.6,0,'SIGA'],
 ['croqui-da-area.pdf','Croqui','03/09/2026','indisponivel',0,0,0,'SIGA'],
 ['documento-do-representante.pdf','Documento pessoal','03/09/2026','vigente',0,1.1,0,'Pessoal'],
];
const docsDe=p=>MODELO_DOCS.slice(0,p.docs).map((m,i)=>({id:p.id+'#'+i,nome:m[0],tipo:m[1],data:m[2],sit:m[3],sig:!!m[4],mb:m[5],conv:!!m[6],origem:m[7]}));
const achar=(tipo,num)=>PROCS.find(p=>tipo==='Processo'?(p.tipo==='Processo'&&p.id===num):p.req===num);
const slug=id=>id.replace('/','-'), deslug=s=>s.replace('-','/');

/* ===== estado ===== */
const S={
 user:null,perfil:'OPERADOR',bioma:pref.get('bioma','cerrado'),
 sim:{disco:false,scorp:false,lento:false,conexao:'sorteio',livreMB:1200},
 sel:{},ordem:{},filtro:{},dossies:[],audit:[],recentes:pref.get('recentes',['1530/2026|Processo','0942/2026|Processo']),
 usuarios:[{login:'operador.exemplo',nome:'Operador Exemplo 01',perfil:'OPERADOR',ativo:true},{login:'sigilo.exemplo',nome:'Operador Exemplo 02',perfil:'OPERADOR_SIGILO',ativo:true},{login:'admin.exemplo',nome:'Administrador Exemplo',perfil:'ADMINISTRADOR',ativo:true},{login:'antigo.exemplo',nome:'Servidor Exemplo 09',perfil:'OPERADOR',ativo:false}],
 params:{DOSSIE_ESPACO_MINIMO_MB:500,CONEXAO_TESTE_TIMEOUT_SEGUNDOS:15,DOSSIE_EXPIRACAO_HORAS:24,DOCUMENTOS_MAX_POR_DOSSIE:200},
 rascunho:null,seq:1,
};
const LIMITES={DOSSIE_ESPACO_MINIMO_MB:[0,100000],CONEXAO_TESTE_TIMEOUT_SEGUNDOS:[1,120],DOSSIE_EXPIRACAO_HORAS:[1,72],DOCUMENTOS_MAX_POR_DOSSIE:[1,500]};
function audita(tipo,detalhe,extra={}){S.audit.unshift({t:new Date(),user:S.user?.login||'—',perfil:S.perfil,tipo,detalhe,ok:extra.ok!==false,proc:extra.proc||''})}
(function semente(){const h=m=>new Date(Date.now()-m*60000);
 [[190,'operador.exemplo','ACESSO','Entrada no sistema'],[185,'operador.exemplo','CONSULTA','Processo 0942/2026','0942/2026'],[160,'operador.exemplo','VISUALIZACAO','Visualizou projeto-tecnico-v3.pdf','1530/2026'],[120,'sigilo.exemplo','ACESSO','Entrada no sistema'],[118,'sigilo.exemplo','DOSSIE','Dossiê gerado com 4 documentos','2207/2025'],[60,'admin.exemplo','CONFIG','Parâmetro CONEXAO_TESTE_TIMEOUT_SEGUNDOS alterado'],[30,'desconhecido','ACESSO','Tentativa de entrada recusada']].forEach(e=>S.audit.push({t:h(e[0]),user:e[1],perfil:'—',tipo:e[2],detalhe:e[3],ok:e[2]!=='ACESSO'||!e[3].includes('recusada'),proc:e[4]||''}))})();

/* ===== PDF mínimo (para testar o download) ===== */
function pdfDe(linhas){const t=s=>s.normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/[()\\]/g,'');
 let c='BT /F1 14 Tf 56 780 Td 18 TL\n'+linhas.map(l=>`(${t(l)}) Tj T*`).join('\n')+'\nET';
 const o=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',`<< /Length ${c.length} >>\nstream\n${c}\nendstream`,'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
 let out='%PDF-1.4\n',off=[];o.forEach((b,i)=>{off.push(out.length);out+=`${i+1} 0 obj\n${b}\nendobj\n`});
 const x=out.length;out+=`xref\n0 ${o.length+1}\n0000000000 65535 f \n`+off.map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('')+`trailer\n<< /Size ${o.length+1} /Root 1 0 R >>\nstartxref\n${x}\n%%EOF`;
 return new Blob([out],{type:'application/pdf'})}
function baixar(blob,nome){const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=nome;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),2000)}

/* ===== tema ===== */
const cssv=n=>getComputedStyle(document.documentElement).getPropertyValue(n).trim();
function arvore(x,y,s){return `<g transform="translate(${x} ${y}) scale(${s})"><path d="M0 0c2-14 -1-22 -6-30M0 -12c4-6 9-10 14-14" stroke="var(--l3)" stroke-width="3" fill="none" stroke-linecap="round"/><ellipse cx="-4" cy="-34" rx="20" ry="8" fill="var(--l3)"/><ellipse cx="16" cy="-28" rx="14" ry="6" fill="var(--l3)"/></g>`}
function palmeira(x,y,s){let f='';for(let a=-70;a<=70;a+=28)f+=`<path d="M0 -60Q${a*.5} -${70-Math.abs(a)*.15} ${a} -${52-Math.abs(a)*.2}" stroke="var(--l3)" stroke-width="3" fill="none" stroke-linecap="round"/>`;return `<g transform="translate(${x} ${y}) scale(${s})"><path d="M0 0V-60" stroke="var(--l3)" stroke-width="4"/>${f}</g>`}
function terra(b,tipo){if(b==='cerrado')return CERRADO_RODAPE;const W=1200,H=tipo==='login'?420:240,L=tipo==='login';let g='';
 if(b==='cerrado'){g+=`<circle cx="${W*.78}" cy="${H*.3}" r="${H*.11}" fill="var(--sun)" opacity=".85"/><path d="M0 ${H*.62}C200 ${H*.5} 380 ${H*.58} 560 ${H*.52}S900 ${H*.45} ${W} ${H*.55}V${H}H0z" fill="var(--l1)"/><path d="M0 ${H*.74}C220 ${H*.64} 420 ${H*.76} 640 ${H*.7}S980 ${H*.62} ${W} ${H*.72}V${H}H0z" fill="var(--l2)"/><path d="M0 ${H*.88}C260 ${H*.8} 520 ${H*.9} 760 ${H*.84}S1060 ${H*.8} ${W} ${H*.86}V${H}H0z" fill="var(--l3)"/>`+arvore(W*.62,H*.72,L?1.5:1)+arvore(W*(L?.2:.42),H*.66,L?1.1:.8)+arvore(W*.9,H*.7,L?1.2:.9)}
 else if(b==='pantanal'){g+=`<circle cx="${W*.75}" cy="${H*.28}" r="${H*.1}" fill="var(--sun)" opacity=".9"/><rect y="${H*.55}" width="${W}" height="${H*.45}" fill="var(--l1)"/>`;for(let i=0;i<9;i++){g+=`<path d="M${(i*173)%W} ${H*(.6+i*.045)}h${120+i*20}" stroke="#fff" stroke-opacity=".5" stroke-width="2" stroke-linecap="round"/>`}
  g+=`<path d="M0 ${H*.55}C150 ${H*.52} 260 ${H*.55} 420 ${H*.53}S760 ${H*.54} ${W} ${H*.53}V${H*.58}H0z" fill="var(--l3)"/>`+palmeira(W*(L?.15:.56),H*.56,L?1.6:1)+palmeira(W*(L?.19:.6),H*.56,L?1.2:.8)+palmeira(W*.9,H*.55,L?1.4:.9);
  for(let i=0;i<7;i++)g+=`<ellipse cx="${W*(.3+i*.07)}" cy="${H*(.8+(i%3)*.05)}" rx="22" ry="6" fill="var(--l3)" opacity=".75"/>`}
 else{g+=`<circle cx="${W*.8}" cy="${H*.32}" r="${H*.09}" fill="var(--sun)" opacity=".8"/>`;
  const copa=(yy,col,n,r)=>{let s='';for(let i=0;i<n;i++){s+=`<circle cx="${(i/(n-1))*(W+80)-40}" cy="${yy+((i*37)%24)-12}" r="${r+((i*13)%10)}" fill="${col}"/>`}return s+`<rect x="0" y="${yy}" width="${W}" height="${H}" fill="${col}"/>`};
  g+=copa(H*.55,'var(--l1)',16,52)+copa(H*.7,'var(--l2)',13,64)+`<path d="M${W*.1} ${H}C${W*.3} ${H*.82} ${W*.45} ${H*.9} ${W*.6} ${H*.8}S${W*.9} ${H*.74} ${W} ${H*.76}" stroke="var(--sky2)" stroke-width="${H*.07}" fill="none" opacity=".85"/>`+copa(H*.9,'var(--l3)',11,70)}
 const px=L?[60,300,560,800,1040]:[40,360,640,960,1160],py=L?[H*.96,H*.86,H*.93,H*.8,H*.88]:[H*.84,H*.76,H*.82,H*.74,H*.8];
 g+=`<path d="M${px[0]} ${py[0]}C${px[1]} ${py[1]} ${px[1]} ${py[1]} ${px[2]} ${py[2]}S${px[3]} ${py[3]} ${px[4]} ${py[4]}" stroke="#fff" stroke-width="5" fill="none" stroke-linecap="round" stroke-dasharray="1 13"/><g transform="translate(${px[3]*.97} ${py[3]-6})"><circle r="13" fill="var(--ink)"/><circle r="8" fill="var(--marker)"/></g>`;
 return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMax slice" aria-hidden="true">${g}</svg>`}
function aplicaTema(){document.documentElement.dataset.bioma=S.bioma;
 const c=encodeURIComponent(cssv('--contour'));
 document.documentElement.style.setProperty('--contour-img',`url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='520' height='520' fill='none' stroke='${c}' stroke-opacity='.08'%3E%3Cpath d='M-20 380C80 300 140 340 220 260S360 120 540 180'/%3E%3Cpath d='M-20 420C90 340 150 380 230 300S370 160 540 220'/%3E%3Cpath d='M-20 460C100 380 160 420 240 340S380 200 540 260'/%3E%3Cpath d='M-20 100C60 60 150 90 210 40S330-10 540 40'/%3E%3Cpath d='M-20 140C70 100 160 130 220 80S340 30 540 80'/%3E%3C/svg%3E")`);
 $$('[data-land]').forEach(e=>e.innerHTML=terra(S.bioma,e.dataset.land));}
const CERRADO_RODAPE=`/*CERRADO*/`;

/* ===== componentes ===== */
const ic=n=>`<svg class="ic"><use href="#i-${n}"/></svg>`;
const tag=(t,k='')=>`<span class="tag ${k}">${esc(t)}</span>`;
const SIT={vigente:['Vigente','ok'],substituido:['Substituído','gray'],indisponivel:['Indisponível','bad']};
function toast(msg,bad){const w=$('#toast'),d=document.createElement('div');d.textContent=msg;if(bad)d.className='bad';w.appendChild(d);setTimeout(()=>d.remove(),3800)}
function nav(h){location.hash=h}
const rota=()=>{const h=(location.hash||'#/').slice(2).split('/').filter(Boolean);return h};

/* ===== telas ===== */
function telaLogin(){return `<section class="view on" aria-label="Entrar"><div class="login">
 <div class="marca"><svg class="mk" viewBox="0 0 240 240" aria-hidden="true"><use href="#logo"/></svg><h1 class="wm"><span class="l">SIG</span>RASTRO</h1><p>Acompanhamento de processos de licenciamento ambiental</p></div>
 <form class="panel" id="f-login" novalidate><h2>Entrar</h2>
  <label>Usuário<input type="text" name="u" value="operador.exemplo" autocomplete="off"></label>
  <label>Senha<input type="password" name="s" value="demo"></label>
  <div class="msg-erro" id="e-login" role="alert"></div>
  <button class="btn" type="submit">Entrar</button>
  <p class="ajuda">Acesso de demonstração, com dados fictícios. Usuários: <b>operador.exemplo</b>, <b>sigilo.exemplo</b> e <b>admin.exemplo</b>. Senha: <b>demo</b>.</p></form></div></section>`}

function cabecalho(ativa){const u=S.user;const it=(h,r,t,i,on)=>`<a href="#/${h}" ${on?'aria-current="page"':''}>${ic(i)}<span>${t}</span></a>`;
 return `<header class="top"><a class="logo" href="#/consulta" aria-label="SIGRASTRO, início"><svg class="mk" viewBox="0 0 240 240" aria-hidden="true"><use href="#logo"/></svg><span class="wm"><span class="l">SIG</span>RASTRO</span></a>
 <nav class="nav" aria-label="Principal">${it('consulta','consulta','Consulta','search',ativa==='consulta')}${it('dossies','dossies','Dossiês gerados','layers',ativa==='dossies')}${it('aud/docs','aud','Auditoria','shield',ativa==='aud')}${can.config(S.perfil)?it('config/usuarios','config','Configurações','gear',ativa==='config'):''}</nav>
 <div class="who"><span class="perfil-chip">${esc(PERFIS[S.perfil])}</span><span>${esc(u.nome)}</span><b>${esc(u.nome.split(' ').map(x=>x[0]).slice(0,2).join(''))}</b><button data-act="sair">Sair</button></div></header>`}

function telaConsulta(){const tipo=S.tipoBusca||'Processo';
 const rec=S.recentes.map(r=>{const [n,t]=r.split('|');const p=achar(t,n);return p?`<li><a href="#/p/${slug(p.id)}"><b>${esc(t)} ${esc(n)}</b><span>${esc(p.interessado)} · ${esc(p.objeto)}</span>${tag(p.sit[0],p.sit[1])}</a></li>`:''}).join('');
 return `<section class="view on" aria-label="Consulta"><div class="faixa"><div class="txt"><h1>Consulta</h1><p>Busque pelo número do processo ou do requerimento.</p></div><span class="bio">Tema ${NOMES[S.bioma]}</span><div data-land="faixa" aria-hidden="true" style="position:absolute;inset:0"></div></div>
 <form class="panel finder sobre" id="f-busca" role="search" novalidate><div class="seg" role="group" aria-label="Tipo de busca"><button type="button" data-tipo="Processo" aria-pressed="${tipo==='Processo'}">Processo</button><button type="button" data-tipo="Requerimento" aria-pressed="${tipo==='Requerimento'}">Requerimento</button></div>
 <input type="text" id="q" placeholder="Número no formato N/AAAA, por exemplo 1530/2026" aria-label="Número" inputmode="numeric" autocomplete="off"><button class="btn" type="submit">${ic('search')}Consultar</button></form>
 <div class="msg-erro" id="e-busca" role="alert" style="margin:10px 20px 0"></div>
 <div class="ex"><span>Para testar:</span>${PROCS.map(p=>`<button data-ex="${esc(p.tipo)}|${esc(p.id)}">${esc(p.tipo)} ${esc(p.id)}</button>`).join('')}<button data-ex="Processo|9999/2026">Número que não existe</button></div>
 <div class="recent"><h2>Consultados por último</h2>${rec?`<ul class="rec-list">${rec}</ul>`:`<div class="empty"><b>Nada consultado ainda</b>Os números que você consultar aparecem aqui, neste navegador.</div>`}</div></section>`}

function docsOrdenados(p){const f=S.filtro[p.id]||{q:'',tipo:''},o=S.ordem[p.id]||{k:'data',d:1};
 let l=docsDe(p).filter(d=>(!f.q||d.nome.toLowerCase().includes(f.q.toLowerCase()))&&(!f.tipo||d.tipo===f.tipo));
 const v=(d)=>o.k==='data'?d.data.split('/').reverse().join(''):o.k==='mb'?d.mb:String(d[o.k]).toLowerCase();
 return l.sort((a,b)=>(v(a)>v(b)?1:v(a)<v(b)?-1:0)*o.d)}
function selecionavel(d){if(d.sit==='indisponivel')return false;if(d.sig&&!can.sigilo(S.perfil))return false;if(d.origem==='Pessoal'&&S.sim.scorp)return false;return true}
function selPadrao(p){return new Set(docsDe(p).filter(d=>selecionavel(d)&&d.sit==='vigente').map(d=>d.id))}
function selDe(p){if(!S.sel[p.id])S.sel[p.id]=selPadrao(p);return S.sel[p.id]}
function linhasDocs(p){const sel=selDe(p),l=docsOrdenados(p);if(!l.length)return `<tr><td colspan="7"><div class="empty"><b>Nenhum documento encontrado</b>Mude o texto ou o tipo do filtro.</div></td></tr>`;
 return l.map(d=>{const ok=selecionavel(d);let s=SIT[d.sit];if(d.sig&&!can.sigilo(S.perfil))s=['Sem permissão','warn'];if(d.origem==='Pessoal'&&S.sim.scorp)s=['Indisponível (SCORP)','bad'];
 return `<tr><td><input type="checkbox" data-doc="${esc(d.id)}" ${sel.has(d.id)?'checked':''} ${ok?'':'disabled'} aria-label="Selecionar ${esc(d.nome)}"></td>
 <td><span class="nome">${d.sig?ic('lock').replace('class="ic"','class="ic" style="width:15px;height:15px;vertical-align:-2px;margin-right:4px"'):''}${esc(d.nome)}</span><span class="sub">${d.mb?d.mb.toLocaleString('pt-BR')+' MB':'—'}${d.conv?' · será convertido para PDF':''}${d.sig?' · sigiloso':''}</span></td>
 <td>${esc(d.tipo)}</td><td>${esc(d.data)}</td><td>${esc(d.origem)}</td><td>${tag(s[0],s[1])}</td>
 <td><button class="btn quiet sm" data-prev="${esc(d.id)}" ${ok&&d.sit!=='indisponivel'?'':'disabled'}>Visualizar</button></td></tr>`}).join('')}
function barra(p){const sel=selDe(p),docs=docsDe(p).filter(d=>sel.has(d.id)),mb=docs.reduce((a,d)=>a+d.mb,0);
 return `<span><b>${docs.length} documento${docs.length===1?'':'s'}</b> selecionado${docs.length===1?'':'s'} · ${mb.toLocaleString('pt-BR',{maximumFractionDigits:1})} MB</span><button class="btn" data-act="gerar" ${docs.length?'':'disabled'}>${ic('down')}Gerar dossiê</button>`}

function telaProcesso(p){const dl=docsDe(p),tipos=[...new Set(dl.map(d=>d.tipo))],f=S.filtro[p.id]||{q:'',tipo:''},o=S.ordem[p.id]||{k:'data',d:1};
 const scorp=S.sim.scorp&&dl.some(d=>d.origem==='Pessoal');
 const vig=dl.filter(d=>d.sit==='vigente').length,sub=dl.filter(d=>d.sit==='substituido').length,ind=dl.filter(d=>d.sit==='indisponivel').length;
 const trilha=p.caminho.filter(c=>!c.fim).map((c,i,a)=>`<li class="${c.atual||(i===a.length-1&&!p.caminho.some(x=>x.atual)&&!p.caminho.some(x=>x.fim))?'now':''}"><b>${esc(c.n)}</b><time>${esc(c.d)}</time>${(c.atual||(i===a.length-1&&!p.caminho.some(x=>x.fim)))?`<div class="who-box"><span>${p.aproximado?'Responsável':'Com o grupo'}</span>${esc(p.onde.grupo)}</div>`:''}</li>`).join('');
 const concluido=p.caminho.some(c=>c.fim);
 return `<section class="view on" aria-label="${esc(p.tipo)} ${esc(p.id)}"><a class="back" href="#/consulta">${ic('left')}Voltar para a consulta</a>
 <div class="det-head"><div><small>${esc(p.tipo)} · ${esc(p.objeto)}</small><h1>${esc(p.id)}</h1>
 <div class="tags">${tag(p.sit[0],p.sit[1])}${p.prior?tag('Prioritário'):''}${p.isento?tag('Isento de Taxa','ok'):''}${p.taxaVencida?tag('Taxa vencida em '+p.taxaVencida,'bad'):''}${p.aproximado?tag('Caminho aproximado','gray'):''}</div></div>
 <a class="btn quiet" href="#/p/${slug(p.id)}/caminho">Ver o caminho completo</a></div>
 <div class="det"><aside class="panel rail" aria-label="Rastro do processo"><h2>Rastro do processo</h2><p class="sub">${concluido?'O processo chegou ao fim':'O que já aconteceu e onde está agora'}</p><ol class="trail">${trilha}</ol></aside>
 <div class="stack"><section class="panel info" aria-label="Dados"><h2>Dados do ${p.tipo==='Processo'?'processo':'requerimento'}</h2>
 <dl class="dl"><div><dt>Interessado</dt><dd>${esc(p.interessado)}</dd></div><div><dt>CPF/CNPJ</dt><dd>${esc(p.cnpj)}</dd></div><div><dt>Abertura</dt><dd>${esc(p.abertura)}</dd></div><div><dt>Última atualização</dt><dd>${esc(p.atual)}</dd></div><div><dt>Nº do requerimento</dt><dd>${esc(p.req)}</dd></div><div><dt>Onde está agora</dt><dd>${esc(p.onde.etapa)} · desde ${esc(p.onde.desde)}</dd></div></dl>
 ${p.taxaVencida?`<div class="alert" role="status">${ic('alert')}<div><b>A taxa de protocolo venceu em ${esc(p.taxaVencida)}.</b>O sistema de origem ainda não atualiza essa situação. O aviso some quando o sistema corrigir.</div></div>`:''}
 ${p.aproximado?`<div class="alert" role="status" style="background:var(--marker-tint);color:#5A4300">${ic('alert')}<div><b>Este processo não tem o registro detalhado de tramitação.</b>O caminho mostrado é aproximado e vem do histórico do requerimento.</div></div>`:''}</section>
 <section class="panel docs" aria-label="Documentos"><header><div><h2>Documentos</h2><p>${dl.length} documentos · ${vig} vigentes · ${sub} substituído${sub===1?'':'s'} · ${ind} indisponíve${ind===1?'l':'is'}</p></div><div style="display:flex;gap:8px"><button class="btn quiet sm" data-act="padrao">Seleção padrão</button><button class="btn quiet sm" data-act="todos">Selecionar todos</button><button class="btn quiet sm" data-act="limpar">Limpar</button></div></header>
 ${scorp?`<div class="note" role="status">${ic('alert')}<span>Os documentos pessoais do interessado estão indisponíveis agora (SCORP fora do ar). O dossiê sai sem eles.</span><button class="btn quiet sm" data-act="reconsultar">Consultar novamente</button></div>`:''}
 <div class="toolbar"><input type="text" id="fq" placeholder="Filtrar pelo nome do arquivo" aria-label="Filtrar pelo nome do arquivo" value="${esc(f.q)}"><select id="ft" aria-label="Filtrar por tipo"><option value="">Todos os tipos</option>${tipos.map(t=>`<option ${f.tipo===t?'selected':''}>${esc(t)}</option>`).join('')}</select></div>
 <div class="tbl-wrap"><table><thead><tr><th><span class="sr">Selecionar</span></th>${[['nome','Arquivo'],['tipo','Tipo'],['data','Data'],['origem','Origem'],['sit','Situação']].map(([k,t])=>`<th data-sort="${k}" tabindex="0" ${o.k===k?`aria-sort="${o.d>0?'ascending':'descending'}"`:''}>${t}</th>`).join('')}<th><span class="sr">Ações</span></th></tr></thead><tbody id="tb-docs">${linhasDocs(p)}</tbody></table></div></section></div></div>
 <div class="bar" id="barra" role="region" aria-label="Dossiê">${barra(p)}</div></section>`}

function telaCaminho(p){const fl=p.caminho;let corpo;
 if(p.aproximado)corpo=`<div class="note bad" style="margin:0 0 16px">${ic('alert')}<span><b>Caminho aproximado.</b> Este processo não tem tramitação detalhada; os marcos vêm do histórico do requerimento e podem não mostrar todas as etapas.</span></div><ol class="timeline-aprox">${fl.map(c=>`<li><time>${esc(c.d)}</time><span>${esc(c.n)}</span></li>`).join('')}</ol>`;
 else corpo=`<div class="flow" role="list">${fl.map((c,i)=>{const ult=i===fl.length-1;if(c.fim)return `<span class="end" role="listitem"><em>${esc(c.fim)}</em></span>`;
   return `<div class="node ${c.atual?'now':''}" role="listitem"><b>${esc(c.n)}</b><span>${esc(c.d)}</span>${c.p?`<span class="pass">${esc(c.p)}</span>`:''}</div>${ult?'':`<i class="link ${fl[i+1]?.atual?'dash':''}"></i>`}`}).join('')}</div>
   <div class="legend"><span><i></i>Etapa concluída</span><span><i style="background:var(--marker-tint);box-shadow:inset 0 0 0 2px var(--ink)"></i>Etapa atual</span><span>Bolinha preta: processo concluído</span></div>`;
 return `<section class="view on" aria-label="Caminho do processo"><a class="back" href="#/p/${slug(p.id)}">${ic('left')}Voltar para o ${p.tipo.toLowerCase()} ${esc(p.id)}</a>
 <div class="cons-head"><h1>Caminho do ${p.tipo.toLowerCase()} ${esc(p.id)}</h1><p>Só o que aconteceu até agora, na ordem em que aconteceu.</p></div>
 <div class="panel flow-wrap" style="padding-top:${p.aproximado?20:36}px"><p class="cap">${esc(p.tipo)} ${esc(p.id)} ${tag(p.sit[0],p.sit[1])}</p>${corpo}</div></section>`}

function telaDossies(){const l=S.dossies;
 const ln=l.map(d=>{const sit=d.estado==='PRONTO'?tag('Pronto','ok'):d.estado==='BAIXADO'?tag('Baixado','gray'):d.estado==='FALHA'?tag('Falhou','bad'):tag('Em andamento');
 const ac=d.estado==='PRONTO'?`<button class="btn sm" data-dl="${d.id}">${ic('down')}Baixar</button> <button class="btn quiet sm" data-desc="${d.id}">Descartar</button>`:d.estado==='FALHA'?`<button class="btn sm" data-retry="${d.id}">Tentar novamente</button> <button class="btn quiet sm" data-desc="${d.id}">Descartar</button>`:d.estado==='BAIXADO'?'<span class="sub">O arquivo sai do servidor ao fim do download.</span>':'';
 return `<tr><td>${esc(d.proc)}</td><td>${d.n}</td><td>${d.mb.toLocaleString('pt-BR',{maximumFractionDigits:1})} MB</td><td>${fmtDH(d.criado)}</td><td>${sit}${d.estado==='PRONTO'?`<span class="sub">Expira às ${fmtH(new Date(d.criado.getTime()+S.params.DOSSIE_EXPIRACAO_HORAS*3600000))}</span>`:''}${d.estado==='FALHA'?`<span class="sub" style="color:var(--bad)">${esc(d.erro)}</span>`:''}</td><td>${ac}</td></tr>`}).join('');
 return `<section class="view on"><div class="cons-head"><h1>Dossiês gerados</h1><p>Os dossiês que você gerou nesta sessão.</p></div>
 <div class="panel docs" style="margin-top:20px"><div class="tbl-wrap">${l.length?`<table><thead><tr><th style="padding-left:24px">Processo</th><th>Documentos</th><th>Tamanho</th><th>Gerado em</th><th>Situação</th><th>Ações</th></tr></thead><tbody>${ln}</tbody></table>`:`<div class="empty"><b>Nenhum dossiê gerado ainda</b>Abra um processo, escolha os documentos e use "Gerar dossiê".<br><br><a class="btn" href="#/p/1530-2026">Abrir o processo 1530/2026</a></div>`}</div></div></section>`}

function abasAud(a){return `<div class="sub-tabs"><a href="#/aud/docs" ${a==='docs'?'aria-current="page"':''}>Documentos</a>${can.acessos(S.perfil)?`<a href="#/aud/acessos" ${a==='acessos'?'aria-current="page"':''}>Acessos</a>`:''}</div>`}
function telaAud(a){const tipos=a==='acessos'?['ACESSO']:['CONSULTA','VISUALIZACAO','DOSSIE','DOWNLOAD'];const f=S.filtro['aud'+a]||{q:'',tipo:''};
 const l=S.audit.filter(e=>(a==='acessos'?['ACESSO','CONFIG','CONEXAO'].includes(e.tipo):tipos.includes(e.tipo))&&(!f.tipo||e.tipo===f.tipo)&&(!f.q||(e.user+' '+e.detalhe+' '+e.proc).toLowerCase().includes(f.q.toLowerCase())));
 const opts=(a==='acessos'?['ACESSO','CONFIG','CONEXAO']:tipos);
 return `<section class="view on"><div class="cons-head"><h1>Auditoria</h1><p>Quem fez o quê, quando e com qual resultado. Os registros novos entram conforme você usa o protótipo.</p></div>${abasAud(a)}
 <div class="panel docs"><div class="toolbar" style="padding-top:0"><input type="text" id="aq" placeholder="Buscar por usuário, processo ou detalhe" aria-label="Buscar" value="${esc(f.q)}"><select id="at" aria-label="Tipo de evento"><option value="">Todos os eventos</option>${opts.map(t=>`<option ${f.tipo===t?'selected':''}>${t}</option>`).join('')}</select><span class="grow"></span><button class="btn quiet sm" data-act="csv" data-a="${a}">${ic('down')}Exportar CSV</button></div>
 <div class="tbl-wrap"><table><thead><tr><th style="padding-left:24px;width:auto">Data e hora</th><th>Usuário</th><th>Evento</th><th>Processo</th><th style="min-width:260px">Detalhe</th><th>Resultado</th></tr></thead><tbody>${l.length?l.map(e=>`<tr><td style="padding-left:24px;white-space:nowrap">${fmtDH(e.t)}</td><td>${esc(e.user)}</td><td>${tag(e.tipo,'gray')}</td><td>${esc(e.proc||'—')}</td><td style="max-width:420px;word-break:break-word">${esc(e.detalhe)}</td><td>${e.ok?tag('Concluído','ok'):tag('Recusado','bad')}</td></tr>`).join(''):`<tr><td colspan="6"><div class="empty"><b>Nenhum evento encontrado</b>Mude a busca ou o tipo de evento.</div></td></tr>`}</tbody></table></div></div></section>`}

function abasCfg(a){return `<div class="sub-tabs">${[['usuarios','Usuários e perfis'],['parametros','Parâmetros'],['conexoes','Conexões'],['aparencia','Aparência']].map(([k,t])=>`<a href="#/config/${k}" ${a===k?'aria-current="page"':''}>${t}</a>`).join('')}</div>`}
function telaCfg(a){let c='';
 if(a==='usuarios')c=`<div class="panel docs"><div class="toolbar" style="padding-top:0"><b>${S.usuarios.length} usuários</b><span class="grow"></span></div><div class="tbl-wrap"><table><thead><tr><th style="padding-left:24px;width:auto">Nome</th><th>Login</th><th>Perfil</th><th>Situação</th></tr></thead><tbody>${S.usuarios.map((u,i)=>`<tr><td style="padding-left:24px">${esc(u.nome)}</td><td>${esc(u.login)}</td><td><select data-uperfil="${i}" aria-label="Perfil de ${esc(u.nome)}">${Object.entries(PERFIS).map(([k,v])=>`<option value="${k}" ${u.perfil===k?'selected':''}>${v}</option>`).join('')}</select></td><td><label class="switch"><input type="checkbox" data-uativo="${i}" ${u.ativo?'checked':''}>${u.ativo?'Ativo':'Inativo'}</label></td></tr>`).join('')}</tbody></table></div>
 <form class="toolbar" id="f-user" style="padding:16px 24px 20px;border-top:1px solid var(--line);margin-top:8px" novalidate><b>Novo usuário</b><input type="text" name="login" placeholder="login" aria-label="Login"><input type="text" name="nome" placeholder="Nome" aria-label="Nome"><select name="perfil" aria-label="Perfil">${Object.entries(PERFIS).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select><button class="btn sm" type="submit">Adicionar</button><span class="erro-campo" id="e-user"></span></form></div>`;
 if(a==='parametros'){const r=S.rascunho||{...S.params},sujo=JSON.stringify(r)!==JSON.stringify(S.params);
  c=`${sujo?`<div class="bar-pend" role="status"><span><b>Alterações não salvas.</b> Valem só depois de salvar.</span><span style="display:flex;gap:8px"><button class="btn quiet sm" data-act="descartar-p">Descartar</button><button class="btn sm" data-act="salvar-p">Salvar</button></span></div>`:''}
  <div class="panel info"><h2>Parâmetros do dossiê e das conexões</h2><p style="color:var(--slate)">Valores em memória, como no protótipo. O espaço livre simulado é de <b>${S.sim.livreMB.toLocaleString('pt-BR')} MB</b> (mude no painel de testes). Com o mínimo acima disso, o dossiê é recusado antes de começar.</p>
  <div class="dl" style="margin-top:18px">${Object.keys(S.params).map(k=>`<div class="field"><label for="p-${k}">${k}</label><input id="p-${k}" type="text" inputmode="numeric" data-par="${k}" value="${esc(r[k])}"><span class="hint">Entre ${LIMITES[k][0]} e ${LIMITES[k][1]}</span><span class="erro-campo" id="pe-${k}"></span></div>`).join('')}</div></div>`}
 if(a==='conexoes')c=`<div class="cards3">${[['arquivos','API de arquivos'],['scorp','SCORP'],['siga','Banco do SIGA (leitura)']].map(([k,t])=>`<div class="card"><h3>${t}</h3><button class="btn sm" data-teste="${k}" style="justify-self:start">Testar conexão</button><div class="res" id="res-${k}" aria-live="polite"><span class="sub" style="color:var(--slate)">Ainda não testada. Limite do teste: ${S.params.CONEXAO_TESTE_TIMEOUT_SEGUNDOS} s.</span></div></div>`).join('')}</div>`;
 if(a==='aparencia')c=`<div class="panel info"><h2>Aparência</h2><p style="color:var(--slate)">O tema vem do bioma. O que você escolhe fica salvo neste navegador.</p><div class="cards3">${Object.entries(NOMES).map(([k,v])=>`<label class="card" style="cursor:pointer;${S.bioma===k?'box-shadow:0 0 0 3px var(--trail)':''}"><span class="switch"><input type="radio" name="bio" value="${k}" ${S.bioma===k?'checked':''}> <b>${v}</b></span><div class="faixa" style="height:90px" data-prev-bio="${k}"></div></label>`).join('')}</div></div>`;
 return `<section class="view on"><div class="cons-head"><h1>Configurações</h1><p>Só o perfil Administrador chega aqui.</p></div>${abasCfg(a)}${c}</section>`}
function telaProibida(){return `<section class="view on"><div class="cons-head"><h1>Sem permissão</h1><p>O seu perfil (${esc(PERFIS[S.perfil])}) não acessa esta tela. Entre com um perfil Administrador pelo painel de testes.</p></div><p><a class="btn" href="#/consulta">Voltar para a consulta</a></p></section>`}
function telaNaoAchou(){return `<section class="view on"><div class="cons-head"><h1>Não encontrei esse registro</h1><p>Confira o número e o tipo e tente de novo.</p></div><p><a class="btn" href="#/consulta">Voltar para a consulta</a></p></section>`}

/* ===== roteamento ===== */
function desenha(){const r=rota();const app=$('#app');
 if(!S.user){if(r[0]!=='login')return nav('/login');app.innerHTML=telaLogin();document.body.classList.add('pg-login');aplicaTema();return}
 if(r[0]==='login'||!r.length)return nav('/consulta');
 let ativa='consulta',html;
 if(r[0]==='consulta')html=telaConsulta();
 else if(r[0]==='p'){const p=PROCS.find(x=>slug(x.id)===r[1]||(x.tipo==='Requerimento'&&slug(x.req)===r[1]));html=p?(r[2]==='caminho'?telaCaminho(p):telaProcesso(p)):telaNaoAchou();
   if(p&&r[2]!=='caminho'&&S.ultimoVisto!==p.id){S.ultimoVisto=p.id;const k=p.id+'|'+p.tipo;S.recentes=[k,...S.recentes.filter(x=>x!==k)].slice(0,5);pref.set('recentes',S.recentes);audita('CONSULTA',`${p.tipo} ${p.id}`,{proc:p.id})}}
 else if(r[0]==='dossies'){ativa='dossies';html=telaDossies()}
 else if(r[0]==='aud'){ativa='aud';const a=r[1]==='acessos'?'acessos':'docs';html=(a==='acessos'&&!can.acessos(S.perfil))?telaProibida():telaAud(a)}
 else if(r[0]==='config'){ativa='config';html=can.config(S.perfil)?telaCfg(r[1]||'usuarios'):telaProibida()}
 else html=telaNaoAchou();
 if(r[0]!=='p')S.ultimoVisto=null;
 document.body.classList.remove('pg-login');app.innerHTML=cabecalho(ativa)+'<main>'+html+'</main>';aplicaTema();
 $$('[data-prev-bio]').forEach(e=>{const b=S.bioma;const sv=document.documentElement.dataset.bioma;e.innerHTML=''; /* pré-visualização usa o tema atual */ e.style.background='linear-gradient(var(--sky1),var(--sky2))';e.innerHTML=terraDe(e.dataset.prevBio)});
 window.scrollTo(0,0)}
/* pré-visualização do bioma: gera com variáveis do bioma pedido */
function terraDe(b){const tmp=document.createElement('div');tmp.dataset.bioma=b;const m={cerrado:['#D9B77A','#B9803F','#8A4A1F','#E8B712','#F4E3B8','#FBF4DC'],pantanal:['#9CCBD2','#5FA3AE','#1F6B78','#F0B429','#BFE3EA','#F3FAFB'],amazonia:['#7DB48F','#3F8A5D','#17512F','#E4572E','#CFE8D6','#F1F8F3']}[b];
 return terra(b,'faixa').replace(/var\(--l1\)/g,m[0]).replace(/var\(--l2\)/g,m[1]).replace(/var\(--l3\)/g,m[2]).replace(/var\(--sun\)/g,m[3]).replace(/var\(--sky2\)/g,m[5]).replace(/var\(--ink\)/g,'#222').replace(/var\(--marker\)/g,m[3]).replace('<svg ','<svg style="background:linear-gradient('+m[4]+','+m[5]+')" ')}

/* ===== modal de visualização ===== */
function abreModal(html){const v=document.createElement('div');v.className='veil';v.innerHTML=`<div class="dlg" role="dialog" aria-modal="true" aria-labelledby="dlg-t">${html}</div>`;v.addEventListener('click',e=>{if(e.target===v)fecha()});$('#camada').replaceChildren(v);const b=$('[data-fechar]',v);b&&b.focus()}
function fecha(){$('#camada').replaceChildren()}
function visualiza(p,d){audita('VISUALIZACAO',`Visualizou ${d.nome}`,{proc:p.id});
 abreModal(`<header><div><h2 id="dlg-t">${esc(d.nome)}</h2><span class="sub" style="color:var(--slate)">${esc(d.tipo)} · ${esc(d.data)}${d.conv?' · convertido para PDF na visualização':''}</span></div><button class="btn quiet sm" data-fechar>Fechar</button></header><div class="body"><div class="pagina"><b>${esc(d.nome)}</b><i></i><i style="width:80%"></i><i></i><i style="width:60%"></i><i></i><i style="width:90%"></i><i style="width:40%"></i><span class="sub" style="color:var(--slate);margin-top:8px">Página 1 de 3 (conteúdo fictício)</span></div></div>`)}

/* ===== geração do dossiê ===== */
let ger=null;
function gaveta(h){$('#camada').replaceChildren();const g=document.createElement('aside');g.className='gaveta';g.setAttribute('role','dialog');g.setAttribute('aria-label','Gerar dossiê');g.innerHTML=h;$('#camada').appendChild(g);const f=$('[data-fechar]',g);f&&f.focus()}
function renderGaveta(){const g=ger;const e=g.etapas.map(x=>`<li class="${x.s}">${esc(x.t)}</li>`).join('');const fim=g.estado;
 gaveta(`<header><div><h2 style="font-size:1.25rem">Gerar dossiê</h2><span style="color:var(--slate);font-size:.875rem">${esc(g.proc)} · ${g.n} documentos</span></div><button class="btn quiet sm" data-fechar aria-label="Fechar">Fechar</button></header>
 <div class="corpo"><div class="prog" role="progressbar" aria-valuenow="${g.pct}" aria-valuemin="0" aria-valuemax="100"><i style="width:${g.pct}%"></i></div><ol class="etapas">${e}</ol>
 ${fim==='erro'?`<div class="alert" role="alert">${ic('alert')}<div><b>${esc(g.erro)}</b>${g.dica?esc(g.dica):''}</div></div>`:''}
 ${fim==='pronto'?`<div class="note info" style="margin:0" role="status">${ic('down')}<span>Dossiê pronto, com ${g.n} documentos (${g.mb.toLocaleString('pt-BR',{maximumFractionDigits:1})} MB). O arquivo sai do servidor ao fim do download.</span></div>`:''}
 ${fim==='baixado'?`<div class="note info" style="margin:0" role="status"><span>Download concluído. Para ter o arquivo de novo, gere o dossiê outra vez.</span></div>`:''}</div>
 <footer>${fim==='pronto'?`<button class="btn" data-dl="${g.did}">${ic('down')}Baixar dossiê</button>`:''}${fim==='erro'?`<button class="btn" data-act="tentar">Tentar novamente</button>`:''}${fim==='baixado'?`<button class="btn quiet" data-act="gerar">Gerar novamente</button>`:''}<button class="btn quiet" data-fechar>${fim==='andando'?'Continuar navegando':'Fechar'}</button></footer>`)}
async function geraDossie(p,ids){const docs=docsDe(p).filter(d=>ids.has(d.id)),mb=docs.reduce((a,d)=>a+d.mb,0),k=S.sim.lento?3:1;
 const max=S.params.DOCUMENTOS_MAX_POR_DOSSIE;
 ger={proc:`${p.tipo} ${p.id}`,pid:p.id,n:docs.length,mb,pct:0,estado:'andando',etapas:[{t:'Verificando o espaço em disco',s:'agora'},{t:'Obtendo os documentos',s:''},{t:'Convertendo planilhas e imagens',s:''},{t:'Montando o PDF',s:''}],ids};
 renderGaveta();await sleep(500*k);
 const falhar=(msg,dica,i)=>{ger.etapas[i].s='erro';ger.estado='erro';ger.erro=msg;ger.dica=dica;audita('DOSSIE',`Falhou: ${msg}`,{proc:p.id,ok:false});S.dossies.unshift({id:'d'+(S.seq++),proc:`${p.tipo} ${p.id}`,n:docs.length,mb,criado:new Date(),estado:'FALHA',erro:msg,ids});renderGaveta()};
 if(docs.length>max)return falhar(`O dossiê passa do limite de ${max} documentos.`,' Tire alguns documentos da seleção.',0);
 if(S.params.DOSSIE_ESPACO_MINIMO_MB>S.sim.livreMB)return falhar('Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.',` O mínimo configurado (${S.params.DOSSIE_ESPACO_MINIMO_MB} MB) é maior que o espaço livre (${S.sim.livreMB} MB). Código ARMAZENAMENTO_INSUFICIENTE (507).`,0);
 ger.etapas[0].s='ok';ger.etapas[1].s='agora';
 for(let i=1;i<=docs.length;i++){ger.pct=Math.round(i/docs.length*55);ger.etapas[1].t=`Obtendo os documentos (${i} de ${docs.length})`;renderGaveta();await sleep(380*k)}
 ger.etapas[1].s='ok';ger.etapas[2].s='agora';const nc=docs.filter(d=>d.conv).length;ger.etapas[2].t=nc?`Convertendo ${nc} documento${nc>1?'s':''} para PDF`:'Nenhum documento precisa de conversão';ger.pct=70;renderGaveta();await sleep(600*k);
 ger.etapas[2].s='ok';ger.etapas[3].s='agora';ger.pct=88;renderGaveta();await sleep(700*k);
 if(S.sim.disco)return falhar('Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.',' O arquivo parcial foi apagado. Código ARMAZENAMENTO_INSUFICIENTE.',3);
 ger.etapas[3].s='ok';ger.pct=100;ger.estado='pronto';
 const d={id:'d'+(S.seq++),proc:`${p.tipo} ${p.id}`,pid:p.id,n:docs.length,mb,criado:new Date(),estado:'PRONTO',ids,nomes:docs.map(x=>x.nome)};S.dossies.unshift(d);ger.did=d.id;
 audita('DOSSIE',`Dossiê gerado com ${docs.length} documentos`,{proc:p.id});renderGaveta()}
function baixaDossie(id){const d=S.dossies.find(x=>x.id===id);if(!d||d.estado!=='PRONTO')return toast('Esse dossiê não está mais disponível.',true);
 baixar(pdfDe(['SIGRASTRO - dossie de demonstracao',d.proc,'Documentos incluidos:',...(d.nomes||[]).map(n=>'- '+n),'Conteudo ficticio, gerado para teste.']),`dossie-${slug(d.proc.split(' ')[1])}.pdf`);
 d.estado='BAIXADO';audita('DOWNLOAD',`Baixou o dossiê de ${d.proc}`,{proc:d.pid});toast('Download concluído.');
 if(ger&&ger.did===id){ger.estado='baixado';renderGaveta()}else if(rota()[0]==='dossies')desenha()}

/* ===== conexões simuladas ===== */
async function testaConexao(k){const el=$('#res-'+k);el.innerHTML='<span class="sub">Testando…</span>';const t0=performance.now();await sleep(500+Math.random()*500);
 let m=S.sim.conexao;if(m==='sorteio')m=['ok','ressalva','auth','falha'][Math.floor(Math.random()*4)];
 const ms=Math.round(performance.now()-t0),lim=S.params.CONEXAO_TESTE_TIMEOUT_SEGUNDOS;
 const R={ok:['OK','ok','O serviço respondeu como esperado.'],ressalva:['Alcançável com ressalva','warn','O serviço respondeu 400 para a chamada de teste (esperado para uma chave fictícia). Isso não é falha.'],auth:['Falha de autenticação','bad','O serviço respondeu 401. Renove o token e teste de novo.'],falha:['Falha','bad','Sem resposta no tempo limite ou conexão recusada.']}[m];
 el.innerHTML=`${tag(R[0],R[1])}<p style="margin:8px 0 0">${R[2]}</p><span class="sub" style="color:var(--slate)">${ms} ms · limite de ${lim} s</span>`;
 audita('CONEXAO',`Teste de conexão: ${k} (${R[0]})`,{ok:m!=='falha'&&m!=='auth'})}

/* ===== csv ===== */
function csv(a){const l=S.audit.filter(e=>a==='acessos'?['ACESSO','CONFIG','CONEXAO'].includes(e.tipo):['CONSULTA','VISUALIZACAO','DOSSIE','DOWNLOAD'].includes(e.tipo));
 const q=s=>'"'+String(s).replace(/"/g,'""')+'"';baixar(new Blob(['﻿'+['data_hora;usuario;evento;processo;detalhe;resultado',...l.map(e=>[fmtDH(e.t),e.user,e.tipo,e.proc,e.detalhe,e.ok?'concluido':'recusado'].map(q).join(';'))].join('\n')],{type:'text/csv'}),`auditoria-${a}.csv`);toast('CSV gerado, sem CPF e sem chave de arquivo.')}

/* ===== eventos ===== */
document.addEventListener('submit',e=>{const f=e.target;
 if(f.id==='f-login'){e.preventDefault();const u=f.u.value.trim(),s=f.s.value,us=S.usuarios.find(x=>x.login===u);
  if(!us||s!=='demo'){audita('ACESSO',`Tentativa de entrada recusada (${u||'sem usuário'})`,{ok:false});$('#e-login').textContent='Usuário ou senha inválidos. Use um dos usuários de demonstração, com a senha demo.';return}
  if(!us.ativo){$('#e-login').textContent='Este usuário está inativo.';return}
  S.user={login:us.login,nome:us.nome};S.perfil=us.perfil;audita('ACESSO','Entrada no sistema');nav('/consulta');desenha();syncLab()}
 if(f.id==='f-busca'){e.preventDefault();const n=$('#q').value.trim(),er=$('#e-busca');
  if(!/^\d{1,6}\/\d{4}$/.test(n)){er.textContent='Digite o número no formato N/AAAA, por exemplo 1530/2026.';return}
  const p=achar(S.tipoBusca||'Processo',n);if(!p){er.textContent=`Não encontrei ${(S.tipoBusca||'Processo').toLowerCase()} ${n}. Confira o número e o tipo.`;audita('CONSULTA',`Sem resultado: ${n}`,{ok:false});return}
  nav('/p/'+slug(p.tipo==='Requerimento'?p.req:p.id))}
 if(f.id==='f-user'){e.preventDefault();const l=f.login.value.trim(),n=f.nome.value.trim(),er=$('#e-user');
  if(!l||!n){er.textContent='Preencha o login e o nome.';return}if(S.usuarios.some(u=>u.login===l)){er.textContent='Já existe um usuário com esse login.';return}
  S.usuarios.push({login:l,nome:n,perfil:f.perfil.value,ativo:true});audita('CONFIG',`Usuário ${l} criado`);toast('Usuário adicionado.');desenha()}});
document.addEventListener('input',e=>{const t=e.target;
 if(t.id==='fq'||t.id==='ft'){const p=PROCS.find(x=>slug(x.id)===rota()[1]||slug(x.req)===rota()[1]);S.filtro[p.id]={q:$('#fq').value,tipo:$('#ft').value};$('#tb-docs').innerHTML=linhasDocs(p)}
 if(t.id==='aq'||t.id==='at'){const a=rota()[1]==='acessos'?'acessos':'docs';S.filtro['aud'+a]={q:$('#aq').value,tipo:$('#at').value};const pos=t.selectionStart;desenha();const n=$(t.id==='aq'?'#aq':'#at');n.focus();if(pos!=null&&n.setSelectionRange)try{n.setSelectionRange(pos,pos)}catch{}}
 if(t.dataset.par){S.rascunho=S.rascunho||{...S.params};S.rascunho[t.dataset.par]=t.value;const pos=t.selectionStart;
  const sujo=JSON.stringify(S.rascunho)!==JSON.stringify(S.params);desenha();const n=$(`[data-par="${t.dataset.par}"]`);n.focus();try{n.setSelectionRange(pos,pos)}catch{}if(!sujo)S.rascunho=null}});
document.addEventListener('change',e=>{const t=e.target;
 if(t.dataset.doc){const p=PROCS.find(x=>slug(x.id)===rota()[1]||slug(x.req)===rota()[1]),s=selDe(p);t.checked?s.add(t.dataset.doc):s.delete(t.dataset.doc);$('#barra').innerHTML=barra(p)}
 if(t.dataset.uperfil){const u=S.usuarios[+t.dataset.uperfil];u.perfil=t.value;audita('CONFIG',`Perfil de ${u.login} alterado para ${PERFIS[t.value]}`);toast('Perfil alterado.')}
 if(t.dataset.uativo){const u=S.usuarios[+t.dataset.uativo];u.ativo=t.checked;audita('CONFIG',`${u.login} ${u.ativo?'ativado':'desativado'}`);desenha()}
 if(t.name==='bio'){S.bioma=t.value;pref.set('bioma',t.value);desenha();syncLab()}
});
document.addEventListener('click',async e=>{const t=e.target.closest('button,a,th[data-sort]');if(!t)return;
 if(t.dataset.fechar!==undefined){fecha();return}
 if(t.dataset.tipo){S.tipoBusca=t.dataset.tipo;$$('.seg button').forEach(b=>b.setAttribute('aria-pressed',b===t));return}
 if(t.dataset.ex){const [tp,n]=t.dataset.ex.split('|');S.tipoBusca=tp;desenha();$('#q').value=n;$('#f-busca').requestSubmit();return}
 const p=PROCS.find(x=>slug(x.id)===rota()[1]||slug(x.req)===rota()[1]);
 if(t.dataset.sort&&p){const o=S.ordem[p.id]||{k:'data',d:1};S.ordem[p.id]={k:t.dataset.sort,d:o.k===t.dataset.sort?-o.d:1};$('#tb-docs').innerHTML=linhasDocs(p);$$('th[data-sort]').forEach(h=>{h.removeAttribute('aria-sort');if(h.dataset.sort===S.ordem[p.id].k)h.setAttribute('aria-sort',S.ordem[p.id].d>0?'ascending':'descending')});return}
 if(t.dataset.prev&&p){visualiza(p,docsDe(p).find(d=>d.id===t.dataset.prev));return}
 if(t.dataset.dl){baixaDossie(t.dataset.dl);return}
 if(t.dataset.desc){S.dossies=S.dossies.filter(d=>d.id!==t.dataset.desc);toast('Dossiê descartado.');desenha();return}
 if(t.dataset.retry){const d=S.dossies.find(x=>x.id===t.dataset.retry),pp=PROCS.find(x=>d.proc.endsWith(x.id));S.dossies=S.dossies.filter(x=>x!==d);S.sel[pp.id]=new Set(d.ids);geraDossie(pp,d.ids);return}
 if(t.dataset.teste){testaConexao(t.dataset.teste);return}
 const a=t.dataset.act;if(!a)return;
 if(a==='sair'){audita('ACESSO','Saída do sistema');S.user=null;nav('/login');desenha();syncLab()}
 else if(p&&a==='padrao'){S.sel[p.id]=selPadrao(p);desenha()}
 else if(p&&a==='todos'){S.sel[p.id]=new Set(docsDe(p).filter(selecionavel).map(d=>d.id));desenha()}
 else if(p&&a==='limpar'){S.sel[p.id]=new Set();desenha()}
 else if(p&&a==='reconsultar'){if(S.sim.scorp)toast('O SCORP continua indisponível.',true);else{toast('Documentos pessoais disponíveis de novo.');desenha()}}
 else if(p&&a==='gerar'){geraDossie(p,new Set(selDe(p)))}
 else if(a==='tentar'&&ger){const p2=PROCS.find(x=>x.id===ger.pid);geraDossie(p2,ger.ids)}
 else if(a==='salvar-p'){const r=S.rascunho;let ok=true;for(const k in r){const v=+r[k],[mn,mx]=LIMITES[k];if(!/^\d+$/.test(String(r[k]).trim())||v<mn||v>mx){ok=false;const el=$('#pe-'+k);if(el)el.textContent=`Digite um número entre ${mn} e ${mx}.`}}
   if(!ok){toast('Corrija os campos marcados.',true);return}
   for(const k in r){if(+r[k]!==S.params[k])audita('CONFIG',`Parâmetro ${k} alterado`);S.params[k]=+r[k]}S.rascunho=null;toast('Parâmetros salvos.');desenha()}
 else if(a==='descartar-p'){S.rascunho=null;desenha()}
 else if(a==='csv')csv(t.dataset.a)});
document.addEventListener('keydown',e=>{if(e.key==='Escape')fecha();if((e.key==='Enter'||e.key===' ')&&e.target.matches('th[data-sort]')){e.preventDefault();e.target.click()}});
window.addEventListener('hashchange',desenha);

/* ===== painel de testes ===== */
function montaLab(){$('#lab').innerHTML=`<div class="pn" id="pn" role="region" aria-label="Painel de testes"><h2>Painel de testes</h2><p>Mexa aqui para ver o protótipo reagir. Nada disso existe na versão real.</p>
 <div class="row">Perfil<select id="l-perfil">${Object.entries(PERFIS).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></div>
 <div class="row">Bioma do tema<select id="l-bio">${Object.entries(NOMES).map(([k,v])=>`<option value="${k}">${v}</option>`).join('')}</select></div>
 <label class="switch"><input type="checkbox" id="l-disco"> Disco cheio ao montar o PDF</label>
 <label class="switch"><input type="checkbox" id="l-scorp"> SCORP indisponível</label>
 <label class="switch"><input type="checkbox" id="l-lento"> Geração lenta (3×)</label>
 <div class="row">Espaço livre simulado (MB)<select id="l-livre"><option>200</option><option selected>1200</option><option>5000</option></select></div>
 <div class="row">Resultado do teste de conexão<select id="l-con"><option value="sorteio">Sortear</option><option value="ok">OK</option><option value="ressalva">Alcançável com ressalva</option><option value="auth">Falha de autenticação</option><option value="falha">Falha</option></select></div>
 <button class="btn quiet sm" id="l-reset" style="justify-self:start">Zerar sessão de teste</button></div>
 <button id="lab-btn" aria-expanded="false" aria-controls="pn">Painel de testes</button>`;
 $('#lab-btn').onclick=()=>{const on=$('#pn').classList.toggle('on');$('#lab-btn').setAttribute('aria-expanded',on)};
 $('#l-perfil').onchange=e=>{if(!S.user)return toast('Entre primeiro.',true);S.perfil=e.target.value;audita('ACESSO',`Perfil trocado para ${PERFIS[S.perfil]}`);S.sel={};desenha()};
 $('#l-bio').onchange=e=>{S.bioma=e.target.value;pref.set('bioma',S.bioma);desenha()};
 $('#l-disco').onchange=e=>S.sim.disco=e.target.checked;$('#l-scorp').onchange=e=>{S.sim.scorp=e.target.checked;S.sel={};if(S.user)desenha()};$('#l-lento').onchange=e=>S.sim.lento=e.target.checked;
 $('#l-livre').onchange=e=>{S.sim.livreMB=+e.target.value;if(S.user&&rota()[0]==='config')desenha()};$('#l-con').onchange=e=>S.sim.conexao=e.target.value;
 $('#l-reset').onclick=()=>{S.dossies=[];S.sel={};S.filtro={};S.ordem={};S.rascunho=null;S.audit=S.audit.slice(-7);S.params={DOSSIE_ESPACO_MINIMO_MB:500,CONEXAO_TESTE_TIMEOUT_SEGUNDOS:15,DOSSIE_EXPIRACAO_HORAS:24,DOCUMENTOS_MAX_POR_DOSSIE:200};fecha();toast('Sessão de teste zerada.');desenha()}}
function syncLab(){const p=$('#l-perfil'),b=$('#l-bio');if(p)p.value=S.perfil;if(b)b.value=S.bioma}
montaLab();desenha();syncLab();
