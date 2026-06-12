'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { api, Group, GroupMember, GroupRole, User } from '../utils/api';

interface GroupsSectionProps {
  users: User[];
  showToast: (msg: string, type?: 'success' | 'error') => void;
}

export default function GroupsSection({ users, showToast }: GroupsSectionProps) {
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Selected group details
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);

  // Modals / forms state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [newGroupData, setNewGroupData] = useState({
    name: '',
    description: '',
    creatorId: '',
    avatarUrl: '',
    memberIds: [] as string[]
  });
  const [editGroupData, setEditGroupData] = useState({
    id: 0,
    name: '',
    description: '',
    avatarUrl: ''
  });

  // Member management inside drawer
  const [newMemberId, setNewMemberId] = useState('');
  const [editingNicknameUserId, setEditingNicknameUserId] = useState<string | null>(null);
  const [tempNickname, setTempNickname] = useState('');

  const fetchGroups = async () => {
    setLoading(true);
    try {
      const data = await api.getAllGroups();
      setGroups(data);
    } catch (err: any) {
      showToast(err.message || 'Lỗi tải danh sách nhóm chat', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchGroups();
  }, []);

  // Sync selected group if groups list updates
  useEffect(() => {
    if (selectedGroup) {
      const updated = groups.find(g => g.id === selectedGroup.id);
      if (updated) {
        setSelectedGroup(updated);
      } else {
        setSelectedGroup(null);
      }
    }
  }, [groups]);

  // User display name helper
  const getUserDisplayName = (userId: string) => {
    const u = users.find(usr => usr.keycloakUserId === userId);
    return u ? `${u.fullName} (@${u.username})` : userId;
  };

  // Handle Create Group
  const handleCreateGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newGroupData.name.trim()) {
      showToast('Tên nhóm không được để trống', 'error');
      return;
    }
    if (!newGroupData.creatorId) {
      showToast('Vui lòng chọn người tạo nhóm', 'error');
      return;
    }

    try {
      const created = await api.createGroup({
        name: newGroupData.name.trim(),
        description: newGroupData.description.trim(),
        creatorId: newGroupData.creatorId,
        avatarUrl: newGroupData.avatarUrl.trim(),
        memberIds: [newGroupData.creatorId, ...newGroupData.memberIds]
      });
      showToast(`Đã tạo thành công nhóm: ${created.name}`);
      setIsCreateModalOpen(false);
      setNewGroupData({ name: '', description: '', creatorId: '', avatarUrl: '', memberIds: [] });
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi tạo nhóm', 'error');
    }
  };

  // Handle Edit Group Info
  const handleEditGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editGroupData.name.trim()) {
      showToast('Tên nhóm không được để trống', 'error');
      return;
    }

    try {
      const updated = await api.updateGroup(editGroupData.id, {
        name: editGroupData.name.trim(),
        description: editGroupData.description.trim(),
        avatarUrl: editGroupData.avatarUrl.trim()
      });
      showToast(`Đã cập nhật thông tin nhóm: ${updated.name}`);
      setIsEditModalOpen(false);
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi cập nhật nhóm', 'error');
    }
  };

  // Handle Delete Group
  const handleDeleteGroup = async (groupId: number, groupName: string, creatorId: string) => {
    if (!confirm(`Bạn có chắc chắn muốn giải tán nhóm "${groupName}"? Hành động này không thể hoàn tác.`)) {
      return;
    }

    try {
      await api.deleteGroup(groupId, creatorId);
      showToast(`Đã giải tán nhóm: ${groupName}`);
      setSelectedGroup(null);
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi giải tán nhóm', 'error');
    }
  };

  // Handle Add Member
  const handleAddMember = async () => {
    if (!selectedGroup) return;
    if (!newMemberId) {
      showToast('Vui lòng chọn thành viên cần thêm', 'error');
      return;
    }

    try {
      await api.addGroupMember(selectedGroup.id, {
        userId: newMemberId,
        invitedBy: 'admin',
        role: 'MEMBER'
      });
      showToast('Đã thêm thành viên vào nhóm');
      setNewMemberId('');
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi thêm thành viên', 'error');
    }
  };

  // Handle Remove Member
  const handleRemoveMember = async (userId: string, displayName: string) => {
    if (!selectedGroup) return;
    if (!confirm(`Bạn có muốn xóa thành viên ${displayName} ra khỏi nhóm?`)) return;

    try {
      await api.removeGroupMember(selectedGroup.id, userId);
      showToast('Đã xóa thành viên khỏi nhóm');
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi xóa thành viên', 'error');
    }
  };

  // Handle Change Role
  const handleChangeRole = async (userId: string, currentRole: GroupRole) => {
    if (!selectedGroup) return;
    const nextRole: GroupRole = currentRole === 'ADMIN' ? 'MEMBER' : 'ADMIN';
    
    try {
      await api.updateGroupMemberRole(selectedGroup.id, userId, nextRole);
      showToast(`Đã thay đổi quyền thành viên thành ${nextRole}`);
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi thay đổi quyền', 'error');
    }
  };

  // Handle Transfer Ownership
  const handleTransferOwnership = async (newOwnerId: string, displayName: string) => {
    if (!selectedGroup) return;
    const owner = selectedGroup.members?.find(m => m.role === 'OWNER');
    const currentOwnerId = owner ? owner.userId : selectedGroup.creatorId;

    if (!confirm(`Bạn có chắc chắn muốn chuyển quyền sở hữu nhóm cho ${displayName}?`)) return;

    try {
      await api.transferGroupOwnership(selectedGroup.id, currentOwnerId, newOwnerId);
      showToast(`Đã chuyển quyền sở hữu thành công cho ${displayName}`);
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi chuyển quyền sở hữu', 'error');
    }
  };

  // Handle Nickname Save
  const handleSaveNickname = async (userId: string) => {
    if (!selectedGroup) return;
    try {
      await api.updateGroupMemberNickname(selectedGroup.id, userId, tempNickname.trim());
      showToast('Đã cập nhật biệt danh');
      setEditingNicknameUserId(null);
      fetchGroups();
    } catch (err: any) {
      showToast(err.message || 'Lỗi khi cập nhật biệt danh', 'error');
    }
  };

  // Filtering groups
  const filteredGroups = useMemo(() => {
    return groups.filter(g =>
      g.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (g.description && g.description.toLowerCase().includes(searchQuery.toLowerCase())) ||
      g.id.toString() === searchQuery.trim()
    );
  }, [groups, searchQuery]);

  // Pagination calculations
  const totalPages = Math.max(1, Math.ceil(filteredGroups.length / pageSize));
  const paginatedGroups = useMemo(() => {
    const startIndex = (currentPage - 1) * pageSize;
    return filteredGroups.slice(startIndex, startIndex + pageSize);
  }, [filteredGroups, currentPage, pageSize]);

  // Adjust page if it exceeds total pages
  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [totalPages, currentPage]);

  const openEditModal = (group: Group) => {
    setEditGroupData({
      id: group.id,
      name: group.name,
      description: group.description || '',
      avatarUrl: group.avatarUrl || ''
    });
    setIsEditModalOpen(true);
  };

  return (
    <div className="space-y-6">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4 bg-white border border-zinc-200/80 p-4 rounded-2xl shadow-sm">
        <div className="relative w-full sm:max-w-xs">
          <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center text-zinc-400">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </span>
          <input
            type="text"
            placeholder="Tìm kiếm nhóm chat..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(1);
            }}
            className="w-full pl-10 pr-4 py-1.5 bg-zinc-50 border border-zinc-200 rounded-xl text-zinc-800 outline-none focus:border-indigo-500 text-sm focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <button
          onClick={() => setIsCreateModalOpen(true)}
          className="w-full sm:w-auto px-4 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-xl text-sm font-semibold transition-all shadow-md shadow-indigo-600/10 flex items-center justify-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Tạo Nhóm Mới
        </button>
      </div>

      {/* Main Groups List Table */}
      <div className="bg-white border border-zinc-200/80 rounded-2xl overflow-hidden shadow-sm flex flex-col">
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center gap-3">
            <svg className="animate-spin h-8 w-8 text-indigo-600" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
            <span className="text-zinc-500 text-sm font-semibold">Đang tải danh sách nhóm chat...</span>
          </div>
        ) : paginatedGroups.length === 0 ? (
          <div className="p-12 text-center text-zinc-400">
            <svg className="w-12 h-12 mx-auto mb-3 text-zinc-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
            <p className="text-sm font-semibold">Không tìm thấy nhóm chat nào</p>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-zinc-50 border-b border-zinc-200 text-[10px] font-bold text-zinc-500 uppercase tracking-wider">
                    <th className="px-6 py-4">Mã Nhóm</th>
                    <th className="px-6 py-4">Tên Nhóm</th>
                    <th className="px-6 py-4">Mô Tả</th>
                    <th className="px-6 py-4">Người Tạo</th>
                    <th className="px-6 py-4">Số Thành Viên</th>
                    <th className="px-6 py-4">Trạng Thái</th>
                    <th className="px-6 py-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100">
                  {paginatedGroups.map((g) => (
                    <tr
                      key={g.id}
                      onClick={() => setSelectedGroup(g)}
                      className="hover:bg-slate-50/70 transition-colors cursor-pointer group text-sm"
                    >
                      <td className="px-6 py-4 font-mono font-bold text-indigo-600">#{g.id}</td>
                      <td className="px-6 py-4 font-semibold text-zinc-950">
                        <div className="flex items-center gap-2">
                          <div className="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs shrink-0 overflow-hidden">
                            {g.avatarUrl ? (
                              <img src={g.avatarUrl} alt="" className="w-full h-full object-cover" />
                            ) : (
                              g.name.slice(0, 2).toUpperCase()
                            )}
                          </div>
                          <span className="truncate max-w-[150px]">{g.name}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-zinc-500 text-xs truncate max-w-[200px]">
                        {g.description || <span className="italic text-zinc-300">Không có mô tả</span>}
                      </td>
                      <td className="px-6 py-4 text-zinc-700 font-medium">
                        {getUserDisplayName(g.creatorId)}
                      </td>
                      <td className="px-6 py-4 font-semibold text-zinc-900">
                        {g.members?.length || 0} thành viên
                      </td>
                      <td className="px-6 py-4" onClick={(e) => e.stopPropagation()}>
                        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          g.isActive
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-zinc-100 text-zinc-600 border border-zinc-200'
                        }`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${g.isActive ? 'bg-emerald-500' : 'bg-zinc-400'}`} />
                          {g.isActive ? 'HOẠT ĐỘNG' : 'TẠM KHÓA'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-2 opacity-60 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => openEditModal(g)}
                            title="Sửa thông tin nhóm"
                            className="p-1.5 text-zinc-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-all border border-transparent hover:border-indigo-100"
                          >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                            </svg>
                          </button>
                          <button
                            onClick={() => handleDeleteGroup(g.id, g.name, g.creatorId)}
                            title="Giải tán nhóm"
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

            {/* Pagination Controls */}
            <div className="px-6 py-4 border-t border-zinc-100 bg-zinc-50/50 flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="text-xs text-zinc-500">
                Hiển thị từ <span className="font-semibold text-zinc-800">{filteredGroups.length ? (currentPage - 1) * pageSize + 1 : 0}</span> đến{' '}
                <span className="font-semibold text-zinc-800">{Math.min(currentPage * pageSize, filteredGroups.length)}</span> trong tổng số{' '}
                <span className="font-semibold text-zinc-800">{filteredGroups.length}</span> nhóm chat
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}
                  disabled={currentPage === 1}
                  className="px-3 py-1.5 border border-zinc-200 rounded-lg text-xs font-semibold text-zinc-600 hover:bg-white disabled:opacity-40 disabled:hover:bg-transparent transition-colors disabled:cursor-not-allowed"
                >
                  Trang trước
                </button>

                {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
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
                ))}

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

      {/* Slide-out Group Detail Drawer */}
      {selectedGroup && (
        <div className="fixed inset-0 z-40 flex justify-end bg-black/30 backdrop-blur-xs" onClick={() => setSelectedGroup(null)}>
          <div
            className="w-full max-w-2xl bg-white border-l border-zinc-200 shadow-2xl h-full flex flex-col relative z-50 animate-slide-in"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Drawer Header */}
            <div className="p-6 border-b border-zinc-200 bg-zinc-50 flex items-center justify-between">
              <div>
                <span className="text-[10px] text-zinc-400 font-bold uppercase tracking-wider block">Quản lý chi tiết nhóm chat</span>
                <h3 className="text-xl font-bold text-zinc-950 mt-1">{selectedGroup.name}</h3>
              </div>
              <button
                onClick={() => setSelectedGroup(null)}
                className="text-zinc-400 hover:text-zinc-600 p-1 hover:bg-zinc-100 rounded-lg"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Drawer Body */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Group Info Card */}
              <div className="flex items-center gap-4 bg-zinc-50 p-4 rounded-2xl border border-zinc-200/80">
                <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-indigo-600 to-purple-600 text-white font-extrabold text-2xl flex items-center justify-center shadow-sm overflow-hidden shrink-0">
                  {selectedGroup.avatarUrl ? (
                    <img src={selectedGroup.avatarUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    selectedGroup.name.slice(0, 2).toUpperCase()
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-zinc-900 font-bold text-lg truncate">{selectedGroup.name}</span>
                    <span className="bg-indigo-50 border border-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full text-[9px] font-bold shrink-0">
                      ID: #{selectedGroup.id}
                    </span>
                  </div>
                  <span className="block text-xs text-zinc-500 mt-1">
                    {selectedGroup.description || <span className="italic">Không có mô tả cho nhóm chat này.</span>}
                  </span>
                  <span className="block text-[10px] text-zinc-400 mt-1.5">
                    Người tạo: <span className="font-semibold">{getUserDisplayName(selectedGroup.creatorId)}</span>
                  </span>
                </div>
              </div>

              {/* Members Section */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-indigo-600 uppercase tracking-wider">
                    Thành viên nhóm ({selectedGroup.members?.length || 0})
                  </h4>
                </div>

                {/* Add Member Form */}
                <div className="flex gap-2 bg-slate-50 p-3 rounded-xl border border-zinc-200">
                  <select
                    value={newMemberId}
                    onChange={(e) => setNewMemberId(e.target.value)}
                    className="flex-1 px-3 py-1.5 bg-white border border-zinc-200 rounded-lg text-xs outline-none focus:border-indigo-500"
                  >
                    <option value="">-- Chọn nhân sự để thêm --</option>
                    {users
                      .filter(u => !selectedGroup.members?.some(m => m.userId === u.keycloakUserId))
                      .map(u => (
                        <option key={u.keycloakUserId} value={u.keycloakUserId}>
                          {u.fullName} (@{u.username})
                        </option>
                      ))}
                  </select>
                  <button
                    onClick={handleAddMember}
                    className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold transition-all shadow-sm"
                  >
                    Thêm
                  </button>
                </div>

                {/* Members List */}
                <div className="border border-zinc-200 rounded-xl divide-y divide-zinc-250/70 overflow-hidden bg-white">
                  {selectedGroup.members?.map((member) => {
                    const displayName = getUserDisplayName(member.userId);
                    const isOwner = member.role === 'OWNER';
                    const isAdmin = member.role === 'ADMIN';

                    return (
                      <div key={member.userId} className="p-3 flex items-center justify-between gap-4 text-xs hover:bg-slate-50/50 transition-colors">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="font-bold text-zinc-950">{displayName}</span>
                            <span className={`px-1.5 py-0.5 rounded text-[8px] font-bold ${
                              isOwner ? 'bg-red-50 text-red-750 border border-red-200' :
                              isAdmin ? 'bg-indigo-50 text-indigo-750 border border-indigo-200' :
                              'bg-zinc-150 text-zinc-600 border border-zinc-200'
                            }`}>
                              {member.role}
                            </span>
                          </div>
                          
                          {/* Nickname display */}
                          <div className="mt-1 flex items-center gap-2 text-[10px] text-zinc-400">
                            {editingNicknameUserId === member.userId ? (
                              <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                                <input
                                  type="text"
                                  value={tempNickname}
                                  onChange={(e) => setTempNickname(e.target.value)}
                                  placeholder="Nhập biệt danh..."
                                  className="px-2 py-0.5 border rounded outline-none w-32"
                                />
                                <button
                                  onClick={() => handleSaveNickname(member.userId)}
                                  className="text-emerald-600 hover:text-emerald-700 font-bold"
                                >
                                  Lưu
                                </button>
                                <button
                                  onClick={() => setEditingNicknameUserId(null)}
                                  className="text-zinc-400 hover:text-zinc-600"
                                >
                                  Hủy
                                </button>
                              </div>
                            ) : (
                              <>
                                <span>Biệt danh: {member.nickname || <span className="italic text-zinc-300">Không có</span>}</span>
                                <button
                                  onClick={() => {
                                    setEditingNicknameUserId(member.userId);
                                    setTempNickname(member.nickname || '');
                                  }}
                                  className="text-indigo-600 hover:underline text-[9px]"
                                >
                                  Sửa
                                </button>
                              </>
                            )}
                          </div>
                        </div>

                        {/* Actions for member */}
                        <div className="flex items-center gap-1.5 shrink-0">
                          {/* Promotion / Demotion (only if not Owner) */}
                          {!isOwner && (
                            <button
                              onClick={() => handleChangeRole(member.userId, member.role)}
                              title={isAdmin ? 'Hạ quyền xuống Member' : 'Thăng quyền lên Admin'}
                              className="px-2 py-1 bg-slate-50 border border-zinc-200 hover:bg-slate-100 rounded text-[10px] font-semibold text-zinc-600 transition-all"
                            >
                              {isAdmin ? 'MEMBER' : 'ADMIN'}
                            </button>
                          )}

                          {/* Transfer Ownership (only if not Owner) */}
                          {!isOwner && (
                            <button
                              onClick={() => handleTransferOwnership(member.userId, displayName)}
                              title="Chuyển quyền OWNER"
                              className="px-2 py-1 bg-amber-50 border border-amber-205 hover:bg-amber-100 rounded text-[10px] font-semibold text-amber-700 transition-all"
                            >
                              OWNER
                            </button>
                          )}

                          {/* Remove from group */}
                          {!isOwner && (
                            <button
                              onClick={() => handleRemoveMember(member.userId, displayName)}
                              title="Xóa khỏi nhóm"
                              className="p-1 hover:bg-red-50 text-zinc-400 hover:text-red-600 rounded border border-transparent hover:border-red-100 transition-all"
                            >
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                              </svg>
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Drawer Footer */}
            <div className="p-4 border-t border-zinc-200 bg-zinc-50 flex items-center gap-3">
              <button
                onClick={() => openEditModal(selectedGroup)}
                className="flex-1 py-2 bg-zinc-100 hover:bg-zinc-200 text-zinc-800 rounded-xl text-xs font-semibold transition-colors border border-zinc-200 text-center"
              >
                Sửa Thông Tin Nhóm
              </button>
              <button
                onClick={() => handleDeleteGroup(selectedGroup.id, selectedGroup.name, selectedGroup.creatorId)}
                className="py-2 px-3 border border-red-200 hover:bg-red-50 text-red-600 rounded-xl text-xs font-semibold transition-colors"
              >
                Giải Tán Nhóm
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Group Modal */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="w-full max-w-xl bg-white border border-zinc-200 rounded-2xl shadow-2xl my-10">
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 bg-gradient-to-r from-indigo-50/70 to-purple-50/70 rounded-t-2xl">
              <div>
                <p className="text-[10px] font-extrabold uppercase tracking-widest text-indigo-400">Nhóm chat</p>
                <h3 className="text-base font-extrabold text-zinc-900 mt-0.5">Tạo nhóm chat mới</h3>
              </div>
              <button
                onClick={() => setIsCreateModalOpen(false)}
                className="p-1.5 rounded-xl text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 transition-colors"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleCreateGroup} className="p-6 space-y-4">
              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
                  Tên nhóm <span className="text-red-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Phòng Kế Toán"
                  value={newGroupData.name}
                  onChange={(e) => setNewGroupData(p => ({ ...p, name: e.target.value }))}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">Mô tả</label>
                <textarea
                  placeholder="Mô tả mục đích nhóm chat..."
                  value={newGroupData.description}
                  onChange={(e) => setNewGroupData(p => ({ ...p, description: e.target.value }))}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all h-24 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
                    Người tạo (Owner) <span className="text-red-400">*</span>
                  </label>
                  <select
                    required
                    value={newGroupData.creatorId}
                    onChange={(e) => setNewGroupData(p => ({ ...p, creatorId: e.target.value }))}
                    className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                  >
                    <option value="">-- Chọn Trưởng Nhóm --</option>
                    {users.map(u => (
                      <option key={u.keycloakUserId} value={u.keycloakUserId}>
                        {u.fullName} (@{u.username})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">URL Avatar nhóm</label>
                  <input
                    type="text"
                    placeholder="https://..."
                    value={newGroupData.avatarUrl}
                    onChange={(e) => setNewGroupData(p => ({ ...p, avatarUrl: e.target.value }))}
                    className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">Chọn thành viên ban đầu</label>
                <div className="border border-zinc-200 rounded-xl max-h-40 overflow-y-auto divide-y divide-zinc-100 p-2 bg-zinc-50">
                  {users
                    .filter(u => u.keycloakUserId !== newGroupData.creatorId)
                    .map(u => (
                      <label key={u.keycloakUserId} className="flex items-center gap-3 p-2 hover:bg-white rounded-lg cursor-pointer text-xs font-semibold">
                        <input
                          type="checkbox"
                          checked={newGroupData.memberIds.includes(u.keycloakUserId)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setNewGroupData(p => ({ ...p, memberIds: [...p.memberIds, u.keycloakUserId] }));
                            } else {
                              setNewGroupData(p => ({ ...p, memberIds: p.memberIds.filter(id => id !== u.keycloakUserId) }));
                            }
                          }}
                          className="rounded text-indigo-600 focus:ring-indigo-500"
                        />
                        <span>{u.fullName} (@{u.username})</span>
                      </label>
                    ))}
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-zinc-150">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="px-5 py-2 border border-zinc-200 text-zinc-600 rounded-xl hover:bg-zinc-100 text-sm font-semibold transition-all"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="px-6 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-xl font-semibold text-sm shadow-md shadow-indigo-600/15 transition-all"
                >
                  Tạo Nhóm
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Group Modal */}
      {isEditModalOpen && (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 backdrop-blur-sm p-4 overflow-y-auto">
          <div className="w-full max-w-xl bg-white border border-zinc-200 rounded-2xl shadow-2xl my-10">
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100 bg-gradient-to-r from-indigo-50/70 to-purple-50/70 rounded-t-2xl">
              <div>
                <p className="text-[10px] font-extrabold uppercase tracking-widest text-indigo-400">Nhóm chat</p>
                <h3 className="text-base font-extrabold text-zinc-900 mt-0.5">Sửa thông tin nhóm chat</h3>
              </div>
              <button
                onClick={() => setIsEditModalOpen(false)}
                className="p-1.5 rounded-xl text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 transition-colors"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleEditGroup} className="p-6 space-y-4">
              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">
                  Tên nhóm <span className="text-red-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Phòng Kế Toán"
                  value={editGroupData.name}
                  onChange={(e) => setEditGroupData(p => ({ ...p, name: e.target.value }))}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">Mô tả</label>
                <textarea
                  placeholder="Mô tả mục đích nhóm chat..."
                  value={editGroupData.description}
                  onChange={(e) => setEditGroupData(p => ({ ...p, description: e.target.value }))}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all h-24 resize-none"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold uppercase tracking-wider text-zinc-500 mb-1.5">URL Avatar nhóm</label>
                <input
                  type="text"
                  placeholder="https://..."
                  value={editGroupData.avatarUrl}
                  onChange={(e) => setEditGroupData(p => ({ ...p, avatarUrl: e.target.value }))}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-900 placeholder:text-zinc-400 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/15 transition-all"
                />
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-zinc-150">
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="px-5 py-2 border border-zinc-200 text-zinc-600 rounded-xl hover:bg-zinc-100 text-sm font-semibold transition-all"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="px-6 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-xl font-semibold text-sm shadow-md shadow-indigo-600/15 transition-all"
                >
                  Cập Nhật
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
