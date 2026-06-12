'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { api, User, getApiBaseUrl, getUseMockData, setUseMockData, setApiBaseUrl } from '../utils/api';
import UserModal from './UserModal';
import GroupsSection from './GroupsSection';

interface DashboardProps {
  onLogout: () => void;
}

export default function Dashboard({ onLogout }: DashboardProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchType, setSearchType] = useState<'all' | 'username' | 'email' | 'id'>('all');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [deptFilter, setDeptFilter] = useState('ALL');

  // Tab state
  const [activeTab, setActiveTab] = useState<'users' | 'groups'>('users');

  // Pagination states
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Modals / Drawer state
  const [isUserModalOpen, setIsUserModalOpen] = useState(false);
  const [userToEdit, setUserToEdit] = useState<User | null>(null);
  const [selectedUserDetail, setSelectedUserDetail] = useState<User | null>(null);

  // Settings
  const [showSettingsDrawer, setShowSettingsDrawer] = useState(false);
  const [apiUrlSetting, setApiUrlSetting] = useState('');
  const [useMockSetting, setUseMockSetting] = useState(false);

  // Toast notification state
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const data = await api.getAllUsers();
      setUsers(data);
      setCurrentPage(1); // Reset page to 1 on fresh load
    } catch (err: any) {
      showToast(err.message || 'Lỗi tải danh sách người dùng', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
    setApiUrlSetting(getApiBaseUrl());
    setUseMockSetting(getUseMockData());
  }, []);

  const handleLogout = async () => {
    const refreshToken = typeof window !== 'undefined' ? localStorage.getItem('refreshToken') || '' : '';
    await api.logout(refreshToken);
    onLogout();
  };

  // Perform search
  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setCurrentPage(1); // Reset to page 1 on search

    if (!searchQuery.trim()) {
      fetchUsers();
      return;
    }

    setLoading(true);
    try {
      let foundUsers: User[] = [];
      if (searchType === 'username') {
        const u = await api.getUserByUsername(searchQuery.trim());
        if (u) foundUsers = [u];
      } else if (searchType === 'email') {
        const u = await api.getUserByEmail(searchQuery.trim());
        if (u) foundUsers = [u];
      } else if (searchType === 'id') {
        const u = await api.getUserById(searchQuery.trim());
        if (u) foundUsers = [u];
      } else {
        // Local filter
        const all = await api.getAllUsers();
        foundUsers = all.filter(
          (u) =>
            u.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
            u.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
            u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
            u.keycloakUserId.toLowerCase().includes(searchQuery.toLowerCase())
        );
      }
      setUsers(foundUsers);
      showToast(`Tìm thấy ${foundUsers.length} kết quả phù hợp`);
    } catch (err: any) {
      showToast(err.message || 'Không tìm thấy người dùng phù hợp', 'error');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveUser = async (formData: any) => {
    try {
      if (userToEdit) {
        // Edit flow
        const updated = await api.updateUser(userToEdit.keycloakUserId, formData);
        showToast(`Đã cập nhật thông tin: ${updated.fullName}`);
      } else {
        // Create flow
        const created = await api.createUser(formData);
        showToast(`Đã thêm thành công nhân sự: ${created.fullName}`);
      }
      fetchUsers();
      if (selectedUserDetail && userToEdit && selectedUserDetail.keycloakUserId === userToEdit.keycloakUserId) {
        setSelectedUserDetail({ ...selectedUserDetail, ...formData });
      }
    } catch (err: any) {
      throw err;
    }
  };

  const handleDeleteUser = async (userId: string, name: string) => {
    if (!confirm(`Bạn có chắc chắn muốn xóa nhân sự ${name} (${userId}) không?`)) return;

    try {
      await api.deleteUser(userId);
      showToast(`Đã xóa thành công nhân sự: ${name}`);
      fetchUsers();
      if (selectedUserDetail?.keycloakUserId === userId) {
        setSelectedUserDetail(null);
      }
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi xóa nhân sự', 'error');
    }
  };

  const handleApplySettings = () => {
    setApiBaseUrl(apiUrlSetting);
    setUseMockData(useMockSetting);
    showToast('Đã áp dụng cấu hình kết nối mới');
    setShowSettingsDrawer(false);
    fetchUsers();
  };

  // Reset page when filters change
  const handleStatusFilterChange = (val: string) => {
    setStatusFilter(val);
    setCurrentPage(1);
  };

  const handleDeptFilterChange = (val: string) => {
    setDeptFilter(val);
    setCurrentPage(1);
  };

  // Get department options dynamically
  const departments = useMemo(() => {
    const depts = new Set<string>();
    users.forEach((u) => {
      if (u.department) depts.add(u.department);
    });
    return Array.from(depts);
  }, [users]);

  // Client-side filtering
  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchStatus = statusFilter === 'ALL' || u.status === statusFilter;
      const matchDept = deptFilter === 'ALL' || u.department === deptFilter;
      return matchStatus && matchDept;
    });
  }, [users, statusFilter, deptFilter]);

  // Pagination calculations
  const totalPages = Math.max(1, Math.ceil(filteredUsers.length / pageSize));
  
  // Adjust page if it exceeds total pages
  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [totalPages, currentPage]);

  const paginatedUsers = useMemo(() => {
    const startIndex = (currentPage - 1) * pageSize;
    return filteredUsers.slice(startIndex, startIndex + pageSize);
  }, [filteredUsers, currentPage, pageSize]);

  // Statistics
  const stats = useMemo(() => {
    const total = users.length;
    const active = users.filter((u) => u.status === 'ACTIVE').length;
    const pending = users.filter((u) => u.status === 'INACTIVE').length;
    const avgLeave = users.length
      ? Math.round(users.reduce((acc, u) => acc + (u.remainingLeaveDays || 0), 0) / users.length)
      : 0;

    return { total, active, pending, avgLeave };
  }, [users]);

  return (
    <div className="min-h-screen bg-slate-50 text-zinc-800 flex font-sans">
      {/* Toast Alert */}
      {toast && (
        <div className={`fixed bottom-5 right-5 z-[100] max-w-sm p-4 rounded-xl border shadow-xl flex items-start gap-3 transition-all transform translate-y-0 ${
          toast.type === 'success'
            ? 'bg-emerald-50 border-emerald-200 text-emerald-700'
            : 'bg-red-50 border-red-200 text-red-700'
        }`}>
          <div className="shrink-0">
            {toast.type === 'success' ? (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            ) : (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            )}
          </div>
          <div className="text-sm font-semibold">{toast.message}</div>
        </div>
      )}

      {/* Sidebar Navigation */}
      <aside className="w-64 border-r border-zinc-200 bg-white shrink-0 hidden md:flex flex-col justify-between shadow-sm">
        <div>
          {/* Logo Brand */}
          <div className="p-6 border-b border-zinc-100 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-purple-600 flex items-center justify-center font-bold text-white shadow-md shadow-indigo-600/20">
              SC
            </div>
            <div>
              <span className="font-extrabold text-zinc-950 tracking-wider block text-sm">SECRET CHAT</span>
              <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Admin Console</span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="p-4 space-y-1">
            <button
              onClick={() => { setActiveTab('users'); setSelectedUserDetail(null); }}
              className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                activeTab === 'users'
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-zinc-500 hover:text-zinc-900 hover:bg-zinc-50'
              }`}
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
              </svg>
              Quản Lý Nhân Sự
            </button>

            <button
              onClick={() => { setActiveTab('groups'); setSelectedUserDetail(null); }}
              className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                activeTab === 'groups'
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-zinc-500 hover:text-zinc-900 hover:bg-zinc-50'
              }`}
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
              </svg>
              Quản Lý Nhóm Chat
            </button>

            <button
              onClick={() => setShowSettingsDrawer(true)}
              className="w-full flex items-center gap-3 px-4 py-2.5 text-zinc-500 hover:text-zinc-900 hover:bg-zinc-50 rounded-xl text-sm font-semibold transition-all text-left"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              Cấu Hình Hệ Thống
            </button>
          </nav>
        </div>

        {/* User profile & Logout */}
        <div className="p-4 border-t border-zinc-100 bg-zinc-50/40">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 rounded-full bg-zinc-200 flex items-center justify-center font-bold text-zinc-700">
              QT
            </div>
            <div>
              <span className="block text-sm font-semibold text-zinc-950">Quản Trị Viên</span>
              <span className="block text-xs text-zinc-400">Master Console</span>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2 py-2 border border-zinc-200 hover:border-red-200 hover:text-red-600 text-zinc-600 rounded-xl text-sm font-semibold transition-all hover:bg-red-50"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            Đăng Xuất
          </button>
        </div>
      </aside>

      {/* Main Panel Content Area */}
      <main className="flex-1 min-w-0 flex flex-col">
        {/* Header Search & Actions */}
        <header className="h-16 border-b border-zinc-200 bg-white flex items-center justify-between px-6 z-20 shrink-0 shadow-sm">
          {activeTab === 'users' ? (
            <div className="flex items-center gap-4 flex-1 max-w-xl">
              <form onSubmit={handleSearch} className="relative w-full flex items-center gap-2">
                <div className="relative flex-1">
                  <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center text-zinc-400">
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                  </span>
                  <input
                    type="text"
                    placeholder={
                      searchType === 'username' ? 'Tìm bằng chính xác Username...' :
                      searchType === 'email' ? 'Tìm bằng chính xác Email...' :
                      searchType === 'id' ? 'Tìm bằng chính xác User ID...' :
                      'Tìm kiếm nhân sự trên hệ thống...'
                    }
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-1.5 bg-zinc-50 border border-zinc-200 rounded-xl text-zinc-800 outline-none focus:border-indigo-500 text-sm focus:ring-1 focus:ring-indigo-500"
                  />
                </div>

                <select
                  value={searchType}
                  onChange={(e) => setSearchType(e.target.value as any)}
                  className="px-2 py-1.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-600 outline-none focus:border-indigo-500"
                >
                  <option value="all">Tìm Toàn Bộ</option>
                  <option value="username">Bằng Username</option>
                  <option value="email">Bằng Email</option>
                  <option value="id">Bằng User ID</option>
                </select>

                <button
                  type="submit"
                  className="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold text-xs transition-all shadow-sm"
                >
                  Tìm
                </button>
              </form>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <h2 className="text-base font-extrabold text-zinc-950 tracking-wide">QUẢN LÝ NHÓM CHAT</h2>
            </div>
          )}

          <div className="flex items-center gap-3">
            <span className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 bg-zinc-50 border border-zinc-200 rounded-xl text-xs text-zinc-500">
              <span className={`w-2 h-2 rounded-full ${getUseMockData() ? 'bg-amber-500' : 'bg-emerald-500 animate-pulse'}`} />
              API: {getUseMockData() ? 'Dữ liệu Giả Lập' : 'Cổng 8088 (API Thật)'}
            </span>

            {activeTab === 'users' && (
              <button
                onClick={() => {
                  setUserToEdit(null);
                  setIsUserModalOpen(true);
                }}
                className="px-4 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-xl text-sm font-semibold transition-all shadow-md shadow-indigo-600/10 flex items-center gap-2"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                Thêm Nhân Sự
              </button>
            )}
          </div>
        </header>

        {/* Dashboard Panels */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {activeTab === 'users' ? (
            <>
              {/* Stat Cards */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white border border-zinc-200/80 p-5 rounded-2xl flex items-center justify-between shadow-sm">
              <div>
                <span className="text-zinc-400 text-xs font-bold uppercase tracking-wider block">Tổng Nhân Sự</span>
                <span className="text-3xl font-extrabold text-zinc-950 mt-1 block">
                  {loading ? '...' : stats.total}
                </span>
              </div>
              <div className="w-12 h-12 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-100">
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
              </div>
            </div>

            <div className="bg-white border border-zinc-200/80 p-5 rounded-2xl flex items-center justify-between shadow-sm">
              <div>
                <span className="text-zinc-400 text-xs font-bold uppercase tracking-wider block">Đang Hoạt Động</span>
                <span className="text-3xl font-extrabold text-emerald-600 mt-1 block">
                  {loading ? '...' : stats.active}
                </span>
              </div>
              <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-100">
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
            </div>

            <div className="bg-white border border-zinc-200/80 p-5 rounded-2xl flex items-center justify-between shadow-sm">
              <div>
                <span className="text-zinc-400 text-xs font-bold uppercase tracking-wider block">Chờ Kích Hoạt</span>
                <span className="text-3xl font-extrabold text-amber-600 mt-1 block">
                  {loading ? '...' : stats.pending}
                </span>
              </div>
              <div className="w-12 h-12 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-100">
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
            </div>

            <div className="bg-white border border-zinc-200/80 p-5 rounded-2xl flex items-center justify-between shadow-sm">
              <div>
                <span className="text-zinc-400 text-xs font-bold uppercase tracking-wider block">Nghỉ Phép Trung Bình</span>
                <span className="text-3xl font-extrabold text-indigo-600 mt-1 block">
                  {loading ? '...' : `${stats.avgLeave} ngày`}
                </span>
              </div>
              <div className="w-12 h-12 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-100">
                <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </div>
            </div>
          </div>

          {/* Filtering controls */}
          <div className="bg-white border border-zinc-200/80 p-4 rounded-2xl flex flex-wrap items-center justify-between gap-4 shadow-sm">
            <div className="flex flex-wrap items-center gap-4">
              <div>
                <label className="block text-zinc-400 text-[10px] font-semibold uppercase tracking-wider mb-1">Trạng thái</label>
                <select
                  value={statusFilter}
                  onChange={(e) => handleStatusFilterChange(e.target.value)}
                  className="bg-zinc-50 border border-zinc-200 rounded-xl px-3 py-1.5 text-xs text-zinc-700 outline-none focus:border-indigo-500"
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="ACTIVE">ACTIVE (Hoạt động)</option>
                  <option value="INACTIVE">INACTIVE (Chưa hoạt động)</option>
                  <option value="DEACTIVATED">DEACTIVATED (Vô hiệu)</option>
                  <option value="PENDING">PENDING (Chờ phê duyệt)</option>
                </select>
              </div>

              <div>
                <label className="block text-zinc-400 text-[10px] font-semibold uppercase tracking-wider mb-1">Phòng ban (Department)</label>
                <select
                  value={deptFilter}
                  onChange={(e) => handleDeptFilterChange(e.target.value)}
                  className="bg-zinc-50 border border-zinc-200 rounded-xl px-3 py-1.5 text-xs text-zinc-700 outline-none focus:border-indigo-500"
                >
                  <option value="ALL">Tất cả phòng ban</option>
                  {departments.map((dept) => (
                    <option key={dept} value={dept}>
                      {dept}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="text-xs text-zinc-500">
                Hiển thị <span className="text-zinc-950 font-bold">{filteredUsers.length}</span> /{' '}
                <span className="text-zinc-950 font-bold">{users.length}</span> nhân sự
              </div>

              <div className="flex items-center gap-2">
                <label className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider">Cỡ trang:</label>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(parseInt(e.target.value, 10));
                    setCurrentPage(1);
                  }}
                  className="bg-zinc-50 border border-zinc-200 rounded-xl px-2 py-1 text-xs text-zinc-700 outline-none focus:border-indigo-500"
                >
                  <option value="5">5 dòng</option>
                  <option value="10">10 dòng</option>
                  <option value="25">25 dòng</option>
                  <option value="50">50 dòng</option>
                  <option value="100">100 dòng</option>
                </select>
              </div>
            </div>
          </div>

          {/* Main User List Table */}
          <div className="bg-white border border-zinc-200/80 rounded-2xl overflow-hidden shadow-sm flex flex-col">
            {loading ? (
              <div className="p-12 flex flex-col items-center justify-center gap-3">
                <svg className="animate-spin h-8 w-8 text-indigo-600" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                <span className="text-zinc-500 text-sm font-semibold">Đang đồng bộ dữ liệu API thật...</span>
              </div>
            ) : paginatedUsers.length === 0 ? (
              <div className="p-12 text-center text-zinc-400">
                <svg className="w-12 h-12 mx-auto mb-3 text-zinc-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <p className="text-sm font-semibold">Không tìm thấy thông tin nhân sự</p>
                <p className="text-xs text-zinc-400 mt-1">Vui lòng reset các bộ lọc hoặc kiểm tra lại kết nối đến cổng 8080.</p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="bg-zinc-50 border-b border-zinc-200 text-[10px] font-bold text-zinc-500 uppercase tracking-wider">
                        <th className="px-6 py-4">Mã Nhân Viên</th>
                        <th className="px-6 py-4">Username</th>
                        <th className="px-6 py-4">Họ và Tên</th>
                        <th className="px-6 py-4">Email</th>
                        <th className="px-6 py-4">Vị trí / Level</th>
                        <th className="px-6 py-4">Phòng ban</th>
                        <th className="px-6 py-4">Trạng thái</th>
                        <th className="px-6 py-4 text-right">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-100">
                      {paginatedUsers.map((u) => (
                        <tr
                          key={u.keycloakUserId}
                          onClick={() => setSelectedUserDetail(u)}
                          className="hover:bg-slate-50/70 transition-colors cursor-pointer group text-sm"
                        >
                          <td className="px-6 py-4 font-mono font-bold text-indigo-600">{u.keycloakUserId}</td>
                          <td className="px-6 py-4 font-semibold text-zinc-950">{u.username}</td>
                          <td className="px-6 py-4 text-zinc-800">{u.fullName}</td>
                          <td className="px-6 py-4 text-zinc-500">{u.email}</td>
                          <td className="px-6 py-4 text-xs font-semibold text-zinc-700">
                            {u.position || '—'}
                            {u.level && <span className="ml-1.5 text-[9px] bg-zinc-100 text-zinc-600 px-1 py-0.5 rounded">{u.level}</span>}
                          </td>
                          <td className="px-6 py-4 text-zinc-500 text-xs">{u.department || '—'}</td>
                          <td className="px-6 py-4" onClick={(e) => e.stopPropagation()}>
                            <div className="flex flex-col gap-1 items-start">
                              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                                  u.status === 'ACTIVE'
                                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                    : (u.status === 'SUSPENDED' || u.status === 'DELETED')
                                    ? 'bg-red-50 text-red-700 border border-red-200'
                                    : 'bg-zinc-100 text-zinc-600 border border-zinc-200'
                                }`}>
                                  <span className={`w-1.5 h-1.5 rounded-full ${u.status === 'ACTIVE' ? 'bg-emerald-500' : (u.status === 'SUSPENDED' || u.status === 'DELETED') ? 'bg-red-500' : 'bg-zinc-400'}`} />
                                {u.status}
                              </span>
                            </div>
                          </td>
                          <td className="px-6 py-4 text-right" onClick={(e) => e.stopPropagation()}>
                            <div className="flex items-center justify-end gap-2 opacity-60 group-hover:opacity-100 transition-opacity">
                              <button
                                onClick={() => {
                                  setUserToEdit(u);
                                  setIsUserModalOpen(true);
                                }}
                                title="Sửa thông tin"
                                className="p-1.5 text-zinc-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-all border border-transparent hover:border-indigo-100"
                              >
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                                </svg>
                              </button>

                              <button
                                onClick={() => handleDeleteUser(u.keycloakUserId, u.fullName)}
                                title="Xóa nhân sự"
                                className="p-1.5 text-zinc-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-all border border-transparent hover:border-red-100"
                              >
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                </svg>
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination Controls Footer */}
                <div className="px-6 py-4 border-t border-zinc-100 bg-zinc-50/50 flex flex-col sm:flex-row items-center justify-between gap-4">
                  <div className="text-xs text-zinc-500">
                    Hiển thị từ <span className="font-semibold text-zinc-800">{filteredUsers.length ? (currentPage - 1) * pageSize + 1 : 0}</span> đến{' '}
                    <span className="font-semibold text-zinc-800">{Math.min(currentPage * pageSize, filteredUsers.length)}</span> trong tổng số{' '}
                    <span className="font-semibold text-zinc-800">{filteredUsers.length}</span> nhân sự
                  </div>

                  <div className="flex items-center gap-1">
                    {/* Previous page button */}
                    <button
                      onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                      disabled={currentPage === 1}
                      className="px-3 py-1.5 border border-zinc-200 rounded-lg text-xs font-semibold text-zinc-600 hover:bg-white disabled:opacity-40 disabled:hover:bg-transparent transition-colors disabled:cursor-not-allowed"
                    >
                      Trang trước
                    </button>

                    {/* Page Numbers */}
                    {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => {
                      // Only show a sliding window of page numbers if too many
                      if (totalPages > 6 && Math.abs(page - currentPage) > 2 && page !== 1 && page !== totalPages) {
                        if (page === 2 || page === totalPages - 1) {
                          return <span key={page} className="px-1.5 text-zinc-400 text-xs">...</span>;
                        }
                        return null;
                      }

                      return (
                        <button
                          key={page}
                          onClick={() => setCurrentPage(page)}
                          className={`w-8 h-8 rounded-lg text-xs font-semibold flex items-center justify-center transition-all ${
                            currentPage === page
                              ? 'bg-indigo-600 text-white shadow-sm'
                              : 'border border-zinc-200 text-zinc-600 hover:bg-white'
                          }`}
                        >
                          {page}
                        </button>
                      );
                    })}

                    {/* Next page button */}
                    <button
                      onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}
                      disabled={currentPage === totalPages}
                      className="px-3 py-1.5 border border-zinc-200 rounded-lg text-xs font-semibold text-zinc-600 hover:bg-white disabled:opacity-40 disabled:hover:bg-transparent transition-colors disabled:cursor-not-allowed"
                    >
                      Trang sau
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>
          </>
          ) : (
            <GroupsSection users={users} showToast={showToast} />
          )}
        </div>
      </main>

      {/* Slide-out User Profile details Drawer */}
      {selectedUserDetail && (
        <div className="fixed inset-0 z-40 flex justify-end bg-black/30 backdrop-blur-xs" onClick={() => setSelectedUserDetail(null)}>
          <div
            className="w-full max-w-lg bg-white border-l border-zinc-200 shadow-2xl h-full flex flex-col relative z-50 animate-slide-in"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Drawer Header */}
            <div className="p-6 border-b border-zinc-200 bg-zinc-50 flex items-center justify-between">
              <div>
                <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Hồ Sơ Chi Tiết Nhân Sự</span>
                <h3 className="text-xl font-bold text-zinc-950 mt-1">{selectedUserDetail.fullName}</h3>
              </div>
              <button
                onClick={() => setSelectedUserDetail(null)}
                className="text-zinc-400 hover:text-zinc-600 p-1 hover:bg-zinc-100 rounded-lg"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Drawer Body Info */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Profile Card Header */}
              <div className="flex items-center gap-4 bg-zinc-50 p-4 rounded-2xl border border-zinc-200/80">
                <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-indigo-600 to-purple-600 text-white font-extrabold text-2xl flex items-center justify-center shadow-sm">
                  {selectedUserDetail.fullName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-zinc-900 font-bold">{selectedUserDetail.username}</span>
                    <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-bold ${
                      selectedUserDetail.status === 'ACTIVE'
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-zinc-100 text-zinc-600'
                    }`}>
                      {selectedUserDetail.status}
                    </span>
                  </div>
                  <span className="block text-xs text-zinc-500 mt-1">{selectedUserDetail.email}</span>
                  {selectedUserDetail.phoneNumber && (
                    <span className="block text-xs text-zinc-400 mt-0.5">{selectedUserDetail.phoneNumber}</span>
                  )}
                </div>
              </div>

              {/* Department details */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider">Công việc & Chức vụ</h4>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Phòng ban</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.department || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Nhóm (Team)</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.team || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Vị trí</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.position || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Level</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.level || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Người quản lý</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.manager || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Loại hợp đồng</span>
                    <span className="text-zinc-900 font-semibold mt-1 block">{selectedUserDetail.userType || '—'}</span>
                  </div>
                </div>
              </div>

              {/* Geographic details */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider">Địa điểm & Múi giờ</h4>
                <div className="grid grid-cols-3 gap-3 text-xs">
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Địa điểm</span>
                    <span className="text-zinc-900 font-semibold mt-0.5 block">{selectedUserDetail.location || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Quốc gia</span>
                    <span className="text-zinc-900 font-semibold mt-0.5 block">{selectedUserDetail.country || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Múi giờ</span>
                    <span className="text-zinc-900 font-semibold mt-0.5 block">{selectedUserDetail.timezone || '—'}</span>
                  </div>
                </div>
              </div>

              {/* Dates and Leave */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider">Thời gian & Ngày phép</h4>
                <div className="grid grid-cols-3 gap-3 text-xs">
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Ngày bắt đầu</span>
                    <span className="text-zinc-900 font-semibold mt-0.5 block">{selectedUserDetail.startDate || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Hết thử việc</span>
                    <span className="text-zinc-900 font-semibold mt-0.5 block">{selectedUserDetail.probationEndDate || '—'}</span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200">
                    <span className="text-zinc-400 block">Phép còn lại</span>
                    <span className="text-indigo-600 font-bold mt-0.5 block">{selectedUserDetail.remainingLeaveDays ?? 0} Ngày</span>
                  </div>
                </div>
              </div>

              {/* Skills and Projects */}
              <div className="space-y-4">
                <div>
                  <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider mb-2">Kỹ năng chuyên môn</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedUserDetail.skills ? (
                      selectedUserDetail.skills.split(',').map((s) => (
                        <span key={s.trim()} className="bg-zinc-100 border border-zinc-200 text-zinc-700 px-2.5 py-1 rounded-lg text-[10px] font-semibold">
                          {s.trim()}
                        </span>
                      ))
                    ) : (
                      <span className="text-zinc-400 text-xs italic">Chưa cập nhật kỹ năng</span>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider mb-2">Dự án tham gia</h4>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedUserDetail.projects ? (
                      selectedUserDetail.projects.split(',').map((p) => (
                        <span key={p.trim()} className="bg-indigo-50 border border-indigo-100 text-indigo-700 px-2.5 py-1 rounded-lg text-[10px] font-semibold">
                          {p.trim()}
                        </span>
                      ))
                    ) : (
                      <span className="text-zinc-400 text-xs italic">Chưa gán dự án</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Contacts */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider">Liên hệ mạng xã hội</h4>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200 flex items-center gap-2">
                    <span className="text-zinc-400 font-semibold">GitHub:</span>
                    <span className="text-zinc-900 font-semibold truncate">
                      {selectedUserDetail.github ? (
                        <a href={`https://${selectedUserDetail.github}`} target="_blank" rel="noreferrer" className="hover:underline text-indigo-600">
                          {selectedUserDetail.github.replace('github.com/', '')}
                        </a>
                      ) : (
                        '—'
                      )}
                    </span>
                  </div>
                  <div className="bg-zinc-50/40 p-2.5 rounded-xl border border-zinc-200 flex items-center gap-2">
                    <span className="text-zinc-400 font-semibold">Slack:</span>
                    <span className="text-zinc-900 font-semibold truncate">{selectedUserDetail.slack || '—'}</span>
                  </div>
                </div>
              </div>

              {/* User ID block */}
              <div className="pt-2 text-[10px] text-zinc-400 font-mono flex items-center justify-between border-t border-zinc-200">
                <span>Mã định danh User: {selectedUserDetail.keycloakUserId}</span>
              </div>
            </div>

            {/* Drawer Footer Actions */}
            <div className="p-4 border-t border-zinc-200 bg-zinc-50 flex items-center gap-3">
              <button
                onClick={() => {
                  setUserToEdit(selectedUserDetail);
                  setIsUserModalOpen(true);
                }}
                className="flex-1 py-2 bg-zinc-100 hover:bg-zinc-200 text-zinc-800 rounded-xl text-xs font-semibold transition-colors border border-zinc-200"
              >
                Sửa Thông Tin
              </button>
              <button
                onClick={() => handleDeleteUser(selectedUserDetail.keycloakUserId, selectedUserDetail.fullName)}
                className="py-2 px-3 border border-red-200 hover:bg-red-50 text-red-600 rounded-xl text-xs font-semibold transition-colors"
              >
                Xóa Nhân Sự
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Floating Developer settings modal */}
      {showSettingsDrawer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-xs p-4">
          <div className="w-full max-w-md bg-white border border-zinc-200 rounded-2xl shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-200 bg-zinc-50">
              <h3 className="text-lg font-bold text-zinc-950">Cấu hình kết nối API</h3>
              <button
                onClick={() => setShowSettingsDrawer(false)}
                className="text-zinc-400 hover:text-zinc-650 rounded-lg p-1 hover:bg-zinc-100 transition-colors"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="p-6 space-y-4">
              <div>
                <label className="block text-zinc-500 text-xs font-semibold mb-1.5">Địa chỉ API Gateway/Backend</label>
                <input
                  type="text"
                  value={apiUrlSetting}
                  onChange={(e) => setApiUrlSetting(e.target.value)}
                  placeholder="http://localhost:8080"
                  className="w-full px-3 py-2 bg-zinc-50 border border-zinc-200 rounded-xl text-zinc-800 text-xs outline-none focus:border-indigo-500"
                />
              </div>

              <div className="flex items-center justify-between pt-2">
                <div>
                  <span className="block text-zinc-800 text-xs font-semibold">Chạy Chế Độ Giả Lập (Mock Mode)</span>
                  <span className="block text-zinc-400 text-[10px] mt-0.5">Sử dụng dữ liệu tạm trong localStorage</span>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={useMockSetting}
                    onChange={(e) => setUseMockSetting(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-9 h-5 bg-zinc-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-zinc-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-indigo-600"></div>
                </label>
              </div>

              <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-200">
                <button
                  type="button"
                  onClick={() => setShowSettingsDrawer(false)}
                  className="px-4 py-2 border border-zinc-200 text-zinc-600 rounded-xl hover:bg-zinc-100 hover:text-zinc-800 transition-all text-xs font-semibold"
                >
                  Hủy bỏ
                </button>
                <button
                  type="button"
                  onClick={handleApplySettings}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-all text-xs shadow-sm"
                >
                  Lưu cấu hình
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Shared Modals */}
      <UserModal
        isOpen={isUserModalOpen}
        onClose={() => {
          setIsUserModalOpen(false);
          setUserToEdit(null);
        }}
        onSave={handleSaveUser}
        userToEdit={userToEdit}
      />
    </div>
  );
}
