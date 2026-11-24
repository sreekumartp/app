"""Turn the web atlas (artifact HTML) into the offline page bundled in the Android app.

Usage: python3 build_app_page.py <atlas.html> <android/app/src/main/assets/index.html>

It swaps CDN fonts and d3 for the bundled copies, adds a full HTML document shell,
and adds the Android bridge (native Share and the Back-button hook).
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
s = open(src, encoding="utf-8").read()


def rep(a, b):
    global s
    if a not in s:
        sys.exit(f"pattern not found: {a[:70]!r}")
    s = s.replace(a, b, 1)


s = re.sub(r'<meta name="claude:viewport-fit-sides"[^>]*>\n', "", s)
s = re.sub(r'<link rel="preconnect"[^>]*>\n', "", s)
s = re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\n', "", s)
s = ('<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
     '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n') + s

faces = [("Instrument Sans", "instrument-sans-latin", [400, 500, 600], None),
         ("Bricolage Grotesque", "bricolage-grotesque-latin", [500, 700], None),
         ("JetBrains Mono", "jetbrains-mono-greek", [400, 500], "U+0370-03FF"),
         ("JetBrains Mono", "jetbrains-mono-latin", [400, 500], None)]
ff = ""
for fam, base, weights, urange in faces:
    for w in weights:
        ur = f"unicode-range:{urange};" if urange else ""
        ff += (f'@font-face{{font-family:"{fam}";font-style:normal;font-weight:{w};font-display:swap;'
               f'src:url("fonts/{base}-{w}-normal.woff2") format("woff2");{ur}}}\n')
reset = ("html,body{margin:0}body{font-size:14px;-webkit-tap-highlight-color:transparent}"
         "img{max-width:100%}[hidden]{display:none!important}\n")
rep("<style>\n", "<style>\n" + ff + reset)
rep("</style>\n", "</style>\n</head>\n<body>\n")
rep('<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js"></script>',
    '<script src="d3.min.js"></script>')
rep('<button id="mUnlocks">Show what it unlocks</button>',
    '<button id="mUnlocks">Show what it unlocks</button>\n'
    '      ${window.AtlasNative?`<button id="mShare">Share</button>`:""}')
rep('  if(ev.target.closest("#mClose")) return closeModal();',
    '  if(ev.target.closest("#mClose")) return closeModal();\n'
    '  if(ev.target.closest("#mShare")){ shareTopic(modalId); return; }')
rep("/* ---------- Boot ---------- */", r'''/* ---------- Android bridge ---------- */
function shareTopic(id){
  const n=byId.get(id); if(!n||!window.AtlasNative) return;
  const r=resourcesFor(n); const subs=(SYL[id]||"").split(";").map(x=>x.trim()).filter(Boolean);
  const k=kissFor(n);
  const text=[`${n.label} (Level ${n.lv}, ${dName[n.d]})`,n.desc,"",
    "K.I.S.S. ladder:",...KISS_STAGES.map((st,i)=>`${st.name}: ${k[i]}`),"",
    "Topics to cover:",...subs.map((x,i)=>`${i+1}. ${x}`),"",
    "Resources:",...[...r.learn,...r.papers].map(x=>`• ${x.t}: ${x.u}`)].join("\n");
  window.AtlasNative.share(n.label, text);
}
/* ---------- Boot ---------- */''')
s = s.rstrip() + "\n</body>\n</html>\n"
open(dst, "w", encoding="utf-8").write(s)
print("wrote", dst, len(s), "bytes")
