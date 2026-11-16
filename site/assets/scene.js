/*
 * Autobuild GUI — a placement being built.
 *
 * The faint orange blocks are the schematic, as Litematica shows it. The solid
 * ones are what Baritone has placed so far, bottom layer first. When a build
 * is done the next placement starts. Click or tap to pick the next one
 * yourself — that is all the mod asks of you in the game, too.
 */
export default function scene(stage) {
  const { THREE, frame } = stage;
  stage.lights({ ambient: 0.7, key: 2.1, rim: 26 });

  const root = new THREE.Group();
  frame.add(root);
  root.add(stage.halo(stage.accent, 8, 0.2));

  const UNIT = 0.36;
  const MAX = 420;

  /* ------------------------------------------------------- placements */

  const STONE = 0;
  const DARK = 1;
  const GLASS = 2;
  const LAMP = 3;
  const palette = [new THREE.Color('#bcc1c7'), new THREE.Color('#70757c'), new THREE.Color('#a9d4e6'), stage.accent.clone()];

  const house = () => {
    const v = [];
    for (let x = -3; x <= 3; x += 1) for (let z = -3; z <= 3; z += 1) v.push([x, 0, z, DARK]);
    for (let y = 1; y <= 3; y += 1) {
      for (let x = -3; x <= 3; x += 1) {
        for (let z = -3; z <= 3; z += 1) {
          const wall = Math.abs(x) === 3 || Math.abs(z) === 3;
          if (!wall) continue;
          if (z === 3 && x === 0 && y < 3) continue; // the door
          const window = y === 2 && (Math.abs(x) === 1 || Math.abs(z) === 1) && !(Math.abs(x) === 3 && Math.abs(z) === 3);
          v.push([x, y, z, window ? GLASS : STONE]);
        }
      }
    }
    for (let step = 0; step <= 3; step += 1) {
      const r = 4 - step;
      for (let x = -r; x <= r; x += 1) {
        for (let z = -r; z <= r; z += 1) {
          if (Math.abs(x) === r || Math.abs(z) === r || step === 3) v.push([x, 4 + step, z, DARK]);
        }
      }
    }
    v.push([0, 8, 0, LAMP]);
    return { name: 'Cathedral_v3', voxels: v };
  };

  const tower = () => {
    const v = [];
    for (let y = 0; y <= 9; y += 1) {
      for (let x = -2; x <= 2; x += 1) {
        for (let z = -2; z <= 2; z += 1) {
          const ring = Math.max(Math.abs(x), Math.abs(z)) === 2 && !(Math.abs(x) === 2 && Math.abs(z) === 2);
          if (y === 0 && Math.max(Math.abs(x), Math.abs(z)) <= 2) v.push([x, y, z, DARK]);
          else if (ring) v.push([x, y, z, y % 3 === 2 && (x === 0 || z === 0) ? GLASS : STONE]);
        }
      }
    }
    for (let x = -3; x <= 3; x += 1) {
      for (let z = -3; z <= 3; z += 1) {
        const edge = Math.max(Math.abs(x), Math.abs(z)) === 3;
        if (edge) v.push([x, 10, z, DARK]);
        if (edge && (x + z) % 2 === 0) v.push([x, 11, z, STONE]);
      }
    }
    v.push([0, 10, 0, LAMP]);
    return { name: 'Sky_Base_final', voxels: v };
  };

  const bridge = () => {
    const v = [];
    for (let x = -6; x <= 6; x += 1) {
      const arch = Math.round(3 - (x * x) / 12);
      for (let z = -1; z <= 1; z += 1) {
        v.push([x, arch + 1, z, DARK]);
        if (Math.abs(z) === 1) v.push([x, arch + 2, z, x % 3 === 0 ? LAMP : STONE]);
      }
    }
    for (const px of [-6, 6]) {
      for (let y = 0; y <= 1; y += 1) for (let z = -1; z <= 1; z += 1) v.push([px, y, z, STONE]);
    }
    for (const px of [-3, 3]) for (let y = 0; y <= 2; y += 1) v.push([px, y, 0, STONE]);
    return { name: 'Bridge_North', voxels: v };
  };

  // The twelve edges of a block, as pairs of corners.
  const EDGES = [];
  for (const [a, b] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
    EDGES.push(-1, a, b, 1, a, b, a, -1, b, a, 1, b, a, b, -1, a, b, 1);
  }

  const placements = [house(), tower(), bridge()].map((p) => {
    // Baritone works its way up: lowest layer first, then across.
    const voxels = p.voxels
      .slice(0, MAX)
      .sort((a, b) => a[1] - b[1] || a[0] + a[2] * 0.37 - (b[0] + b[2] * 0.37));
    const ys = voxels.map((q) => q[1]);
    const mid = (Math.min(...ys) + Math.max(...ys)) / 2;

    // The schematic's outline: twelve edges per block, in build order, so the
    // part that is already built can be left out with a draw range.
    const lines = new Float32Array(voxels.length * EDGES.length);
    voxels.forEach((q, i) => {
      for (let k = 0; k < EDGES.length; k += 3) {
        lines[i * EDGES.length + k] = (q[0] + EDGES[k] * 0.48) * UNIT;
        lines[i * EDGES.length + k + 1] = (q[1] - mid + EDGES[k + 1] * 0.48) * UNIT;
        lines[i * EDGES.length + k + 2] = (q[2] + EDGES[k + 2] * 0.48) * UNIT;
      }
    });
    const outline = new THREE.BufferGeometry();
    outline.setAttribute('position', new THREE.BufferAttribute(lines, 3));
    return { name: p.name, voxels, mid, outline };
  });

  /* ------------------------------------------------------------ meshes */

  const cube = new THREE.BoxGeometry(UNIT * 0.96, UNIT * 0.96, UNIT * 0.96);
  const solid = new THREE.InstancedMesh(cube, stage.mat.solid(0xffffff, { roughness: 0.78 }), MAX);
  const ghost = new THREE.InstancedMesh(
    cube,
    new THREE.MeshBasicMaterial({ color: stage.accent, transparent: true, opacity: 0.05, depthWrite: false, blending: THREE.AdditiveBlending }),
    MAX,
  );
  solid.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
  ghost.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
  const outline = new THREE.LineSegments(placements[0].outline, stage.mat.line(stage.accent, 0.3));
  const build = new THREE.Group();
  build.add(ghost, outline, solid);
  root.add(build);

  // What Baritone is placing right now.
  const cursor = stage.edges(new THREE.BoxGeometry(UNIT * 1.1, UNIT * 1.1, UNIT * 1.1), stage.white, 0.95);
  build.add(cursor);
  const spark = stage.halo(stage.accentHi, 0.9, 0.7);
  build.add(spark);

  // The ground it stands on.
  const ground = new THREE.GridHelper(UNIT * 16, 16, 0x555b62, 0x2a2e33);
  ground.material.transparent = true;
  ground.material.opacity = 0.55;
  build.add(ground);

  let label = '';
  const plate = stage.plate(3.4, 0.36, (g, w, h) => {
    g.fillStyle = '#f2f5f2';
    g.font = stage.font.mono(Math.round(h * 0.44));
    g.textBaseline = 'middle';
    g.textAlign = 'center';
    g.fillText(label, w / 2, h / 2);
  });
  plate.position.set(0, -2.3, 1.4);
  // The small stage in a page head has no room for it.
  plate.visible = stage.mode === 'hero';
  frame.add(plate);

  stage.dust({ count: 160, radius: 6.5, color: stage.accentHi, opacity: 0.45 });

  /* --------------------------------------------------------- behaviour */

  const dummy = new THREE.Object3D();
  const hidden = new THREE.Matrix4().makeScale(0, 0, 0);
  const tint = new THREE.Color();
  let current = -1;
  let placed = 0;
  let progress = 0;
  let rest = 0;

  const at = (voxel, mid) => dummy.position.set(voxel[0] * UNIT, (voxel[1] - mid) * UNIT, voxel[2] * UNIT);

  const start = (index) => {
    current = index % placements.length;
    const p = placements[current];
    placed = 0;
    progress = 0;
    rest = 0;
    for (let i = 0; i < MAX; i += 1) {
      solid.setMatrixAt(i, hidden);
      if (i < p.voxels.length) {
        at(p.voxels[i], p.mid);
        dummy.scale.setScalar(1);
        dummy.updateMatrix();
        ghost.setMatrixAt(i, dummy.matrix);
        // Blocks of one kind still differ a little, like real stone.
        const shade = 0.86 + ((i * 7919) % 100) / 700;
        solid.setColorAt(i, tint.copy(palette[p.voxels[i][3]]).multiplyScalar(shade));
      } else {
        ghost.setMatrixAt(i, hidden);
      }
    }
    outline.geometry = p.outline;
    p.outline.setDrawRange(0, Infinity);
    ground.position.y = (-p.mid - 0.5) * UNIT;
    solid.instanceMatrix.needsUpdate = true;
    ghost.instanceMatrix.needsUpdate = true;
    if (solid.instanceColor) solid.instanceColor.needsUpdate = true;
  };
  start(0);

  stage.onClick(() => start(current + 1));

  stage.onFrame((time, delta) => {
    const p = placements[current];
    const total = p.voxels.length;

    root.rotation.y = 0.65 + time * 0.16 + stage.yaw;
    root.rotation.x = 0.3 + stage.pitch;
    root.position.y = 0.25 + Math.sin(time * 0.6) * 0.04;

    if (placed < total) {
      progress += delta * 24;
      const upTo = Math.max(placed, Math.min(total, Math.floor(progress)));
      for (let i = placed; i < upTo; i += 1) ghost.setMatrixAt(i, hidden);
      // The newest few blocks are still growing into place.
      for (let i = Math.max(0, upTo - 6); i < upTo; i += 1) {
        at(p.voxels[i], p.mid);
        dummy.scale.setScalar(Math.min(1, 0.35 + (progress - i) / 5));
        dummy.updateMatrix();
        solid.setMatrixAt(i, dummy.matrix);
      }
      placed = upTo;
      p.outline.setDrawRange(placed * 24, Infinity);
      solid.instanceMatrix.needsUpdate = true;
      ghost.instanceMatrix.needsUpdate = true;
      const next = p.voxels[Math.min(total - 1, placed)];
      cursor.visible = spark.visible = true;
      cursor.position.set(next[0] * UNIT, (next[1] - p.mid) * UNIT, next[2] * UNIT);
      spark.position.copy(cursor.position);
    } else {
      cursor.visible = spark.visible = false;
      rest += delta;
      if (rest > 3.2) start(current + 1);
    }

    const text = placed < total ? `${p.name} · ${placed} / ${total} blocks` : `${p.name} · done`;
    if (text !== label) {
      label = text;
      plate.userData.redraw();
    }
  });
}
