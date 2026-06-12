'use client';

import React, { useState, useEffect } from 'react';
import { api, getApiBaseUrl, setApiBaseUrl, getUseMockData, setUseMockData } from '../utils/api';

interface LoginProps {
  onLoginSuccess: () => void;
}

export default function Login({ onLoginSuccess }: LoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Developer Settings States
  const [showDevSettings, setShowDevSettings] = useState(false);
  const [apiUrl, setApiUrl] = useState('');
  const [useMock, setUseMock] = useState(false);

  useEffect(() => {
    setApiUrl(getApiBaseUrl());
    setUseMock(getUseMockData());
  }, []);

  const handleSaveSettings = () => {
    setApiBaseUrl(apiUrl);
    setUseMockData(useMock);
    setSuccess('Cập nhật cấu hình thành công!');
    setTimeout(() => setSuccess(''), 3000);
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');

    try {
      const resp = await api.login(username, password);
      if (resp.success) {
        setSuccess(resp.message || 'Đăng nhập thành công! Đang chuyển hướng...');
        setTimeout(() => {
          onLoginSuccess();
        }, 1000);
      } else {
        setError(resp.message || 'Đăng nhập thất bại.');
      }
    } catch (err: any) {
      setError(err.message || 'Đã xảy ra lỗi kết nối. Hãy kiểm tra lại Server API.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 via-zinc-100 to-indigo-50 p-6 overflow-hidden">
      {/* Decorative Blur Orbs */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-purple-200/40 rounded-full blur-3xl -translate-x-1/2 -translate-y-1/2" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-indigo-200/40 rounded-full blur-3xl translate-x-1/2 translate-y-1/2" />

      <div className="w-full max-w-md bg-white/90 backdrop-blur-xl border border-zinc-200/80 rounded-3xl p-8 shadow-2xl relative z-10">
        {/* Brand & Heading */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-tr from-indigo-600 to-purple-600 text-white shadow-lg shadow-indigo-600/30 mb-4 font-bold text-2xl tracking-wider">
            SC
          </div>
          <h2 className="text-3xl font-extrabold text-zinc-800">
            Secret Chat
          </h2>
          <p className="text-zinc-500 text-sm mt-1">Hệ Thống Quản Trị Admin (Tone Sáng)</p>
        </div>

        {error && (
          <div className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-600 text-sm flex items-start gap-3">
            <svg className="w-5 h-5 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="mb-6 p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-600 text-sm flex items-start gap-3">
            <svg className="w-5 h-5 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{success}</span>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-5">
          <div>
            <label className="block text-zinc-700 text-xs font-semibold uppercase tracking-wider mb-2">
              Tên tài khoản (Username)
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-4 flex items-center text-zinc-400">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                </svg>
              </span>
              <input
                type="text"
                required
                placeholder="Nhập tên đăng nhập..."
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full pl-11 pr-4 py-3 bg-zinc-50 border border-zinc-200 focus:border-indigo-500 rounded-xl text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
          </div>

          <div>
            <label className="block text-zinc-700 text-xs font-semibold uppercase tracking-wider mb-2">
              Mật khẩu (Password)
            </label>
            <div className="relative">
              <span className="absolute inset-y-0 left-0 pl-4 flex items-center text-zinc-400">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </span>
              <input
                type="password"
                required
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full pl-11 pr-4 py-3 bg-zinc-50 border border-zinc-200 focus:border-indigo-500 rounded-xl text-zinc-900 outline-none transition-all placeholder:text-zinc-400 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 bg-gradient-to-r from-indigo-600 to-purple-600 text-white font-semibold rounded-xl hover:from-indigo-700 hover:to-purple-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform active:scale-[0.98] shadow-lg shadow-indigo-600/20 mt-2"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                Đang đăng nhập...
              </span>
            ) : (
              'Đăng Nhập Hệ Thống'
            )}
          </button>
        </form>

        {/* Developer configuration panel */}
        <div className="mt-8 border-t border-zinc-100 pt-4">
          <button
            onClick={() => setShowDevSettings(!showDevSettings)}
            className="w-full flex items-center justify-between text-zinc-400 hover:text-zinc-600 text-xs font-semibold tracking-wider uppercase focus:outline-none"
          >
            <span>Cấu hình kết nối API & Cổng Server</span>
            <svg
              className={`w-4 h-4 transition-transform duration-200 ${showDevSettings ? 'rotate-180' : ''}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          {showDevSettings && (
            <div className="mt-4 space-y-4 bg-zinc-50 p-4 rounded-xl border border-zinc-200 transition-all duration-300">
              <div>
                <label className="block text-zinc-500 text-[10px] font-semibold uppercase tracking-wider mb-1.5">
                  Địa chỉ API Backend
                </label>
                <input
                  type="text"
                  value={apiUrl}
                  onChange={(e) => setApiUrl(e.target.value)}
                  placeholder="http://localhost:8080"
                  className="w-full px-3 py-1.5 bg-white border border-zinc-200 rounded-lg text-zinc-800 text-xs outline-none focus:border-indigo-500"
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <span className="block text-zinc-700 text-[11px] font-semibold">Chạy Chế Độ Giả Lập (Mock Mode)</span>
                  <span className="block text-zinc-400 text-[9px]">Sử dụng dữ liệu tạm thời khi server offline</span>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={useMock}
                    onChange={(e) => setUseMock(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-9 h-5 bg-zinc-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-zinc-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600"></div>
                </label>
              </div>

              <button
                type="button"
                onClick={handleSaveSettings}
                className="w-full py-1.5 px-3 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs rounded-lg transition-colors focus:outline-none"
              >
                Áp Dụng Thiết Lập
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
