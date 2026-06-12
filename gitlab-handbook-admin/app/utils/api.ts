// ─── Domain Types ─────────────────────────────────────────────────────────────

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'DELETED';
export type UserRole   = 'EMPLOYEE' | 'CONTRACTOR' | 'ADMIN';

export interface User {
  keycloakUserId: string;
  username:       string;
  email:          string;
  fullName:       string;
  avatar?:        string;
  phoneNumber?:   string;
  status:         UserStatus;
  role?:          UserRole;
  createdAt?:     string;
  position?:      string;
  level?:         string;
  department?:    string;
  team?:          string;
  manager?:       string;
  userType?:      string;
  location?:      string;
  country?:       string;
  timezone?:      string;
  startDate?:     string;
  probationEndDate?: string;
  remainingLeaveDays?: number;
  skills?:        string;
  projects?:      string;
  github?:        string;
  slack?:         string;
}

export type GroupRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface GroupMember {
  groupId: number;
  userId: string;
  role: GroupRole;
  nickname?: string;
  invitedBy?: string;
  joinedAt?: string;
}

export interface Group {
  id: number;
  name: string;
  description?: string;
  creatorId: string;
  avatarUrl?: string;
  isActive: boolean;
  members: GroupMember[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateGroupRequest {
  name: string;
  description?: string;
  creatorId: string;
  avatarUrl?: string;
  memberIds?: string[];
}

export interface UpdateGroupRequest {
  name?: string;
  description?: string;
  avatarUrl?: string;
}

export interface AddGroupMemberRequest {
  userId: string;
  invitedBy: string;
  role?: string;
}


// ─── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginResponse {
  success: boolean; message?: string;
  accessToken?: string; refreshToken?: string; expiresIn?: number;
}

// ─── Request Payloads ─────────────────────────────────────────────────────────

/** POST /api/users  — matches CreateUserCommand */
export interface CreateUserRequest {
  username:     string;
  email:        string;
  fullName:     string;
  phoneNumber?: string;
  avatar?:      string;
  password?:    string;
}

/** PUT /api/users/{id} — matches UpdateUserCommand */
export interface UpdateUserRequest {
  fullName?:    string;
  avatar?:      string;
  phoneNumber?: string;
  status?:      UserStatus;
  username?:    string;
  newPassword?: string;
}

/** PATCH /api/users/{id}/role — matches ChangeRoleRequest */
export interface ChangeRoleRequest {
  role: UserRole;
}

// ─── API Config ───────────────────────────────────────────────────────────────

const DEFAULT_API_URL = 'https://localhost:8088';

export function getApiBaseUrl(): string {
  if (typeof window !== 'undefined') return localStorage.getItem('API_BASE_URL') || DEFAULT_API_URL;
  return DEFAULT_API_URL;
}
export function setApiBaseUrl(url: string) {
  if (typeof window !== 'undefined') localStorage.setItem('API_BASE_URL', url);
}
export function getUseMockData(): boolean {
  if (typeof window !== 'undefined') return localStorage.getItem('USE_MOCK_DATA') === 'true';
  return false;
}
export function setUseMockData(v: boolean) {
  if (typeof window !== 'undefined') localStorage.setItem('USE_MOCK_DATA', v ? 'true' : 'false');
}

// ─── Mock Data ────────────────────────────────────────────────────────────────

const MOCK_KEY = 'mock_users';
const DEFAULTS: User[] = [
  { keycloakUserId: 'kc-001', username: 'nguyenvana', email: 'vana@company.com',
    fullName: 'Nguyen Van A', phoneNumber: '0912345678', status: 'ACTIVE', role: 'EMPLOYEE', createdAt: '2025-01-15T08:00:00' },
  { keycloakUserId: 'kc-002', username: 'tranthib', email: 'thib@company.com',
    fullName: 'Tran Thi B', phoneNumber: '0987654321', status: 'ACTIVE', role: 'CONTRACTOR', createdAt: '2025-02-10T09:00:00' },
  { keycloakUserId: 'kc-003', username: 'lehoangc', email: 'hoangc@company.com',
    fullName: 'Le Hoang C', phoneNumber: '0933445566', status: 'INACTIVE', role: 'EMPLOYEE', createdAt: '2025-05-01T10:00:00' },
  { keycloakUserId: 'kc-004', username: 'admin', email: 'admin@company.com',
    fullName: 'System Administrator', status: 'ACTIVE', role: 'ADMIN', createdAt: '2024-01-01T00:00:00' },
];

function getMock(): User[] {
  if (typeof window === 'undefined') return DEFAULTS;
  const s = localStorage.getItem(MOCK_KEY);
  if (!s) { localStorage.setItem(MOCK_KEY, JSON.stringify(DEFAULTS)); return DEFAULTS; }
  return JSON.parse(s);
}
function saveMock(u: User[]) {
  if (typeof window !== 'undefined') localStorage.setItem(MOCK_KEY, JSON.stringify(u));
}

const MOCK_GROUPS_KEY = 'mock_groups';
const DEFAULT_GROUPS: Group[] = [
  {
    id: 1,
    name: 'Phòng Dự Án Alpha',
    description: 'Nơi thảo luận về các vấn đề kỹ thuật của dự án Alpha',
    creatorId: 'kc-001',
    avatarUrl: '',
    isActive: true,
    createdAt: '2025-03-01T10:00:00',
    updatedAt: '2025-03-01T10:00:00',
    members: [
      { groupId: 1, userId: 'kc-001', role: 'OWNER', nickname: 'Anh Nguyễn', invitedBy: 'system', joinedAt: '2025-03-01T10:00:00' },
      { groupId: 1, userId: 'kc-002', role: 'ADMIN', nickname: 'Chị Trần', invitedBy: 'kc-001', joinedAt: '2025-03-01T10:05:00' },
      { groupId: 1, userId: 'kc-003', role: 'MEMBER', nickname: 'Lê C', invitedBy: 'kc-002', joinedAt: '2025-03-02T11:00:00' }
    ]
  },
  {
    id: 2,
    name: 'Ban Giám Đốc',
    description: 'Kênh thông tin nội bộ của các lãnh đạo công ty',
    creatorId: 'kc-004',
    avatarUrl: '',
    isActive: true,
    createdAt: '2025-01-01T08:00:00',
    updatedAt: '2025-01-01T08:00:00',
    members: [
      { groupId: 2, userId: 'kc-004', role: 'OWNER', nickname: 'Boss Admin', invitedBy: 'system', joinedAt: '2025-01-01T08:00:00' },
      { groupId: 2, userId: 'kc-001', role: 'MEMBER', nickname: 'Trưởng Phòng A', invitedBy: 'kc-004', joinedAt: '2025-01-02T09:00:00' }
    ]
  }
];

function getMockGroups(): Group[] {
  if (typeof window === 'undefined') return DEFAULT_GROUPS;
  const s = localStorage.getItem(MOCK_GROUPS_KEY);
  if (!s) { localStorage.setItem(MOCK_GROUPS_KEY, JSON.stringify(DEFAULT_GROUPS)); return DEFAULT_GROUPS; }
  return JSON.parse(s);
}
function saveMockGroups(g: Group[]) {
  if (typeof window !== 'undefined') localStorage.setItem(MOCK_GROUPS_KEY, JSON.stringify(g));
}

// ─── HTTP Helper ──────────────────────────────────────────────────────────────

async function req<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  const h = new Headers(init.headers || {});
  h.set('Content-Type', 'application/json');
  if (token) h.set('Authorization', `Bearer ${token}`);
  const r = await fetch(`${getApiBaseUrl()}${path}`, { ...init, headers: h });
  if (!r.ok) { const t = await r.text().catch(() => `HTTP ${r.status}`); throw new Error(t); }
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('application/json')) return r.json() as Promise<T>;
  return r.text() as unknown as Promise<T>;
}

