/**
 * 函证系统接口（示例/前端 mock）
 *
 * 实际接入时把以下 mock 函数替换为真实 HTTP 调用即可。
 * 真实接口一般由函证系统（qy 系统）提供，可能的形态：
 *   GET  /qy/confirmation/list?recipient=xxx  → 函证记录列表
 *   GET  /qy/confirmation/by-code?code=xxx   → 用函证编号反查
 *
 * 本文件目前用 setTimeout 模拟网络延迟 + 内存 mock 数据，
 * 让前端示例界面能完整跑通流程。
 */

// ====== Mock 数据 ======
// 一个被询证单位（收函单位）可能对应多个函证记录（多个发行人/项目并行函证）
const MOCK_CONFIRMATIONS = [
  // 国泰君安证券股份有限公司 — 唯一匹配（只有 1 条）
  {
    code: 'CONF-2026-0001',
    recipient: '国泰君安证券股份有限公司',
    issuer: '示例科技股份母公司',
    project: '2025年财报函证',
    issue_date: '2026-01-15',
    contact_person: '张三',
  },
  // 华泰证券股份有限公司 — 多条匹配（子母公司并行函证）
  {
    code: 'CONF-2026-0010',
    recipient: '华泰证券股份有限公司',
    issuer: '蓝海集团股份',
    project: '2025年年报函证',
    issue_date: '2026-02-01',
    contact_person: '李四',
  },
  {
    code: 'CONF-2026-0011',
    recipient: '华泰证券股份有限公司',
    issuer: '蓝海集团（香港）',
    project: '2025年年报函证（合并）',
    issue_date: '2026-02-05',
    contact_person: '李四',
  },
  {
    code: 'CONF-2026-0012',
    recipient: '华泰证券股份有限公司',
    issuer: '蓝海集团股份母公司',
    project: '2025年年报函证（母公司单体）',
    issue_date: '2026-02-08',
    contact_person: '李四',
  },
  // 招商证券股份有限公司 — 唯一匹配
  {
    code: 'CONF-2026-0020',
    recipient: '招商证券股份有限公司',
    issuer: '示例科技股份母公司',
    project: '2025年财报函证',
    issue_date: '2026-01-15',
    contact_person: '张三',
  },
  // 海通证券股份有限公司 — 唯一匹配
  {
    code: 'CONF-2026-0030',
    recipient: '海通证券股份有限公司',
    issuer: '示例科技股份母公司',
    project: '2025年财报函证',
    issue_date: '2026-01-15',
    contact_person: '张三',
  },
];

/**
 * 根据被询证单位查询函证系统
 * @param {string} recipient - 被询证单位名称（收函单位）
 * @returns {Promise<Array>} 函证记录列表
 */
export function fetchConfirmationList(recipient) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (!recipient || !recipient.trim()) {
        resolve([]);
        return;
      }
      const matched = MOCK_CONFIRMATIONS.filter(
        (c) => c.recipient.indexOf(recipient.trim()) >= 0
      );
      resolve(matched);
    }, 300);
  });
}

/**
 * 通过函证编号反查
 * @param {string} code - 函证编号（唯一）
 * @returns {Promise<object|null>} 单条函证记录
 */
export function fetchConfirmationByCode(code) {
  return new Promise((resolve) => {
    setTimeout(() => {
      const found = MOCK_CONFIRMATIONS.find((c) => c.code === code.trim());
      resolve(found || null);
    }, 200);
  });
}

/**
 * 绑定函证编号到文件
 * @param {string} fileId - 上传的 PDF 文件 id（来自 /batch/upload）
 * @param {string} code - 函证编号
 * @returns {Promise<{success: boolean, message?: string}>}
 */
export function bindConfirmationCode(fileId, code) {
  return new Promise((resolve) => {
    setTimeout(() => {
      const found = MOCK_CONFIRMATIONS.find((c) => c.code === code.trim());
      if (!found) {
        resolve({ success: false, message: '函证编号在系统中未找到' });
        return;
      }
      resolve({ success: true, message: '绑定成功', record: found });
    }, 250);
  });
}