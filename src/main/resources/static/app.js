'use strict';

const state = {
  bucket: '',
  consoleUrl: 'http://127.0.0.1:9001',
};

const $ = (id) => document.getElementById(id);

function log(message, kind = '') {
  const pre = $('log');
  const line = document.createElement('div');
  if (kind) {
    line.className = 'log-' + kind;
  }
  const time = new Date().toLocaleTimeString();
  line.textContent = `[${time}] ${message}`;
  pre.appendChild(line);
  pre.scrollTop = pre.scrollHeight;
}

function clearLog() {
  $('log').textContent = '';
}

async function request(url, options = {}) {
  const response = await fetch(url, options);
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!response.ok) {
    const message = data && typeof data === 'object' && data.message
      ? `${data.code || response.status}: ${data.message}`
      : (text || response.statusText);
    throw new Error(message);
  }
  return data;
}

function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) {
    return '-';
  }
  if (bytes < 1024) {
    return bytes + ' B';
  }
  const units = ['KiB', 'MiB', 'GiB', 'TiB'];
  let value = bytes;
  let index = -1;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index++;
  }
  return value.toFixed(value >= 10 ? 0 : 1) + ' ' + units[index];
}

function formatTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function currentBucket() {
  if (!state.bucket) {
    throw new Error('请先选择或创建桶');
  }
  return state.bucket;
}

function objectQuery(key) {
  const params = new URLSearchParams({ bucket: currentBucket(), key });
  return params.toString();
}

async function loadConfig() {
  const config = await request('/api/config');
  $('cfgEndpoint').textContent = config.endpoint;
  $('cfgBucket').textContent = config.bucket;
  if (config.consoleUrl) {
    state.consoleUrl = config.consoleUrl;
    $('consoleLink').href = config.consoleUrl;
  }
}

async function loadBuckets(preferred) {
  const buckets = await request('/api/buckets');
  const select = $('bucketSelect');
  select.textContent = '';
  for (const bucket of buckets) {
    const option = document.createElement('option');
    option.value = bucket.name;
    option.textContent = bucket.name;
    select.appendChild(option);
  }
  const target = preferred && buckets.some((b) => b.name === preferred)
    ? preferred
    : (state.bucket && buckets.some((b) => b.name === state.bucket) ? state.bucket : (buckets[0]?.name || ''));
  if (target) {
    select.value = target;
    state.bucket = target;
  }
  log(`桶列表已刷新，共 ${buckets.length} 个`, 'ok');
}

