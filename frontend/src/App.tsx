import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AppShell } from './components/layout/AppShell';
import { AuthProvider } from './context/AuthContext';
import { RealtimeProvider } from './context/RealtimeContext';
import { AuthPage } from './pages/AuthPage';
import { CtHubPage } from './pages/CtHubPage';
import { MessagingPage } from './pages/MessagingPage';
import { ModuleSlotPage } from './pages/ModuleSlotPage';
import { OpportunitiesPage } from './pages/OpportunitiesPage';
import { OpportunityDetailPage } from './pages/OpportunityDetailPage';
import { OverviewPage } from './pages/OverviewPage';
import { PlaygroundsPage } from './pages/PlaygroundsPage';
import { PlaygroundWorkspacePage } from './pages/PlaygroundWorkspacePage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/ct-hub" element={<CtHubPage />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/verify-email" element={<AuthPage />} />
        <Route path="/messaging" element={<MessagingPage />} />
        <Route path="/opportunities" element={<OpportunitiesPage />} />
        <Route path="/opportunities/:id" element={<OpportunityDetailPage />} />
        <Route path="/playgrounds" element={<PlaygroundsPage />} />
        <Route path="/playgrounds/:id" element={<PlaygroundWorkspacePage />} />
        <Route
          path="/study-circles"
          element={
            <ModuleSlotPage
              title="Study Circles"
              owner="Member C"
              description="Peer study circles, Pomodoro timers, and shared course checklists mount into this AppShell route."
            />
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <RealtimeProvider>
        <Toaster position="top-right" richColors />
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </RealtimeProvider>
    </AuthProvider>
  );
}
