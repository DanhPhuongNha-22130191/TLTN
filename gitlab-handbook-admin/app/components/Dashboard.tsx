'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import {
  api,
  CreateUserRequest,
  getApiBaseUrl,
  getUseMockData,
  setApiBaseUrl,
  setUseMockData,
  User,
  UserRole,
} from '../utils/api';
import AiDocumentsSection from './AiDocumentsSection';
import GroupsSection from './GroupsSection';
import UserModal from './UserModal';

interface DashboardProps {
  onLogout: () => void;
}

type Tab = 'overview' | 'users' | 'groups' | 'ai-documents';
type SearchMode = 'all' | 'username' | 'email' | 'id';
type Toast = { message: string; type: 'success' | 'error' };

function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'U';
}

function formatDate(value?: string): string {
  if (!value) return 'Chưa có';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}

export default function Dashboard({ onLogout }: DashboardProps) {
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [searchMode, setSearchMode] = useState<SearchMode>('all');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [showCreateUser, setShowCreateUser] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [apiUrl, setApiUrl] = useState(() => getApiBaseUrl());
  const [mockMode, setMockMode] = useState(() => getUseMockData());
  const [toast, setToast] = useState<Toast | null>(null);

  const notify = useCallback((message: string, type: Toast['type'] = 'success') => {
    setToast({ message, type });
    window.setTimeout(() => setToast(null), 4000);
  }, []);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await api.getAllUsers());
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Không thể tải danh sách người dùng.', 'error');
    } finally {
      setLoading(false);
    }
  }, [notify]);

  useEffect(() => {
    let cancelled = false;
    api.getAllUsers()
      .then((data) => {
        if (!cancelled) setUsers(data);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          notify(error instanceof Error ? error.message : 'Không thể tải danh sách người dùng.', 'error');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [notify]);

  const filteredUsers = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return users.filter((user) => {
      const matchesStatus = statusFilter === 'ALL' || user.status === statusFilter;
      const matchesQuery = !normalized || [user.fullName, user.username, user.email, user.keycloakUserId]
        .some((value) => (value || '').toLowerCase().includes(normalized));
      return matchesStatus && matchesQuery;
    });
  }, [query, statusFilter, users]);

  const stats = useMemo(() => ({
    total: users.length,
    active: users.filter((user) => user.status === 'ACTIVE').length,
    locked: users.filter((user) => user.status === 'SUSPENDED' || user.status === 'INACTIVE').length,
    newest: [...users].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))[0],
  }), [users]);

  async function handleExactSearch(event: FormEvent) {
    event.preventDefault();
    if (!query.trim() || searchMode === 'all') return;
    setLoading(true);
    try {
      const value = query.trim();
      const found = searchMode === 'username'
        ? await api.getUserByUsername(value)
        : searchMode === 'email'
          ? await api.getUserByEmail(value)
          : await api.getUserById(value);
      setUsers([found]);
      setStatusFilter('ALL');
      notify('Đã tải người dùng từ endpoint tìm kiếm.');
    } catch (error) {
      setUsers([]);
      notify(error instanceof Error ? error.message : 'Không tìm thấy người dùng.', 'error');
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateUser(data: CreateUserRequest) {
    const created = await api.createUser(data);
    notify(`Đã tạo tài khoản @${created.username}.`);
    await loadUsers();
  }

  async function handleRoleChange(user: User, role: UserRole) {
    if (!confirm(`Gán role ${role} cho @${user.username}?`)) return;
    try {
      await api.changeRole(user.keycloakUserId, role);
      notify(`Đã gán role ${role} cho @${user.username}.`);
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Không thể đổi role.', 'error');
    }
  }

  async function handleDelete(user: User) {
    if (!confirm(`Xóa vĩnh viễn tài khoản @${user.username}?`)) return;
    try {
      await api.deleteUser(user.keycloakUserId);
      setSelectedUser(null);
      notify(`Đã xóa @${user.username}.`);
      await loadUsers();
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Không thể xóa người dùng.', 'error');
    }
  }

  async function handleLogout() {
    await api.logout().catch(() => undefined);
    onLogout();
  }

  function applySettings() {
    setApiBaseUrl(apiUrl.trim());
    setUseMockData(mockMode);
    setShowSettings(false);
    notify('Đã cập nhật cấu hình kết nối.');
    void loadUsers();
  }

  const navigation: Array<{ id: Tab; label: string; note: string }> = [
    { id: 'overview', label: 'Tổng quan', note: 'Trạng thái hệ thống' },
    { id: 'users', label: 'Người dùng', note: 'Tài khoản và phân quyền' },
    { id: 'groups', label: 'Nhóm chat', note: 'Tra cứu và quản trị nhóm' },
    { id: 'ai-documents', label: 'Tài liệu AI', note: 'Import vào RAG database' },
  ];

  return (
    <div className="min-h-screen bg-[#f3f6fb] text-slate-900 lg:flex">
      {toast && (
        <div className={`fixed right-5 top-5 z-[100] max-w-sm rounded-2xl border px-4 py-3 text-sm font-semibold shadow-xl ${
          toast.type === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-red-200 bg-red-50 text-red-700'
        }`}>{toast.message}</div>
      )}

      <aside className="flex w-full flex-col bg-[#0b1739] text-white lg:fixed lg:inset-y-0 lg:w-72">
        <div className="flex items-center gap-3 border-b border-white/10 px-6 py-6">
          <div className="grid h-11 w-11 place-items-center rounded-2xl bg-blue-500 font-black shadow-lg shadow-blue-500/25">SC</div>
          <div>
            <p className="font-bold tracking-wide">Secret Chat</p>
            <p className="text-xs text-blue-200/70">Administration</p>
          </div>
        </div>
        <nav className="grid flex-1 gap-2 p-4 sm:grid-cols-3 lg:block lg:space-y-2">
          {navigation.map((item) => (
            <button key={item.id} onClick={() => setActiveTab(item.id)}
              className={`w-full rounded-2xl px-4 py-3 text-left transition ${
                activeTab === item.id ? 'bg-blue-500 text-white shadow-lg shadow-blue-950/30' : 'text-blue-100/75 hover:bg-white/10 hover:text-white'
              }`}>
              <span className="block text-sm font-bold">{item.label}</span>
              <span className="mt-0.5 hidden text-xs opacity-65 lg:block">{item.note}</span>
            </button>
          ))}
        </nav>
        <div className="flex gap-2 border-t border-white/10 p-4 lg:block lg:space-y-2">
          <button onClick={() => setShowSettings(true)} className="flex-1 rounded-xl border border-white/10 px-4 py-2.5 text-sm font-semibold text-blue-100 hover:bg-white/10 lg:w-full">Cấu hình API</button>
          <button onClick={handleLogout} className="flex-1 rounded-xl px-4 py-2.5 text-sm font-semibold text-red-200 hover:bg-red-500/10 lg:w-full">Đăng xuất</button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 lg:ml-72">
        <header className="sticky top-0 z-20 flex items-center justify-between border-b border-slate-200/80 bg-white/90 px-5 py-4 backdrop-blur-xl sm:px-8">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">Admin console</p>
            <h1 className="mt-1 text-xl font-bold">{navigation.find((item) => item.id === activeTab)?.label}</h1>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-600 sm:block">
              {mockMode ? 'Mock data' : 'API thật'}
            </span>
            <button onClick={() => void loadUsers()} className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">Làm mới</button>
          </div>
        </header>

        <div className="p-5 sm:p-8">
          {activeTab === 'overview' && (
            <div className="space-y-7">
              <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-[#102250] to-[#173b84] p-7 text-white shadow-xl shadow-blue-950/10">
                <div className="max-w-2xl">
                  <p className="text-sm font-semibold text-blue-200">Không gian quản trị tập trung</p>
                  <h2 className="mt-2 text-3xl font-black tracking-tight">Quản lý đúng những gì backend đang hỗ trợ.</h2>
                  <p className="mt-3 text-sm leading-6 text-blue-100/75">Tài khoản được quản lý qua user-service. Nhóm chat được tra cứu theo ID vì API hiện chưa cung cấp endpoint liệt kê toàn bộ nhóm.</p>
                </div>
              </section>
              <section className="grid gap-4 md:grid-cols-3">
                {[
                  ['Tổng tài khoản', stats.total, 'Đồng bộ từ GET /api/users'],
                  ['Đang hoạt động', stats.active, 'Trạng thái ACTIVE'],
                  ['Chưa hoạt động', stats.locked, 'INACTIVE hoặc SUSPENDED'],
                ].map(([label, value, note]) => (
                  <div key={String(label)} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                    <p className="text-sm font-semibold text-slate-500">{label}</p>
                    <p className="mt-3 text-3xl font-black text-slate-950">{value}</p>
                    <p className="mt-2 text-xs text-slate-400">{note}</p>
                  </div>
                ))}
              </section>
              <section className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
                <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <h3 className="font-bold">Thao tác nhanh</h3>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <button onClick={() => { setActiveTab('users'); setShowCreateUser(true); }} className="rounded-2xl bg-blue-600 p-4 text-left text-white hover:bg-blue-700">
                      <span className="block font-bold">Tạo người dùng</span><span className="mt-1 block text-xs text-blue-100">Gọi POST /api/users</span>
                    </button>
                    <button onClick={() => setActiveTab('groups')} className="rounded-2xl bg-slate-100 p-4 text-left text-slate-800 hover:bg-slate-200">
                      <span className="block font-bold">Tra cứu nhóm</span><span className="mt-1 block text-xs text-slate-500">Tải nhóm bằng ID</span>
                    </button>
                    <button onClick={() => setActiveTab('ai-documents')} className="rounded-2xl bg-cyan-700 p-4 text-left text-white hover:bg-cyan-800 sm:col-span-2">
                      <span className="block font-bold">Import tài liệu AI</span><span className="mt-1 block text-xs text-cyan-50/80">Upload vào POST /api/ai/upload để cập nhật RAG database</span>
                    </button>
                  </div>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                  <h3 className="font-bold">Tài khoản mới nhất</h3>
                  {stats.newest ? (
                    <div className="mt-5 flex items-center gap-3">
                      <div className="grid h-12 w-12 place-items-center rounded-2xl bg-blue-50 font-bold text-blue-700">{initials(stats.newest.fullName || stats.newest.username)}</div>
                      <div className="min-w-0">
                        <p className="truncate font-bold">{stats.newest.fullName || stats.newest.username}</p>
                        <p className="truncate text-sm text-slate-500">@{stats.newest.username}</p>
                        <p className="mt-1 text-xs text-slate-400">{formatDate(stats.newest.createdAt)}</p>
                      </div>
                    </div>
                  ) : <p className="mt-4 text-sm text-slate-500">Chưa có dữ liệu.</p>}
                </div>
              </section>
            </div>
          )}

          {activeTab === 'users' && (
            <div className="space-y-5">
              <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm xl:flex-row xl:items-center">
                <form onSubmit={handleExactSearch} className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row">
                  <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tên, username, email hoặc Keycloak ID"
                    className="min-w-0 flex-1 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10" />
                  <select value={searchMode} onChange={(event) => setSearchMode(event.target.value as SearchMode)}
                    className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold text-slate-600">
                    <option value="all">Lọc cục bộ</option><option value="username">Username chính xác</option>
                    <option value="email">Email chính xác</option><option value="id">Keycloak ID</option>
                  </select>
                  {searchMode !== 'all' && <button className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white">Tra cứu</button>}
                </form>
                <div className="flex gap-2">
                  <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}
                    className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-semibold text-slate-600">
                    <option value="ALL">Mọi trạng thái</option><option value="ACTIVE">ACTIVE</option>
                    <option value="INACTIVE">INACTIVE</option><option value="SUSPENDED">SUSPENDED</option><option value="DELETED">DELETED</option>
                  </select>
                  <button onClick={() => setShowCreateUser(true)} className="whitespace-nowrap rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-blue-700">+ Tạo tài khoản</button>
                </div>
              </div>

              <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
                  <div><h2 className="font-bold">Danh sách tài khoản</h2><p className="mt-0.5 text-xs text-slate-500">{filteredUsers.length} kết quả</p></div>
                  {searchMode !== 'all' && <button onClick={() => { setQuery(''); setSearchMode('all'); void loadUsers(); }} className="text-sm font-semibold text-blue-600">Xóa kết quả tra cứu</button>}
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[850px] text-left text-sm">
                    <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                      <tr><th className="px-5 py-3.5">Người dùng</th><th className="px-5 py-3.5">Liên hệ</th><th className="px-5 py-3.5">Trạng thái</th><th className="px-5 py-3.5">Ngày tạo</th><th className="px-5 py-3.5 text-right">Thao tác</th></tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {loading ? (
                        <tr><td colSpan={5} className="px-5 py-14 text-center text-slate-500">Đang tải dữ liệu...</td></tr>
                      ) : filteredUsers.length === 0 ? (
                        <tr><td colSpan={5} className="px-5 py-14 text-center text-slate-500">Không có người dùng phù hợp.</td></tr>
                      ) : filteredUsers.map((user) => (
                        <tr key={user.keycloakUserId} className="hover:bg-slate-50/80">
                          <td className="px-5 py-4">
                            <button onClick={() => setSelectedUser(user)} className="flex items-center gap-3 text-left">
                              <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 font-bold text-blue-700">{initials(user.fullName || user.username)}</div>
                              <div><p className="font-bold text-slate-900">{user.fullName || 'Chưa cập nhật họ tên'}</p><p className="text-xs text-slate-500">@{user.username}</p></div>
                            </button>
                          </td>
                          <td className="px-5 py-4"><p>{user.email}</p><p className="mt-1 text-xs text-slate-400">{user.phoneNumber || 'Chưa có số điện thoại'}</p></td>
                          <td className="px-5 py-4"><span className={`rounded-full px-2.5 py-1 text-xs font-bold ${user.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>{user.status}</span></td>
                          <td className="px-5 py-4 text-slate-500">{formatDate(user.createdAt)}</td>
                          <td className="px-5 py-4">
                            <div className="flex justify-end gap-2">
                              <button onClick={() => void handleRoleChange(user, 'ADMIN')} className="rounded-lg border border-blue-200 px-2.5 py-1.5 text-xs font-bold text-blue-700 hover:bg-blue-50">ADMIN</button>
                              <button onClick={() => void handleRoleChange(user, 'USER')} className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-100">USER</button>
                              <button onClick={() => void handleDelete(user)} className="rounded-lg border border-red-200 px-2.5 py-1.5 text-xs font-bold text-red-600 hover:bg-red-50">Xóa</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'groups' && <GroupsSection users={users} showToast={notify} />}

          {activeTab === 'ai-documents' && <AiDocumentsSection showToast={notify} />}
        </div>
      </main>

      {selectedUser && (
        <div className="fixed inset-0 z-40 flex justify-end bg-slate-950/35 backdrop-blur-sm" onClick={() => setSelectedUser(null)}>
          <aside className="h-full w-full max-w-md overflow-y-auto bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex justify-between"><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">User detail</p><button onClick={() => setSelectedUser(null)} className="text-slate-400">✕</button></div>
            <div className="mt-7 flex items-center gap-4"><div className="grid h-16 w-16 place-items-center rounded-2xl bg-blue-600 text-xl font-black text-white">{initials(selectedUser.fullName || selectedUser.username)}</div><div><h2 className="text-xl font-bold">{selectedUser.fullName || 'Chưa cập nhật họ tên'}</h2><p className="text-sm text-slate-500">@{selectedUser.username}</p></div></div>
            <dl className="mt-8 space-y-4 text-sm">
              {[['Email', selectedUser.email], ['Số điện thoại', selectedUser.phoneNumber || 'Chưa có'], ['Trạng thái', selectedUser.status], ['Ngày tạo', formatDate(selectedUser.createdAt)], ['Keycloak ID', selectedUser.keycloakUserId]].map(([label, value]) => (
                <div key={label} className="rounded-xl bg-slate-50 p-4"><dt className="text-xs font-bold uppercase tracking-wider text-slate-400">{label}</dt><dd className="mt-1 break-all font-semibold text-slate-800">{value}</dd></div>
              ))}
            </dl>
            <p className="mt-6 rounded-xl border border-blue-100 bg-blue-50 p-4 text-xs leading-5 text-blue-800">API hiện không trả role của người dùng và không cho admin sửa hồ sơ người khác. Vì vậy màn hình chỉ cung cấp gán role và xóa tài khoản.</p>
            <div className="mt-6 grid grid-cols-3 gap-2">
              <button onClick={() => void handleRoleChange(selectedUser, 'ADMIN')} className="rounded-xl bg-blue-600 py-2.5 text-sm font-bold text-white">Gán ADMIN</button>
              <button onClick={() => void handleRoleChange(selectedUser, 'USER')} className="rounded-xl bg-slate-100 py-2.5 text-sm font-bold text-slate-700">Gán USER</button>
              <button onClick={() => void handleDelete(selectedUser)} className="rounded-xl bg-red-50 py-2.5 text-sm font-bold text-red-600">Xóa</button>
            </div>
          </aside>
        </div>
      )}

      {showSettings && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/45 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-3xl bg-white p-6 shadow-2xl">
            <div className="flex items-start justify-between"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">Connection</p><h2 className="mt-1 text-xl font-bold">Cấu hình API Gateway</h2></div><button onClick={() => setShowSettings(false)} className="text-slate-400">✕</button></div>
            <label className="mt-6 block space-y-2 text-sm font-semibold text-slate-700">Base URL
              <input value={apiUrl} onChange={(event) => setApiUrl(event.target.value)} className="w-full rounded-xl border border-slate-200 px-4 py-2.5 outline-none focus:border-blue-500" placeholder="https://localhost:8088" />
            </label>
            <label className="mt-5 flex items-center justify-between rounded-xl bg-slate-50 p-4">
              <span><span className="block text-sm font-bold">Mock mode</span><span className="text-xs text-slate-500">Dùng dữ liệu trình duyệt để kiểm thử UI</span></span>
              <input type="checkbox" checked={mockMode} onChange={(event) => setMockMode(event.target.checked)} className="h-5 w-5 accent-blue-600" />
            </label>
            <div className="mt-6 flex justify-end gap-3"><button onClick={() => setShowSettings(false)} className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600">Hủy</button><button onClick={applySettings} className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-bold text-white">Áp dụng</button></div>
          </div>
        </div>
      )}

      {showCreateUser && <UserModal isOpen onClose={() => setShowCreateUser(false)} onSave={handleCreateUser} />}
    </div>
  );
}
