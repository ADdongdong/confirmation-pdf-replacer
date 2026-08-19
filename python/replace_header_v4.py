"""
函证头部替换工具 v4.0
========================
核心改进：
- 分区域处理：头部（白化重写）+ 联系方式（原位替换）
- 手动 CJK 断行：预计算每行字符宽度，避免 PyMuPDF 自动换行标点错位
- 保持表格区域完全不动

用法：
  python replace_header_v4.py <会所PDF> -t <话术.txt> -o <输出.pdf>

话术文件格式：
  TITLE:标题文字          → 居中大字号
  RIGHT:右对齐文字        → 右上角小字
  正文内容...              → 左对齐正文字
  ---                     → 分隔线（上面是头部，下面是联系方式）
  项目联系人：张三         → 联系方式区，按行逐一原位替换
"""

import sys
import io
import os
import re
import argparse
import fitz  # pyright: ignore[reportMissingImports]
import pdfplumber
from collections import defaultdict

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# ============================================================
# CJK 字符宽度估算（NSimSun 等宽字体）
# ============================================================
def _is_cjk_fullwidth(cp):
    """判断是否全角字符"""
    return (0x4E00 <= cp <= 0x9FFF or      # CJK 统一表意文字
            0x3000 <= cp <= 0x303F or      # CJK 标点
            0xFF00 <= cp <= 0xFFEF or      # 全角字母/符号
            0x2E80 <= cp <= 0x2FDF or      # CJK 部首
            0x3400 <= cp <= 0x4DBF or      # CJK 扩展A
            0xF900 <= cp <= 0xFAFF)        # CJK 兼容


def _char_width(ch, fontsize):
    """估算单个字符的渲染宽度"""
    cp = ord(ch)
    if _is_cjk_fullwidth(cp):
        return fontsize
    elif cp < 0x80:
        return fontsize * 0.55
    else:
        return fontsize


def wrap_cjk_text(text, fontsize, avail_width):
    """手动断行：按字符宽度计算，在段落宽度内自然换行。
    返回字符串列表，每项为一个不超宽的文本行。
    """
    if not text:
        return ['']

    lines = []
    cur_line = ""
    cur_width = 0.0

    for ch in text:
        cw = _char_width(ch, fontsize)

        if cur_width + cw > avail_width and cur_line:
            # 当前行已满，开始新行
            lines.append(cur_line)
            cur_line = ch
            cur_width = cw
        else:
            cur_line += ch
            cur_width += cw

    if cur_line:
        lines.append(cur_line)

    return lines if lines else ['']


def find_bold_font_file():
    """查找黑体/粗体字体文件（优先 simhei.ttf）。

    与 Java 版 PdfProcessor.findBoldFontFile() 保持一致：
    Windows 上的 simsunb.ttf 实际上是 SimSun-ExtB，缺少常用汉字，因此仅作为最后回退。
    """
    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(script_dir, 'fonts', 'simhei.ttf'),
        r'C:\Windows\Fonts\simhei.ttf',
        os.path.join(script_dir, 'fonts', 'simfang.ttf'),
        r'C:\Windows\Fonts\simfang.ttf',
        os.path.join(script_dir, 'fonts', 'simsunb.ttf'),
        r'C:\Windows\Fonts\simsunb.ttf',
    ]
    for p in candidates:
        if os.path.exists(p):
            return p
    return None


