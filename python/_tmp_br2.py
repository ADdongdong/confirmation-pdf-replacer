# -*- coding: utf-8 -*-
"""查询远端全部分支（含最新 commit）。"""
import subprocess, os, requests

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
    print('ERROR: token'); raise SystemExit(1)

S = requests.Session(); S.trust_env = False
S.proxies = {'http': None, 'https': None}
H = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'}
API = 'https://api.github.com'
login = S.get(f'{API}/user', headers=H, timeout=20).json().get('login')

r = S.get(f'{API}/repos/{login}/{REPO}/branches', headers=H, timeout=20)
print('远端分支:')
for b in r.json():
    print(f'  {b["name"]} -> {b["commit"]["sha"][:12]}')
