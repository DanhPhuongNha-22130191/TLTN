'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { api, Group, GroupMember, User } from '../utils/api';

interface GroupsSectionProps {
  users: User[];
  showToast: (message: string, type?: 'success' | 'error') => void;
}

const inputClass = 'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10';

function formatDate(value?: string): string {
  return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Chưa có';
}

export default function GroupsSection({ users, showToast }: GroupsSectionProps) {
  const [groupId, setGroupId] = useState('');
  const [group, setGroup] = useState<Group | null>(null);
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingGroups, setLoadingGroups] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [newMemberId, setNewMemberId] = useState('');
  const [createForm, setCreateForm] = useState({ name: '', description: '', creatorId: '', avatarUrl: '', memberIds: [] as string[] });
  const [editForm, setEditForm] = useState({ name: '', description: '', avatarUrl: '' });
  const [nicknameEditor, setNicknameEditor] = useState<{ userId: string; value: string } | null>(null);

  const userMap = useMemo(() => new Map(users.map((user) => [user.keycloakUserId, user])), [users]);
  const availableUsers = useMemo(
    () => users.filter((user) => !group?.members.some((member) => member.userId === user.keycloakUserId)),
    [group, users],
  );

  function displayUser(userId: string): string {
    const user = userMap.get(userId);
    return user ? `${user.fullName || user.username} (@${user.username})` : userId;
  }

  const loadAllGroups = useCallback(async () => {
    setLoadingGroups(true);
    try {
      const data = await api.getAllGroups();
      setGroups(data);
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể tải danh sách nhóm.', 'error');
    } finally {
      setLoadingGroups(false);
    }
  }, [showToast]);

  useEffect(() => {
    let cancelled = false;
    api.getAllGroups()
      .then((data) => {
        if (!cancelled) setGroups(data);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          showToast(error instanceof Error ? error.message : 'Không thể tải danh sách nhóm.', 'error');
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingGroups(false);
      });
    return () => {
      cancelled = true;
    };
  }, [showToast]);

  async function loadGroup(idValue: string | number = groupId) {
    const id = Number(idValue);
    if (!Number.isInteger(id) || id <= 0) {
      showToast('Vui lòng nhập ID nhóm là số nguyên dương.', 'error');
      return;
    }
    setLoading(true);
    try {
      const found = await api.getGroupDetails(id);
      setGroup(found);
      setGroupId(String(id));
    } catch (error) {
      setGroup(null);
      showToast(error instanceof Error ? error.message : 'Không thể tải nhóm.', 'error');
    } finally {
      setLoading(false);
    }
  }

  async function handleLookup(event: FormEvent) {
    event.preventDefault();
    await loadGroup();
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!createForm.creatorId) {
      showToast('Vui lòng chọn chủ sở hữu nhóm.', 'error');
      return;
    }
    try {
      const created = await api.createGroup({
        ...createForm,
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        avatarUrl: createForm.avatarUrl.trim() || undefined,
      });
      setShowCreate(false);
      setCreateForm({ name: '', description: '', creatorId: '', avatarUrl: '', memberIds: [] });
      setGroup(created);
      setGroupId(String(created.id));
      showToast(`Đã tạo nhóm #${created.id}: ${created.name}.`);
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể tạo nhóm.', 'error');
    }
  }

  function openEdit() {
    if (!group) return;
    setEditForm({ name: group.name, description: group.description || '', avatarUrl: group.avatarUrl || '' });
    setShowEdit(true);
  }

  async function handleEdit(event: FormEvent) {
    event.preventDefault();
    if (!group) return;
    try {
      const updated = await api.updateGroup(group.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim(),
        avatarUrl: editForm.avatarUrl.trim(),
      });
      setGroup(updated);
      setShowEdit(false);
      showToast('Đã cập nhật thông tin nhóm.');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể cập nhật nhóm.', 'error');
    }
  }

  async function handleDelete() {
    if (!group || !confirm(`Giải tán nhóm #${group.id} "${group.name}"?`)) return;
    try {
      await api.deleteGroup(group.id, group.creatorId);
      showToast('Đã giải tán nhóm.');
      setGroup(null);
      setGroupId('');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể xóa nhóm.', 'error');
    }
  }

  async function addMember() {
    if (!group || !newMemberId) return;
    try {
      const updated = await api.addGroupMember(group.id, newMemberId, group.creatorId);
      setGroup(updated);
      setNewMemberId('');
      showToast('Đã thêm thành viên.');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể thêm thành viên.', 'error');
    }
  }

  async function removeMember(member: GroupMember) {
    if (!group || !confirm(`Xóa ${displayUser(member.userId)} khỏi nhóm?`)) return;
    try {
      await api.removeGroupMember(group.id, member.userId);
      await loadGroup(group.id);
      showToast('Đã xóa thành viên khỏi nhóm.');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể xóa thành viên.', 'error');
    }
  }

  async function toggleRole(member: GroupMember) {
    if (!group || member.role === 'OWNER') return;
    const role = member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN';
    try {
      await api.updateGroupMemberRole(group.id, member.userId, role);
      await loadGroup(group.id);
      showToast(`Đã đổi quyền thành ${role}.`);
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể đổi quyền thành viên.', 'error');
    }
  }

  async function saveNickname() {
    if (!group || !nicknameEditor) return;
    try {
      await api.updateGroupMemberNickname(group.id, nicknameEditor.userId, nicknameEditor.value.trim());
      setNicknameEditor(null);
      await loadGroup(group.id);
      showToast('Đã cập nhật biệt danh.');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể cập nhật biệt danh.', 'error');
    }
  }

  async function transferOwner(member: GroupMember) {
    if (!group || member.role === 'OWNER' || !confirm(`Chuyển quyền sở hữu cho ${displayUser(member.userId)}?`)) return;
    try {
      const updated = await api.transferGroupOwnership(group.id, group.creatorId, member.userId);
      setGroup(updated);
      showToast('Đã chuyển quyền sở hữu nhóm.');
      void loadAllGroups();
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Không thể chuyển quyền sở hữu.', 'error');
    }
  }

  return (
    <div className="space-y-5">
      <section className="rounded-3xl bg-gradient-to-r from-[#102250] to-[#1c4ba5] p-6 text-white shadow-xl shadow-blue-950/10">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-xl">
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-200">GET /api/groups</p>
            <h2 className="mt-2 text-2xl font-black">Danh sách và quản trị nhóm chat</h2>
            <p className="mt-2 text-sm leading-6 text-blue-100/75">Xem toàn bộ nhóm hoặc nhập ID để tải chi tiết thông tin và thực hiện các thao tác quản trị nhóm.</p>
          </div>
          <form onSubmit={handleLookup} className="flex w-full gap-2 lg:max-w-md">
            <input type="number" min="1" required value={groupId} onChange={(event) => setGroupId(event.target.value)}
              placeholder="Ví dụ: 12" className="min-w-0 flex-1 rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-sm text-white outline-none placeholder:text-blue-200/60 focus:border-white/60" />
            <button disabled={loading} className="rounded-xl bg-white px-5 py-3 text-sm font-bold text-blue-800 hover:bg-blue-50 disabled:opacity-50">{loading ? 'Đang tải...' : 'Tra cứu'}</button>
          </form>
        </div>
      </section>

      <div className="flex justify-end">
        <button onClick={() => setShowCreate(true)} className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-blue-700">+ Tạo nhóm mới</button>
      </div>

      {!group && (
        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 px-5 py-4">
            <h3 className="font-bold">Danh sách tất cả nhóm ({groups.length})</h3>
            <p className="mt-0.5 text-xs text-slate-500">Tất cả nhóm chat đang hoạt động trên hệ thống.</p>
          </div>
          {loadingGroups ? (
            <div className="py-12 text-center text-sm text-slate-500">Đang tải danh sách nhóm...</div>
          ) : groups.length === 0 ? (
            <div className="py-16 text-center">
              <div className="mx-auto grid h-12 w-12 place-items-center rounded-xl bg-slate-50 text-slate-400 text-lg">✕</div>
              <h4 className="mt-3 font-bold text-sm">Chưa có nhóm nào</h4>
              <p className="mt-1 text-xs text-slate-500">Hãy nhấn &quot;+ Tạo nhóm mới&quot; để bắt đầu.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-left text-sm text-slate-600">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/75 text-xs font-bold uppercase tracking-wider text-slate-500">
                    <th className="px-5 py-3.5">ID</th>
                    <th className="px-5 py-3.5">Tên nhóm</th>
                    <th className="px-5 py-3.5">Chủ sở hữu</th>
                    <th className="px-5 py-3.5">Trạng thái</th>
                    <th className="px-5 py-3.5">Ngày tạo</th>
                    <th className="px-5 py-3.5 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {groups.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-50/50">
                      <td className="px-5 py-4 font-mono text-xs text-slate-400">#{item.id}</td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 font-bold text-blue-700">
                            {item.name.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className="block font-bold text-slate-900">{item.name}</span>
                            {item.description && <span className="block text-xs text-slate-400 truncate max-w-xs">{item.description}</span>}
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-xs font-medium">{displayUser(item.creatorId)}</td>
                      <td className="px-5 py-4">
                        <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${item.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                          {item.isActive ? 'ACTIVE' : 'INACTIVE'}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-xs text-slate-400">{formatDate(item.createdAt)}</td>
                      <td className="px-5 py-4 text-right">
                        <button onClick={() => void loadGroup(item.id)} className="rounded-lg bg-blue-50 px-3 py-1.5 text-xs font-bold text-blue-700 hover:bg-blue-100">
                          Chi tiết & Quản lý
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {group && (
        <>
          <section className="grid gap-5 xl:grid-cols-[1fr_320px]">
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex gap-4">
                  <div className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-blue-600 text-lg font-black text-white">#{group.id}</div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-xl font-bold">{group.name}</h2>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${group.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                        {group.isActive ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </div>
                    <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{group.description || 'Nhóm chưa có mô tả.'}</p>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => { setGroup(null); setGroupId(''); }} className="rounded-xl border border-slate-200 px-3.5 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">Quay lại danh sách</button>
                  <button onClick={openEdit} className="rounded-xl border border-slate-200 px-3.5 py-2 text-sm font-bold text-slate-700 hover:bg-slate-50">Sửa</button>
                  <button onClick={() => void handleDelete()} className="rounded-xl border border-red-200 px-3.5 py-2 text-sm font-bold text-red-600 hover:bg-red-50">Giải tán</button>
                </div>
              </div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Thông tin hệ thống</p>
              <dl className="mt-4 space-y-3 text-sm">
                <div>
                  <dt className="text-slate-400">Owner</dt>
                  <dd className="mt-0.5 break-all font-semibold">{displayUser(group.creatorId)}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Ngày tạo</dt>
                  <dd className="mt-0.5 font-semibold">{formatDate(group.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-slate-400">Cập nhật</dt>
                  <dd className="mt-0.5 font-semibold">{formatDate(group.updatedAt)}</dd>
                </div>
              </dl>
            </div>
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div><h3 className="font-bold">Thành viên ({group.members.length})</h3><p className="mt-0.5 text-xs text-slate-500">Quản lý role, biệt danh và quyền sở hữu.</p></div>
              <div className="flex gap-2">
                <select value={newMemberId} onChange={(event) => setNewMemberId(event.target.value)} className="min-w-0 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm">
                  <option value="">Chọn người dùng</option>
                  {availableUsers.map((user) => <option key={user.keycloakUserId} value={user.keycloakUserId}>{user.fullName || user.username} (@{user.username})</option>)}
                </select>
                <button disabled={!newMemberId} onClick={() => void addMember()} className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white disabled:opacity-40">Thêm</button>
              </div>
            </div>
            <div className="divide-y divide-slate-100">
              {group.members.map((member) => (
                <div key={member.userId} className="flex flex-col gap-4 px-5 py-4 lg:flex-row lg:items-center">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="truncate font-bold">{displayUser(member.userId)}</p>
                      <span className={`rounded-full px-2 py-0.5 text-[11px] font-black ${member.role === 'OWNER' ? 'bg-amber-50 text-amber-700' : member.role === 'ADMIN' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-600'}`}>{member.role}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-400">Biệt danh: {member.nickname || 'Chưa đặt'} · Tham gia: {formatDate(member.joinedAt)}</p>
                  </div>
                  {nicknameEditor?.userId === member.userId ? (
                    <div className="flex gap-2">
                      <input autoFocus value={nicknameEditor.value} onChange={(event) => setNicknameEditor({ ...nicknameEditor, value: event.target.value })} className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm" />
                      <button onClick={() => void saveNickname()} className="text-sm font-bold text-blue-600">Lưu</button>
                      <button onClick={() => setNicknameEditor(null)} className="text-sm text-slate-500">Hủy</button>
                    </div>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <button onClick={() => setNicknameEditor({ userId: member.userId, value: member.nickname || '' })} className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-bold text-slate-600">Biệt danh</button>
                      {member.role !== 'OWNER' && (
                        <>
                          <button onClick={() => void toggleRole(member)} className="rounded-lg border border-blue-200 px-2.5 py-1.5 text-xs font-bold text-blue-700">{member.role === 'ADMIN' ? 'Hạ MEMBER' : 'Nâng ADMIN'}</button>
                          <button onClick={() => void transferOwner(member)} className="rounded-lg border border-amber-200 px-2.5 py-1.5 text-xs font-bold text-amber-700">Chuyển OWNER</button>
                          <button onClick={() => void removeMember(member)} className="rounded-lg border border-red-200 px-2.5 py-1.5 text-xs font-bold text-red-600">Xóa khỏi nhóm</button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        </>
      )}

      {showCreate && (
        <Modal title="Tạo nhóm chat" endpoint="POST /api/groups" onClose={() => setShowCreate(false)}>
          <form onSubmit={handleCreate} className="space-y-4">
            <label className="block space-y-1.5 text-sm font-semibold">Tên nhóm <span className="text-red-500">*</span><input required value={createForm.name} onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })} className={inputClass} /></label>
            <label className="block space-y-1.5 text-sm font-semibold">Mô tả<textarea value={createForm.description} onChange={(event) => setCreateForm({ ...createForm, description: event.target.value })} className={`${inputClass} min-h-24 resize-y`} /></label>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="space-y-1.5 text-sm font-semibold">Chủ sở hữu <span className="text-red-500">*</span><select required value={createForm.creatorId} onChange={(event) => setCreateForm({ ...createForm, creatorId: event.target.value, memberIds: createForm.memberIds.filter((id) => id !== event.target.value) })} className={inputClass}><option value="">Chọn người dùng</option>{users.map((user) => <option key={user.keycloakUserId} value={user.keycloakUserId}>{user.fullName || user.username}</option>)}</select></label>
              <label className="space-y-1.5 text-sm font-semibold">URL avatar<input type="url" value={createForm.avatarUrl} onChange={(event) => setCreateForm({ ...createForm, avatarUrl: event.target.value })} className={inputClass} /></label>
            </div>
            <div>
              <p className="text-sm font-semibold">Thành viên ban đầu</p>
              <div className="mt-2 max-h-40 overflow-y-auto rounded-xl border border-slate-200 bg-white p-2">
                {users.filter((user) => user.keycloakUserId !== createForm.creatorId).map((user) => (
                  <label key={user.keycloakUserId} className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-sm hover:bg-slate-50">
                    <input type="checkbox" checked={createForm.memberIds.includes(user.keycloakUserId)} onChange={(event) => setCreateForm({ ...createForm, memberIds: event.target.checked ? [...createForm.memberIds, user.keycloakUserId] : createForm.memberIds.filter((id) => id !== user.keycloakUserId) })} className="accent-blue-600" />
                    {user.fullName || user.username} (@{user.username})
                  </label>
                ))}
              </div>
            </div>
            <ModalActions onCancel={() => setShowCreate(false)} submitLabel="Tạo nhóm" />
          </form>
        </Modal>
      )}

      {showEdit && group && (
        <Modal title={`Sửa nhóm #${group.id}`} endpoint={`PUT /api/groups/${group.id}`} onClose={() => setShowEdit(false)}>
          <form onSubmit={handleEdit} className="space-y-4">
            <label className="block space-y-1.5 text-sm font-semibold">Tên nhóm <span className="text-red-500">*</span><input required value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} className={inputClass} /></label>
            <label className="block space-y-1.5 text-sm font-semibold">Mô tả<textarea value={editForm.description} onChange={(event) => setEditForm({ ...editForm, description: event.target.value })} className={`${inputClass} min-h-24`} /></label>
            <label className="block space-y-1.5 text-sm font-semibold">URL avatar<input type="url" value={editForm.avatarUrl} onChange={(event) => setEditForm({ ...editForm, avatarUrl: event.target.value })} className={inputClass} /></label>
            <ModalActions onCancel={() => setShowEdit(false)} submitLabel="Lưu thay đổi" />
          </form>
        </Modal>
      )}
    </div>
  );
}

function Modal({ title, endpoint, onClose, children }: { title: string; endpoint: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-slate-950/45 p-4 backdrop-blur-sm">
      <div className="my-8 w-full max-w-2xl overflow-hidden rounded-3xl bg-slate-50 shadow-2xl">
        <div className="flex items-start justify-between border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-blue-600">{endpoint}</p>
            <h2 className="mt-1 text-xl font-bold">{title}</h2>
          </div>
          <button onClick={onClose} className="text-slate-400">✕</button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}

function ModalActions({ onCancel, submitLabel }: { onCancel: () => void; submitLabel: string }) {
  return (
    <div className="flex justify-end gap-3 border-t border-slate-200 pt-5">
      <button type="button" onClick={onCancel} className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600">Hủy</button>
      <button className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-bold text-white">{submitLabel}</button>
    </div>
  );
}