# ============================================================
# 格式提取
# ============================================================
def extract_format(input_path):
    """自动提取原 PDF 格式参数"""
    with pdfplumber.open(input_path) as pdf:
        page = pdf.pages[0]
        chars = page.chars

    # 按 y 分组
    line_groups = defaultdict(list)
    for c in chars:
        line_groups[round(c['top'] / 3) * 3].append(c)

    sorted_ys = sorted(line_groups.keys())

    # 字号统计
    from collections import Counter
    size_counter = Counter()
    for c in chars:
        if 50 < c['top'] < 270:
            size_counter[round(c.get('height', 0), 1)] += 1

    valid_sizes = {s: n for s, n in size_counter.items() if 8 <= s <= 14}
    body_size = max(valid_sizes, key=lambda k: valid_sizes[k]) if valid_sizes else 10.5
    title_size = max(size_counter.keys()) if size_counter else 18.0

    # 边距
    header_chars = [c for c in chars if 50 < c['top'] < 260]
    if header_chars:
        left_margin = min(c['x0'] for c in header_chars)
        right_edge = max(c['x1'] for c in header_chars)
    else:
        left_margin = 50
        right_edge = 560

    para_width = page.width - left_margin - (page.width - right_edge) - 8

    # 行距
    gaps = []
    prev_y = None
    for y in sorted_ys:
        if y < 50 or y > 270:
            continue
        if prev_y and 10 < y - prev_y < 40:
            gaps.append(y - prev_y)
        prev_y = y
    line_spacing = sum(gaps) / len(gaps) if gaps else 15.7

    # 表格起始
    table_y = _find_table_start(line_groups, sorted_ys)

    print(f"  原 PDF 格式:")
    print(f"    正文字号: {body_size}pt    标题字号: {title_size}pt")
    print(f"    左边距: {left_margin:.0f}pt    段落宽度: {para_width:.0f}pt")
    print(f"    行间距: {line_spacing:.1f}pt    表格起始: y={table_y:.0f}")

    return {
        'body_size': body_size,
        'title_size': title_size,
        'left_margin': left_margin,
        'para_width': para_width,
        'line_spacing': line_spacing,
        'table_y': table_y,
    }


def _find_table_start(line_groups, sorted_ys):
    """检测"列示如下"行的 y 坐标（该行需要保留，不做白化）"""
    keywords = ['往来余额', '往来款项', '列示如下', '余额列示', '往来账项']
    for y in sorted_ys:
        if y < 250:
            continue
        lc = sorted(line_groups[y], key=lambda c: c['x0'])
        line_text = ''.join(c['text'] for c in lc)
        for kw in keywords:
            if kw in line_text:
                # 返回该行的 y 坐标（白化区域不应低于此值）
                return y - 3
    # fallback
    for y in sorted_ys:
        if y < 250:
            continue
        lc = sorted(line_groups[y], key=lambda c: c['x0'])
        line_text = ''.join(c['text'] for c in lc).strip()
        if re.match(r'^\d+[\.\s、]', line_text):
            return y - 3
    return 267


# ============================================================
# 收函单位自动提取（批量处理用）
# ============================================================
def _is_likely_company_name(text):
    """判断文本是否像公司名称（纯/主要为中文，4~30 字符）

    注意：公司名本身常含"公司/银行/事务所"等词，不能过滤这些词。
    """
    candidate = text.strip()
    if not (4 <= len(candidate) <= 30):
        return False
    # 过滤明显不是公司名的关键词（多为联系方式、标题、正文说明）
    skip_keywords = [
        '单位：', '致：', '敬启者', '编号：', '编号', '索引',
        '询证函', '会计师', '审计', '列示', '余额',
        '项目联系人', '回函地址', '收件人', '邮编', '邮箱',
        '电话', '传真',
    ]
    for kw in skip_keywords:
        if kw in candidate:
            return False
    chinese_chars = [c for c in candidate if '\u4e00' <= c <= '\u9fff']
    # 至少要有一半以上是中文字符，且至少 4 个中文
    return len(chinese_chars) >= 4 and len(chinese_chars) >= len(candidate) * 0.5



