'use client';

import React, { useState, useEffect } from 'react';
import { User, UserStatus, CreateUserRequest, UpdateUserRequest } from '../utils/api';

interface UserModalProps {
  isOpen:      boolean;
  onClose:     () => void;
  onSave:      (data: CreateUserRequest | UpdateUserRequest) => Promise<void>;
  userToEdit?: User | null;
}

const STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE:    'ACTIVE – Đang hoạt động',
  INACTIVE:  'INACTIVE – Chưa kích hoạt',
  SUSPENDED: 'SUSPENDED – Tạm khóa',
  DELETED:   'DELETED – Đã xóa',
};

const STATUS_STYLE: Record<UserStatus, string> = {
  ACTIVE:    'border-emerald-300 bg-emerald-50 text-emerald-800',
  INACTIVE:  'border-amber-300 bg-amber-50 text-amber-800',
  SUSPENDED: 'border-red-300 bg-red-50 text-red-800',
  DELETED:   'border-zinc-300 bg-zinc-100 text-zinc-600',
};

function Field({ label, children, required }: { label: string; children: React.ReactNode; required?: boolean }) {
  return (
    <div>
      <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
        {label}{required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      {children}
    </div>
  );
}

const inputCls = 'w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all disabled:opacity-50 disabled:cursor-not-allowed';

export default function UserModal({ isOpen, onClose, onSave, userToEdit }: UserModalProps) {
  const isEdit = !!userToEdit;
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');

  const [createForm, setCreateForm] = useState<CreateUserRequest>({
    username: '', password: '', email: '', fullName: '', phoneNumber: '', avatar: '',
  });

  const [updateForm, setUpdateForm] = useState<UpdateUserRequest>({
    fullName: '', phoneNumber: '', avatar: '', status: 'ACTIVE',
  });

  useEffect(() => {
    setError('');
    if (userToEdit) {
      setUpdateForm({
        fullName:    userToEdit.fullName    || '',
        phoneNumber: userToEdit.phoneNumber || '',
        avatar:      userToEdit.avatar      || '',
        status:      userToEdit.status      || 'ACTIVE',
      });
    } else {
      setCreateForm({ username: '', password: '', email: '', fullName: '', phoneNumber: '', avatar: '' });
    }
  }, [userToEdit, isOpen]);

  if (!isOpen) return null;

  const onCC = (e: React.ChangeEvent<HTMLInputElement>) =>
    setCreateForm(p => ({ ...p, [e.target.name]: e.target.value }));

  const onUC = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setUpdateForm(p => ({ ...p, [e.target.name]: e.target.value }));

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true); setError('');
    try { await onSave(isEdit ? updateForm : createForm); onClose(); }
    catch (err: unknown) { setError(err instanceof Error ? err.message : 'Lỗi lưu thông tin.'); }
    finally { setLoading(false); }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="w-full max-w-xl bg-white border border-zinc-200 rounded-2xl shadow-2xl my-10">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 bg-gradient-to-r from-indigo-50/70 to-purple-50/70 rounded-t-2xl">
          <div>
            <p className="text-[10px] font-extrabold uppercase tracking-widest text-indigo-400">
              {isEdit ? 'Chỉnh sửa người dùng' : 'Thêm người dùng mới'}
            </p>
            <h3 className="text-base font-extrabold text-zinc-900 mt-0.5">
              {isEdit ? userToEdit!.fullName : 'Đăng ký tài khoản hệ thống'}
            </h3>
          </div>
          <button id="modal-close-btn" onClick={onClose}
            className="p-1.5 rounded-xl text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Form */}
        <form id="user-form" onSubmit={handleSubmit} className="p-6 space-y-5">
          {error && (
            <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 text-red-600 text-sm flex gap-2">
              <svg className="w-4 h-4 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              {error}
            </div>
          )}

          {/* CREATE */}
          {!isEdit && (
            <>
              <div className="grid grid-cols-2 gap-4">
                <Field label="Tên tài khoản" required>
                  <input id="f-username" name="username" className={inputCls} required
                    value={createForm.username} onChange={onCC} placeholder="nguyenvana" />
                </Field>
                <Field label="Mật khẩu" required>
                  <input id="f-password" name="password" type="password" className={inputCls} required
                    value={createForm.password} onChange={onCC} placeholder="••••••••" />
                </Field>
              </div>
              <Field label="Email" required>
                <input id="f-email" name="email" type="email" className={inputCls} required
                  value={createForm.email} onChange={onCC} placeholder="name@company.com" />
              </Field>
              <Field label="Họ và Tên đầy đủ" required>
                <input id="f-fullName" name="fullName" className={inputCls} required
                  value={createForm.fullName} onChange={onCC} placeholder="Nguyễn Văn A" />
              </Field>
              <div className="grid grid-cols-2 gap-4">
                <Field label="Số điện thoại">
                  <input id="f-phone" name="phoneNumber" className={inputCls}
                    value={createForm.phoneNumber || ''} onChange={onCC} placeholder="0912345678" />
                </Field>
                <Field label="URL Avatar">
                  <input id="f-avatar" name="avatar" className={inputCls}
                    value={createForm.avatar || ''} onChange={onCC} placeholder="https://..." />
                </Field>
              </div>
            </>
          )}

          {/* EDIT */}
          {isEdit && (
            <>
              {/* Read-only info */}
              <div className="bg-zinc-50 border border-zinc-200 rounded-xl p-4 grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Keycloak ID</span>
                  <span className="font-mono text-indigo-600 text-xs font-semibold">{userToEdit!.keycloakUserId}</span>
                </div>
                <div>
                  <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Username</span>
                  <span className="font-semibold text-zinc-800">{userToEdit!.username}</span>
                </div>
                <div className="col-span-2">
                  <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Email</span>
                  <span className="font-semibold text-zinc-800">{userToEdit!.email}</span>
                </div>
              </div>

              <Field label="Họ và Tên đầy đủ" required>
                <input id="u-fullName" name="fullName" className={inputCls} required
                  value={updateForm.fullName || ''} onChange={onUC} placeholder="Nguyễn Văn A" />
              </Field>
              <div className="grid grid-cols-2 gap-4">
                <Field label="Số điện thoại">
                  <input id="u-phone" name="phoneNumber" className={inputCls}
                    value={updateForm.phoneNumber || ''} onChange={onUC} placeholder="0912345678" />
                </Field>
                <Field label="URL Avatar">
                  <input id="u-avatar" name="avatar" className={inputCls}
                    value={updateForm.avatar || ''} onChange={onUC} placeholder="https://..." />
                </Field>
              </div>
              <Field label="Trạng thái tài khoản">
                <select id="u-status" name="status" value={updateForm.status} onChange={onUC}
                  className={`w-full px-3 py-2.5 border rounded-xl text-sm font-semibold outline-none transition-all focus:ring-2 focus:ring-indigo-500/15 ${STATUS_STYLE[updateForm.status as UserStatus] || ''}`}>
                  {(Object.keys(STATUS_LABELS) as UserStatus[]).map(s => (
                    <option key={s} value={s}>{STATUS_LABELS[s]}</option>
                  ))}
                </select>
              </Field>
            </>
          )}
        </form>

        {/* Footer */}
        <div className="flex justify-end gap-3 px-6 py-4 border-t border-zinc-100 bg-zinc-50/50 rounded-b-2xl">
          <button id="modal-cancel-btn" type="button" onClick={onClose}
            className="px-5 py-2 border border-zinc-200 text-zinc-600 rounded-xl hover:bg-zinc-100 text-sm font-semibold transition-all">
            Hủy bỏ
          </button>
          <button id="modal-save-btn" type="submit" form="user-form" disabled={loading}
            className="px-6 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-xl font-semibold text-sm shadow-md shadow-indigo-600/15 disabled:opacity-50 flex items-center gap-2 transition-all">
            {loading
              ? <><svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/></svg>Đang lưu...</>
              : isEdit ? 'Cập nhật hồ sơ' : 'Tạo tài khoản'
            }
          </button>
        </div>
      </div>
    </div>
  );
}
