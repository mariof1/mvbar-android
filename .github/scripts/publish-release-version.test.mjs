import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('./publish-release-version.sh', import.meta.url));
const bash = process.env.MVBAR_TEST_BASH || (process.platform === 'win32'
  ? 'C:/Program Files/Git/bin/bash.exe' : 'bash');
const initialVersion = 'VERSION_NAME=1.0.0\nVERSION_CODE=1\n';
const releaseVersion = 'VERSION_NAME=1.0.1\nVERSION_CODE=2\n';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'mvbar-release-test-'));
  t.after(() => {
    // Delete only this test's verified temporary directory.
    assert.equal(dirname(resolve(root)), resolve(tmpdir()));
    assert.ok(basename(root).startsWith('mvbar-release-test-'));
    rmSync(root, { recursive: true, force: true });
  });
  const remote = join(root, 'remote.git');
  const runner = join(root, 'runner');
  const other = join(root, 'other');
  const env = { ...process.env, GIT_CONFIG_NOSYSTEM: '1', GIT_CONFIG_GLOBAL: join(root, 'no-config') };
  const git = (cwd, ...args) => execFileSync('git', args, { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git(root, 'init', '--bare', '--initial-branch=main', remote);
  git(root, 'clone', remote, runner);
  for (const repo of [runner]) {
    git(repo, 'config', 'user.name', 'Release Test');
    git(repo, 'config', 'user.email', 'test@example.invalid');
    git(repo, 'config', 'core.autocrlf', 'false');
  }
  writeFileSync(join(runner, 'version.properties'), initialVersion);
  writeFileSync(join(runner, 'source.txt'), 'built source\n');
  git(runner, 'add', '.');
  git(runner, 'commit', '-m', 'Initial source');
  git(runner, 'push', 'origin', 'main');
  git(root, 'clone', remote, other);
  git(other, 'config', 'user.name', 'Concurrent Test');
  git(other, 'config', 'user.email', 'test@example.invalid');
  git(other, 'config', 'core.autocrlf', 'false');
  writeFileSync(join(runner, 'version.properties'), releaseVersion);
  const run = () => spawnSync(bash, [script], {
    cwd: runner, env: { ...env, RELEASE_VERSION: '1.0.1', GITHUB_REF_NAME: 'main' }, encoding: 'utf8',
  });
  const advance = (file = 'source.txt', content = 'new concurrent source\n', ref = 'main') => {
    writeFileSync(join(other, file), content);
    git(other, 'add', file);
    git(other, 'commit', '-m', 'Concurrent change');
    git(other, 'push', 'origin', `HEAD:refs/heads/${ref}`);
    return git(other, 'rev-parse', 'HEAD');
  };
  const show = (ref, file) => git(remote, 'show', `${ref}:${file}`);
  return { root, remote, runner, other, git, run, advance, show };
}

function passed(result) {
  assert.equal(result.status, 0, `${result.error || ''}\n${result.stdout}\n${result.stderr}`);
}

test('normal release publishes version and a tag matching the built source', t => {
  const f = fixture(t);
  passed(f.run());
  assert.equal(f.show('main', 'version.properties'), releaseVersion.trim());
  assert.equal(f.show('v1.0.1', 'source.txt'), 'built source');
  assert.equal(f.git(f.remote, 'rev-parse', 'main'), f.git(f.remote, 'rev-parse', 'v1.0.1^{}'));
  assert.equal(f.git(f.remote, 'log', '-1', '--format=%an <%ae>', 'main'),
    'mariof1 <32400371+mariof1@users.noreply.github.com>');
});

test('preserves work pushed during the build without including unbuilt code in the tag', t => {
  const f = fixture(t);
  const concurrent = f.advance();
  passed(f.run());
  assert.equal(f.show('main', 'source.txt'), 'new concurrent source');
  assert.equal(f.show('main', 'version.properties'), releaseVersion.trim());
  assert.equal(f.show('v1.0.1', 'source.txt'), 'built source');
  assert.equal(f.show('v1.0.1', 'version.properties'), releaseVersion.trim());
  f.git(f.remote, 'merge-base', '--is-ancestor', concurrent, 'main');
});

test('retries if the branch advances between fetch and atomic push', t => {
  const f = fixture(t);
  const concurrent = f.advance('source.txt', 'racing source\n', 'race');
  const hooks = join(f.runner, '.git', 'hooks');
  mkdirSync(hooks, { recursive: true });
  const quote = value => `'${value.replaceAll('\\', '/').replaceAll("'", "'\\''")}'`;
  const marker = join(f.root, 'raced');
  writeFileSync(join(hooks, 'pre-push'), `#!/usr/bin/env bash\nif [[ ! -f ${quote(marker)} ]]; then\n  touch ${quote(marker)}\n  git --git-dir=${quote(f.remote)} update-ref refs/heads/main ${concurrent}\nfi\n`, { mode: 0o755 });
  passed(f.run());
  assert.equal(f.show('main', 'source.txt'), 'racing source');
  assert.equal(f.show('v1.0.1', 'source.txt'), 'built source');
  assert.equal(f.show('main', 'version.properties'), releaseVersion.trim());
});

test('refuses to overwrite another release version and publishes no tag', t => {
  const f = fixture(t);
  const newer = 'VERSION_NAME=1.0.2\nVERSION_CODE=3\n';
  const concurrent = f.advance('version.properties', newer);
  const result = f.run();
  assert.notEqual(result.status, 0);
  assert.match(result.stdout + result.stderr, /Another release changed version.properties/);
  assert.equal(f.git(f.remote, 'rev-parse', 'main'), concurrent);
  assert.equal(f.show('main', 'version.properties'), newer.trim());
  assert.equal(f.git(f.remote, 'tag', '--list', 'v1.0.1'), '');
});
