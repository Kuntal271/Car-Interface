/* COCKPIT design site runtime: icons, shell, hydrators, simulator, gauge builders. No dependencies. */
(function () {
  'use strict';
  var UI = (window.UI = {});
  var Q = (UI.q = new URLSearchParams(location.search));
  /* theme: ?theme= wins, then localStorage, default 'ford'. Set before first paint. */
  UI.theme = Q.get('theme') || (function () { try { return localStorage.getItem('cockpit-theme'); } catch (e) { return null; } })() || 'ford';
  if (UI.theme !== 'ford' && UI.theme !== 'cockpit') UI.theme = 'ford';
  try { if (Q.get('theme')) localStorage.setItem('cockpit-theme', UI.theme); } catch (e) {}
  document.documentElement.setAttribute('data-theme', UI.theme);
  if (Q.has('noanim') && Q.get('noanim') !== '0') document.documentElement.classList.add('noanim');
  var BASE = document.currentScript ? document.currentScript.src.replace(/[^/]*$/, '') : '';
  document.write('<link rel="stylesheet" href="' + BASE + 'theme-ford.css"><script src="' + BASE + 'brand.js"><\/script>');
  UI.setTheme = function (t) { try { localStorage.setItem('cockpit-theme', t); } catch (e) {} var u = new URL(location.href); u.searchParams.set('theme', t); location.href = u.toString(); };
  UI.embed = Q.has('embed') || Q.has('raw');
  if (UI.embed) document.documentElement.classList.add('raw');
  document.addEventListener('DOMContentLoaded', function () { if (UI.embed) document.body.classList.add('raw'); });

  /* ---------------- icons (24px grid, 1.8 stroke, square caps) ---------------- */
  var I = {
    home: '<path d="M3 11.5 12 4l9 7.5M5.5 10v10.5h13V10M10 20.5v-6h4v6"/>',
    gauge: '<path d="M3.5 17.5a9 9 0 1 1 17 0"/><path d="M12 14l4.5-5.5"/><circle cx="12" cy="14" r="1.4"/>',
    car: '<path d="M5 17v-5l1.8-5.2A1.5 1.5 0 0 1 8.2 6h7.6a1.5 1.5 0 0 1 1.4.8L19 12v5M3.5 17h17M7 17v2.5M17 17v2.5M5.5 12h13M8 14.5h.01M16 14.5h.01"/>',
    music: '<path d="M9 18V5l11-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
    phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A15 15 0 0 1 4 5a1 1 0 0 1 1-1z"/>',
    nav: '<path d="M4 11.5 20 4l-7.5 16-2-6.5z"/>',
    grid: '<path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"/>',
    sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M9 14v6"/>',
    radio: '<path d="M3 9h18v11H3zM7 9l11-5.5"/><circle cx="8" cy="14.5" r="2"/><path d="M13 13.5h5M13 16.5h5"/>',
    usb: '<path d="M12 3v15M9.5 5.5 12 3l2.5 2.5M12 13l-4-2.5V8M12 15.500l4-2.500v-2"/><circle cx="12" cy="19.500" r="2"/><rect x="6.500" y="5.500" width="3" height="3"/><circle cx="16" cy="9" r="1.800"/>',
    bt: '<path d="M7 7.5 17 16l-5 4.5V3.5L17 8 7 16.5"/>',
    wifi: '<path d="M3 9a14 14 0 0 1 18 0M6 12.5a9.5 9.5 0 0 1 12 0M9 16a5 5 0 0 1 6 0M12 19.5h.01"/>',
    camera: '<path d="M3 8h4l1.5-2.5h7L17 8h4v11H3z"/><circle cx="12" cy="13.5" r="3.5"/>',
    park: '<path d="M4 4h16v16H4z"/><path d="M9.500 17V7.500H13a2.500 2.500 0 0 1 0 5H9.500"/>',
    pulse: '<path d="M2.5 12H7l2.5-6 5 12 2.5-6h4.5"/>',
    route: '<circle cx="5" cy="19" r="2"/><circle cx="19" cy="5" r="2"/><path d="M7 19h7a4 4 0 0 0 0-8h-4a4 4 0 0 1 0-8h7"/>',
    fan: '<circle cx="12" cy="12" r="1.5"/><path d="M12 10.5C12 6 14 3.5 16.5 4.5c2 1 .5 4.5-4.5 6M13.5 12c4.5 0 7 2 6.5 4.5-.5 2.2-4 1.5-6.5-3M12 13.5c0 4.5-2 7-4.5 6-2-1-.5-4.5 4.5-6M10.5 12C6 12 3.5 10 4 7.5c.5-2.200 4-1.500 6.500 3"/>',
    thermo: '<path d="M10 14.5V5a2 2 0 1 1 4 0v9.5a4 4 0 1 1-4 0zM12 9v8"/>',
    fuel: '<path d="M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15M4 20h11M14 9h2.500a1.500 1.500 0 0 1 1.500 1.500V16a1.500 1.500 0 0 0 3 0V9l-2.500-2.500M7 8h5"/>',
    batt: '<path d="M2.5 8h16v9h-16zM20.500 11v3M6 11.500v2M9.500 11.500v2M13 11.500v2"/>',
    door: '<path d="M5 20.500V5.500L14 3l5 2.500v15zM14 12.500h.01M3 20.500h18"/>',
    play: '<path fill="currentColor" stroke="none" d="M7 4.500v15l12.500-7.500z"/>',
    pause: '<path fill="currentColor" stroke="none" d="M6.500 5h4v14h-4zM13.500 5h4v14h-4z"/>',
    next: '<path fill="currentColor" stroke="none" d="M5 5l10 7-10 7z"/><path d="M18 5v14"/>',
    prev: '<path fill="currentColor" stroke="none" d="M19 5 9 12l10 7z"/><path d="M6 5v14"/>',
    vol: '<path d="M4 9.500h3.500L12 6v12l-4.500-3.500H4zM15.500 9a4 4 0 0 1 0 6M18 6.500a8 8 0 0 1 0 11"/>',
    mute: '<path d="M4 9.500h3.500L12 6v12l-4.500-3.500H4zM16 9.500l5 5M21 9.500l-5 5"/>',
    search: '<circle cx="10.500" cy="10.500" r="6"/><path d="M15 15l5.500 5.500"/>',
    close: '<path d="M5 5l14 14M19 5 5 19"/>',
    chevr: '<path d="M9 5l7 7-7 7"/>', chevl: '<path d="M15 5l-7 7 7 7"/>', chevu: '<path d="M5 15l7-7 7 7"/>', chevd: '<path d="M5 9l7 7 7-7"/>',
    check: '<path d="M4 12.500l5 5L20 6.500"/>',
    warn: '<path d="M12 3 22 20H2zM12 10v5M12 17.500h.01"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.500 2"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.500v3M12 18.500v3M2.500 12h3M18.500 12h3M5.300 5.300l2.100 2.100M16.600 16.600l2.100 2.100M18.700 5.300l-2.100 2.100M7.400 16.600l-2.100 2.100"/>',
    moon: '<path d="M20 14.500A8.500 8.500 0 1 1 9.500 4 7 7 0 0 0 20 14.500z"/>',
    signal: '<path d="M4 20v-3M9 20v-7M14 20V9M19 20V4"/>',
    repeat: '<path d="M4 11V9a3 3 0 0 1 3-3h13M17 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H4M7 21l-3-3 3-3"/>',
    shuffle: '<path d="M3 7h3.500c3 0 4 10 8 10H21M18 14l3 3-3 3M3 17h3.500c1.500 0 2.500-2 3.500-4M14.500 7H21M18 4l3 3-3 3"/>',
    list: '<path d="M8 6h13M8 12h13M8 18h13M3.500 6h.01M3.500 12h.01M3.500 18h.01"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    keypad: '<g fill="currentColor" stroke="none"><circle cx="5" cy="5" r="1.600"/><circle cx="12" cy="5" r="1.600"/><circle cx="19" cy="5" r="1.600"/><circle cx="5" cy="12" r="1.600"/><circle cx="12" cy="12" r="1.600"/><circle cx="19" cy="12" r="1.600"/><circle cx="5" cy="19" r="1.600"/><circle cx="12" cy="19" r="1.600"/><circle cx="19" cy="19" r="1.600"/></g>',
    mic: '<path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5.500 11a6.500 6.500 0 0 0 13 0M12 17.500V21"/>',
    tyre: '<path d="M6 18.500v-8a6 6 0 0 1 12 0v8zM12 7.500v5M12 15.500h.01"/>',
    lights: '<path d="M9 5c5 0 8 3 8 7s-3 7-8 7zM3 8h3M3 12h3M3 16h3"/>',
    belt: '<circle cx="12" cy="5" r="2"/><path d="M8 21l1-9 3 2 3-2 1 9M9 12l6 3"/>',
    brake: '<circle cx="12" cy="12" r="9"/><path d="M10 16V8h3a2.500 2.500 0 0 1 0 5h-3"/>',
    power: '<path d="M12 3v8M6.500 6.500a8 8 0 1 0 11 0"/>',
    cast: '<path d="M3 6h18v11H3zM8 21h8M12 17v4M9.500 10.500l2.500 2.500 2.500-2.500"/>',
    folder: '<path d="M3 6.500h6.500l2 2.500H21V19H3z"/>',
    image: '<rect x="3" y="5" width="18" height="14" rx="2.500"/><circle cx="9" cy="10" r="1.700"/><path d="M4 17l5-4.500 3.500 3L16 12l4.500 4.500"/>',
    fog: '<path d="M10 5c5 0 8 3 8 7s-3 7-8 7z"/><path d="M2.500 8l5 1.200M2.500 12.500h5M2.500 17l5-1.200"/>',
    storage: '<rect x="3" y="14" width="18" height="6" rx="2"/><path d="M5 14l2-8h10l2 8"/><path d="M7.500 17h.01M11 17h6"/>',
    video: '<path d="M3 6h13v12H3zM16 10.500l5-3v9l-5-3"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.500 3 14.500 0 18M12 3c-3 3.500-3 14.500 0 18"/>',
    calc: '<path d="M5 3h14v18H5zM8 7h8M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"/>',
    cloud: '<path d="M7 18a4.500 4.500 0 1 1 1-8.900A6 6 0 0 1 19.500 11 3.500 3.500 0 0 1 18 18z"/>',
    plus: '<path d="M12 4v16M4 12h16"/>', minus: '<path d="M4 12h16"/>',
    bell: '<path d="M6 17V11a6 6 0 0 1 12 0v6l1.500 2h-15zM10 21h4"/>',
    eq: '<path d="M5 4v16M12 4v16M19 4v16"/><path d="M3 9h4M10 15h4M17 8h4"/>',
    flag: '<path d="M5 21V4M5 4h13l-2.500 4.500L18 13H5"/>',
    bolt: '<path d="M13 2.500 5 13.500h6l-1 8 8-11h-6z"/>',
    rear: '<path d="M4 17V11l2-4h12l2 4v6M3.500 17h17M7 20v-3M17 20v-3M7.500 13.500h.01M16.500 13.500h.01M9 4l-2 2M15 4l2 2"/>',
    trash: '<path d="M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13"/>',
    export: '<path d="M12 3v12M7 8l5-5 5 5M4 15v6h16v-6"/>',
    chat: '<path d="M4 5h16v11H9l-5 4z"/>',
    calendar: '<path d="M4 6h16v14H4zM4 10h16M8 3v5M16 3v5"/>',
    steer: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2"/><path d="M3 12h7M14 12h7M12 14v7"/>',
    snow: '<path d="M12 3v18M4.200 7.500l15.600 9M19.800 7.500l-15.600 9M9 4.500l3 2.500 3-2.500M9 19.500l3-2.500 3 2.500"/>',
    recirc: '<path d="M5 15a8 8 0 0 1 13-6M19 9V4M19 9h-5M19 9a8 8 0 0 1-13 6M5 15v5M5 15h5"/>',
    defrost: '<path d="M4 18c2-3 14-3 16 0M7 14c1.500-2 8.500-2 10 0M9 10c1-1.500 5-1.500 6 0M8 5c1.500 2 0 3 1.500 4M12 4c1.500 2 0 3 1.500 5M16 5c1.500 2 0 3 1.500 4"/>',
    key: '<circle cx="8" cy="12" r="4"/><path d="M12 12h9M18 12v3M21 12v2"/>'
  };
  UI.icon = function (n, s, cls) {
    s = s || 24;
    return '<svg class="ic ' + (cls || '') + '" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" aria-hidden="true">' + (I[n] || I.close) + '</svg>';
  };
  UI.icons = I;

  /* ---------------- simulator: same shape as vehicle/DriveCycle.kt, retuned for visual drama ---------------- */
  var RATIO = [190, 100, 68, 52, 41, 34], UP = [27, 52, 76, 100, 125];
  function speedAt(t) {
    t = ((t % 100) + 100) % 100;
    return t < 45 ? 130 * t / 45 : t < 60 ? 130 : t < 85 ? 130 * (1 - (t - 60) / 25) : 0;
  }
  UI.Sim = {
    snapT: 25.6,
    at: function (t) {
      var v = speedAt(t), g = 0; UP.forEach(function (u) { if (v >= u) g++; });
      var rpm = Math.max(850, v * RATIO[g]);
      var d = speedAt(t + 0.5) - speedAt(t - 0.5), accel = d > 0.1, decel = d < -0.1;
      var boost = accel ? Math.min(0.95, Math.max(0, 0.95 * (rpm - 1300) / 2200)) : decel ? -0.65 : (v > 5 ? -0.3 : -0.62);
      return {
        t: t, speed: v, rpm: rpm, gear: v < 3 ? 'N' : g + 1, boost: boost, map: 100 + boost * 100, baro: 100,
        throttle: accel ? 78 : decel ? 0 : v > 5 ? 24 : 0, load: accel ? 88 : decel ? 12 : v > 5 ? 38 : 18,
        coolant: 92, iat: accel ? 44 : 36, volt: 14.2, fuel: 62 - t * 0.01, ambient: 31, odo: 48213,
        accel: accel, decel: decel
      };
    }
  };
  UI.live = function (cb) {
    var snap = UI.Sim.snapT; if (Q.has('t')) snap = parseFloat(Q.get('t'));
    if (Q.has('bench')) {                         /* scripting cost of one frame's update (not rasterisation): tools/design.sh bench drive */
      var n = 600, tt = [], i; for (i = 0; i < 30; i++) cb(UI.Sim.at(snap + i * .016));
      for (i = 0; i < n; i++) { var a = performance.now(); cb(UI.Sim.at(snap + i * .016)); tt.push(performance.now() - a); }
      tt.sort(function (x, y) { return x - y; });
      var pre = document.createElement('pre'); pre.id = 'bench-out';
      pre.textContent = 'frames=' + n + ' mean=' + (tt.reduce(function (x, y) { return x + y; }, 0) / n).toFixed(3) + 'ms p50=' + tt[n >> 1].toFixed(3) + 'ms p99=' + tt[Math.floor(n * .99)].toFixed(3) + 'ms max=' + tt[n - 1].toFixed(3) + 'ms';
      document.body.appendChild(pre); return;
    }
    if (!Q.has('live')) { cb(UI.Sim.at(snap)); return; }
    var t0 = performance.now(), last = t0, cur = null, ford = UI.theme === 'ford';
    var KEYS = ['speed', 'rpm', 'boost', 'map', 'throttle', 'load', 'iat', 'fuel'];
    (function f(now) {
      if (!ford && now - last < 45) { requestAnimationFrame(f); return; }
      var dt = Math.min(.1, (now - last) / 1000); last = now;
      var tgt = UI.Sim.at(snap + (now - t0) / 1000);
      if (!ford) { cb(tgt); requestAnimationFrame(f); return; }
      if (!cur) cur = Object.assign({}, tgt);
      var a = 1 - Math.exp(-dt / .055);                       /* ~55 ms time constant, frame-rate independent */
      KEYS.forEach(function (k) { cur[k] = cur[k] + (tgt[k] - cur[k]) * a; });
      cur.gear = tgt.gear; cur.t = tgt.t; cur.coolant = tgt.coolant; cur.volt = tgt.volt; cur.accel = tgt.accel; cur.decel = tgt.decel;
      cb(cur); requestAnimationFrame(f);
    })(t0);
  };

  /* ---------------- helpers ---------------- */
  UI.f = function (n, d) { return (+n).toFixed(d || 0); };
  UI.pol = function (cx, cy, r, deg) { var a = deg * Math.PI / 180; return [cx + r * Math.sin(a), cy - r * Math.cos(a)]; };
  UI.sector = function (cx, cy, r1, r2, a, b) {
    var p1 = UI.pol(cx, cy, r2, a), p2 = UI.pol(cx, cy, r2, b), p3 = UI.pol(cx, cy, r1, b), p4 = UI.pol(cx, cy, r1, a), big = (b - a) > 180 ? 1 : 0;
    return 'M' + p1 + 'A' + r2 + ' ' + r2 + ' 0 ' + big + ' 1 ' + p2 + 'L' + p3 + 'A' + r1 + ' ' + r1 + ' 0 ' + big + ' 0 ' + p4 + 'Z';
  };
  /* segmented arc: o = {cx,cy,r1,r2,a0,a1,n,gap,v,split,red} (angles: degrees clockwise from 12 o'clock) */
  UI.arcSegs = function (o) {
    var step = (o.a1 - o.a0) / o.n, out = '';
    for (var i = 0; i < o.n; i++) {
      var f = (i + 0.5) / o.n, lit = f <= o.v, hi = f >= o.split, fill;
      if (lit) fill = hi ? 'var(--lime)' : 'var(--cobalt)';
      else fill = (o.red != null && f >= o.red) ? 'rgba(var(--lime-rgb),.20)' : 'rgba(var(--cobalt-rgb),.24)';
      out += '<path d="' + UI.sector(o.cx, o.cy, o.r1, o.r2, o.a0 + i * step + o.gap / 2, o.a0 + (i + 1) * step - o.gap / 2) + '" fill="' + fill + '"/>';
    }
    return out;
  };
  /* tachometer: 270deg, numerals, ticks, tip marker */
  UI.tach = function (el, o) {
    if (UI.theme === 'ford') return UI.tachFord(el, o);
    o = Object.assign({ w: 700, h: 700, cx: 350, cy: 350, r2: 318, r1: 270, max: 7000, split: 4500, red: 6000, rpm: 0, n: 70 }, o);
    var a0 = -135, a1 = 135, fr = o.rpm / o.max, s = '';
    s += UI.arcSegs({ cx: o.cx, cy: o.cy, r1: o.r1, r2: o.r2, a0: a0, a1: a1, n: o.n, gap: 1.1, v: fr, split: o.split / o.max, red: o.red / o.max });
    for (var k = 0; k <= o.max / 500; k++) {
      var f = k * 500 / o.max, a = a0 + f * (a1 - a0), maj = k % 2 === 0, p1 = UI.pol(o.cx, o.cy, o.r1 - 12, a), p2 = UI.pol(o.cx, o.cy, o.r1 - (maj ? 30 : 20), a);
      s += '<line x1="' + p1[0] + '" y1="' + p1[1] + '" x2="' + p2[0] + '" y2="' + p2[1] + '" stroke="' + (k * 500 >= o.red ? 'var(--lime)' : 'var(--cobalt-lt)') + '" stroke-width="' + (maj ? 3 : 1.5) + '"/>';
      if (maj) { var t = UI.pol(o.cx, o.cy, o.r1 - 58, a); s += '<text x="' + t[0] + '" y="' + (t[1] + 11) + '" text-anchor="middle" font-family="var(--f-display)" font-weight="800" font-size="34" fill="' + (k * 500 >= o.red ? 'var(--lime)' : 'var(--text)') + '">' + (k / 2) + '</text>'; }
    }
    var ta = a0 + Math.min(1, fr) * (a1 - a0), tip = [UI.pol(o.cx, o.cy, o.r2 + 4, ta), UI.pol(o.cx, o.cy, o.r2 + 26, ta - 2.4), UI.pol(o.cx, o.cy, o.r2 + 26, ta + 2.4)];
    s += '<polygon points="' + tip.join(' ') + '" fill="var(--lime)"/>';
    s += '<text x="' + o.cx + '" y="' + (o.cy + 262) + '" text-anchor="middle" font-family="var(--f-ui)" font-weight="500" font-size="18" letter-spacing="3" fill="var(--dim)">x1000 RPM</text>';
    el.setAttribute('viewBox', '0 0 ' + o.w + ' ' + o.h); el.innerHTML = s;
  };

  /* Ford tachometer. The arc is two masked conic-gradient layers (track + lit), so the ramp is perfectly smooth and each frame only changes one
     custom property (--p). In Compose this is Brush.sweepGradient + drawArc: one draw call, no per-segment work. */
  UI.tachFord = function (el, o) {
    o = Object.assign({ w: 700, h: 700, cx: 350, cy: 350, r2: 322, r1: 262, max: 7000, red: 6000, rpm: 0 }, o);
    var a0 = -135, a1 = 135;
    if (!el._ford) {
      var host = el.parentNode, sc = (parseFloat(el.getAttribute('width')) || o.w) / o.w, size = o.w * sc;
      var ring = 'radial-gradient(circle at 50% 50%,transparent ' + (o.r1 * sc) + 'px,#000 ' + (o.r1 * sc + 1) + 'px,#000 ' + (o.r2 * sc - 1) + 'px,transparent ' + (o.r2 * sc) + 'px)';
      var mk = function (cls, bg, extra) {
        var d = document.createElement('div'); d.className = cls;
        d.style.cssText = 'position:absolute;left:0;top:0;width:' + size + 'px;height:' + size + 'px;border-radius:50%;background:' + bg + ';-webkit-mask-image:' + ring + ',' + extra + ';mask-image:' + ring + ',' + extra + ';-webkit-mask-composite:source-in;mask-composite:intersect';
        host.insertBefore(d, el); return d;
      };
      var fr = o.red / o.max * 270, lo = UI.rgb('--cobalt-rgb').map(function (x) { return x * .62; }), hi = UI.rgb('--lime-rgb');
      var ticks = '', ta;
      var nStr = Math.max(1, Math.round((270 - fr) / 3.2)), pitch = (270 - fr) / nStr, k;
      for (k = 0; k < nStr; k++) { ta = fr + k * pitch; ticks += ',rgba(var(--warn-rgb),.85) ' + ta.toFixed(2) + 'deg,rgba(var(--warn-rgb),.85) ' + (ta + 1.3).toFixed(2) + 'deg,transparent ' + (ta + 1.3).toFixed(2) + 'deg,transparent ' + (ta + pitch).toFixed(2) + 'deg'; }
      var track = 'conic-gradient(from -135deg at 50% 50%,rgba(var(--dim-rgb),.13) 0deg,rgba(var(--dim-rgb),.13) ' + fr + 'deg,transparent ' + fr + 'deg' + ticks + ')';
      var lit = 'conic-gradient(from -135deg at 50% 50%,rgb(' + lo.map(Math.round) + ') 0deg,rgb(' + hi + ') ' + (fr * .72) + 'deg,rgb(' + hi + ') ' + fr + 'deg,var(--warn) ' + fr + 'deg,var(--warn) 270deg)';
      mk('ttrack', track, 'conic-gradient(from -135deg at 50% 50%,#000 0deg,#000 270deg,transparent 270deg)');
      var litEl = mk('tlit', lit, 'conic-gradient(from -135deg at 50% 50%,#000 0deg,#000 calc(var(--p,0) * 270deg),transparent calc(var(--p,0) * 270deg))');
      var s = '', rm = (o.r1 + o.r2) / 2;
      s += '<defs><radialGradient id="tipglow"><stop offset="0" stop-color="#fff" stop-opacity=".9"/><stop offset=".3" stop-color="var(--lime)" stop-opacity=".45"/><stop offset="1" stop-color="var(--lime)" stop-opacity="0"/></radialGradient></defs>';
      for (var k = 0; k <= o.max / 500; k++) {
        var ff = k * 500 / o.max, a = a0 + ff * (a1 - a0), maj = k % 2 === 0, red = k * 500 >= o.red, p1 = UI.pol(o.cx, o.cy, o.r1 - 14, a), p2 = UI.pol(o.cx, o.cy, o.r1 - (maj ? 34 : 24), a);
        s += '<line x1="' + p1[0] + '" y1="' + p1[1] + '" x2="' + p2[0] + '" y2="' + p2[1] + '" stroke="' + (red ? 'var(--warn)' : 'var(--dim)') + '" stroke-width="' + (maj ? 3 : 1.5) + '" stroke-linecap="round"/>';
        if (maj) { var t = UI.pol(o.cx, o.cy, o.r1 - 62, a); s += '<text x="' + t[0] + '" y="' + (t[1] + 13) + '" text-anchor="middle" font-family="var(--f-display)" font-weight="600" font-size="40" fill="' + (red ? 'var(--warn)' : 'var(--text)') + '">' + (k / 2) + '</text>'; }
      }
      s += '<g class="tip"><circle cx="' + o.cx + '" cy="' + (o.cy - rm) + '" r="46" fill="url(#tipglow)"/><circle cx="' + o.cx + '" cy="' + (o.cy - rm) + '" r="12" fill="#fff"/></g>';
      s += '<text x="' + o.cx + '" y="' + (o.cy + 292) + '" text-anchor="middle" font-family="var(--f-ui)" font-weight="500" font-size="24" fill="var(--dim)">rpm x 1000</text>';
      el.setAttribute('viewBox', '0 0 ' + o.w + ' ' + o.h); el.innerHTML = s;
      el._ford = { lit: litEl, tip: el.querySelector('.tip') };
    }
    var v = Math.max(0, Math.min(1, o.rpm / o.max));
    el._ford.lit.style.setProperty('--p', v.toFixed(4));
    el._ford.tip.setAttribute('transform', 'rotate(' + (a0 + v * (a1 - a0)).toFixed(2) + ' ' + o.cx + ' ' + o.cy + ')');
  };

  UI.spark = function (el, pts, o) {
    o = Object.assign({ w: 160, h: 40, c: 'var(--cobalt-lt)', fill: true }, o);
    var mn = o.min != null ? o.min : Math.min.apply(0, pts), mx = o.max != null ? o.max : Math.max.apply(0, pts), r = (mx - mn) || 1, n = pts.length;
    var xy = pts.map(function (p, i) { return [(i / (n - 1)) * o.w, o.h - 3 - ((p - mn) / r) * (o.h - 6)]; });
    var line = xy.map(function (p) { return p[0].toFixed(1) + ',' + p[1].toFixed(1); }).join(' ');
    el.setAttribute('viewBox', '0 0 ' + o.w + ' ' + o.h); el.setAttribute('preserveAspectRatio', 'none');
    el.innerHTML = (o.fill ? '<polygon points="0,' + o.h + ' ' + line + ' ' + o.w + ',' + o.h + '" fill="' + o.c + '" opacity=".10"/>' : '') +
      '<polyline points="' + line + '" fill="none" stroke="' + o.c + '" stroke-width="2"/>';
  };
  /* deterministic pseudo-series for sparklines / charts */
  UI.series = function (n, base, amp, seed) {
    var a = []; for (var i = 0; i < n; i++) a.push(base + amp * (Math.sin(i * 0.5 + seed) * 0.6 + Math.sin(i * 0.17 + seed * 2) * 0.4)); return a;
  };

  /* ---------------- car, top-down (front at top, RHD: driver on the right). EcoSport proportions: ~4.0 m x 1.77 m, rear-mounted spare ---------------- */
  UI.car = function (s) {
    s = s || {};
    var cls = function (b) { return b ? 'open' : 'shut'; };
    var door = function (open, x, y, left) {
      var hx = left ? x + 5 : x + 5, tr = open ? ' transform="rotate(' + (left ? 62 : -62) + ' ' + hx + ' ' + y + ')"' : '';
      var ghost = open ? '<rect class="ghost" x="' + x + '" y="' + y + '" width="10" height="104" rx="2"/>' : '';
      return ghost + '<rect class="dr ' + cls(open) + '" x="' + x + '" y="' + y + '" width="10" height="104" rx="2"' + tr + '/>';
    };
    var gate = s.trunk ? ' transform="rotate(-70 236 490)"' : '';
    var bon = s.bonnet ? ' transform="translate(0 -16)"' : '';
    return '<svg viewBox="' + (s.vb || '0 0 320 600') + '" class="carsvg"><style>' +
      '.carsvg .body{fill:var(--s1);stroke:var(--cobalt-lt);stroke-width:2.5}.carsvg .glass{fill:rgba(var(--cobalt-lt-rgb),.30);stroke:var(--cobalt-lt);stroke-width:1.5}' +
      '.carsvg .dr.shut{fill:var(--cobalt);stroke:var(--cobalt-lt);stroke-width:1}.carsvg .dr.open{fill:var(--lime);stroke:var(--lime);stroke-width:1}.carsvg .ghost{fill:rgba(var(--lime-rgb),.14);stroke:var(--lime);stroke-width:1.5;stroke-dasharray:4 4}' +
      '.carsvg .wh{fill:var(--s3);stroke:var(--cobalt-lt);stroke-width:2.5}.carsvg .roof{fill:rgba(var(--cobalt-rgb),.16);stroke:var(--cobalt);stroke-width:1.5}' +
      '.carsvg .gate.shut{fill:rgba(var(--cobalt-rgb),.22);stroke:var(--cobalt-lt);stroke-width:1.5}.carsvg .gate.open{fill:rgba(var(--lime-rgb),.35);stroke:var(--lime);stroke-width:2.5}' +
      '.carsvg .bon.shut{fill:rgba(var(--cobalt-rgb),.10);stroke:var(--cobalt);stroke-width:1.5}.carsvg .bon.open{fill:rgba(var(--lime-rgb),.35);stroke:var(--lime);stroke-width:2.5}' +
      '.carsvg .lamp{fill:var(--cobalt-lt)}' +
      '</style>' +
      '<rect class="wh" x="52" y="84" width="22" height="66" rx="7"/><rect class="wh" x="246" y="84" width="22" height="66" rx="7"/>' +
      '<rect class="wh" x="52" y="368" width="22" height="66" rx="7"/><rect class="wh" x="246" y="368" width="22" height="66" rx="7"/>' +
      '<path class="body" d="M70 96Q70 40 124 40H196Q250 40 250 96V468Q250 490 228 490H92Q70 490 70 468Z"/>' +
      '<rect class="lamp" x="82" y="46" width="30" height="6" rx="2"/><rect class="lamp" x="208" y="46" width="30" height="6" rx="2"/>' +
      '<path class="bon ' + cls(s.bonnet) + '" d="M90 64Q90 58 98 58H222Q230 58 230 64V158H90Z"' + bon + '/><path d="M160 62V156" stroke="var(--cobalt)" stroke-width="1.5"' + bon + '/>' +
      '<path class="glass" d="M94 166H226L216 214H104Z"/><rect class="roof" x="104" y="214" width="112" height="190" rx="8"/>' +
      '<path class="glass" d="M104 404H216L224 438H96Z"/>' +
      door(s.fl, 60, 190, true) + door(s.rl, 60, 300, true) + door(s.fr, 250, 190, false) + door(s.rr, 250, 300, false) +
      '<path d="M52 196l-14 7 6 10 12-4zM268 196l14 7-6 10-12-4z" fill="var(--s3)" stroke="var(--cobalt-lt)" stroke-width="1.5"/>' +
      '<g' + gate + '><rect class="gate ' + cls(s.trunk) + '" x="84" y="456" width="152" height="34" rx="6"/>' +
      '<circle cx="160" cy="514" r="30" fill="var(--s3)" stroke="var(--cobalt-lt)" stroke-width="3"/><circle cx="160" cy="514" r="13" fill="none" stroke="var(--cobalt)" stroke-width="2.5"/><path d="M160 484v-4" stroke="var(--cobalt-lt)" stroke-width="3"/></g>' +
      (s.extra || '') + '</svg>';
  };
  /* parking-radar arcs hugging a bumper. levels: 4 values 0..3 (left to right). y: bumper line, dir: -1 front (up), +1 rear (down) */
  UI.radar = function (levels, y, dir) {
    var xs = [92, 133, 187, 228], angs = [-26, -9, 9, 26], out = '';
    levels.forEach(function (lv, i) {
      for (var k = 1; k <= 3; k++) {
        var r = 8 + k * 17, a = (dir < 0 ? 0 : 180) + angs[i] * (dir < 0 ? 1 : -1), p1 = UI.pol(xs[i], y, r, a - 17), p2 = UI.pol(xs[i], y, r, a + 17);
        var col = k > lv ? 'rgba(var(--cobalt-rgb),.22)' : (UI.theme === 'ford' ? (k === 3 ? 'var(--warn)' : 'var(--lime)') : (k === 3 ? 'var(--lime)' : k === 2 ? 'var(--cobalt-lt)' : 'var(--cobalt)'));
        out += '<path d="M' + p1[0].toFixed(1) + ' ' + p1[1].toFixed(1) + 'A' + r + ' ' + r + ' 0 0 1 ' + p2[0].toFixed(1) + ' ' + p2[1].toFixed(1) + '" fill="none" stroke="' + col + '" stroke-width="9"/>';
      }
    });
    return out;
  };


  /* ---------------- Ford oval (trademark; personal use) ---------------- */
  UI.logo = function (h, cls) {
    var L = window.FORD_LOGO; if (!L) return '';
    return '<svg class="flogo ' + (cls || '') + '" viewBox="' + L.vb + '" height="' + h + '" width="' + (h * 2.7782) + '" aria-label="Ford"><path class="fo-oval" d="' + L.oval + '"/><path class="fo-ring" d="' + L.ring + '"/><path class="fo-script" d="' + L.script + '"/></svg>';
  };
  /* three stacked layers so the boot animation can reveal oval, ring and script independently */
  UI.logoLayers = function (h) {
    var L = window.FORD_LOGO, w = h * 2.7782, one = function (c, d) { return '<svg class="fl ' + c + '" viewBox="' + L.vb + '" width="' + w + '" height="' + h + '"><path d="' + d + '"/></svg>'; };
    return '<div class="logo-layers" style="width:' + w + 'px;height:' + h + 'px">' + one('l-oval', L.oval) + one('l-ring', L.ring) + one('l-script', L.script) + '</div>';
  };
  UI.rgb = function (name) { return getComputedStyle(document.documentElement).getPropertyValue(name).split(',').map(function (x) { return parseFloat(x); }); };
  UI.mix = function (a, b, t) { return 'rgb(' + a.map(function (x, i) { return Math.round(x + (b[i] - x) * t); }).join(',') + ')'; };

  /* ---------------- hydrators ---------------- */
  /* segmented bar: value v in 0..1 (or position for bipolar bars: lit between zero and v) */
  UI.setSeg = function (e, v) {
    var o = e._o, h = '';
    for (var i = 0; i < o.n; i++) {
      var f = (i + 0.5) / o.n, c = '';
      if (o.zero != null) { if (v >= o.zero) { if (f >= o.zero && f <= v) c = 'l'; } else if (f <= o.zero && f >= v) c = 'c'; }
      else if (f <= v) c = (UI.theme === 'ford' && o.c !== 'w') ? 'l' : (o.c || (f >= o.sp ? 'l' : 'c'));
      h += '<i class="' + c + '"></i>';
    }
    e.innerHTML = h;
  };
  function hydrate(root) {
    root.querySelectorAll('i[data-i]').forEach(function (e) { e.innerHTML = UI.icon(e.dataset.i, +e.dataset.s || 24); e.removeAttribute('data-i'); });
    root.querySelectorAll('.seg[data-n]').forEach(function (e) {
      e._o = { n: +e.dataset.n, sp: e.dataset.split != null ? parseFloat(e.dataset.split) : 2, zero: e.dataset.zero != null ? parseFloat(e.dataset.zero) : null, c: e.dataset.c || '' };
      UI.setSeg(e, parseFloat(e.dataset.v || 0)); e.removeAttribute('data-n');
    });
    root.querySelectorAll('.slider[data-v]').forEach(function (e) {
      var v = parseFloat(e.dataset.v), n = +(e.dataset.n || 28), h = '<div class="seg sk">';
      for (var i = 0; i < n; i++) h += '<i class="' + ((i + 0.5) / n <= v ? (UI.theme === 'ford' ? 'l' : 'c') : '') + '"></i>';
      e.innerHTML = h + '</div><div class="knob" style="left:' + (v * 100) + '%"></div>'; e.removeAttribute('data-v');
    });
    root.querySelectorAll('svg[data-spark]').forEach(function (e) {
      var pts = e.dataset.spark.split(',').map(parseFloat); UI.spark(e, pts, { w: +(e.dataset.w || 160), h: +(e.dataset.h || 40), c: (e.dataset.c === 'l' || UI.theme === 'ford') ? 'var(--lime)' : 'var(--cobalt-lt)' });
      e.removeAttribute('data-spark');
    });
  }
  UI.hydrate = hydrate;

  /* ---------------- shell ---------------- */
  var DOCK = [['home', 'home', 'Home'], ['drive', 'gauge', 'Drive'], ['car', 'car', 'Car'], ['media', 'music', 'Media'], ['phone', 'phone', 'Phone'], ['projection', 'nav', 'Maps'], ['apps', 'grid', 'Apps'], ['settings', 'sliders', 'Settings']];
  function shell(fr) {
    var active = fr.dataset.active || '', qs = location.search;
    if (fr.dataset.shell !== '0') {
      var sb = '<header class="sb"><div class="sbl"><span class="clock">21:47</span><span class="date">' + (UI.theme === 'ford' ? 'Fri 30 Oct' : 'FRI 30 OCT') + '</span></div>' +
        '<div class="sbc"><span class="link on">OBD</span><span class="link on">Canbox</span><span class="link on">BT</span><span class="link on">Android Auto</span></div>' +
        '<div class="sbr"><span class="sbs">' + UI.icon('sun', 22) + '31°</span><span class="sbs">' + UI.icon('batt', 22) + '14.2 V</span><span class="sbs">' + UI.icon('vol', 22) + '18</span></div></header>';
      fr.insertAdjacentHTML('afterbegin', sb);
    }
    if (fr.dataset.dock !== '0' && fr.dataset.shell !== '0') {
      var dk = '<nav class="dock">' + DOCK.map(function (d) {
        return '<a href="' + d[0] + '.html' + qs + '" class="' + (d[0] === active ? 'on' : '') + '">' + UI.icon(d[1], 32) + '<span>' + d[2] + '</span></a>';
      }).join('') + '</nav>';
      fr.insertAdjacentHTML('beforeend', dk);
    }
    var m = fr.querySelector('.main'); if (m && (fr.dataset.dock === '0')) m.classList.add('full');
  }
  UI.qa = function () {
    var out = []; document.documentElement.classList.add('noanim');
    document.querySelectorAll('.frame').forEach(function (fr, fi) {
    var fb = fr.getBoundingClientRect();
    var nm = function (e) { return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && e.className.baseVal === undefined ? '.' + String(e.className).trim().split(/\s+/).join('.') : '') + ' "' + (e.textContent || '').trim().slice(0, 28).replace(/\s+/g, ' ') + '"'; };
    fr.querySelectorAll('*').forEach(function (e) {
      if (e.closest('svg') || e.closest('.scaled')) return;
      var r = e.getBoundingClientRect(); if (!r.width || !r.height) return;
      if (r.right > fb.right + 1 || r.bottom > fb.bottom + 1 || r.left < fb.left - 1 || r.top < fb.top - 1) out.push('[f' + fi + '] OUTSIDE ' + nm(e) + ' ' + Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.right) + ',' + Math.round(r.bottom));
      var cs = getComputedStyle(e); if (cs.position === 'absolute' || e.classList.contains('frame')) return;
      Array.prototype.forEach.call(e.children, function (c) {
        if (c.closest('svg') && c.tagName.toLowerCase() !== 'svg') return;
        var cr = c.getBoundingClientRect(), pcs = getComputedStyle(c); if (!cr.width || pcs.position === 'absolute' || pcs.display === 'inline') return;
        if (cs.display.indexOf('inline') === 0 || e.classList.contains('seg')) return;
        if (cr.right > r.right + 4 || cr.bottom > r.bottom + 4) out.push('[f' + fi + '] OVERFLOW child ' + nm(c) + ' beyond parent ' + nm(e) + ' dx=' + Math.round(cr.right - r.right) + ' dy=' + Math.round(cr.bottom - r.bottom));
      });
      if (e.scrollWidth > e.clientWidth + 2 && cs.overflowX !== 'visible') out.push('SCROLLX ' + nm(e));
      if (e.classList.contains('seg') && !e.classList.contains('v') && e.scrollWidth > e.clientWidth + 7) out.push('[f' + fi + '] SEG-OVERFLOW ' + nm(e) + ' +' + (e.scrollWidth - e.clientWidth) + 'px');
    });
    });
    var pre = document.createElement('pre'); pre.id = 'qa-out'; pre.textContent = out.length ? out.join('\n') : 'QA-CLEAN'; document.body.appendChild(pre);
  };
  function fit() { if (UI.embed) return; var k = Math.min(innerWidth / 1280, innerHeight / 800); document.documentElement.style.setProperty('--k', k); document.querySelectorAll('.frame').forEach(function (f) { f.style.setProperty('--k', k); }); }
  document.addEventListener('DOMContentLoaded', function () {
    var bar = document.querySelector('.nav .wrap, .top');
    if (bar && !bar.querySelector('.themesw')) {
      var sw = document.createElement('span'); sw.className = 'themesw';
      sw.innerHTML = '<button data-th="ford">Ford</button><button data-th="cockpit">Cockpit</button>';
      sw.querySelectorAll('button').forEach(function (b) { if (b.dataset.th === UI.theme) b.className = 'on'; b.onclick = function () { UI.setTheme(b.dataset.th); }; });
      bar.appendChild(sw);
    }
    var st = document.querySelector('.stage'); if (!st) return;
    document.querySelectorAll('.frame').forEach(shell);
    document.querySelectorAll('.main .card').forEach(function (c, i) { c.style.setProperty('--i', i); });
    document.querySelectorAll('.chip, .btn, [onclick]').forEach(function (e) { if (e.closest('svg')) return; var p = getComputedStyle(e).position; if (e.offsetHeight && e.offsetHeight < 56 && (p === 'static' || p === 'relative')) e.classList.add('hit'); });
    hydrate(document); fit(); addEventListener('resize', fit);
    if (UI.onready) UI.onready();
    if (Q.has('qa')) setTimeout(UI.qa, 50);
    if (Q.has('probe')) setTimeout(function () {                       /* measurements for design critiques: small touch targets and small text */
      var o = [], seen = {};
      document.querySelectorAll('a, button, [onclick], .btn, .chip, .tog, .sl').forEach(function (e) {
        var r = e.getBoundingClientRect(); if (!r.width || e.closest('svg, .scaled')) return;
        if (e.classList.contains('hit')) return;
        if (Math.min(r.width, r.height) < 56) o.push('TARGET ' + Math.round(r.width) + 'x' + Math.round(r.height) + ' ' + (e.className && e.className.baseVal === undefined ? e.className : '') + ' "' + (e.textContent || '').trim().slice(0, 24) + '"');
      });
      var cards = [].slice.call(document.querySelectorAll('.card, .qt, .app, .rc')).filter(function (c) { return !c.closest('svg, .scaled') && c.offsetWidth; }), gs = {};
      cards.forEach(function (a, i) { cards.slice(i + 1).forEach(function (b) {
        if (a.contains(b) || b.contains(a)) return;
        var A = a.getBoundingClientRect(), B = b.getBoundingClientRect(), gx = Math.max(B.left - A.right, A.left - B.right), gy = Math.max(B.top - A.bottom, A.top - B.bottom);
        var ox = Math.min(A.right, B.right) - Math.max(A.left, B.left), oy = Math.min(A.bottom, B.bottom) - Math.max(A.top, B.top), g = null;
        if (gx > 0 && gx < 40 && oy > 8) g = gx; else if (gy > 0 && gy < 40 && ox > 8) g = gy;
        if (g !== null && Math.abs(g - 12) > 1.5 && Math.abs(g - 24) > 1.5) { var k = 'GAP ' + Math.round(g) + 'px'; gs[k] = (gs[k] || 0) + 1; }
      }); });
      Object.keys(gs).forEach(function (k) { o.push(k + ' x' + gs[k]); });
      var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      while (w.nextNode()) { var n = w.currentNode, t = n.textContent.trim(); if (!t || n.parentElement.closest('svg, script, style, pre, figcaption')) continue;
        var f = parseFloat(getComputedStyle(n.parentElement).fontSize); if (f < 18) { var k = Math.round(f) + 'px ' + t.slice(0, 30); if (!seen[k]) { seen[k] = 1; o.push('TEXT ' + k); } } }
      var pre = document.createElement('pre'); pre.id = 'probe-out'; pre.textContent = o.length ? o.join('\n') : 'PROBE-CLEAN'; document.body.appendChild(pre);
    }, 120);
    document.documentElement.setAttribute('data-ready', '1');
  });
})();