def extract_recipient(input_path):
    """从 PDF 首页自动提取「收函单位」（被审计单位名称）

    搜索区域：y ∈ [50, 300]（覆盖首页上半部），逐行搜索文本
    优先级：
    1. "单位：" → 提取冒号后整行
    2. "致：" → 提取冒号后整行
    3. "敬启者" → 先取同一行中"敬启者"之前的文本（常见格式：XXX公司 敬启者）
                  再查前后 ±3 行中符合公司名特征的行
    4. 兜底 → 取搜索区域内第一个符合公司名特征的行

    Returns:
        (recipient_name: str, confidence: str)
        confidence: 'high' | 'medium' | 'low'
    """
    import pdfplumber

    with pdfplumber.open(input_path) as pdf:
        page = pdf.pages[0]
        chars = page.chars

    if not chars:
        return '', 'low'

    # 按 y 坐标分组，构建文本行（扩大搜索范围到 300）
    line_groups = defaultdict(list)
    for c in chars:
        if 50 <= c['top'] <= 300:
            line_groups[round(c['top'] / 3) * 3].append(c)

    # 按 y 排序，逐行构建完整文本
    sorted_ys = sorted(line_groups.keys())
    text_lines = []
    for y in sorted_ys:
        lc = sorted(line_groups[y], key=lambda c: c['x0'])
        line_text = ''.join(c['text'] for c in lc).strip()
        if line_text:
            text_lines.append(line_text)

    if not text_lines:
        return '', 'low'

    # ---- 策略1：查找 "单位：" ----
    for line in text_lines:
        if '单位：' in line:
            after = line.split('单位：', 1)[1].strip()
            if after and _is_likely_company_name(after):
                return after, 'high'
            if after:
                return after, 'high'

    # ---- 策略2：查找 "致：" ----
    for line in text_lines:
        if '致：' in line:
            after = line.split('致：', 1)[1].strip()
            if after and _is_likely_company_name(after):
                return after, 'high'
            if after:
                return after, 'high'

    # ---- 策略3：查找 "敬启者" ----
    for i, line in enumerate(text_lines):
        if '敬启者' in line:
            # 3a: 先尝试提取同一行中"敬启者"之前的文本
            # 常见格式："某某科技有限公司 敬启者：" 或 "敬启者：某某科技有限公司"
            before_kq = line.split('敬启者')[0].strip()
            after_kq = line.split('敬启者', 1)[1].lstrip('：:').strip()

            if _is_likely_company_name(before_kq):
                return before_kq, 'high'
            if _is_likely_company_name(after_kq):
                return after_kq, 'high'

            # 3b: 向前后各看 3 行，找符合公司名特征的行
            for offset in range(-3, 4):
                idx = i + offset
                if idx == i:
                    continue  # 同一行已检查过
                if 0 <= idx < len(text_lines):
                    if _is_likely_company_name(text_lines[idx]):
                        return text_lines[idx].strip(), 'medium'

    # ---- 策略4：兜底 ----
    for line in text_lines:
        if _is_likely_company_name(line):
            return line.strip(), 'medium'

    return '', 'low'


# ============================================================
# 话术解析
# ============================================================
def parse_config(text_path):
    """
    返回 (header_lines, contact_lines)

    header_lines: [(type, text), ...]   type in ('title', 'right', 'body')
    contact_lines: [text, ...]          每行完整文字（含标签）
    """
    header_lines = []
    contact_lines = []
    in_contact = False

    with open(text_path, 'r', encoding='utf-8') as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith('#'):
                continue

            if line == '---':
                in_contact = True
                continue

            if in_contact:
                # 联系方式区：直接保留整行
                contact_lines.append(line)
            elif line.startswith('TITLE:') or line.startswith('title:'):
                header_lines.append(('title', line[6:]))
            elif line.startswith('RIGHT:') or line.startswith('right:'):
                header_lines.append(('right', line[6:]))
            else:
                header_lines.append(('body', line))

    print(f"\n  话术: 头部 {len(header_lines)} 行, 联系方式 {len(contact_lines)} 行")
    for lt, t in header_lines:
        preview = t[:45] + ('...' if len(t) > 45 else '')
        print(f"    [H {lt:5s}] {preview}")
    for t in contact_lines:
        preview = t[:45] + ('...' if len(t) > 45 else '')
        print(f"    [C       ] {preview}")

    return header_lines, contact_lines


