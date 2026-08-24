# -*- coding: utf-8 -*-
"""查询远端 memory 目录文件，并与本地对比。"""
import subprocess, os, requests

GCM = r'C:\Program Files\Git\mingw64\bin\git-credential-manager.exe'
REPO = 'confirmation-pdf-replacer'
PROJECT_DIR = r'e:\13_dingdian\z999_归档的文件\06_workbuddy_dingdian\投行产品设计师\hanzheng_pdf_tool_project'

env = dict(os.environ)
for k in ['http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'all_proxy']:
    env.pop(k, None)
raw = subprocess.run([GCM, 'get'], input='protocol=https\nhost=github.com\n',
                     capture_output=True, text=True, env=env).stdout
token = ''
for line in raw.splitlines():
    if line.startswith('password='):
        token = line[len('password='):].strip()
if not token:
    print('ERROR: token'); raise SystemExit(1)

S = requests.Session(); S.trust_env = False
S.proxies = {'http': None, 'https': None}
H = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'}
API = 'https://api.github.com'
login = S.get(f'{API}/user', headers=H, timeout=20).json().get('login')

# 远端 tree
tree = S.get(f'{API}/repos/{login}/{REPO}/git/trees/main?recursive=1', headers=H, timeout=30).json()
remote_mem = sorted(t['path'] for t in tree.get('tree', []) if t['type'] == 'blob' and t['path'].startswith('memory/'))
print('=== 远端 memory 目录 ===')
for f in remote_mem: print('  ', f)

print()
print('=== 本地 memory 目录 ===')
local_mem_dir = os.path.join(PROJECT_DIR, 'memory')
if os.path.isdir(local_mem_dir):
    for f in sorted(os.listdir(local_mem_dir)):
        print('  ', 'memory/' + f)
