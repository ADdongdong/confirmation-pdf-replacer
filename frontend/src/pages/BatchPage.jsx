import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Card,
  Button,
  Input,
  Upload,
  Select,
  Space,
  Tag,
  Alert,
  Typography,
  Checkbox,
  message,
} from 'antd';
import {
  InboxOutlined,
  DownloadOutlined,
  SettingOutlined,
  DownOutlined,
} from '@ant-design/icons';
import PreviewOverlay from '../components/PreviewOverlay';
import {
  configDefault,
  configList,
  configLoad,
  configDelete,
  configSave,
  configPreview,
  batchUpload,
  batchReRecognize,
  batchGenerate,
  batchDownloadUrl,
  CONTACT_KEYS,
} from '../api';

const { TextArea } = Input;
const { Text } = Typography;

// 联系方式字段 id 映射（与旧版 upload.html 一致）
const CONTACT_FIELDS = [
  { key: '项目联系人', id: 'contact_person', placeholder: '如：张三' },
  { key: '项目联系人电话', id: 'contact_phone', placeholder: '如：13800138000' },
  { key: '收件人', id: 'recipient', placeholder: '如：开源证券投行部' },
  { key: '收件人电话', id: 'recipient_phone', placeholder: '如：0755-88888888' },
  { key: '邮箱', id: 'email', placeholder: '如：touhang@kzq.com.cn' },
  { key: '回函地址', id: 'return_address', placeholder: '如：深圳市福田中心区金田路3088号中洲大厦20层' },
];

