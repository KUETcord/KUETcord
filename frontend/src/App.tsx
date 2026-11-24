import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AppShell } from './components/layout/AppShell';
import { AuthProvider } from './context/AuthContext';
import { AuthPage } from './pages/AuthPage';
import { CtHubPage } from './pages/CtHubPage';
import { ModuleSlotPage } from './pages/ModuleSlotPage';
import { OverviewPage } from './pages/OverviewPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/ct-hub" element={<CtHubPage />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/verify-email" element={<AuthPage />} />
        <Route
          path="/messaging"
          element={
            <ModuleSlotPage
              title="Core Messaging & Communities"
              owner="Member B"
              description="Backend REST APIs, Discord Server RBAC, Group DMs, and STOMP heartbeat interceptors are merged on develop. Frontend <ChatPanel /> mounts into this AppShell route."
            />
          }
        />
        <Route
          path="/opportunities"
          element={
            <ModuleSlotPage
              title="Contest & Challenge Board"
              owner="Member C"
              description="Backend opportunity catalog and Deadline/Recent ranking strategies are merged on develop. Frontend contest board mounts into this AppShell route."
            />
          }
        />
        <Route
          path="/playgrounds"
          element={
            <ModuleSlotPage
              title="Project Playgrounds & Kanban"
              owner="Member C"
              description="Backend Playgrounds and optimistic-locked Kanban tasks are merged on develop. Frontend <PlaygroundBoard /> mounts into this AppShell route."
            />
          }
        />
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
      <Toaster position="top-right" richColors />
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
