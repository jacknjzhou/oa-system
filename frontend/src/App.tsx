import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import StartProcess from './pages/StartProcess'
import TaskList from './pages/TaskList'
import ApprovalForm from './pages/ApprovalForm'
import ProcessTracking from './pages/ProcessTracking'
import MyInstances from './pages/MyInstances'
import CcList from './pages/CcList'
import DocumentList from './pages/DocumentList'
import DocumentDetail from './pages/DocumentDetail'
import TemplateList from './pages/TemplateList'
import TemplateEditor from './pages/TemplateEditor'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Navigate to="/start" replace />} />
        <Route path="/start" element={<StartProcess />} />
        <Route path="/tasks/todo" element={<TaskList mode="todo" />} />
        <Route path="/tasks/done" element={<TaskList mode="done" />} />
        <Route path="/task/:id" element={<ApprovalForm />} />
        <Route path="/tracking/:id" element={<ProcessTracking />} />
        <Route path="/my-instances" element={<MyInstances />} />
        <Route path="/cc" element={<CcList />} />
        <Route path="/documents" element={<DocumentList />} />
        <Route path="/documents/:id" element={<DocumentDetail />} />
        <Route path="/templates" element={<TemplateList />} />
        <Route path="/templates/new" element={<TemplateEditor mode="new" />} />
        <Route path="/templates/:id" element={<TemplateEditor mode="edit" />} />
      </Route>

      <Route path="*" element={<Navigate to="/start" replace />} />
    </Routes>
  )
}