# ============================================================
# 主处理
# ============================================================
def process_page(doc, header_lines, contact_lines, fmt, font_file,
                 contact_prompt=None, whiteout_bottom=None, verbose=False,
                 bold_font_file=None):
    """处理第 1 页"""
    page = doc[0]
    page_w = page.rect.width
    left = fmt['left_margin']
    para_w = fmt['para_width']
    body_fs = fmt['body_size']
    title_fs = fmt['title_size']
    spacing = fmt['line_spacing']
    table_y = fmt['table_y']

    # 读取字体（常规 + 粗体）
    font_name = "NSimSun"
    bold_font_name = "SimHei"
    font_buf = None
    bold_font_buf = None
    if font_file:
        with open(font_file, 'rb') as f:
            font_buf = f.read()
    if bold_font_file:
        with open(bold_font_file, 'rb') as f:
            bold_font_buf = f.read()

    # ==== Phase 1: 计算布局 ====

    # ----- 头部区域 -----
    header_blocks = []   # [(rect, text, fontsize, align)]
    y = 48.0             # 起始 y

    for lt, text in header_lines:
        if lt == 'title':
            fs = title_fs
            align = fitz.TEXT_ALIGN_CENTER
            x = 0
            w = page_w
            wrapped = [text]
        elif lt == 'right':
            fs = body_fs
            align = fitz.TEXT_ALIGN_RIGHT
            x = left
            w = para_w
            wrapped = [text]
        else:
            fs = body_fs
            align = fitz.TEXT_ALIGN_LEFT
            x = left
            w = para_w
            # 手动断行
            wrapped = wrap_cjk_text(text, fs, para_w)

        # 为每个断行后的子行分配空间
        for sub_text in wrapped:
            line_h = fs + 5
            rect = fitz.Rect(x, y, x + w, y + line_h)
            header_blocks.append((rect, sub_text, fs, align))
            y += spacing

    # 留一点间距
    header_end_y = y + 6

    # ----- 加粗提示行（联系方式前置说明）-----
    prompt_blocks = []
    prompt_end_y = header_end_y

    if contact_prompt and len(contact_lines) > 0:
        contact_start_y = max(header_end_y + 4, 188)
        yp = contact_start_y

        # 联系方式可用空间
        max_contact_end = table_y - 5
        avail_for_contact = max_contact_end - contact_start_y

        # 联系方式行距与正文一致，若放不下则压缩
        n_total = 1 + len(contact_lines)  # 一行提示 + N行联系方式
        contact_gap = spacing
        if contact_gap * n_total > avail_for_contact:
            contact_gap = max(avail_for_contact / n_total, 12)

        # 提示行使用 wrap_cjk_text 断行（可能多行）
        wrapped_prompt = wrap_cjk_text(contact_prompt, body_fs, para_w)
        for sub_text in wrapped_prompt:
            line_h = body_fs + 4
            rect = fitz.Rect(left, yp, left + para_w, yp + line_h)
            prompt_blocks.append((rect, sub_text, body_fs))
            yp += contact_gap

        prompt_end_y = yp + 2
    else:
        contact_start_y = max(header_end_y + 4, 188)
        # 联系方式可用空间
        max_contact_end = table_y - 5
        avail_for_contact = max_contact_end - contact_start_y
        n_contact = len(contact_lines)
        if n_contact > 0:
            contact_gap = spacing
            if contact_gap * n_contact > avail_for_contact:
                contact_gap = max(avail_for_contact / n_contact, 12)
        else:
            contact_gap = spacing

    # ----- 联系方式区域（两栏布局，行距与正文一致）-----
    contact_blocks = []
    yc = max(prompt_end_y, contact_start_y)

    # 联系方式可用空间：不能低于表格保护线
    max_contact_end = table_y - 5
    avail_for_contact = max_contact_end - yc

    # 行距与正文一致
    n_contact = len(contact_lines)
    if n_contact > 0:
        contact_gap = spacing
        if contact_gap * n_contact > avail_for_contact:
            contact_gap = max(avail_for_contact / n_contact, 12)
    else:
        contact_gap = spacing

    # 两栏布局参数
    COL_GAP = 20  # 左右栏间距
    col_width = (para_w - COL_GAP) / 2
    right_x = left + col_width + COL_GAP

    for item in contact_lines:
        if yc + contact_gap > max_contact_end + 4:
            if verbose:
                print(f"    ⚠ 联系方式行超出保护线(y={max_contact_end})，截断")
            break
        line_h = body_fs + 4

        if isinstance(item, tuple) and item[0] == 'pair':
            # 两栏配对：左栏(col_width) + 右栏(剩余宽度)
            _, left_text, right_text = item
            if left_text:
                left_rect = fitz.Rect(left, yc, left + col_width, yc + line_h)
                contact_blocks.append((left_rect, left_text, body_fs, fitz.TEXT_ALIGN_LEFT))
            if right_text:
                right_rect = fitz.Rect(right_x, yc, left + para_w, yc + line_h)
                contact_blocks.append((right_rect, right_text, body_fs, fitz.TEXT_ALIGN_LEFT))
        elif isinstance(item, tuple) and item[0] == 'single':
            # 单行全宽（邮箱）
            _, text = item
            rect = fitz.Rect(left, yc, left + para_w, yc + line_h)
            contact_blocks.append((rect, text, body_fs, fitz.TEXT_ALIGN_LEFT))
        else:
            # 向后兼容：纯字符串（话术文件路径）
            text = item
            rect = fitz.Rect(left, yc, left + para_w, yc + line_h)
            contact_blocks.append((rect, text, body_fs, fitz.TEXT_ALIGN_LEFT))

        yc += contact_gap

    # 统一白化下界：扩展到表格保护线上方（清除模板残留文字）
    contact_end_y = min(yc, max_contact_end)
    auto_whiteout_bottom = max(header_end_y, prompt_end_y, yc + 30, contact_end_y)
    auto_whiteout_bottom = min(auto_whiteout_bottom, table_y - 3)

    # 用户自定义白化边界（来自前端红色遮罩拖拽）
    if whiteout_bottom is not None:
        whiteout_bottom = min(float(whiteout_bottom), table_y - 3)
        if verbose:
            print(f"  白化边界（用户指定）: y={whiteout_bottom:.0f} (自动为 y={auto_whiteout_bottom:.0f})")
    else:
        whiteout_bottom = auto_whiteout_bottom

    if verbose:
        print(f"\n  布局计算:")
        print(f"    头部: y=48 → y={header_end_y:.0f} ({len(header_blocks)} 个子行)")
        if prompt_blocks:
            print(f"    提示行(加粗): {len(prompt_blocks)} 行")
        print(f"    联系方式: y={contact_start_y:.0f} → y={contact_end_y:.0f} ({len(contact_blocks)}/{n_contact} 行) 间距={contact_gap:.1f}pt")
        print(f"    表格保护线: y={table_y:.0f}")

    # ==== Phase 2: 执行操作 ====

    # 一次性白化：从页面顶部到联系方式结束（或表格保护线）
    redact_all = fitz.Rect(0, 0, page_w, whiteout_bottom)
    page.add_redact_annot(redact_all, fill=(1, 1, 1))
    print(f"\n  白化区域: y=0 → y={whiteout_bottom:.0f}")

    # 执行白化（一次 apply_redactions 处理所有 redaction）
    page.apply_redactions()

    # 重新嵌入字体
    if font_buf:
        page.insert_font(fontname=font_name, fontbuffer=font_buf)
    if bold_font_buf:
        page.insert_font(fontname=bold_font_name, fontbuffer=bold_font_buf)

    # 逐块写入
    print(f"\n  写入内容:")
    ok = 0
    total = len(header_blocks) + len(prompt_blocks) + len(contact_blocks)

    for rect, text, fs, align in header_blocks:
        try:
            rc = page.insert_textbox(rect, text, fontname=font_name,
                                     fontsize=fs, align=align)
            if rc < 0:
                # 扩展 rect 再试
                tall_rect = fitz.Rect(rect.x0, rect.y0, rect.x1, rect.y1 + fs * 2)
                rc = page.insert_textbox(tall_rect, text, fontname=font_name,
                                         fontsize=fs, align=align)
            if rc >= 0:
                ok += 1
                if verbose:
                    print(f"    ✓ y={rect.y0:.0f} [{text[:40]}]")
            elif verbose:
                print(f"    ✗ y={rect.y0:.0f} [{text[:30]}...] (rc={rc})")
        except Exception as e:
            if verbose:
                print(f"    ✗ y={rect.y0:.0f} 异常: {e}")

    # 加粗提示行：优先使用黑体（SimHei）真加粗，未找到时回退到 NSimSun 伪加粗
    use_bold_font = bold_font_buf is not None
    for rect, text, fs in prompt_blocks:
        try:
            if use_bold_font:
                rc = page.insert_textbox(rect, text,
                                         fontname=bold_font_name,
                                         fontsize=fs,
                                         align=fitz.TEXT_ALIGN_LEFT)
            else:
                # 第一遍：正常位置渲染
                rc = page.insert_textbox(rect, text,
                                         fontname=font_name,
                                         fontsize=fs,
                                         align=fitz.TEXT_ALIGN_LEFT)
                # 第二遍：右偏移 0.5pt 叠加，形成伪加粗效果
                offset_rect = fitz.Rect(rect.x0 + 0.5, rect.y0,
                                        rect.x1 + 0.5, rect.y1)
                page.insert_textbox(offset_rect, text,
                                    fontname=font_name,
                                    fontsize=fs,
                                    align=fitz.TEXT_ALIGN_LEFT)
            if rc >= 0:
                ok += 1
                if verbose:
                    print(f"    ✓ y={rect.y0:.0f} [B] [{text[:40]}]")
            elif verbose:
                print(f"    ✗ y={rect.y0:.0f} [B] [{text[:30]}...] (rc={rc})")
        except Exception as e:
            if verbose:
                print(f"    ✗ y={rect.y0:.0f} [B] 异常: {e}")

    for rect, text, fs, align in contact_blocks:
        try:
            rc = page.insert_textbox(rect, text, fontname=font_name,
                                     fontsize=fs, align=align)
            if rc >= 0:
                ok += 1
                if verbose:
                    print(f"    ✓ y={rect.y0:.0f} [{text[:40]}]")
            elif verbose:
                print(f"    ✗ y={rect.y0:.0f} [{text[:30]}...]")
        except Exception as e:
            if verbose:
                print(f"    ✗ y={rect.y0:.0f} 异常: {e}")

    print(f"  成功: {ok}/{total}")
    return ok == total


