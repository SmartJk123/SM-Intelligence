module.exports = {
  '/sample-api/**': {
    target: 'https://sm-backend-dev.onrender.com',
    secure: true,
    changeOrigin: true,
    pathRewrite: { '^/sample-api': '' },
    timeout: 95000,
    proxyTimeout: 95000,
  },
};
