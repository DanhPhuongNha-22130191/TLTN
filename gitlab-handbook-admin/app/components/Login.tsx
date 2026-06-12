'use client';

import { FormEvent, useState } from 'react';
import { api, getApiBaseUrl, getUseMockData, setApiBaseUrl, setUseMockData } from '../utils/api';

interface LoginProps {
  onLoginSuccess: () => void;
}

export default function Login({ onLoginSuccess }: LoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [apiUrl, setApiUrl] = useState(() => getApiBaseUrl());
  const [mockMode, setMockMode] = useState(() => getUseMockData());

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await api.login(username.trim(), password);
      onLoginSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đăng nhập.');
    } finally {
      setLoading(false);
    }
  }

  function saveSettings() {
    setApiBaseUrl(apiUrl.trim());
    setUseMockData(mockMode);
    setShowSettings(false);
  }

  return (
    <main className="relative grid min-h-screen overflow-hidden bg-[#07132f] lg:grid-cols-[1.15fr_0.85fr]">
      <div className="absolute -left-24 top-1/4 h-80 w-80 rounded-full bg-blue-500/20 blur-3xl" />
      <div className="absolute bottom-0 right-1/3 h-96 w-96 rounded-full bg-cyan-400/10 blur-3xl" />

      <section className="relative z-10 hidden flex-col justify-between p-12 text-white lg:flex">
        <div className="flex items-center gap-3">
          <div className="grid h-11 w-11 place-items-center rounded-2xl bg-blue-500 font-black shadow-lg shadow-blue-500/30">SC</div>
          <div><p className="font-bold">Secret Chat</p><p className="text-xs text-blue-200/60">Administration Platform</p></div>
        </div>
        <div className="max-w-2xl">
          <p className="text-sm font-bold uppercase tracking-[0.22em] text-blue-300">Control center</p>
          <h1 className="mt-5 text-5xl font-black leading-tight tracking-tight">Quản trị tài khoản và nhóm chat trong một không gian rõ ràng.</h1>
          <p className="mt-5 max-w-xl text-base leading-7 text-blue-100/65">Giao diện được xây theo đúng contract hiện tại của user-service và chat-service, không hiển thị các chức năng backend chưa hỗ trợ.</p>
        </div>
        <p className="text-xs text-blue-200/40">Secret Chat Admin Console</p>
      </section>

      <section className="relative z-10 flex items-center justify-center bg-white px-5 py-10 sm:px-10 lg:rounded-l-[3rem]">
        <div className="w-full max-w-md">
          <div className="mb-9 lg:hidden">
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-blue-600 font-black text-white">SC</div>
          </div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-600">Admin access</p>
          <h2 className="mt-2 text-3xl font-black tracking-tight text-slate-950">Đăng nhập quản trị</h2>
          <p className="mt-2 text-sm leading-6 text-slate-500">Tài khoản cần có realm role <strong>ADMIN</strong> để sử dụng đầy đủ API quản trị.</p>

          {error && <div className="mt-6 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <form onSubmit={handleSubmit} className="mt-8 space-y-5">
            <label className="block space-y-2 text-sm font-bold text-slate-700">
              Username
              <input required autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 font-normal outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10" placeholder="admin" />
            </label>
            <label className="block space-y-2 text-sm font-bold text-slate-700">
              Mật khẩu
              <input required type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 font-normal outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-500/10" placeholder="Nhập mật khẩu" />
            </label>
            <button disabled={loading} className="w-full rounded-2xl bg-blue-600 px-4 py-3.5 text-sm font-bold text-white shadow-xl shadow-blue-600/20 transition hover:bg-blue-700 disabled:opacity-50">
              {loading ? 'Đang xác thực...' : 'Đăng nhập'}
            </button>
          </form>

          <button onClick={() => setShowSettings((value) => !value)} className="mt-7 w-full text-center text-xs font-bold text-slate-400 hover:text-blue-600">
            {showSettings ? 'Ẩn cấu hình kết nối' : 'Cấu hình API Gateway'}
          </button>

          {showSettings && (
            <div className="mt-4 space-y-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <label className="block space-y-1.5 text-xs font-bold text-slate-600">Base URL
                <input value={apiUrl} onChange={(event) => setApiUrl(event.target.value)} className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-normal outline-none focus:border-blue-500" />
              </label>
              <label className="flex items-center justify-between text-sm font-semibold text-slate-700">
                Dùng mock data
                <input type="checkbox" checked={mockMode} onChange={(event) => setMockMode(event.target.checked)} className="h-5 w-5 accent-blue-600" />
              </label>
              {mockMode && <p className="text-xs text-slate-500">Tài khoản mock: <strong>admin / admin</strong></p>}
              <button type="button" onClick={saveSettings} className="w-full rounded-xl bg-slate-900 px-3 py-2.5 text-xs font-bold text-white">Lưu cấu hình</button>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
