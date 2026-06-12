'use client';

import { FormEvent, useState } from 'react';
import { CreateUserRequest } from '../utils/api';

interface UserModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (data: CreateUserRequest) => Promise<void>;
}

const inputClass = 'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10';

export default function UserModal({ isOpen, onClose, onSave }: UserModalProps) {
  const [form, setForm] = useState<CreateUserRequest>({
    username: '',
    email: '',
    fullName: '',
    phoneNumber: '',
    avatar: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!isOpen) return null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await onSave(form);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tạo người dùng.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-sm">
      <div className="w-full max-w-2xl overflow-hidden rounded-3xl border border-white/60 bg-slate-50 shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">POST /api/users</p>
            <h2 className="mt-1 text-xl font-bold text-slate-950">Tạo tài khoản mới</h2>
            <p className="mt-1 text-sm text-slate-500">Backend tự sinh mật khẩu tạm thời; endpoint hiện không nhận trường mật khẩu.</p>
          </div>
          <button onClick={onClose} className="rounded-xl p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700" aria-label="Đóng">✕</button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5 p-6">
          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm font-semibold text-slate-700">
              Username <span className="text-red-500">*</span>
              <input required minLength={3} pattern="[A-Za-z0-9_]+" className={inputClass} value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })} placeholder="nguyen_minh" />
            </label>
            <label className="space-y-1.5 text-sm font-semibold text-slate-700">
              Email <span className="text-red-500">*</span>
              <input required type="email" className={inputClass} value={form.email}
                onChange={(event) => setForm({ ...form, email: event.target.value })} placeholder="minh@company.com" />
            </label>
          </div>
          <label className="block space-y-1.5 text-sm font-semibold text-slate-700">
            Họ tên
            <input className={inputClass} value={form.fullName}
              onChange={(event) => setForm({ ...form, fullName: event.target.value })} placeholder="Nguyễn Minh" />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm font-semibold text-slate-700">
              Số điện thoại
              <input inputMode="tel" pattern="[+]?[0-9]{7,15}" className={inputClass} value={form.phoneNumber}
                onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })} placeholder="0912345678" />
            </label>
            <label className="space-y-1.5 text-sm font-semibold text-slate-700">
              URL avatar
              <input type="url" className={inputClass} value={form.avatar}
                onChange={(event) => setForm({ ...form, avatar: event.target.value })} placeholder="https://..." />
            </label>
          </div>
          <div className="flex justify-end gap-3 border-t border-slate-200 pt-5">
            <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-100">Hủy</button>
            <button disabled={loading} className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-lg shadow-blue-600/20 hover:bg-blue-700 disabled:opacity-50">
              {loading ? 'Đang tạo...' : 'Tạo người dùng'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