// ─── API ──────────────────────────────────────────────────────────────────────

export const api = {

  async login(username: string, password: string): Promise<LoginResponse> {
    if (getUseMockData()) {
      if (username === 'admin' && password === 'admin') {
        const r: LoginResponse = { success: true, accessToken: 'mock-token', refreshToken: 'mock-refresh', expiresIn: 3600, message: 'OK' };
        localStorage.setItem('accessToken', r.accessToken!);
        localStorage.setItem('refreshToken', r.refreshToken!);
        return r;
      }
      throw new Error('Sai thông tin đăng nhập (mock: admin / admin)');
    }
    const r = await req<LoginResponse>('/api/users/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) });
    if (r.accessToken)  localStorage.setItem('accessToken',  r.accessToken);
    if (r.refreshToken) localStorage.setItem('refreshToken', r.refreshToken);
    return r;
  },

  async logout(refreshToken: string): Promise<void> {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    if (!getUseMockData()) await req('/api/users/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) });
  },

  async getAllUsers(): Promise<User[]> {
    if (getUseMockData()) return getMock();
    return req<User[]>('/api/users');
  },

  async getUserById(id: string): Promise<User> {
    if (getUseMockData()) {
      const u = getMock().find(u => u.keycloakUserId === id);
      if (!u) throw new Error('Không tìm thấy người dùng');
      return u;
    }
    return req<User>(`/api/users/${id}`);
  },

  async getUserByUsername(username: string): Promise<User> {
    if (getUseMockData()) {
      const u = getMock().find(u => u.username === username);
      if (!u) throw new Error('Không tìm thấy username');
      return u;
    }
    return req<User>(`/api/users/username/${username}`);
  },

  async getUserByEmail(email: string): Promise<User> {
    if (getUseMockData()) {
      const u = getMock().find(u => u.email === email);
      if (!u) throw new Error('Không tìm thấy email');
      return u;
    }
    return req<User>(`/api/users/email/${email}`);
  },

  /** POST /api/users — no password (Keycloak handles credentials) */
  async createUser(data: CreateUserRequest): Promise<User> {
    if (getUseMockData()) {
      const list = getMock();
      if (list.some(u => u.username === data.username)) throw new Error(`Username '${data.username}' đã tồn tại`);
      if (list.some(u => u.email    === data.email))    throw new Error(`Email '${data.email}' đã được dùng`);
      const nu: User = { keycloakUserId: `kc-${Date.now()}`, ...data, status: 'ACTIVE', role: 'EMPLOYEE', createdAt: new Date().toISOString() };
      list.push(nu); saveMock(list); return nu;
    }
    return req<User>('/api/users', { method: 'POST', body: JSON.stringify(data) });
  },

  /** PUT /api/users/{id} — fullName, avatar, phoneNumber only */
  async updateUser(id: string, data: UpdateUserRequest): Promise<User> {
    if (getUseMockData()) {
      const list = getMock();
      const i = list.findIndex(u => u.keycloakUserId === id);
      if (i === -1) throw new Error('Người dùng không tồn tại');
      list[i] = { ...list[i], ...data };
      saveMock(list); return list[i];
    }
    return req<User>(`/api/users/${id}`, { method: 'PUT', body: JSON.stringify(data) });
  },

  /** DELETE /api/users/{id} */
  async deleteUser(id: string): Promise<void> {
    if (getUseMockData()) { saveMock(getMock().filter(u => u.keycloakUserId !== id)); return; }
    await req(`/api/users/${id}`, { method: 'DELETE' });
  },

  /** PATCH /api/users/{id}/role */
  async changeRole(id: string, role: UserRole): Promise<void> {
    if (getUseMockData()) {
      const list = getMock();
      const i = list.findIndex(u => u.keycloakUserId === id);
      if (i !== -1) { list[i] = { ...list[i], role }; saveMock(list); }
      return;
    }
    await req(`/api/users/${id}/role`, { method: 'PATCH', body: JSON.stringify({ role }) });
  },

  async getAllGroups(): Promise<Group[]> {
    if (getUseMockData()) return getMockGroups();
    return req<Group[]>('/api/groups');
  },

  async getGroupDetails(id: number): Promise<Group> {
    if (getUseMockData()) {
      const g = getMockGroups().find(g => g.id === id);
      if (!g) throw new Error('Không tìm thấy nhóm');
      return g;
    }
    return req<Group>(`/api/groups/${id}`);
  },

  async createGroup(data: CreateGroupRequest): Promise<Group> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const newId = list.length > 0 ? Math.max(...list.map(g => g.id)) + 1 : 1;
      
      const newMembers: GroupMember[] = [
        { groupId: newId, userId: data.creatorId, role: 'OWNER', nickname: '', invitedBy: 'system', joinedAt: new Date().toISOString() }
      ];
      if (data.memberIds) {
        data.memberIds.forEach(mId => {
          if (mId !== data.creatorId) {
            newMembers.push({ groupId: newId, userId: mId, role: 'MEMBER', nickname: '', invitedBy: data.creatorId, joinedAt: new Date().toISOString() });
          }
        });
      }

      const ng: Group = {
        id: newId,
        name: data.name,
        description: data.description,
        creatorId: data.creatorId,
        avatarUrl: data.avatarUrl,
        isActive: true,
        members: newMembers,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      list.push(ng);
      saveMockGroups(list);
      return ng;
    }
    return req<Group>('/api/groups', { method: 'POST', body: JSON.stringify(data) });
  },

  async updateGroup(id: number, data: UpdateGroupRequest): Promise<Group> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === id);
      if (idx === -1) throw new Error('Nhóm không tồn tại');
      list[idx] = { ...list[idx], ...data, updatedAt: new Date().toISOString() };
      saveMockGroups(list);
      return list[idx];
    }
    return req<Group>(`/api/groups/${id}`, { method: 'PUT', body: JSON.stringify(data) });
  },

  async deleteGroup(id: number, userId: string): Promise<void> {
    if (getUseMockData()) {
      const list = getMockGroups().filter(g => g.id !== id);
      saveMockGroups(list);
      return;
    }
    await req(`/api/groups/${id}?userId=${userId}`, { method: 'DELETE' });
  },

  async addGroupMember(groupId: number, data: AddGroupMemberRequest): Promise<Group> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === groupId);
      if (idx === -1) throw new Error('Nhóm không tồn tại');
      const g = list[idx];
      if (!g.members) g.members = [];
      if (g.members.some(m => m.userId === data.userId)) throw new Error('Thành viên đã ở trong nhóm');
      
      const newMember: GroupMember = {
        groupId,
        userId: data.userId,
        role: (data.role as GroupRole) || 'MEMBER',
        invitedBy: data.invitedBy,
        joinedAt: new Date().toISOString()
      };
      g.members.push(newMember);
      g.updatedAt = new Date().toISOString();
      saveMockGroups(list);
      return g;
    }
    return req<Group>(`/api/groups/${groupId}/members`, { method: 'POST', body: JSON.stringify(data) });
  },

  async removeGroupMember(groupId: number, userId: string): Promise<void> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === groupId);
      if (idx !== -1) {
        const g = list[idx];
        if (g.members) {
          g.members = g.members.filter(m => m.userId !== userId);
          g.updatedAt = new Date().toISOString();
          saveMockGroups(list);
        }
      }
      return;
    }
    await req(`/api/groups/${groupId}/members/${userId}`, { method: 'DELETE' });
  },

  async updateGroupMemberNickname(groupId: number, userId: string, nickname: string): Promise<GroupMember> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === groupId);
      if (idx === -1) throw new Error('Nhóm không tồn tại');
      const g = list[idx];
      const mIdx = g.members?.findIndex(m => m.userId === userId);
      if (mIdx === undefined || mIdx === -1) throw new Error('Thành viên không ở trong nhóm');
      const m = g.members![mIdx];
      m.nickname = nickname;
      g.updatedAt = new Date().toISOString();
      saveMockGroups(list);
      return m;
    }
    return req<GroupMember>(`/api/groups/${groupId}/members/${userId}/nickname`, {
      method: 'PUT',
      body: JSON.stringify({ nickname })
    });
  },

  async updateGroupMemberRole(groupId: number, userId: string, role: GroupRole): Promise<GroupMember> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === groupId);
      if (idx === -1) throw new Error('Nhóm không tồn tại');
      const g = list[idx];
      const mIdx = g.members?.findIndex(m => m.userId === userId);
      if (mIdx === undefined || mIdx === -1) throw new Error('Thành viên không ở trong nhóm');
      const m = g.members![mIdx];
      m.role = role;
      g.updatedAt = new Date().toISOString();
      saveMockGroups(list);
      return m;
    }
    return req<GroupMember>(`/api/groups/${groupId}/members/${userId}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role })
    });
  },

  async transferGroupOwnership(groupId: number, currentOwnerId: string, newOwnerId: string): Promise<Group> {
    if (getUseMockData()) {
      const list = getMockGroups();
      const idx = list.findIndex(g => g.id === groupId);
      if (idx === -1) throw new Error('Nhóm không tồn tại');
      const g = list[idx];
      
      const curr = g.members?.find(m => m.userId === currentOwnerId);
      if (curr) curr.role = 'MEMBER';
      
      const next = g.members?.find(m => m.userId === newOwnerId);
      if (next) next.role = 'OWNER';
      else {
        g.members?.push({
          groupId,
          userId: newOwnerId,
          role: 'OWNER',
          invitedBy: currentOwnerId,
          joinedAt: new Date().toISOString()
        });
      }
      g.updatedAt = new Date().toISOString();
      saveMockGroups(list);
      return g;
    }
    return req<Group>(`/api/groups/${groupId}/owner?currentOwnerId=${currentOwnerId}&newOwnerId=${newOwnerId}`, {
      method: 'PUT'
    });
  },

  async getGroupMembers(groupId: number): Promise<GroupMember[]> {
    if (getUseMockData()) {
      const g = getMockGroups().find(g => g.id === groupId);
      if (!g) throw new Error('Nhóm không tồn tại');
      return g.members || [];
    }
    return req<GroupMember[]>(`/api/groups/${groupId}/members`);
  }
};
