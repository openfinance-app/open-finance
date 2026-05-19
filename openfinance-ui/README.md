# Open-Finance Frontend

Modern React + TypeScript frontend for the Open-Finance personal wealth management application.

## Tech Stack

- **Framework**: React 19 with TypeScript 5.9
- **Build Tool**: Vite 7
- **Styling**: Tailwind CSS v4 with custom dark theme
- **Routing**: React Router v7
- **State Management**: 
  - TanStack React Query (server state)
  - Zustand (client state)
- **HTTP Client**: Axios with interceptors
- **Forms**: React Hook Form with Zod validation
- **Charts**: Recharts
- **Icons**: Lucide React

## Getting Started

### Prerequisites

- Node.js 20+ or Bun.js
- npm 9+

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

The development server runs on **http://localhost:3000** by default.

## Project Structure

```
src/
├── assets/         # Static assets (images, fonts, etc.)
├── components/     # Reusable UI components
├── context/        # React context providers
├── hooks/          # Custom React hooks
├── pages/          # Page components (routes)
├── services/       # API service clients
├── types/          # TypeScript type definitions
├── utils/          # Utility functions
├── App.tsx         # Main app component with routing
├── index.css       # Global styles with Tailwind
└── main.tsx        # Application entry point
```

## API Configuration

The frontend connects to the backend API via the configured base URL:

- **Development**: `http://localhost:8080/api/v1` (default)
- **Production**: Set via `VITE_API_BASE_URL` environment variable

Create a `.env.local` file for local overrides:

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_ENV=development
```

## Design System

### Color Palette

The app uses a sophisticated dark theme inspired by modern financial platforms:

- **Background**: `#0a0a0a` (pure black)
- **Surface**: `#1a1a1a` (cards/panels)
- **Primary**: `#f5a623` (gold - CTAs, highlights)
- **Success**: `#00c853` (positive gains)
- **Error**: `#ff5252` (negative losses)
- **Text**: `#ffffff` (primary), `#a0a0a0` (secondary)

### Typography

- **UI Font**: Inter (sans-serif)
- **Number Font**: JetBrains Mono (monospace, tabular numbers)

### Key Utilities

```css
.number-display       /* Monospace font with tabular numbers */
.gain-positive        /* Green text for positive values */
.gain-negative        /* Red text for negative values */
.shimmer              /* Loading animation with gold accent */
```

## Available Scripts

```bash
# Development
npm run dev           # Start dev server (http://localhost:3000)

# Build
npm run build         # Build for production
npm run preview       # Preview production build

# Linting
npm run lint          # Run ESLint
```

## Routes

- `/` - Redirects to dashboard
- `/login` - User login (to be implemented)
- `/register` - User registration (to be implemented)
- `/dashboard` - Main dashboard
- `/accounts` - Account management
- `/transactions` - Transaction history

## Development Guidelines

### Code Style

- Use functional components with hooks
- TypeScript strict mode enabled
- Prefer `const` over `let`
- Use path aliases: `@/` maps to `src/`
- Follow conventional commits for git messages

### Type Safety

- Always define explicit types for function parameters and returns
- Avoid `any` - use `unknown` if type is truly unknown
- Use type unions instead of enums (for `erasableSyntaxOnly` compliance)

### API Calls

Use the configured `apiClient` from `src/services/apiClient.ts`:

```typescript
import apiClient from '@/services/apiClient';

// Example
const response = await apiClient.get('/accounts');
```

The client automatically:
- Adds JWT Bearer token from localStorage
- Handles 401 (redirects to login)
- Logs errors

### State Management

- **Server State**: Use React Query hooks
- **Client State**: Use Zustand stores
- **Form State**: Use React Hook Form

## Next Steps (Sprint 1)

- [ ] Implement authentication (login/register)
- [ ] Create AuthContext for auth state
- [ ] Add ProtectedRoute component
- [ ] Build account management UI
- [ ] Implement transaction management

## Contributing

See the main project [CONTRIBUTING.md](../CONTRIBUTING.md) for development guidelines.

## License

Private - All rights reserved
