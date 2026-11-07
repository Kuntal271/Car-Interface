#!/usr/bin/env python3
"""Dump the layout of a design screen (text boxes, filled/stroked boxes, svg shapes) as JSON lines, from headless Chrome.
usage: tools/.venv/bin/python tools/geom.py <screen> [query]   e.g. geom.py car "theme=ford&one=3"
Serves docs/design/site on a private port with a wrapper page that loads the screen in an iframe and walks its DOM."""
import sys, os, subprocess, threading, http.server, socketserver, functools, re, html, json, pathlib, tempfile
root = pathlib.Path(__file__).resolve().parent.parent / "docs/design/site"
screen = sys.argv[1]; query = sys.argv[2] if len(sys.argv) > 2 else ""
WRAP = r"""<!doctype html><meta charset=utf-8><body style="margin:0"><iframe id=f style="width:1280px;height:800px;border:0" src="screens/%s.html?raw=1&noanim=1&%s"></iframe><pre id=out></pre><script>
const f=document.getElementById('f');
f.onload=()=>setTimeout(()=>{const d=f.contentDocument,w=f.contentWindow,out=[];
const r2=e=>{const b=e.getBoundingClientRect();return [Math.round(b.left*10)/10,Math.round(b.top*10)/10,Math.round(b.width*10)/10,Math.round(b.height*10)/10]};
const walk=(e)=>{ if(e.nodeType!==1)return; const cs=w.getComputedStyle(e); if(cs.display==='none'||cs.visibility==='hidden')return;
 const tag=e.tagName.toLowerCase(); if(tag==='script'||tag==='style')return;
 let own=''; for(const c of e.childNodes) if(c.nodeType===3) own+=c.data; own=own.trim().replace(/\s+/g,' ');
 const o={t:tag,c:(e.getAttribute('class')||'').slice(0,40),r:r2(e)};
 if(e.getAttribute('data-od-id')) o.id=e.getAttribute('data-od-id');
 if(own){o.text=own;o.fs=cs.fontSize;o.fw=cs.fontWeight;o.ff=cs.fontFamily.split(',')[0];o.col=cs.color;o.ls=cs.letterSpacing;o.ta=cs.textAlign;o.lh=cs.lineHeight;o.tt=cs.textTransform}
 const bg=cs.backgroundColor; if(bg&&bg!=='rgba(0, 0, 0, 0)')o.bg=bg; if(cs.backgroundImage!=='none')o.bgi=cs.backgroundImage.slice(0,160);
 if(parseFloat(cs.borderTopWidth)>0&&cs.borderTopStyle!=='none')o.bd=cs.borderTopWidth+' '+cs.borderTopColor; if(cs.borderRadius!=='0px')o.rad=cs.borderRadius; if(cs.boxShadow!=='none')o.sh=cs.boxShadow.slice(0,100);
 if(cs.opacity!=='1')o.op=cs.opacity; if(tag==='svg'){o.vb=e.getAttribute('viewBox')}
 if(['path','rect','circle','line','polygon','ellipse','polyline'].includes(tag)){o.fill=cs.fill;o.stroke=cs.stroke;o.sw=cs.strokeWidth;o.d=(e.getAttribute('d')||e.getAttribute('points')||'').slice(0,90);}
 if(tag==='text'){}
 const interesting=own||o.bg||o.bgi||o.bd||o.sh||o.fill&&o.fill!=='none'||o.stroke&&o.stroke!=='none'||tag==='svg';
 if(interesting)out.push(o);
 for(const c of e.children)walk(c);};
walk(d.body);document.getElementById('out').textContent=out.map(o=>JSON.stringify(o)).join('\n');document.title='done';},1500);
</script>"""
tmp = pathlib.Path(tempfile.mkdtemp(prefix="geom-"))
os.symlink(root / "screens", tmp / "screens"); os.symlink(root / "assets", tmp / "assets")
(tmp / "wrap.html").write_text(WRAP % (screen, query))
class Q(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a): pass
class S(socketserver.TCPServer): allow_reuse_address = True
h = functools.partial(Q, directory=str(tmp))
srv = S(("127.0.0.1", 35035), h); threading.Thread(target=srv.serve_forever, daemon=True).start()
chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
out = subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--window-size=1280,900", "--virtual-time-budget=6000", "--dump-dom", "http://127.0.0.1:35035/wrap.html"], capture_output=True, text=True, timeout=60).stdout
srv.shutdown()
m = re.search(r'<pre id="out">(.*?)</pre>', out, re.S)
if not m: print("NO OUTPUT"); sys.exit(1)
txt = html.unescape(m.group(1))
dest = root.parent.parent.parent / "artifacts/geom" / f"{screen}{('-' + re.sub('[^a-z0-9]+','_',query)) if query else ''}.jsonl"
dest.write_text(txt); print(dest, len(txt.splitlines()), "elements")
