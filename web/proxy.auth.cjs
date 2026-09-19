module.exports = { '/api/**': { target: 'http://127.0.0.1:' + (process.env.AUTH_ADAPTER_PORT || 4301), secure: false, changeOrigin: false } };
