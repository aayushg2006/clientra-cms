/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "Segoe UI", "sans-serif"],
      },
      colors: {
        // "Teal & Sand" identity from the FA-I prototype
        teal: {
          950: "#0B3D38",
          900: "#0E4A43",
          800: "#11574F",
          700: "#14776B",
          600: "#178A7C",
          500: "#1FAE9C",
          400: "#2DD4BF",
          100: "#DDEFEA",
          50: "#EAF5F2",
        },
        sand: {
          50: "#FAF8F4",
          100: "#F7F4EE",
          200: "#F1ECE3",
          300: "#E8E1D4",
          400: "#D9CFBE",
        },
        ink: {
          900: "#1F2933",
          700: "#3E4C59",
          500: "#6B7280",
          400: "#9AA5B1",
        },
      },
      boxShadow: {
        card: "0 1px 2px rgba(16, 24, 40, 0.04)",
        pop: "0 12px 32px rgba(16, 24, 40, 0.14)",
      },
    },
  },
  plugins: [],
};
