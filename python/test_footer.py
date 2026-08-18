"""离线测试：跑一次完整的 process_from_data，对比原始 vs 输出 PDF 的页脚。"""
import os
import sys

# 把项目根的 python 目录加入路径
ROOT = r'e:\13_dingdian\z999_归档的文件\06_workbuddy_dingdian\投行产品设计师\hanzheng_pdf_tool_project'
PY_DIR = os.path.join(ROOT, 'python')
sys.path.insert(0, PY_DIR)

import fitz

src = os.path.join(ROOT, 'data', '会所pdf4.pdf')
out_dir = os.path.join(PY_DIR, 'outputs')
os.makedirs(out_dir, exist_ok=True)
dst = os.path.join(out_dir, 'test_footer_redact.pdf')

# 1) 先打印原始 PDF 末页底部文字
print('=' * 60)
print('原始 PDF 概况:')
src_doc = fitz.open(src)
print(f'  页数: {src_doc.page_count}')
for i, p in enumerate(src_doc):
    pw, ph = p.rect.width, p.rect.height
    # 取底部 25pt 范围的文字
    clip = fitz.Rect(0, ph - 30, pw, ph)
    text = p.get_text(clip=clip).strip()
    print(f'  第{i+1}页 (w={pw:.0f} h={ph:.0f}) 底部30pt文字:')
    for line in text.splitlines():
        if line.strip():
            print(f'    | {line}')
src_doc.close()

# 2) 跑处理
print()
print('=' * 60)
print('开始 process_from_data ...')
from replace_header_v4 import process_from_data
ok, msg = process_from_data(
    src, dst,
    company_name='测试证券股份有限公司',
    body_text='本公司聘请的中德证券股份有限公司正在对本公司进行尽职调查。',
    contacts={
        '项目联系人': '张三',
        '项目联系人电话': '13800010002',
        '收件人': '李四',
        '收件人电话': '13900020003',
        '邮箱': 'test@example.com',
    },
    whiteout_bottom=None,
    verbose=True,
)
print(f'返回: ok={ok}  msg={msg}')

# 3) 对比输出 PDF 末页底部
print()
print('=' * 60)
print('输出 PDF 底部检查:')
out_doc = fitz.open(dst)
print(f'  页数: {out_doc.page_count}')
for i, p in enumerate(out_doc):
    pw, ph = p.rect.width, p.rect.height
    clip = fitz.Rect(0, ph - 30, pw, ph)
    text = p.get_text(clip=clip).strip()
    print(f'  第{i+1}页 (w={pw:.0f} h={ph:.0f}) 底部30pt文字:')
    if not text:
        print('    <空>（已被白化）')
    else:
        for line in text.splitlines():
            if line.strip():
                print(f'    | {line}')
out_doc.close()
print()
print(f'输出 PDF: {dst}')
