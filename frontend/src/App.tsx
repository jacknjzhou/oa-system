import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell'
import ProtectedRoute from './components/ProtectedRoute'
import SystemTabs from './components/SystemTabs'
import Login from './pages/Login'
import StartProcess from './pages/StartProcess'
import Home from './pages/Home'
import TaskList from './pages/TaskList'
import ApprovalForm from './pages/ApprovalForm'
import ProcessTracking from './pages/ProcessTracking'
import MyInstances from './pages/MyInstances'
import CcList from './pages/CcList'
import Attendance from './pages/Attendance'
import AllAttendance from './pages/AllAttendance'
import AttendanceSettings from './pages/AttendanceSettings'
import PrintAttendance from './pages/PrintAttendance'
import Leave from './pages/Leave'
import LeaveBalanceDetail from './pages/LeaveBalanceDetail'
import LeaveTypes from './pages/LeaveTypes'
import Users from './pages/Users'
import Departments from './pages/Departments'
import JobTitles from './pages/JobTitles'
import JobLevels from './pages/JobLevels'
import PermissionGroups from './pages/PermissionGroups'
import Settings from './pages/Settings'
import DocumentList from './pages/DocumentList'
import DocumentDetail from './pages/DocumentDetail'
import TemplateList from './pages/TemplateList'
import TemplateEditor from './pages/TemplateEditor'
import ApprovalTypes from './pages/ApprovalTypes'

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
        <Route path="/" element={<Home />} />
        <Route path="/start" element={<StartProcess />} />
        <Route path="/tasks/todo" element={<TaskList mode="todo" />} />
        <Route path="/tasks/done" element={<TaskList mode="done" />} />
        <Route path="/task/:id" element={<ApprovalForm />} />
        <Route path="/tracking/:id" element={<ProcessTracking />} />
        <Route path="/my-instances" element={<MyInstances />} />
        <Route path="/cc" element={<CcList />} />
        <Route path="/attendance" element={<Attendance />} />
        <Route path="/attendance/all" element={<AllAttendance />} />
        <Route path="/attendance/settings" element={<AttendanceSettings />} />
        <Route path="/print" element={<PrintAttendance />} />
        <Route path="/leave" element={<Leave />} />
        <Route
          path="/leave/balance/:userId"
          element={
            <ProtectedRoute perm="leave:manage">
              <LeaveBalanceDetail />
            </ProtectedRoute>
          }
        />
        <Route
          path="/leave/types"
          element={
            <ProtectedRoute perm="leave:manage">
              <LeaveTypes />
            </ProtectedRoute>
          }
        />
        <Route
          path="/system"
          element={
            <ProtectedRoute perm={['hr:user', 'hr:dept', 'hr:level', 'system:permission', 'system:settings', 'system:log', 'system:company']}>
              <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
                <SystemTabs />
                <Outlet />
              </div>
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/system/users" replace />} />
          <Route path="users" element={<ProtectedRoute perm="hr:user"><Users /></ProtectedRoute>} />
          <Route path="departments" element={<ProtectedRoute perm="hr:dept"><Departments /></ProtectedRoute>} />
          <Route path="job-titles" element={<ProtectedRoute perm="hr:level"><JobTitles /></ProtectedRoute>} />
          <Route path="job-levels" element={<ProtectedRoute perm="hr:level"><JobLevels /></ProtectedRoute>} />
          <Route path="groups" element={<ProtectedRoute perm="system:permission"><PermissionGroups /></ProtectedRoute>} />
          <Route
            path="settings"
            element={<ProtectedRoute perm={['system:settings', 'system:log', 'system:company']}><Settings /></ProtectedRoute>}
          />
        </Route>
        <Route path="/users" element={<Navigate to="/system/users" replace />} />
        <Route path="/permission-groups" element={<Navigate to="/system/groups" replace />} />
        <Route path="/settings" element={<Navigate to="/system/settings" replace />} />
        <Route path="/documents" element={<DocumentList />} />
        <Route path="/documents/:id" element={<DocumentDetail />} />
        <Route path="/approval-types" element={<ApprovalTypes />} />
        <Route path="/templates" element={<TemplateList />} />
        <Route path="/templates/new" element={<TemplateEditor mode="new" />} />
        <Route path="/templates/:id" element={<TemplateEditor mode="edit" />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
