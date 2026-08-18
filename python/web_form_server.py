"""
函证头部替换 Web 表单服务
=========================
提供网页表单让用户输入函证信息，生成替换后的 PDF。

启动方式：
  python web_form_server.py
  然后浏览器访问 http://localhost:8888

依赖：
  pip install flask
"""

import sys
import os
import json
import uuid
import tempfile
import traceback
from datetime import datetime

from flask import Flask, render_template, request, send_file, jsonify

# 将当前目录加入路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 32 * 1024 * 1024  # 最大 32MB 上传

# 输出目录
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'outputs')
TEMP_DIR = tempfile.mkdtemp(prefix='hanzheng_')
# 预览图片目录（复用 TEMP_DIR）
PREVIEW_DIR = TEMP_DIR


@app.route('/')
def index():
    """表单页"""
    return render_template('index.html')


@app.route('/preview', methods=['POST'])
def preview():
    """上传 PDF 后渲染首页为 PNG 预览图"""
    try:
        pdf_file = request.files.get('pdf_file')
        if not pdf_file or not pdf_file.filename:
            return jsonify({'success': False, 'error': '未收到 PDF 文件'})

        # 保存临时文件
        import fitz  # PyMuPDF
        uid = uuid.uuid4().hex[:8]
        tmp_pdf = os.path.join(TEMP_DIR, f'preview_{uid}.pdf')
        pdf_file.save(tmp_pdf)

        try:
            doc = fitz.open(tmp_pdf)
            if len(doc) == 0:
                return jsonify({'success': False, 'error': 'PDF 无页面'})

            page = doc[0]
            page_w = page.rect.width
            page_h = page.rect.height

            # 渲染首页为 PNG (150 DPI ≈ 2.08x zoom)
            mat = fitz.Matrix(2.08, 2.08)
            pix = page.get_pixmap(matrix=mat)
            img_path = os.path.join(TEMP_DIR, f'preview_{uid}.png')
            pix.save(img_path)

            doc.close()

            return jsonify({
                'success': True,
                'imageUrl': f'/preview-img/preview_{uid}.png',
                'pageWidth': round(page_w, 1),
                'pageHeight': round(page_h, 1),
                'imageWidth': pix.width,
                'imageHeight': pix.height,
            })

        finally:
            # 清理临时 PDF
            try:
                os.remove(tmp_pdf)
            except Exception:
                pass

    except Exception as e:
        traceback.print_exc()
        return jsonify({'success': False, 'error': f'预览生成失败: {str(e)}'})


@app.route('/preview-img/<filename>')
def preview_img(filename):
    """提供预览图片静态文件服务"""
    filepath = os.path.join(TEMP_DIR, os.path.basename(filename))
    if not os.path.exists(filepath):
        return "图片不存在或已过期", 404
    return send_file(filepath, mimetype='image/png')


