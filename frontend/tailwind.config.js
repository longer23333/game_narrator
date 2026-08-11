/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './public/**/*.{html,js}',
    './src/**/*.{js,css}'
  ],
  theme: {
    extend: {
      colors: {
        ink: '#16121f', cream: '#fff8e8', popYellow: '#ffd400',
        popPink: '#ff4fa3', popBlue: '#3d7eff', popMint: '#52e0b4', popPurple: '#8b5cf6'
      },
      boxShadow: { brutal: '6px 6px 0 #16121f', 'brutal-sm': '3px 3px 0 #16121f' },
      fontFamily: { display: ['Arial Black', 'Microsoft YaHei UI', 'sans-serif'] }
    }
  },
  plugins: []
};