# ============================================================
# 页脚遮盖：每页底部"索引号/编号/页码"清除
# ============================================================
def redact_footer_per_page(doc, footer_height=22.0, verbose=False):
    """对 PDF 每一页底部一块区域做白化（移除会所档案号/页码等页脚信息）。

    设计要点：
      - 函证每页底部固定有 "索引号:...  编号:...  第N页/共M页"，属于会所内部
        业务流水 + 档案号，券商发函不应携带。
      - 采用"从页底向上取固定高度"的策略（默认 22pt），不依赖关键字识别。
        优点：实现简单、对格式变化鲁棒；缺点：底部恰好有表格末行时可能误伤。
      - 由于 PDF 页边距通常 ≥ 50pt，22pt 不会触及正文表格，仅覆盖页脚带。

    Args:
        doc:          fitz.Document
        footer_height: 底部白化带高度（pt，从页面底部向上量），默认 22
        verbose:      是否打印日志
    """
    if footer_height <= 0:
        return 0

    n_redacted = 0
    for idx, page in enumerate(doc):
        page_h = page.rect.height
        page_w = page.rect.width
        # 布局坐标（从顶部算起）下界 = page_h - footer_height
        y_top = page_h - footer_height
        if y_top <= 0:
            continue
        # 白化矩形：从 (0, y_top) 到 (page_w, page_h)
        rect = fitz.Rect(0, y_top, page_w, page_h)
        page.add_redact_annot(rect, fill=(1, 1, 1))
        # 立即 apply 让本页彻底擦除页脚（避免累积到最后一次性 apply 时图片/字体
        # 跨页引用出问题）
        page.apply_redactions()
        n_redacted += 1
        if verbose:
            print(f"  页脚遮盖: 第{idx+1}页 y={y_top:.0f}→{page_h:.0f}")

    return n_redacted


