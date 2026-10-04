import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { getToken } from '../api/auth'
import { useToast } from '../components/Toast'

const DEMO_ACCOUNTS = ['admin', 'manager', 'employee']

export default function Login() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (getToken()) {
    return <Navigate to="/" replace />
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('请输入用户名和密码')
      return
    }
    setError('')
    setLoading(true)
    try {
      const result = await login(username.trim(), password)
      showToast(`欢迎回来，${result.userInfo.realName || result.userInfo.username}`, 'success')
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-indigo-600 via-indigo-500 to-violet-600 p-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/10 text-3xl font-bold text-white ring-1 ring-white/20 backdrop-blur">
            OA
          </div>
          <h1 className="text-2xl font-bold text-white">OA 审批系统</h1>
          <p className="mt-1 text-sm text-indigo-200">Flowable 工作流 · 高效协作审批</p>
        </div>

        <div className="rounded-2xl bg-white p-8 shadow-2xl dark:bg-slate-800">
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="form-label" htmlFor="username">
                用户名
              </label>
              <input
                id="username"
                className="input"
                type="text"
                autoComplete="username"
                placeholder="请输入用户名"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
            <div>
              <label className="form-label" htmlFor="password">
                密码
              </label>
              <input
                id="password"
                className="input"
                type="password"
                autoComplete="current-password"
                placeholder="请输入密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-400">
                {error}
              </div>
            )}

            <button type="submit" className="btn btn-primary w-full py-2.5" disabled={loading}>
              {loading ? '登录中…' : '登 录'}
            </button>
          </form>

          <div className="mt-6 border-t border-slate-100 pt-4 dark:border-slate-700">
            <p className="mb-2 text-center text-xs text-slate-400 dark:text-slate-500">
              演示账号（点击填入）
            </p>
            <div className="flex justify-center gap-2">
              {DEMO_ACCOUNTS.map((account) => (
                <button
                  key={account}
                  type="button"
                  className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-600 transition-colors hover:bg-primary-50 hover:text-primary-700 dark:bg-slate-700 dark:text-slate-300 dark:hover:bg-primary-500/20 dark:hover:text-primary-300"
                  onClick={() => {
                    setUsername(account)
                    setPassword('')
                    setError('')
                  }}
                >
                  {account}
                </button>
              ))}
            </div>
          </div>
        </div>

        <p className="mt-6 text-center text-xs text-indigo-200/70">
          © {new Date().getFullYear()} OA 审批系统 · Powered by Flowable
        </p>
      </div>
    </div>
  )
}
