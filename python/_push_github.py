# -*- coding: utf-8 -*-
"""调用 hahadong-push skill 的 push_to_github.sh，避免命令行中文路径乱码。
在 Python 内部硬编码 UTF-8 中文路径，用 subprocess cwd 设置正确工作目录，
使 bash 脚本默认 DIR="." 即指向项目根，从而绕过命令行传中文参数。
"""
import subprocess
import os

PROJECT_DIR = r'e:\13_dingdian\z999_归档的文件\06_workbuddy_dingdian\投行产品设计师\hanzheng_pdf_tool_project'
BASH = r'C:\Program Files\Git\bin\bash.exe'
SCRIPT = r'C:\Users\10355\.workbuddy\skills\hahadong-push\scripts\push_to_github.sh'

# 仓库参数：仓库名 + 可见性
REPO_NAME = 'confirmation-pdf-replacer'
VIS = 'public'

assert os.path.isdir(PROJECT_DIR), f'项目目录不存在: {PROJECT_DIR}'
assert os.path.exists(BASH), f'bash 不存在: {BASH}'
assert os.path.exists(SCRIPT), f'push 脚本不存在: {SCRIPT}'

# 环境变量：清空代理（脚本内部也会 unset，这里双保险）
env = dict(os.environ)
for k in ['http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY', 'ALL_PROXY', 'all_proxy']:
    env.pop(k, None)

# bash -c "script . repo vis"  —— cwd=项目根，脚本内 DIR 用 "." 即项目根
cmd = [BASH, '-c', f'"{SCRIPT}" . {REPO_NAME} {VIS}']

print('>>> 工作目录:', PROJECT_DIR)
print('>>> 执行:', ' '.join(cmd))
print('>>> 开始推送（可能耗时，请耐心等待）...')
print('=' * 70)

proc = subprocess.run(cmd, cwd=PROJECT_DIR, env=env,
                      capture_output=True, text=True, encoding='utf-8', errors='replace')
print('---- STDOUT ----')
print(proc.stdout)
print('---- STDERR ----')
print(proc.stderr)
print('=' * 70)
print('>>> 退出码:', proc.returncode)