# ============================================================
# 结构化数据接口（供 Web 表单调用）
# ============================================================
def process_from_data(input_path, output_path, company_name, body_text,
                       contacts, font_file=None, whiteout_bottom=None,
                       footer_height=None, verbose=False):
    """从结构化数据直接替换 PDF 头部（供 Web 表单调用）

    Args:
        input_path:   原始会所 PDF 路径
        output_path:  输出 PDF 路径
        company_name: 致函单位名称（如 "中信证券股份有限公司"）
        body_text:    函证正文段落（不含 "致：XXX" 行）
        contacts:     dict, 字段: 项目联系人, 项目联系人电话, 收件人, 收件人电话, 邮箱
        font_file:    字体文件路径
        whiteout_bottom: 用户自定义白化下界 Y 坐标（从顶部算起，单位 pt），None=自动检测
        footer_height: 页脚遮盖带高度（从页面底部向上，单位 pt），None=默认 22
        verbose:      是否打印详细信息

    Returns:
        (success: bool, message: str)
    """
    # ---- 构建头部 -----
    # 正文首行缩进两个字（CJK 全角空格）
    INDENT = '\u3000\u3000'

    header_lines = [
        ('title', '企业询证函'),
        ('body', f'致：{company_name}'),
        ('body', INDENT + body_text),
    ]

    # ---- 构建联系方式（两栏布局）-----
    # 配对：左栏=字段标签短的在左, 右栏=电话号码等在右
    # 与“致：xxxx公司”行左对齐，不再缩进两个全角空格
    contact_lines = []
    pairs_def = [
        ('项目联系人', '项目联系人电话'),
        ('收件人', '收件人电话'),
    ]
    for left_key, right_key in pairs_def:
        left_val = contacts.get(left_key, '').strip()
        right_val = contacts.get(right_key, '').strip()
        if left_val or right_val:
            left_text = f'{left_key}：{left_val}' if left_val else ''
            right_text = f'{right_key}：{right_val}' if right_val else ''
            contact_lines.append(('pair', left_text, right_text))

    # 邮箱单独一行（全宽）
    email_val = contacts.get('邮箱', '').strip()
    if email_val:
        contact_lines.append(('single', f'邮箱：{email_val}'))

    # 回函地址单独一行（全宽），会所收到函证后需寄回此处
    return_address = contacts.get('回函地址', '').strip()
    if return_address:
        contact_lines.append(('single', f'回函地址：{return_address}'))

    # ---- 加粗提示行（固定文案），与“致：xxxx公司”行左对齐 -----
    PROMPT_LINE = '若您方有相关本询证函函证事项问题，请直接联系下方项目联系人电话：'

    if verbose:
        print(f"\n  结构化输入:")
        print(f"    致函单位: {company_name}")
        print(f"    正文: {len(body_text)} 字符")
        print(f"    联系方式: {len(contact_lines)} 行")

    # ---- 提取格式 + 处理 ----
    try:
        fmt = extract_format(input_path)
    except Exception as e:
        return False, f"提取 PDF 格式失败: {e}"

    script_dir = os.path.dirname(os.path.abspath(__file__))
    if font_file is None:
        candidates = [
            os.path.join(script_dir, 'fonts', 'NSimSun.ttf'),
            r'C:\Windows\Fonts\simsun.ttc',
        ]
        for p in candidates:
            if os.path.exists(p):
                font_file = p
                break

    # 查找黑体粗体字体（参考 Java 版逻辑）
    bold_font_file = find_bold_font_file()

    try:
        doc = fitz.open(input_path)
        if len(doc) == 0:
            return False, "PDF 无页面"

        ok = process_page(doc, header_lines, contact_lines, fmt,
                          font_file, contact_prompt=PROMPT_LINE,
                          whiteout_bottom=whiteout_bottom,
                          verbose=verbose,
                          bold_font_file=bold_font_file)

        # 每页底部页脚遮盖（"索引号/编号/页码"）
        fh = footer_height if footer_height is not None else 22.0
        n_footer = redact_footer_per_page(doc, footer_height=fh,
                                          verbose=verbose)
        if verbose:
            print(f"\n  页脚遮盖: 共 {n_footer} 页")

        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        doc.save(output_path, garbage=4, deflate=True)
        doc.close()

        if ok:
            return True, output_path
        else:
            return False, f"部分内容写入失败，请查看日志"
    except Exception as e:
        return False, f"PDF 处理异常: {e}"


