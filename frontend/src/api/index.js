import http from './client';

// ==================== 单文件处理 ====================

/** 上传 PDF 获取预览图（单文件页 /） */
export function fetchPreview(pdfFile) {
  const fd = new FormData();
  fd.append('pdf_file', pdfFile);
  return http.post('/preview', fd);
}

/** 单文件生成 */
export function generateSingle(payload) {
  const fd = new FormData();
  fd.append('company_name', payload.company_name);
  fd.append('body_text', payload.body_text);
  fd.append('contact_person', payload.contact_person || '');
  fd.append('contact_phone', payload.contact_phone || '');
  fd.append('recipient', payload.recipient || '');
  fd.append('recipient_phone', payload.recipient_phone || '');
  fd.append('email', payload.email || '');
  fd.append('return_address', payload.return_address || '');
  fd.append('pdf_file', payload.pdf_file);
  if (payload.whiteout_bottom != null) {
    fd.append('whiteout_bottom', payload.whiteout_bottom);
  }
  return http.post('/generate', fd);
}

export function downloadUrl(filename) {
  return '/download/' + encodeURIComponent(filename);
}

// ==================== 批量处理 ====================

/** 批量上传 PDF，返回 token 与文件列表 */
export function batchUpload(pdfFiles) {
  const fd = new FormData();
  pdfFiles.forEach((f) => fd.append('pdf_files', f));
  return http.post('/batch/upload', fd);
}

/** 单个文件重新识别收函单位 */
export function batchReRecognize(token, fileId) {
  return http.post('/batch/re-recognize', { token, fileId });
}

/** 批量生成 */
export function batchGenerate(payload) {
  return http.post('/batch/generate', {
    token: payload.token,
    body_text: payload.body_text,
    contacts: payload.contacts,
    recipients: payload.recipients,
    whiteout_bottom: payload.whiteout_bottom,
    footer_height: payload.footer_height,
  });
}

export function batchDownloadUrl(token) {
  return '/batch/download/' + encodeURIComponent(token);
}

// ==================== 配置管理 ====================

/** 上传样本 PDF 进行覆盖区域校准（/config/preview） */
export function configPreview(pdfFile) {
  const fd = new FormData();
  fd.append('pdf_file', pdfFile);
  return http.post('/config/preview', fd);
}

/** 配置列表 */
export function configList() {
  return http.get('/config/list');
}

/** 加载配置 */
export function configLoad(filename) {
  return http.get('/config/load/' + encodeURIComponent(filename));
}

/** 删除配置 */
export function configDelete(filename) {
  return http.delete('/config/delete/' + encodeURIComponent(filename));
}

/** 保存配置 */
export function configSave(payload) {
  return http.post('/config/save', {
    name: payload.name,
    body_text: payload.body_text,
    contacts: payload.contacts,
    whiteout_bottom: payload.whiteout_bottom,
    footer_height: payload.footer_height,
    is_default: payload.is_default,
  });
}

/** 获取默认配置 */
export function configDefault() {
  return http.get('/config/default');
}

// 联系方式字段（与后端约定一致，键名为中文）
export const CONTACT_KEYS = ['项目联系人', '项目联系人电话', '收件人', '收件人电话', '邮箱'];