@app.route('/generate', methods=['POST'])
def generate():
    """接收表单数据，生成 PDF"""
    try:
        # ---- 1. 解析表单数据 ----
        company_name = request.form.get('company_name', '').strip()
        body_text = request.form.get('body_text', '').strip()
        contacts = {
            '项目联系人': request.form.get('contact_person', '').strip(),
            '项目联系人电话': request.form.get('contact_phone', '').strip(),
            '收件人': request.form.get('recipient', '').strip(),
            '收件人电话': request.form.get('recipient_phone', '').strip(),
            '邮箱': request.form.get('email', '').strip(),
        }
        # 用户自定义白化下界（来自前端红色遮罩拖拽，单位 pt）
        whiteout_bottom = request.form.get('whiteout_bottom', '').strip()
        if whiteout_bottom:
            try:
                whiteout_bottom = float(whiteout_bottom)
            except ValueError:
                whiteout_bottom = None
        else:
            whiteout_bottom = None

        # ---- 2. 验证必填字段 ----
        errors = []
        if not company_name:
            errors.append('请输入致函单位')
        if not body_text:
            errors.append('请输入函证正文')
        if len(body_text) > 250:
            errors.append(f'正文内容超过250字符限制（当前 {len(body_text)} 字符）')

        if errors:
            return jsonify({'success': False, 'errors': errors})

        # ---- 3. 处理上传的 PDF ----
        pdf_file = request.files.get('pdf_file')
        if not pdf_file or not pdf_file.filename:
            return jsonify({'success': False, 'errors': ['请上传会所函证 PDF 文件']})

        if not pdf_file.filename.lower().endswith('.pdf'):
            return jsonify({'success': False, 'errors': ['仅支持 PDF 格式文件']})

        # 保存上传文件到临时目录
        input_pdf = os.path.join(TEMP_DIR, f"input_{uuid.uuid4().hex[:8]}.pdf")
        pdf_file.save(input_pdf)

        # ---- 4. 调用 v4 处理 ----
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        safe_company = "".join(c if c.isalnum() or c in '_- ' else '_' for c in company_name)[:30]
        output_pdf = os.path.join(OUTPUT_DIR, f'hanzheng_{safe_company}_{timestamp}.pdf')
        os.makedirs(OUTPUT_DIR, exist_ok=True)

        from replace_header_v4 import process_from_data
        success, result = process_from_data(
            input_pdf, output_pdf, company_name, body_text, contacts,
            whiteout_bottom=whiteout_bottom, verbose=False
        )

        # 清理上传临时文件
        try:
            os.remove(input_pdf)
        except Exception:
            pass

        if not success:
            return jsonify({'success': False, 'errors': [result]})

        return jsonify({
            'success': True,
            'filename': os.path.basename(output_pdf),
            'path': output_pdf,
            'company_name': company_name,
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({'success': False, 'errors': [f'服务器内部错误: {str(e)}']})


@app.route('/download/<filename>')
def download(filename):
    """下载生成的 PDF"""
    filepath = os.path.join(OUTPUT_DIR, os.path.basename(filename))
    if not os.path.exists(filepath):
        return "文件不存在或已过期", 404
    return send_file(filepath, as_attachment=True, download_name=filename,
                     mimetype='application/pdf')


# ============================================================
# 批量处理 API
# ============================================================

# 批量任务临时存储（服务重启后丢失，生产环境可换持久化）
_batch_sessions = {}  # token -> { files: [{name, path, recipient, confidence}], status, template }

# 配置持久化目录
CONFIG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'configs')
os.makedirs(CONFIG_DIR, exist_ok=True)
_DEFAULT_CONFIG_FILE = os.path.join(CONFIG_DIR, '_default.txt')  # 只存默认配置名


@app.route('/batch')
def batch_page():
    """批量处理页面（旧路径，重定向到新的 upload 页面）"""
    from flask import redirect
    return redirect('/upload', code=301)


@app.route('/upload')
def upload_page():
    """PDF 格式转函主页面（自动加载默认配置 + 上传生成）"""
    return render_template('upload.html')


@app.route('/batch/upload', methods=['POST'])
def batch_upload():
    """批量上传 PDF，自动提取每份的收函单位"""
    from replace_header_v4 import extract_recipient

    files = request.files.getlist('pdf_files')
    if not files or len(files) == 0:
        return jsonify({'success': False, 'error': '未收到 PDF 文件'})

    if len(files) > 20:
        return jsonify({'success': False, 'error': '单次最多上传 20 个文件'})

    token = uuid.uuid4().hex[:12]
    file_list = []

    for f in files:
        if not f.filename or not f.filename.lower().endswith('.pdf'):
            continue

        uid = uuid.uuid4().hex[:8]
        tmp_path = os.path.join(TEMP_DIR, f'batch_{token}_{uid}.pdf')
        f.save(tmp_path)

        # 自动提取收函单位
        try:
            recipient, confidence = extract_recipient(tmp_path)
        except Exception:
            recipient, confidence = '', 'low'

        file_list.append({
            'id': uid,
            'name': f.filename,
            'path': tmp_path,
            'recipient': recipient,
            'confidence': confidence,
            'preview_id': None,
        })

    if not file_list:
        return jsonify({'success': False, 'error': '没有有效的 PDF 文件'})

    _batch_sessions[token] = {
        'files': file_list,
        'status': 'uploaded',
    }

    # 为每个文件生成预览图
    import fitz
    for item in file_list:
        try:
            doc = fitz.open(item['path'])
            if len(doc) > 0:
                page = doc[0]
                mat = fitz.Matrix(1.0, 1.0)  # 72 DPI 缩略图，节省带宽
                pix = page.get_pixmap(matrix=mat)
                png_path = os.path.join(TEMP_DIR, f'thumb_{token}_{item["id"]}.png')
                pix.save(png_path)
                item['preview_id'] = f'thumb_{token}_{item["id"]}.png'
            doc.close()
        except Exception:
            pass

    return jsonify({
        'success': True,
        'token': token,
        'files': [{
            'id': f['id'],
            'name': f['name'],
            'recipient': f['recipient'],
            'confidence': f['confidence'],
            'previewUrl': f'/batch/preview/{token}/{f["preview_id"]}' if f['preview_id'] else None,
        } for f in file_list],
    })


@app.route('/batch/preview/<token>/<filename>')
def batch_preview_img(token, filename):
    """批量文件缩略图"""
    filepath = os.path.join(TEMP_DIR, os.path.basename(filename))
    if not os.path.exists(filepath):
        return "图片不存在", 404
    return send_file(filepath, mimetype='image/png')


@app.route('/batch/re-recognize', methods=['POST'])
def batch_re_recognize():
    """重新识别指定文件的收函单位"""
    from replace_header_v4 import extract_recipient

    data = request.get_json()
    token = data.get('token', '')
    file_id = data.get('fileId', '')

    session = _batch_sessions.get(token)
    if not session:
        return jsonify({'success': False, 'error': '会话已过期'})

    target = None
    for f in session['files']:
        if f['id'] == file_id:
            target = f
            break

    if not target:
        return jsonify({'success': False, 'error': '文件不存在'})

    try:
        recipient, confidence = extract_recipient(target['path'])
        target['recipient'] = recipient
        target['confidence'] = confidence
        return jsonify({'success': True, 'recipient': recipient, 'confidence': confidence})
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)})