# ============================================================
# 主入口
# ============================================================
def main():
    parser = argparse.ArgumentParser(description='函证头部替换工具 v4.0')
    parser.add_argument('input', help='会所函证 PDF 路径')
    parser.add_argument('-t', '--text', required=True, help='话术文本文件 (.txt)')
    parser.add_argument('-o', '--output', default=None, help='输出文件路径')
    parser.add_argument('--verbose', action='store_true', help='显示详细信息')
    args = parser.parse_args()

    print("=" * 60)
    print("函证头部替换工具 v4.0")
    print("=" * 60)

    if not os.path.exists(args.input):
        print(f"\n✗ 输入文件不存在: {args.input}")
        return
    if not os.path.exists(args.text):
        print(f"\n✗ 话术文件不存在: {args.text}")
        return

    if not args.output:
        base, ext = os.path.splitext(args.input)
        args.output = f"{base}_fixed{ext}"

    # Step 1: 提取格式
    print(f"\n[1] 分析原 PDF 格式...")
    fmt = extract_format(args.input)

    # Step 2: 解析话术
    print(f"\n[2] 解析话术...")
    header_lines, contact_lines = parse_config(args.text)

    # Step 3: 查找字体
    font_file = None
    paths = [
        os.path.join(os.path.dirname(__file__), 'fonts', 'NSimSun.ttf'),
        r'C:\Windows\Fonts\simsun.ttc',
    ]
    for p in paths:
        if os.path.exists(p):
            font_file = p
            break
    bold_font_file = find_bold_font_file()
    print(f"\n  字体: {font_file or '系统回退'}")
    if bold_font_file:
        print(f"  粗体字体: {bold_font_file}")

    # Step 4: 处理
    print(f"\n[3] 处理 PDF...")
    doc = fitz.open(args.input)
    if len(doc) == 0:
        print("✗ PDF 无页面")
        doc.close()
        return

    process_page(doc, header_lines, contact_lines, fmt, font_file,
                 verbose=args.verbose, bold_font_file=bold_font_file)

    # 保存
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    doc.save(args.output, garbage=4, deflate=True)
    doc.close()
    print(f"\n✓ 输出: {args.output}")
    print("=" * 60)


if __name__ == '__main__':
    main()
