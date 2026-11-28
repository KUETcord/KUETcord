import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AppShell } from './components/layout/AppShell';
import { AuthProvider } from './context/AuthContext';
import { RealtimeProvider } from './context/RealtimeContext';
import { AccountPage } from './pages/AccountPage';
import { AdminConsolePage } from './pages/AdminConsolePage';
import { AuthPage } from './pages/AuthPage';
import { CalendarPage } from './pages/CalendarPage';
import { CampusPulsePage } from './pages/CampusPulsePage';
import { CtHubPage } from './pages/CtHubPage';
import { MessagingPage } from './pages/MessagingPage';
import { ModuleSlotPage } from './pages/ModuleSlotPage';
import { OpportunitiesPage } from './pages/OpportunitiesPage';
import { OpportunityDetailPage } from './pages/OpportunityDetailPage';
import { OverviewPage } from './pages/OverviewPage';
import { PlaygroundsPage } from './pages/PlaygroundsPage';
import { PlaygroundWorkspacePage } from './pages/PlaygroundWorkspacePage';
import { StudyCircleDetailPage } from './pages/StudyCircleDetailPage';
import { StudyCirclesPage } from './pages/StudyCirclesPage';
import { TermHubPage } from './pages/TermHubPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/ct-hub" element={<CtHubPage />} />
        <Route path="/term-hub" element={<TermHubPage />} />
        <Route
          path="/assignments"
          element={
            <ModuleSlotPage
              title="Assignment Hub"
              owner="Member B"
              description="Course assignments with Year–Term filtering, teacher approval, optional submission links, and submitted-list links mount into this route."
            />
          }
        />
        <Route
          path="/presentations"
          element={
            <ModuleSlotPage
              title="Presentation Hub"
              owner="Member B"
              description="Class presentations with room scheduling, Year–Term filtering, teacher approval, optional submission links, and submitted-list links mount into this route."
            />
          }
        />
        <Route
          path="/course-projects"
          element={
            <ModuleSlotPage
              title="Project Hub"
              owner="Member C"
              description="Academic sessional & course projects with Year–Term filtering, teacher approval, optional submission links, submitted-list links, and Playground Kanban linking mount into this route."
            />
          }
        />
        <Route path="/account" element={<AccountPage />} />
        <Route path="/admin" element={<AdminConsolePage />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/verify-email" element={<AuthPage />} />
        <Route path="/messaging" element={<MessagingPage />} />
        <Route path="/opportunities" element={<OpportunitiesPage />} />
        <Route path="/opportunities/:id" element={<OpportunityDetailPage />} />
        <Route path="/playgrounds" element={<PlaygroundsPage />} />
        <Route path="/playgrounds/:id" element={<PlaygroundWorkspacePage />} />
        <Route path="/study-circles" element={<StudyCirclesPage />} />
        <Route path="/study-circles/:id" element={<StudyCircleDetailPage />} />
        <Route path="/campus-pulse" element={<CampusPulsePage />} />
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
