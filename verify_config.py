import requests, re

r = requests.get('http://localhost:8888/config')
html = r.text
print('Status:', r.status_code)

checks = [
    ('no single file entry', '单个文件处理' not in html),
    ('no batch entry', '批量处理' not in html),
    ('no nav-link', 'nav-link' not in html),
    ('has load-section', 'load-section' in html),
    ('has load label', '加载已有配置' in html),
    ('has select option', '-- 请选择 --' in html),
    ('has btn-danger', 'btn-danger' in html),
    ('no long body text in option', ' - 本公司聘请' not in html),
]
for label, ok in checks:
    print(f'  [OK] {label}' if ok else f'  [FAIL] {label}')

m = re.search(r'<script>(.*?)</script>', html, re.DOTALL)
if m:
    js = m.group(1)
    print(f'JS braces: {{{js.count("{")}  }}{js.count("}")}')
