import React, { useEffect, useState, useCallback } from 'react';
import { Select, Input, Button, Tag, Space, message } from 'antd';
import { CheckCircleOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  fetchConfirmationList,
  fetchConfirmationByCode,
  bindConfirmationCode,
} from '../api/confirmation';

/**
 * 单个 PDF 文件的函证系统绑定组件
 *
 * 三种状态自动切换：
 *  A) 唯一匹配 (matched.length === 1) → 直接绑定 + 显示「发行人 + 函证编号」绿色徽标
 *  B) 多条匹配 (matched.length > 1)  → 显示「选择发行人」下拉框，选中后带出函证编号
 *  C) 无匹配 / 识别不出              → 显示「手动输入函证编号」+「绑定」按钮
 *
 * Props:
 *   recipient: string          当前文件的收函单位（识别出的被询证单位）
 *   fileId: string             当前文件 id
 *   bound: object | null       { code, issuer } 已绑定的函证记录
 *   onChange: (bound) => void  绑定状态变化时回调
 */
export default function ConfirmationBinding({ recipient, fileId, bound, onChange }) {
  const [matched, setMatched] = useState([]); // 函证系统返回的列表
  const [loading, setLoading] = useState(false);
  const [querying, setQuerying] = useState(false);

  // 多匹配时用户选中的 issuer code
  const [selectedCode, setSelectedCode] = useState(bound?.code || null);
  // 手动输入模式（用户主动切到手动）
  const [manualMode, setManualMode] = useState(false);
  const [manualCode, setManualCode] = useState('');

  // 查询函证系统
  const query = useCallback(async () => {
    if (!recipient || !recipient.trim()) {
      setMatched([]);
      return;
    }
    setQuerying(true);
    try {
      const list = await fetchConfirmationList(recipient);
      setMatched(list);
      // 自动匹配策略
      if (list.length === 1 && !bound) {
        // 唯一匹配且未绑定 → 自动绑定
        const only = list[0];
        setSelectedCode(only.code);
        onChange?.({ code: only.code, issuer: only.issuer, record: only });
        message.success(`自动绑定函证（唯一匹配）：${only.code}`);
      } else if (list.length === 0 && !bound) {
        // 无匹配：保持手动输入入口可见
        setManualMode(true);
      }
    } catch (e) {
      message.error(`函证系统查询失败：${e.message || e}`);
    } finally {
      setQuerying(false);
    }
  }, [recipient, bound, onChange]);

  // 收函单位变化时重新查询
  useEffect(() => {
    query();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipient]);

  // 选择某个函证记录
  const handleSelect = (code) => {
    const found = matched.find((c) => c.code === code);
    if (!found) return;
    setSelectedCode(code);
    onChange?.({ code: found.code, issuer: found.issuer, record: found });
  };

  // 手动绑定
  const handleManualBind = async () => {
    if (!manualCode.trim()) {
      message.warning('请输入函证编号');
      return;
    }
    setLoading(true);
    try {
      // 先反查，再用绑定接口（保留接口语义完整）
      const record = await fetchConfirmationByCode(manualCode);
      if (!record) {
        message.error(`函证编号「${manualCode}」在系统中未找到`);
        return;
      }
      const res = await bindConfirmationCode(fileId, manualCode);
      if (res.success) {
        setSelectedCode(record.code);
        onChange?.({ code: record.code, issuer: record.issuer, record });
        message.success(`绑定成功：${record.code}（${record.issuer}）`);
      } else {
        message.error(res.message || '绑定失败');
      }
    } catch (e) {
      message.error(`绑定失败：${e.message || e}`);
    } finally {
      setLoading(false);
    }
  };

  // 已绑定状态 → 显示绿色徽标
  if (bound && bound.code) {
    return (
      <div className="conf-binding bound">
        <Space size={6} wrap>
          <CheckCircleOutlined style={{ color: '#52c41a' }} />
          <span className="conf-label">已绑定：</span>
          <Tag color="green" style={{ margin: 0 }}>
            {bound.issuer}
          </Tag>
          <Tag color="cyan" style={{ margin: 0 }}>
            {bound.code}
          </Tag>
          <Button
            size="small"
            type="link"
            icon={<EditOutlined />}
            onClick={() => {
              // 解除绑定，重新进入选择
              onChange?.(null);
              setSelectedCode(null);
              setManualMode(matched.length === 0);
              message.info('已解除绑定，请重新选择');
            }}
          >
            重新选择
          </Button>
        </Space>
      </div>
    );
  }

  // 未绑定：分支判断
  return (
    <div className="conf-binding">
      {querying && <span className="conf-hint">🔍 正在函证系统查询「{recipient}」...</span>}

      {/* 情况 A：唯一匹配 → 立即绑定（query 已自动处理），这里只是兜底展示 */}
      {!querying && matched.length === 1 && (
        <div className="conf-suggestion">
          <span className="conf-hint">函证系统唯一匹配：</span>
          <Button size="small" type="primary" onClick={() => handleSelect(matched[0].code)}>
            绑定「{matched[0].issuer}」({matched[0].code})
          </Button>
        </div>
      )}

      {/* 情况 B：多条匹配 → 选择发行人下拉 */}
      {!querying && matched.length > 1 && (
        <div className="conf-suggestion">
          <span className="conf-hint">
            ⚠️ 该收函单位有 {matched.length} 条函证记录（可能存在子母公司并行函证），请选择对应发行人：
          </span>
          <Select
            style={{ width: '100%', marginTop: 4 }}
            placeholder="选择发行人（自动带出函证编号）"
            value={selectedCode}
            onChange={handleSelect}
            loading={querying}
            options={matched.map((c) => ({
              value: c.code,
              label: `${c.issuer}（${c.code}）`,
            }))}
          />
        </div>
      )}

      {/* 情况 C：无匹配 / 识别不出 → 手动输入 */}
      {!querying && matched.length === 0 && (
        <div className="conf-suggestion manual">
          <span className="conf-hint warn">
            ⚠ 函证系统未匹配到「{recipient || '（空）'}」的函证记录，请手动输入函证编号绑定：
          </span>
          <Space.Compact style={{ width: '100%', marginTop: 4 }}>
            <Input
              placeholder="输入函证编号（如 CONF-2026-0010）"
              value={manualCode}
              onChange={(e) => setManualCode(e.target.value)}
              onPressEnter={handleManualBind}
              disabled={loading}
            />
            <Button type="primary" loading={loading} onClick={handleManualBind}>
              绑定
            </Button>
          </Space.Compact>
        </div>
      )}

      {/* 手动模式切换 */}
      {!manualMode && matched.length > 0 && (
        <Button
          size="small"
          type="link"
          icon={<EditOutlined />}
          style={{ marginTop: 4, padding: 0 }}
          onClick={() => setManualMode(true)}
        >
          没有匹配的，手动输入函证编号
        </Button>
      )}
      {manualMode && matched.length > 0 && (
        <div className="conf-suggestion manual" style={{ marginTop: 6 }}>
          <span className="conf-hint">手动输入函证编号：</span>
          <Space.Compact style={{ width: '100%', marginTop: 4 }}>
            <Input
              placeholder="输入函证编号（如 CONF-2026-0010）"
              value={manualCode}
              onChange={(e) => setManualCode(e.target.value)}
              onPressEnter={handleManualBind}
              disabled={loading}
            />
            <Button type="primary" loading={loading} onClick={handleManualBind}>
              绑定
            </Button>
          </Space.Compact>
        </div>
      )}

      {/* 重新查询 */}
      <Button
        size="small"
        type="link"
        icon={<ReloadOutlined />}
        style={{ marginTop: 4, padding: 0 }}
        onClick={query}
        disabled={querying}
      >
        重新查询函证系统
      </Button>
    </div>
  );
}