@app.route('/batch/generate', methods=['POST'])
def batch_generate():
    """批量生成：用模板 + 各收函单位重写头部"""
    from replace_header_v4 import process_from_data

    data = request.get_json()
    token = data.get('token', '')

    session = _batch_sessions.get(token)
    if not session:
        return jsonify({'success': False, 'error': '会话已过期'})

    # 模板内容
    body_text = data.get('body_text', '').strip()
    contacts = data.get('contacts', {})
    whiteout_bottom = data.get('whiteout_bottom')  # None=自动检测, float=强制覆盖下界

    if not body_text:
        return jsonify({'success': False, 'error': '请输入函证正文'})

    # 收函单位覆盖（用户在前端手动修改后的值）
    recipient_overrides = data.get('recipients', {})  # {fileId: recipientName}

    # 输出目录
    batch_out = os.path.join(TEMP_DIR, f'batch_out_{token}')
    os.makedirs(batch_out, exist_ok=True)

    results = []
    errors = []

    for f in session['files']:
        # 优先用前端手动输入的收函单位，否则用自动提取的
        recipient = recipient_overrides.get(f['id'], '').strip()
        if not recipient:
            recipient = f.get('recipient', '').strip()

        if not recipient:
            errors.append({'file': f['name'], 'error': '收函单位为空'})
            continue

        base_name = os.path.splitext(f['name'])[0]
        safe_name = "".join(c if c.isalnum() or c in '_- ' else '_' for c in base_name)[:40]
        out_pdf = os.path.join(batch_out, f'fixed_{f["id"]}_{safe_name}.pdf')

        try:
            success, result = process_from_data(
                f['path'], out_pdf, recipient, body_text, contacts,
                whiteout_bottom=whiteout_bottom,
                verbose=False
            )
            if success:
                results.append({'name': f['name'], 'file': os.path.basename(out_pdf), 'recipient': recipient})
            else:
                errors.append({'file': f['name'], 'error': result})
        except Exception as e:
            errors.append({'file': f['name'], 'error': str(e)})

    if not results:
        return jsonify({'success': False, 'error': '所有文件处理失败', 'errors': errors})

    # 打包 ZIP
    import zipfile
    zip_name = f'hanzheng_batch_{token}.zip'
    zip_path = os.path.join(TEMP_DIR, zip_name)
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for r in results:
            zf.write(os.path.join(batch_out, r['file']), r['file'])

    session['status'] = 'done'
    session['zip_path'] = zip_path
    session['zip_name'] = zip_name

    return jsonify({
        'success': True,
        'downloadToken': token,
        'total': len(results),
        'errors': errors if errors else None,
    })


