import React from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import BatchPage from './pages/BatchPage';

// 统一入口：所有路径（/、/upload、/config、/batch 等）都显示「PDF 格式转函」页面
// 该页面自身包含配置面板（保存/加载配置 + 覆盖区域校准 + 上传生成），可处理单文件与批量
export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563eb',
          borderRadius: 8,
        },
      }}
    >
      <HashRouter>
        <Routes>
          <Route path="/upload" element={<BatchPage />} />
          <Route path="*" element={<Navigate to="/upload" replace />} />
        </Routes>
      </HashRouter>
    </ConfigProvider>
  );
}
