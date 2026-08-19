module.exports = {
  presets: [
    [
      '@babel/preset-env',
      {
        targets: {
          chrome: '80',
          edge: '80',
          firefox: '78',
          safari: '13',
        },
        useBuiltIns: false,
      },
    ],
    ['@babel/preset-react', { runtime: 'automatic' }],
  ],
};