@app.route('/batch/download/<token>')
def batch_download(token):
    """下载批量生成的 ZIP"""
    session = _batch_sessions.get(token)
    if not session or not session.get('zip_path'):
        return "文件不存在或已过期", 404

    return send_file(session['zip_path'], as_attachment=True,
                     download_name=session.get('zip_name', f'hanzheng_batch_{token}.zip'),
                     mimetype='application/zip')


# ============================================================
# 配置管理 API（持久化模板 + 覆盖区域）
# ============================================================

@app.route('/config')
def config_page():
    """配置管理页面"""
    return render_template('config.html')


@app.route('/config/preview', methods=['POST'])
def config_preview():
    """上传样本 PDF，返回预览图 + 自动检测的表格起始 y 坐标"""
    try:
        pdf_file = request.files.get('pdf_file')
        if not pdf_file or not pdf_file.filename:
            return jsonify({'success': False, 'error': '未收到 PDF 文件'})

        import fitz
        uid = uuid.uuid4().hex[:8]
        tmp_pdf = os.path.join(TEMP_DIR, f'cfg_preview_{uid}.pdf')
        pdf_file.save(tmp_pdf)

        try:
            from replace_header_v4 import extract_format, _find_table_start
            from collections import defaultdict
            import pdfplumber

            doc = fitz.open(tmp_pdf)
            if len(doc) == 0:
                return jsonify({'success': False, 'error': 'PDF 无页面'})

            page = doc[0]
            page_w = page.rect.width
            page_h = page.rect.height

            # 渲染首页为 PNG
            mat = fitz.Matrix(2.08, 2.08)
            pix = page.get_pixmap(matrix=mat)
            img_path = os.path.join(TEMP_DIR, f'cfg_preview_{uid}.png')
            pix.save(img_path)
            doc.close()

            # 自动检测表格起始行（whiteout 不应低过此值）
            table_y = page_h  # fallback
            try:
                with pdfplumber.open(tmp_pdf) as pdf:
                    pg = pdf.pages[0]
                    chars = pg.chars
                    line_groups = defaultdict(list)
                    for c in chars:
                        line_groups[round(c['top'] / 3) * 3].append(c)
                    sorted_ys = sorted(line_groups.keys())
                    table_y = _find_table_start(line_groups, sorted_ys)
            except Exception:
                pass

            # 推荐的默认白化下界（table_y - 3，即表格行上方）
            auto_whiteout = max(48, min(table_y - 3, page_h * 0.55))

            return jsonify({
                'success': True,
                'imageUrl': f'/preview-img/cfg_preview_{uid}.png',
                'pageWidth': round(page_w, 1),
                'pageHeight': round(page_h, 1),
                'imageWidth': pix.width,
                'imageHeight': pix.height,
                'tableY': round(table_y, 1),
                'autoWhiteoutBottom': round(auto_whiteout, 1),
            })

        finally:
            try:
                os.remove(tmp_pdf)
            except Exception:
                pass

    except Exception as e:
        traceback.print_exc()
        return jsonify({'success': False, 'error': f'预览生成失败: {str(e)}'})


