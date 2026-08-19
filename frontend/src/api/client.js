import axios from 'axios';

// 后端 Flask 服务默认同源（8888）；开发环境经 webpack-dev-server 代理
const http = axios.create({
  baseURL: '',
  timeout: 120000,
});

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.error || err.message || '请求失败';
    return Promise.reject(new Error(msg));
  }
);

export default http;
