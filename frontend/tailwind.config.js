/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#090d16',
        surface: '#111827',
        'surface-card': 'rgba(23, 31, 48, 0.7)',
        'surface-border': 'rgba(255, 255, 255, 0.08)',
        primary: {
          50: '#ecfeff',
          100: '#cffafe',
          400: '#22d3ee',
          500: '#06b6d4',
          600: '#0891b2',
        },
        stock: {
          green: '#10b981',
          gold: '#f59e0b',
          blue: '#3b82f6'
        }
      },
      fontFamily: {
        sans: ['Outfit', 'Inter', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        'glow-cyan': '0 0 25px rgba(6, 182, 212, 0.25)',
        'glow-emerald': '0 0 25px rgba(16, 185, 129, 0.25)',
        'glow-amber': '0 0 25px rgba(245, 158, 11, 0.25)'
      }
    },
  },
  plugins: [],
}
