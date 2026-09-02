/*
 * The background block field.
 *
 * A grid of stone blocks is drawn on a fixed canvas behind the page. Clicking
 * mines the block under the pointer; holding the button down and dragging
 * sweeps through them. Broken blocks leave a hole and grow back after a while,
 * so the field never ends up permanently empty.
 *
 * Two ideas keep this cheap:
 *
 *  - Blocks are only stored once they stop being intact. `state` is a Map from
 *    "col,row" to a record, and an absent key means "an untouched block". The
 *    field is therefore unbounded and scrolling it costs nothing.
 *  - Every block face is pre-rendered into a small offscreen canvas at start-up
 *    and per frame only blitted, so a frame is a few hundred drawImage calls.
 *
 * The whole thing is decorative: the canvas is aria-hidden, and nothing on the
 * page depends on it.
 */
(function () {
  'use strict';

  var canvas = document.getElementById('blockfield');
  if (!canvas || !canvas.getContext) return;

  var ctx = canvas.getContext('2d', { alpha: false });
  if (!ctx) return;

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  /* ---- tuning ---------------------------------------------------------- */

  var CELL = 62; // block edge in CSS pixels, shrunk on narrow screens
  var GAP = 2; // mortar between two blocks
  var VARIANTS = 7; // how many pre-rendered stone faces exist
  var PARALLAX = 0.28; // how much of the page scroll the field follows
  var BREAK_MS = 170; // the shrinking flash when a block gives way
  var REGROW_MIN = 7000; // shortest time a hole stays open
  var REGROW_SPAN = 7000; // …plus up to this much more
  var REGROW_MS = 420; // the grow-back animation
  var SWEEP_MS = 45; // minimum gap between two blocks while dragging
  var MAX_PARTICLES = 260;

  /* ---- state ----------------------------------------------------------- */

  /** "col,row" -> { broken: ms, regrow: ms|0 }. Absent = an intact block. */
  var state = new Map();
  var particles = [];
  var cellSize = CELL;
  var faces = []; // the pre-rendered stone faces
  var hole = null; // the pre-rendered empty socket
  var width = 0;
  var height = 0;
  var dpr = 1;
  var offset = 0; // vertical parallax offset, in CSS pixels
  var running = false;
  var mining = false;
  var sweeping = false; // a drag that mines, rather than one that selects text
  var lastSweep = 0;
  var mined = 0;

  var counter = document.getElementById('mined-count');
  try {
    mined = parseInt(window.localStorage.getItem('autobuildgui:mined') || '0', 10) || 0;
  } catch (err) {
    mined = 0; // private mode, a blocked storage — the counter just starts at 0
  }
  showCount();

  /* ---- deterministic per-block noise ------------------------------------ */

  /** A stable 0…1 hash for a block, so a block keeps its face while scrolling. */
  function hash(col, row, salt) {
    var h = (col * 374761393 + row * 668265263 + salt * 2246822519) | 0;
    h = (h ^ (h >>> 13)) * 1274126177;
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  }

  /* ---- pre-rendering ---------------------------------------------------- */

  /**
   * Draws one stone face into its own canvas: a flat grey fill, a bevel that
   * catches light from the top left, and a scatter of darker pixels so no two
   * blocks read as identical.
   */
  function buildFace(variant, size, scale) {
    var c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(size * scale));
    c.height = Math.max(1, Math.round(size * scale));
    var g = c.getContext('2d');
    g.scale(scale, scale);

    var tone = 9 + Math.round(hash(variant, 91, 3) * 10); // 9…19
    g.fillStyle = 'rgb(' + tone + ',' + tone + ',' + tone + ')';
    g.fillRect(0, 0, size, size);

    // The speckle: a fixed number of square pixels per face, lighter or darker.
    var px = Math.max(2, Math.round(size / 14));
    for (var i = 0; i < 16; i++) {
      var x = Math.floor(hash(variant, i, 7) * (size / px)) * px;
      var y = Math.floor(hash(variant, i, 11) * (size / px)) * px;
      var lift = hash(variant, i, 13);
      g.fillStyle =
        lift > 0.55
          ? 'rgba(255,255,255,' + (0.015 + lift * 0.035).toFixed(3) + ')'
          : 'rgba(0,0,0,' + (0.14 + lift * 0.2).toFixed(3) + ')';
      g.fillRect(x, y, px, px);
    }

    // Bevel — light along the top and left, shadow along the bottom and right.
    g.fillStyle = 'rgba(255,255,255,0.06)';
    g.fillRect(0, 0, size, 1);
    g.fillRect(0, 0, 1, size);
    g.fillStyle = 'rgba(0,0,0,0.55)';
    g.fillRect(0, size - 1, size, 1);
    g.fillRect(size - 1, 0, 1, size);

    return c;
  }

  /** The socket left behind: near-black with a faint rim, so holes read as holes. */
  function buildHole(size, scale) {
    var c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(size * scale));
    c.height = Math.max(1, Math.round(size * scale));
    var g = c.getContext('2d');
    g.scale(scale, scale);
    g.fillStyle = '#000';
    g.fillRect(0, 0, size, size);
    g.strokeStyle = 'rgba(255,255,255,0.05)';
    g.lineWidth = 1;
    g.strokeRect(0.5, 0.5, size - 1, size - 1);
    return c;
  }

  function buildTiles() {
    var inner = cellSize - GAP;
    faces = [];
    for (var v = 0; v < VARIANTS; v++) faces.push(buildFace(v, inner, dpr));
    hole = buildHole(inner, dpr);
  }

  /* ---- layout ----------------------------------------------------------- */

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    cellSize = width < 620 ? 44 : width < 980 ? 52 : CELL;

    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    buildTiles();
    draw();
  }

  /** Screen y -> field y. The field drifts more slowly than the page. */
  function fieldY(screenY) {
    return screenY + offset;
  }

  function cellAt(screenX, screenY) {
    return {
      col: Math.floor(screenX / cellSize),
      row: Math.floor(fieldY(screenY) / cellSize),
    };
  }

  /* ---- mining ----------------------------------------------------------- */

  function key(col, row) {
    return col + ',' + row;
  }

  function mine(screenX, screenY) {
    var at = cellAt(screenX, screenY);
    var k = key(at.col, at.row);
    if (state.has(k)) return; // already broken or growing back

    var now = performance.now();
    state.set(k, {
      broken: now,
      regrow: 0,
      wait: REGROW_MIN + Math.random() * REGROW_SPAN,
    });
    mined++;
    showCount();
    spawnParticles(at.col, at.row);
    start();
  }

  function showCount() {
    if (counter) counter.textContent = String(mined);
    try {
      window.localStorage.setItem('autobuildgui:mined', String(mined));
    } catch (err) {
      /* storage is optional */
    }
  }

  function spawnParticles(col, row) {
    if (reduceMotion.matches || particles.length > MAX_PARTICLES) return;
    var cx = col * cellSize + cellSize / 2;
    var cy = row * cellSize + cellSize / 2;
    var size = Math.max(2, Math.round(cellSize / 12));
    for (var i = 0; i < 9; i++) {
      particles.push({
        x: cx + (Math.random() - 0.5) * cellSize * 0.7,
        y: cy + (Math.random() - 0.5) * cellSize * 0.7,
        vx: (Math.random() - 0.5) * 0.22,
        vy: -Math.random() * 0.22 - 0.05,
        life: 1,
        decay: 0.0016 + Math.random() * 0.0016,
        size: size,
        tone: 120 + Math.round(Math.random() * 100),
      });
    }
  }

  /* ---- drawing ---------------------------------------------------------- */

  function draw() {
    var now = performance.now();

    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, width, height);

    var lastCol = Math.ceil(width / cellSize);
    var firstRow = Math.floor(fieldY(0) / cellSize);
    var lastRow = Math.ceil(fieldY(height) / cellSize);
    var inner = cellSize - GAP;

    for (var row = firstRow; row <= lastRow; row++) {
      var y = row * cellSize - offset;
      for (var col = 0; col <= lastCol; col++) {
        var x = col * cellSize;
        var cell = state.get(key(col, row));

        if (!cell) {
          var face = faces[Math.floor(hash(col, row, 1) * VARIANTS) % VARIANTS];
          ctx.globalAlpha = 1;
          ctx.drawImage(face, x, y, inner, inner);
          continue;
        }

        ctx.globalAlpha = 1;
        ctx.drawImage(hole, x, y, inner, inner);

        // The block flashing white as it gives way.
        var age = now - cell.broken;
        if (age < BREAK_MS && !reduceMotion.matches) {
          var t = age / BREAK_MS;
          var shrink = inner * (1 - t) * 0.9;
          ctx.globalAlpha = 1 - t;
          ctx.fillStyle = '#fff';
          ctx.fillRect(
            x + (inner - shrink) / 2,
            y + (inner - shrink) / 2,
            shrink,
            shrink,
          );
        }

        // …and growing back once its time is up.
        if (cell.regrow) {
          var grow = Math.min(1, (now - cell.regrow) / REGROW_MS);
          var side = inner * grow;
          ctx.globalAlpha = grow;
          ctx.drawImage(
            faces[Math.floor(hash(col, row, 1) * VARIANTS) % VARIANTS],
            x + (inner - side) / 2,
            y + (inner - side) / 2,
            side,
            side,
          );
        }
      }
    }

    ctx.globalAlpha = 1;
    for (var i = 0; i < particles.length; i++) {
      var p = particles[i];
      ctx.globalAlpha = Math.max(0, p.life);
      ctx.fillStyle = 'rgb(' + p.tone + ',' + p.tone + ',' + p.tone + ')';
      ctx.fillRect(Math.round(p.x), Math.round(p.y - offset), p.size, p.size);
    }
    ctx.globalAlpha = 1;
  }

  /* ---- the loop --------------------------------------------------------- */

  var last = 0;

  function step(now) {
    var dt = last ? Math.min(now - last, 64) : 16;
    last = now;

    var busy = false;

    // Particles fall, fade, and are dropped once they are gone.
    for (var i = particles.length - 1; i >= 0; i--) {
      var p = particles[i];
      p.vy += 0.0011 * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.life -= p.decay * dt;
      if (p.life <= 0) particles.splice(i, 1);
    }
    if (particles.length) busy = true;

    state.forEach(function (cell, k) {
      if (!cell.regrow) {
        if (now - cell.broken > cell.wait) {
          cell.regrow = now;
        }
        busy = true;
        return;
      }
      if (now - cell.regrow >= REGROW_MS) state.delete(k);
      else busy = true;
    });

    draw();

    if (busy || mining) {
      requestAnimationFrame(step);
    } else {
      running = false;
      last = 0;
    }
  }

  function start() {
    if (running) return;
    running = true;
    last = 0;
    requestAnimationFrame(step);
  }

  /* ---- input ------------------------------------------------------------ */

  /** Anything the visitor can operate. Clicks on these are never mining. */
  var INTERACTIVE =
    'a,button,input,select,textarea,label,summary,details,[role="button"],[contenteditable]';

  /**
   * Anything the visitor plausibly wants to select. A click on text still mines
   * one block — a click selects nothing — but a *drag* starting on text is a
   * selection and is left to the browser. Dragging from open background is a
   * mining sweep instead, and selection is suppressed for its duration.
   */
  var TEXTISH = 'p,h1,h2,h3,h4,h5,h6,li,dl,td,th,code,kbd,blockquote,table,.screen';

  function closest(target, selector) {
    return !!(target && target.closest && target.closest(selector));
  }

  window.addEventListener(
    'pointerdown',
    function (event) {
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      if (closest(event.target, INTERACTIVE)) return;
      mining = true;
      sweeping = !closest(event.target, TEXTISH);
      lastSweep = performance.now();
      mine(event.clientX, event.clientY);
    },
    { passive: true },
  );

  window.addEventListener(
    'pointermove',
    function (event) {
      if (!mining || !sweeping) return;
      var now = performance.now();
      if (now - lastSweep < SWEEP_MS) return;
      lastSweep = now;
      mine(event.clientX, event.clientY);
    },
    { passive: true },
  );

  // A sweep must not drag a text selection along behind it.
  document.addEventListener('selectstart', function (event) {
    if (mining && sweeping) event.preventDefault();
  });

  function stopMining() {
    mining = false;
    sweeping = false;
  }
  window.addEventListener('pointerup', stopMining, { passive: true });
  window.addEventListener('pointercancel', stopMining, { passive: true });
  window.addEventListener('blur', stopMining);

  window.addEventListener('scroll', function () {
    offset = window.scrollY * PARALLAX;
    if (!running) draw();
  }, { passive: true });

  var resizeTimer = 0;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(resize, 120);
  });

  // A tab in the background gets no frames anyway; make sure the field is
  // repainted correctly the moment it comes back.
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) draw();
  });

  offset = window.scrollY * PARALLAX;
  resize();
  document.documentElement.classList.add('has-blockfield');
})();
