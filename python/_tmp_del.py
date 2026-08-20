# -*- coding: utf-8 -*-
"""从 GitHub 远端删除误上传的临时文件 python/_push_github.py"""
import subprocess
import os
import requests

PROJECT_DIR = r'e:\13_dingdian\z999_归档的文件\06_workbuddy_dingdian\投行产品设计师\hanzheng_pdf_tool_project'
GCM = r'C:\Program Files\Git\mingw64\bin\git-credential-manager.exe'
REPO = 'confirmation-pdf-replacer'

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
    print('ERROR: 取不到 token'); raise SystemExit(1)

SESSION = requests.Session()
SESSION.trust_env = False
SESSION.proxies = {'http': None, 'https': None}
H = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'}
API = 'https://api.github.com'

login = SESSION.get(f'{API}/user', headers=H, timeout=20).json().get('login')
f = 'python/_push_github.py'
url = f'{API}/repos/{login}/{REPO}/contents/{f}'
r = SESSION.get(url + '?ref=main', headers=H, timeout=20)
if r.status_code == 200:
    sha = r.json()['sha']
    d = SESSION.delete(url, headers=H, json={'message': f'delete {f}', 'sha': sha, 'branch': 'main'}, timeout=30)
    print(f'DELETE {f} -> {d.status_code}')
else:
    print(f'远端不存在 {f} ({r.status_code})')