@app.route('/config/save', methods=['POST'])
def config_save():
    """保存配置（JSON 文件持久化到 configs/ 目录）"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'success': False, 'error': '请求体为空'})

        name = data.get('name', '').strip()
        if not name:
            return jsonify({'success': False, 'error': '请输入配置名称'})

        # 清洗文件名
        safe_name = "".join(c if c.isalnum() or c in '_- ' else '_' for c in name)[:40]
        config_path = os.path.join(CONFIG_DIR, f'{safe_name}.json')

        config = {
            'name': name,
            'body_text': data.get('body_text', '').strip(),
            'contacts': {
                '项目联系人': data.get('contacts', {}).get('项目联系人', '').strip(),
                '项目联系人电话': data.get('contacts', {}).get('项目联系人电话', '').strip(),
                '收件人': data.get('contacts', {}).get('收件人', '').strip(),
                '收件人电话': data.get('contacts', {}).get('收件人电话', '').strip(),
                '邮箱': data.get('contacts', {}).get('邮箱', '').strip(),
            },
            'whiteout_bottom': data.get('whiteout_bottom'),  # None=自动检测
        }

        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(config, f, ensure_ascii=False, indent=2)

        # 设为默认
        is_default = data.get('is_default', False)
        if is_default:
            with open(_DEFAULT_CONFIG_FILE, 'w', encoding='utf-8') as f:
                f.write(safe_name)

        return jsonify({'success': True, 'filename': f'{safe_name}.json'})

    except Exception as e:
        traceback.print_exc()
        return jsonify({'success': False, 'error': f'保存失败: {str(e)}'})


@app.route('/config/list')
def config_list():
    """列出所有已保存的配置"""
    configs = []
    if os.path.isdir(CONFIG_DIR):
        for fname in sorted(os.listdir(CONFIG_DIR), reverse=True):
            if not fname.endswith('.json'):
                continue
            fpath = os.path.join(CONFIG_DIR, fname)
            try:
                with open(fpath, 'r', encoding='utf-8') as f:
                    cfg = json.load(f)
                configs.append({
                    'filename': fname,
                    'name': cfg.get('name', fname[:-5]),
                    'body_text': cfg.get('body_text', '')[:80],
                    'has_whiteout': cfg.get('whiteout_bottom') is not None,
                })
            except Exception:
                configs.append({
                    'filename': fname,
                    'name': fname[:-5],
                    'body_text': '',
                    'has_whiteout': False,
                })
    return jsonify({'success': True, 'configs': configs})


@app.route('/config/load/<filename>')
def config_load(filename):
    """加载指定配置"""
    safe_name = os.path.basename(filename)
    if not safe_name.endswith('.json'):
        safe_name += '.json'
    fpath = os.path.join(CONFIG_DIR, safe_name)

    if not os.path.exists(fpath):
        return jsonify({'success': False, 'error': '配置不存在'}), 404

    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            cfg = json.load(f)
        return jsonify({'success': True, 'config': cfg})
    except Exception as e:
        return jsonify({'success': False, 'error': f'加载失败: {str(e)}'})


@app.route('/config/delete/<filename>', methods=['DELETE'])
def config_delete(filename):
    """删除指定配置"""
    safe_name = os.path.basename(filename)
    if not safe_name.endswith('.json'):
        safe_name += '.json'
    fpath = os.path.join(CONFIG_DIR, safe_name)

    if not os.path.exists(fpath):
        return jsonify({'success': False, 'error': '配置不存在'}), 404

    try:
        os.remove(fpath)
        # 如果删的是默认配置，清除默认
        if os.path.exists(_DEFAULT_CONFIG_FILE):
            with open(_DEFAULT_CONFIG_FILE, 'r', encoding='utf-8') as f:
                default_name = f.read().strip()
            if default_name == safe_name[:-5]:
                os.remove(_DEFAULT_CONFIG_FILE)
        return jsonify({'success': True})
    except Exception as e:
        return jsonify({'success': False, 'error': f'删除失败: {str(e)}'})


@app.route('/config/default')
def config_default():
    """获取默认配置（供批量页面自动加载）"""
    default_name = None
    if os.path.exists(_DEFAULT_CONFIG_FILE):
        with open(_DEFAULT_CONFIG_FILE, 'r', encoding='utf-8') as f:
            default_name = f.read().strip()

    if default_name:
        fpath = os.path.join(CONFIG_DIR, f'{default_name}.json')
        if os.path.exists(fpath):
            try:
                with open(fpath, 'r', encoding='utf-8') as f:
                    cfg = json.load(f)
                return jsonify({'success': True, 'config': cfg})
            except Exception:
                pass

    # 没有默认配置时，返回第一个配置（如果有的话）
    if os.path.isdir(CONFIG_DIR):
        json_files = sorted([f for f in os.listdir(CONFIG_DIR) if f.endswith('.json')])
        if json_files:
            try:
                with open(os.path.join(CONFIG_DIR, json_files[0]), 'r', encoding='utf-8') as f:
                    cfg = json.load(f)
                return jsonify({'success': True, 'config': cfg})
            except Exception:
                pass

    return jsonify({'success': False, 'error': '暂无默认配置'})


if __name__ == '__main__':
    import json as json_module  # 确保 json 可用
    print("=" * 50)
    print("  函证头部替换 Web 表单服务")
    print("=" * 50)
    print("")
    print("  访问: http://localhost:8888")
    print("  批量处理: http://localhost:8888/batch")
    print("  配置管理: http://localhost:8888/config")
    print("  按 Ctrl+C 停止服务")
    print("")

    os.makedirs('templates', exist_ok=True)
    app.run(host='0.0.0.0', port=8888, debug=False, threaded=True)