export default function BatchPage() {
  // ---- 全局状态 ----
  const [sessionToken, setSessionToken] = useState(null);
  const [fileList, setFileList] = useState([]);
  const [configWhiteoutBottom, setConfigWhiteoutBottom] = useState(null);
  const [defaultConfigName, setDefaultConfigName] = useState(null);
  const [previewData, setPreviewData] = useState(null);
  const [forceWhiteout, setForceWhiteout] = useState(false);
  const [configFooterHeight, setConfigFooterHeight] = useState(null);
  const [footerY, setFooterY] = useState(null);
  const [autoFooterY, setAutoFooterY] = useState(null);
  const [footerForce, setFooterForce] = useState(false);

  // ---- 配置面板 UI 状态 ----
  const [panelOpen, setPanelOpen] = useState(false);
  const [noConfig, setNoConfig] = useState(false);
  const [configListData, setConfigListData] = useState([]);
  const [loadSelect, setLoadSelect] = useState('');
  const [sampleFile, setSampleFile] = useState(null);

  // ---- 模板字段 ----
  const [bodyText, setBodyText] = useState('');
  const [contacts, setContacts] = useState({});
  const [configName, setConfigName] = useState('');
  const [isDefault, setIsDefault] = useState(true);
  const [saving, setSaving] = useState(false);

  // ---- 批量上传 ----
  const [uploading, setUploading] = useState(false);
  const [recognizingId, setRecognizingId] = useState(null);
  const [recognizingAll, setRecognizingAll] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [result, setResult] = useState(null);

  const fileInputRef = useRef(null);

  // ==================== 默认配置自动加载 ====================
  const loadDefaultConfig = async () => {
    try {
      const data = await configDefault();
      if (!data.success || !data.config) {
        setDefaultConfigName(null);
        setConfigName('');
        setNoConfig(true);
        return;
      }
      const c = data.config;
      setDefaultConfigName(c.name || '(未命名)');
      setConfigName(c.name || '');
      if (c.whiteout_bottom != null) {
        setConfigWhiteoutBottom(c.whiteout_bottom);
      } else {
        setConfigWhiteoutBottom(null);
      }
      if (c.body_text) setBodyText(c.body_text);
      if (c.contacts) applyContacts(c.contacts);
      if (c.footer_height != null) setConfigFooterHeight(c.footer_height);
      setNoConfig(false);
    } catch {
      setDefaultConfigName('加载失败');
    }
    loadConfigList();
  };

  const applyContacts = (c) => {
    const next = {};
    CONTACT_FIELDS.forEach((f) => {
      next[f.key] = c[f.key] || '';
    });
    setContacts(next);
  };

  // ==================== 配置列表 / 加载 / 删除 ====================
  const loadConfigList = async () => {
    try {
      const data = await configList();
      if (data.success) {
        setConfigListData(data.configs || []);
      }
    } catch {}
  };

  const loadSelectedConfig = async (filename) => {
    if (!filename) {
      message.error('请先选择一个配置');
      return;
    }
    try {
      const data = await configLoad(filename);
      if (!data.success) {
        message.error(data.error || '加载失败');
        return;
      }
      const c = data.config;
      setBodyText(c.body_text || '');
      applyContacts(c.contacts || {});
      setConfigName(c.name || filename.replace('.json', ''));
      setIsDefault(c.is_default !== false);
      setConfigWhiteoutBottom(c.whiteout_bottom || null);
      if (c.footer_height != null) setConfigFooterHeight(c.footer_height);

      // 更新顶部信息条
      setDefaultConfigName(c.name || '(未命名)');
      // 若已加载预览，应用覆盖值
      if (c.whiteout_bottom != null && previewData) {
        setPreviewData({ ...previewData, userWhiteoutBottom: c.whiteout_bottom });
        setForceWhiteout(true);
      }
      if (c.footer_height != null && previewData) {
        setFooterY(Math.round(previewData.pageHeight - c.footer_height));
        setFooterForce(true);
      }
      message.success('已加载配置: ' + c.name);
    } catch (err) {
      message.error('加载失败: ' + err.message);
    }
  };

  const deleteSelectedConfig = async (filename) => {
    if (!filename) {
      message.error('请先选择一个配置');
      return;
    }
    const sel = configListData.find((x) => x.filename === filename);
    if (!window.confirm('确定删除配置 "' + (sel ? sel.name : filename) + '" 吗？')) return;
    try {
      const data = await configDelete(filename);
      if (data.success) {
        message.success('已删除');
        setLoadSelect('');
        loadConfigList();
      } else {
        message.error(data.error || '删除失败');
      }
    } catch (err) {
      message.error('删除失败: ' + err.message);
    }
  };

  // ==================== 保存配置 ====================
  const saveConfig = async () => {
    const name = configName.trim();
    const text = bodyText.trim();
    if (!name) {
      message.error('请输入配置名称');
      return;
    }
    if (!text) {
      message.error('请输入函证正文');
      return;
    }
    let whiteoutBottom = null;
    if (forceWhiteout && previewData) {
      whiteoutBottom = Math.round(previewData.userWhiteoutBottom);
    }
    let footerHeight = null;
    if (footerForce && previewData && footerY != null) {
      footerHeight = Math.round(previewData.pageHeight - footerY);
    } else if (configFooterHeight != null) {
      footerHeight = configFooterHeight;
    }
    setSaving(true);
    try {
      const data = await configSave({
        name,
        body_text: text,
        contacts,
        whiteout_bottom: whiteoutBottom,
        footer_height: footerHeight,
        is_default: isDefault,
      });
      if (data.success) {
        setConfigWhiteoutBottom(whiteoutBottom);
        setDefaultConfigName(name);
        setNoConfig(false);
        message.success('配置已保存: ' + name);
        loadConfigList();
      } else {
        message.error(data.error || '保存失败');
      }
    } catch (err) {
      message.error('保存失败: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // ==================== 样本 PDF 预览（覆盖区域校准） ====================
  const handleSampleSelect = async (file) => {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      message.error('请选择 PDF 格式文件');
      return;
    }
    setSampleFile(file);
    try {
      const data = await configPreview(file);
      if (!data.success) {
        message.error('预览失败: ' + (data.error || '未知错误'));
        return;
      }
      setPreviewData({ ...data, userWhiteoutBottom: data.autoWhiteoutBottom });
      setForceWhiteout(false);
      if (configFooterHeight != null) {
        setFooterY(Math.round(data.pageHeight - configFooterHeight));
      } else if (data.autoFooterY != null) {
        setFooterY(data.autoFooterY);
        setAutoFooterY(data.autoFooterY);
      }
      setFooterForce(false);
      message.success('预览生成成功，可拖动遮罩调整覆盖区域');
    } catch (err) {
      message.error('预览请求失败: ' + err.message);
    }
  };

  // ==================== 批量上传 ====================
  const handleFileSelect = (files) => {
    if (!files || files.length === 0) return;
    if (files.length > 20) {
      message.error('单次最多上传 20 个文件');
      return;
    }
    uploadFiles(files);
  };

  const uploadFiles = async (files) => {
    setUploading(true);
    message.loading({ content: '正在上传并识别收函单位...', key: 'batch_upload' });
    try {
      const data = await batchUpload(Array.from(files));
      if (!data.success) {
        message.error({ content: data.error || '上传失败', key: 'batch_upload' });
        return;
      }
      setSessionToken(data.token);
      setFileList(data.files);
      message.success({ content: `已选择 ${data.files.length} 个 PDF 文件`, key: 'batch_upload' });
    } catch (err) {
      message.error({ content: '上传失败: ' + err.message, key: 'batch_upload' });
    } finally {
      setUploading(false);
    }
  };

  // ==================== 文件操作 ====================
  const updateRecipient = (id, val) => {
    setFileList((list) => list.map((f) => (f.id === id ? { ...f, recipient: val } : f)));
  };

  const reRecognizeOne = async (id) => {
    setRecognizingId(id);
    try {
      const data = await batchReRecognize(sessionToken, id);
      if (data.success) {
        setFileList((list) =>
          list.map((f) => (f.id === id ? { ...f, recipient: data.recipient, confidence: data.confidence } : f))
        );
        message.success('识别完成');
      } else {
        message.error(data.error || '识别失败');
      }
    } catch (err) {
      message.error('请求失败: ' + err.message);
    } finally {
      setRecognizingId(null);
    }
  };

  const reRecognizeAll = async () => {
    setRecognizingAll(true);
    message.loading({ content: '正在重新识别全部文件...', key: 're_recognize' });
    try {
      const results = await Promise.all(
        fileList.map((f) => batchReRecognize(sessionToken, f.id))
      );
      setFileList((list) =>
        list.map((f, i) => {
          const r = results[i];
          return r && r.success ? { ...f, recipient: r.recipient, confidence: r.confidence } : f;
        })
      );
      message.success({ content: '全部重新识别完成', key: 're_recognize' });
    } catch {
      message.error({ content: '部分识别请求失败', key: 're_recognize' });
    } finally {
      setRecognizingAll(false);
    }
  };

  const removeFile = (id) => {
    setFileList((list) => {
      const next = list.filter((f) => f.id !== id);
      if (next.length === 0) setSessionToken(null);
      return next;
    });
  };

  const clearFiles = () => {
    setFileList([]);
    setSessionToken(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
    setResult(null);
  };

  // ==================== 批量生成 ====================
  const submitBatch = async () => {
    if (fileList.length === 0) {
      message.error('请先上传 PDF 文件');
      return;
    }
    const text = bodyText.trim();
    if (!text) {
      message.error('请输入函证正文');
      return;
    }
    const recipients = {};
    let emptyCount = 0;
    fileList.forEach((f) => {
      recipients[f.id] = (f.recipient || '').trim();
      if (!recipients[f.id]) emptyCount++;
    });
    if (emptyCount > 0) {
      message.error(`有 ${emptyCount} 个文件的收函单位为空，请补充`);
      return;
    }

    // 覆盖下界：优先使用实时预览拖拽值，否则使用配置中的值
    let whiteoutBottom = configWhiteoutBottom;
    if (forceWhiteout && previewData && previewData.userWhiteoutBottom != null) {
      whiteoutBottom = Math.round(previewData.userWhiteoutBottom);
    }

    // 页脚遮盖高度
    let footerHeight = configFooterHeight;
    if (footerForce && previewData && footerY != null) {
      footerHeight = Math.round(previewData.pageHeight - footerY);
    }

    setGenerating(true);
    setResult(null);
    try {
      const data = await batchGenerate({
        token: sessionToken,
        body_text: text,
        contacts,
        recipients,
        whiteout_bottom: whiteoutBottom,
        footer_height: footerHeight,
      });
      if (data.success) {
        setResult(data);
      } else {
        message.error(data.error || '生成失败');
      }
    } catch (err) {
      message.error('请求失败: ' + err.message);
    } finally {
      setGenerating(false);
    }
  };

  useEffect(() => {
    loadDefaultConfig();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 统计
  const cntHigh = useMemo(() => fileList.filter((f) => (f.confidence || 'low') !== 'low').length, [fileList]);
  const cntLow = fileList.length - cntHigh;

  const selectedConfig = configListData.find((x) => x.filename === loadSelect);

  return (
    <div className="page-container">
      {/* ===== 配置信息条（可折叠） ===== */}
      <Card
        style={{ padding: 0, marginBottom: 20, overflow: 'hidden' }}
        styles={{ body: { padding: 0 } }}
      >
        <div
          className="config-bar-inline"
          onClick={() => setPanelOpen(!panelOpen)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '10px 16px',
            background: noConfig ? '#fff5f5' : '#f7fafc',
            borderBottom: panelOpen ? '1px solid #e2e8f0' : 'none',
            cursor: 'pointer',
          }}
        >
          <span>📄 当前配置：</span>
          <Text strong style={{ color: '#1a3a5c' }}>
            {defaultConfigName || '未设置默认配置'}
          </Text>
          {configWhiteoutBottom != null ? (
            <Tag color="volcano">覆盖下界: {configWhiteoutBottom} pt</Tag>
          ) : (
            <Tag color="blue">覆盖: 自动检测</Tag>
          )}
          {!noConfig && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              首次使用请展开检查配置内容
            </Text>
          )}
          <Button
            type="link"
            size="small"
            icon={<SettingOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              setPanelOpen(!panelOpen);
            }}
          >
            修改配置
          </Button>
          <DownOutlined
            style={{ marginLeft: 'auto', transform: panelOpen ? 'rotate(180deg)' : 'none', transition: 'all 0.25s' }}
          />
        </div>

        {panelOpen && (
          <div style={{ padding: '0 16px 16px' }}>
            {noConfig && (
              <Alert
                type="warning"
                showIcon
                style={{ margin: '12px 0' }}
                message="⚠ 尚未配置默认模板，请保存默认配置。"
              />
            )}

            {/* 子区域 1: 覆盖区域校准 */}
            <div style={{ marginTop: 12 }}>
              <Text strong>覆盖区域校准</Text>
              <Text type="secondary" style={{ display: 'block', fontSize: 12, margin: '6px 0' }}>
                上传一份代表性格式的会所函证 PDF，拖动蓝色遮罩下沿调整覆盖范围。绿线为自动检测的表格保护线。
              </Text>
              <Upload.Dragger
                accept=".pdf"
                maxCount={1}
                beforeUpload={(file) => {
                  handleSampleSelect(file);
                  return false;
                }}
                fileList={
                  sampleFile
                    ? [{ uid: '-1', name: sampleFile.name, status: 'done' }]
                    : []
                }
                style={{ padding: '12px' }}
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">点击选择样本会所函证 PDF</p>
                <p className="ant-upload-hint">用于校准覆盖区域，不参与最终生成</p>
              </Upload.Dragger>
            </div>

            {previewData && (
              <PreviewOverlay
                imageUrl={previewData.imageUrl}
                pageHeight={previewData.pageHeight}
                tableY={previewData.tableY}
                unit="pt"
                autoValue={previewData.autoWhiteoutBottom}
                value={previewData.userWhiteoutBottom}
                force={forceWhiteout}
                onValueChange={(v) => {
                  setPreviewData({ ...previewData, userWhiteoutBottom: v });
                  setForceWhiteout(true);
                }}
                onForceChange={setForceWhiteout}
                onReset={() => {
                  setPreviewData({ ...previewData, userWhiteoutBottom: previewData.autoWhiteoutBottom });
                  setForceWhiteout(false);
                }}
                autoFooterY={autoFooterY}
                footerY={footerY}
                footerForce={footerForce}
                onFooterChange={(y) => {
                  setFooterY(y);
                  setFooterForce(true);
                }}
                onFooterForceChange={setFooterForce}
                onFooterReset={() => {
                  if (autoFooterY != null) setFooterY(autoFooterY);
                  setFooterForce(false);
                }}
              />
            )}

            <Alert
              type="info"
              showIcon
              style={{ marginTop: 10 }}
              message={'提示：若"使用自动值"，批量处理时每个 PDF 各自检测表格位置（更安全）；若"强制此值"，所有 PDF 统一使用此覆盖下界。'}
            />

            <div style={{ borderTop: '1px solid #e2e8f0', margin: '16px 0' }} />

            {/* 子区域 2: 模板内容 */}
            <Text strong>模板内容</Text>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '12px 0', padding: '10px 14px', background: '#f7fafc', borderRadius: 8 }}>
              <Text type="secondary">加载已有配置</Text>
              <Select
                style={{ flex: 1 }}
                placeholder="-- 请选择 --"
                value={loadSelect || undefined}
                onChange={(v) => {
                  setLoadSelect(v);
                  if (v) loadSelectedConfig(v);
                }}
                options={configListData.map((c) => ({
                  value: c.filename,
                  label: c.name + (c.has_whiteout ? ' · 已校准' : ''),
                }))}
              />
              <Space>
                <Button
                  size="small"
                  onClick={() => loadSelectedConfig(loadSelect)}
                >
                  加载
                </Button>
                <Button
                  size="small"
                  danger
                  onClick={() => deleteSelectedConfig(loadSelect)}
                >
                  删除
                </Button>
              </Space>
            </div>

            <div style={{ marginBottom: 12 }}>
              <Text strong>
                函证正文 <Text type="danger">*</Text>
              </Text>
              <TextArea
                rows={4}
                maxLength={300}
                showCount
                value={bodyText}
                onChange={(e) => setBodyText(e.target.value)}
                placeholder="请输入函证正文内容，如：本公司聘请的XX证券股份有限公司正在对本公司进行尽职调查..."
              />
            </div>

            <Text strong>联系方式</Text>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 10 }}>
              {CONTACT_FIELDS.slice(0, 4).map((f) => (
                <div key={f.id}>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    {f.key}
                  </Text>
                  <Input
                    value={contacts[f.key] || ''}
                    onChange={(e) => setContacts({ ...contacts, [f.key]: e.target.value })}
                    placeholder={f.placeholder}
                    maxLength={f.key.includes('邮箱') ? 60 : 30}
                  />
                </div>
              ))}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
              <div>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  邮箱
                </Text>
                <Input
                  value={contacts['邮箱'] || ''}
                  onChange={(e) => setContacts({ ...contacts, 邮箱: e.target.value })}
                  placeholder="如：touhang@kzq.com.cn"
                  maxLength={60}
                />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  回函地址
                </Text>
                <Input
                  value={contacts['回函地址'] || ''}
                  onChange={(e) => setContacts({ ...contacts, 回函地址: e.target.value })}
                  placeholder="如：深圳市福田中心区金田路3088号中洲大厦20层"
                  maxLength={120}
                />
              </div>
            </div>

            <div style={{ marginTop: 12 }}>
              <Text strong>
                配置名称 <Text type="danger">*</Text>
              </Text>
              <Input
                value={configName}
                onChange={(e) => setConfigName(e.target.value)}
                placeholder="如：开源证券-默认模板"
                maxLength={40}
                style={{ width: '50%', minWidth: 240, marginTop: 6 }}
              />
            </div>

            <Checkbox
              checked={isDefault}
              onChange={(e) => setIsDefault(e.target.checked)}
              style={{ marginTop: 12 }}
            >
              设为默认配置（批量页面自动加载）
            </Checkbox>

            <Button
              type="primary"
              block
              size="large"
              style={{ marginTop: 14, background: 'linear-gradient(135deg,#48bb78,#38a169)', borderColor: '#38a169' }}
              loading={saving}
              onClick={saveConfig}
            >
              💾 保存配置
            </Button>
          </div>
        )}
      </Card>

      {/* ===== 上传会所函证 PDF（主要操作区） ===== */}
      <Card
        title="上传会所函证 PDF（可多选）"
        styles={{ header: { borderBottom: '2px solid #f0f2f5' } }}
      >
        <Upload.Dragger
          multiple
          accept=".pdf"
          beforeUpload={(file, fileList) => {
            handleFileSelect(fileList);
            return false;
          }}
          fileList={[]}
          disabled={uploading}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击选择多个会所函证 PDF 文件</p>
          <p className="ant-upload-hint">支持多选 .pdf 文件，单次最多 20 个</p>
        </Upload.Dragger>

        {fileList.length > 0 && (
          <div style={{ marginTop: 18 }}>
            <Text strong>
              已上传 {fileList.length} 个文件
            </Text>
            <div style={{ marginTop: 10 }}>
              {fileList.map((f) => {
                const rawConfidence = f.confidence || 'low';
                const isHigh = rawConfidence !== 'low';
                return (
                  <div key={f.id} className="file-item">
                    <div className="file-thumb">
                      {f.previewUrl ? (
                        <img src={f.previewUrl} alt="预览" />
                      ) : (
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#cbd5e0', fontSize: 24 }}>
                          📄
                        </div>
                      )}
                    </div>
                    <div className="file-info">
                      <div className="file-name" title={f.name}>
                        {f.name}
                      </div>
                      <div className="recipient-row">
                        <label>收函单位：</label>
                        <Input
                          className={isHigh ? 'high' : 'low'}
                          value={f.recipient || ''}
                          onChange={(e) => updateRecipient(f.id, e.target.value)}
                          placeholder={!isHigh ? '请手动输入收函单位' : ''}
                        />
                        <span className={'confidence-badge ' + (isHigh ? 'high' : 'low')}>
                          {isHigh ? '已识别 ✅' : '需确认 ⚠'}
                        </span>
                      </div>
                    </div>
                    <div className="file-actions" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <Button size="small" loading={recognizingId === f.id} onClick={() => reRecognizeOne(f.id)}>
                        🔄 重新识别
                      </Button>
                      <Button size="small" danger onClick={() => removeFile(f.id)}>
                        ✕ 移除
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="list-actions" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 14, paddingTop: 14, borderTop: '1px solid #edf2f7' }}>
              <Text type="secondary">
                <span style={{ color: '#48bb78' }}>●</span> 已识别 {cntHigh}
                &nbsp;&nbsp;
                <span style={{ color: '#e53e3e' }}>●</span> 需确认 {cntLow}
              </Text>
              <Space>
                <Button size="small" loading={recognizingAll} onClick={reRecognizeAll}>
                  🔄 全部重新识别
                </Button>
                <Button size="small" danger onClick={clearFiles}>
                  清除全部
                </Button>
              </Space>
            </div>
          </div>
        )}
      </Card>

      {/* ===== 提交 ===== */}
      <Button
        type="primary"
        size="large"
        block
        style={{ marginTop: 20, height: 48, fontSize: 15 }}
        disabled={fileList.length === 0 || generating}
        loading={generating}
        onClick={submitBatch}
      >
        {fileList.length > 0 ? `全部生成（${fileList.length} 个文件）` : '请先上传 PDF 文件'}
      </Button>

      {/* ===== 结果 ===== */}
      {result && (
        <Card className="result-card" style={{ textAlign: 'center', marginTop: 20 }}>
          <div style={{ fontSize: 48, color: '#48bb78' }}>✅</div>
          <Text strong style={{ fontSize: 16, color: '#22543d', display: 'block', marginTop: 8 }}>
            批量生成完成
          </Text>
          <Text type="secondary" style={{ display: 'block', marginTop: 6 }}>
            成功处理 {result.total} 个文件
          </Text>
          <Button
            type="primary"
            size="large"
            style={{ marginTop: 16, background: '#48bb78', borderColor: '#48bb78' }}
            icon={<DownloadOutlined />}
            href={batchDownloadUrl(result.downloadToken)}
          >
            下载 ZIP
          </Button>
          {result.errors && result.errors.length > 0 && (
            <Alert
              type="error"
              style={{ marginTop: 14, textAlign: 'left' }}
              message="以下文件处理异常："
              description={
                <div>
                  {result.errors.map((e, i) => (
                    <div key={i} className="err-item">
                      • {e.file}：{e.error}
                    </div>
                  ))}
                </div>
              }
            />
          )}
        </Card>
      )}
    </div>
  );
}