async function createBucket() {
  const name = $('newBucket').value.trim();
  if (!name) {
    log('请输入桶名', 'error');
    return;
  }
  await request('/api/buckets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  $('newBucket').value = '';
  log(`已创建桶 ${name}`, 'ok');
  await loadBuckets(name);
}

async function uploadObject() {
  const file = $('fileInput').files[0];
  if (!file) {
    log('请选择文件', 'error');
    return;
  }
  const bucket = currentBucket();
  const form = new FormData();
  form.append('bucket', bucket);
  const key = $('objectKey').value.trim();
  if (key) {
    form.append('key', key);
  }
  form.append('file', file);

  const result = await request('/api/objects', { method: 'POST', body: form });
  $('fileInput').value = '';
  $('objectKey').value = '';
  log(`上传成功: s3://${result.bucket}/${result.key} (${formatBytes(result.size)}, etag=${result.etag})`, 'ok');
  await listObjects();
}

function addAction(td, label, action, key, className) {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = label;
  button.className = className || 'ghost';
  button.dataset.action = action;
  button.dataset.key = key;
  td.appendChild(button);
}

function renderObjects(objects) {
  const tbody = $('objectRows');
  tbody.textContent = '';
  if (!objects.length) {
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = 4;
    cell.className = 'empty';
    cell.textContent = '当前前缀下没有对象';
    row.appendChild(cell);
    tbody.appendChild(row);
    return;
  }

  for (const object of objects) {
    const row = document.createElement('tr');

    const keyCell = document.createElement('td');
    keyCell.className = 'key';
    keyCell.textContent = object.key;
    row.appendChild(keyCell);

    const sizeCell = document.createElement('td');
    sizeCell.textContent = formatBytes(object.size);
    row.appendChild(sizeCell);

    const timeCell = document.createElement('td');
    timeCell.textContent = formatTime(object.lastModified);
    row.appendChild(timeCell);

    const actions = document.createElement('td');
    actions.className = 'actions';

    const download = document.createElement('a');
    download.className = 'link';
    download.textContent = '下载';
    download.href = '/api/objects/download?' + objectQuery(object.key);
    actions.appendChild(download);

    addAction(actions, '元数据', 'stat', object.key);
    addAction(actions, '预签名', 'presign', object.key);
    addAction(actions, '删除', 'delete', object.key, 'danger');

    row.appendChild(actions);
    tbody.appendChild(row);
  }
}

async function listObjects() {
  const bucket = currentBucket();
  const prefix = $('prefixInput').value.trim();
  const params = new URLSearchParams({ bucket, prefix, recursive: 'true' });
  const objects = await request('/api/objects?' + params.toString());
  renderObjects(objects);
  log(`列出 ${bucket} 下 ${objects.length} 个对象`, 'ok');
}

async function showStat(key) {
  const stat = await request('/api/objects/stat?' + objectQuery(key));
  log(`元数据 ${key}\n` + JSON.stringify(stat, null, 2));
}

async function deleteObject(key) {
  if (!window.confirm(`确认删除 s3://${currentBucket()}/${key} ？`)) {
    return;
  }
  await request('/api/objects?' + objectQuery(key), { method: 'DELETE' });
  log(`已删除 ${key}`, 'ok');
  await listObjects();
}

async function presign(keyFromRow) {
  const key = keyFromRow || $('presignKey').value.trim();
  if (!key) {
    log('请输入对象 key', 'error');
    return;
  }
  const method = $('presignMethod').value;
  const expirySeconds = Number($('presignExpiry').value);
  const result = await request('/api/presign', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ bucket: currentBucket(), key, method, expirySeconds }),
  });

  $('presignResult').classList.remove('hidden');
  $('presignUrl').value = result.url;
  $('presignKey').value = key;
  const openLink = $('openPresignLink');
  if (method === 'GET') {
    openLink.href = result.url;
    openLink.classList.remove('hidden');
  } else {
    openLink.classList.add('hidden');
  }
  log(`已生成 ${method} 预签名 URL，有效期至 ${formatTime(result.expiresAt)}`, 'ok');
}

async function copyPresignedUrl() {
  const url = $('presignUrl').value;
  if (!url) {
    return;
  }
  try {
    await navigator.clipboard.writeText(url);
    log('预签名 URL 已复制到剪贴板', 'ok');
  } catch {
    $('presignUrl').select();
    document.execCommand('copy');
    log('预签名 URL 已复制（回退方式）', 'ok');
  }
}

function bindEvents() {
  $('bucketSelect').addEventListener('change', (event) => {
    state.bucket = event.target.value;
    $('objectRows').innerHTML = '<tr><td colspan="4" class="empty">点击“查询”加载对象</td></tr>';
    log('当前桶: ' + state.bucket);
  });

  $('createBucketBtn').addEventListener('click', () => createBucket().catch((e) => log(e.message, 'error')));
  $('refreshBucketsBtn').addEventListener('click', () => loadBuckets().catch((e) => log(e.message, 'error')));
  $('uploadBtn').addEventListener('click', () => uploadObject().catch((e) => log(e.message, 'error')));
  $('listBtn').addEventListener('click', () => listObjects().catch((e) => log(e.message, 'error')));
  $('presignBtn').addEventListener('click', () => presign().catch((e) => log(e.message, 'error')));
  $('copyPresignBtn').addEventListener('click', () => copyPresignedUrl());
  $('clearLogBtn').addEventListener('click', clearLog);
  $('prefixInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      listObjects().catch((e) => log(e.message, 'error'));
    }
  });

  $('objectRows').addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
      return;
    }
    const key = button.dataset.key;
    const action = button.dataset.action;
    if (action === 'stat') {
      showStat(key).catch((e) => log(e.message, 'error'));
    } else if (action === 'presign') {
      presign(key).catch((e) => log(e.message, 'error'));
    } else if (action === 'delete') {
      deleteObject(key).catch((e) => log(e.message, 'error'));
    }
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  bindEvents();
  try {
    await loadConfig();
    await loadBuckets();
    if (state.bucket) {
      await listObjects();
    }
  } catch (e) {
    log('初始化失败: ' + e.message, 'error');
    log('请确认 Silo 已启动，且 S3 API 监听 127.0.0.1:9000。', 'error');
  }
});
