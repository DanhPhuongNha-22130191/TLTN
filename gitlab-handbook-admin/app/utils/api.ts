export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'DELETED';
export type UserRole = 'ADMIN' | 'USER';
export type GroupRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export type AccessLevel = 'STAFF' | 'LEAD' | 'MANAGER' | 'DIRECTOR';
export type AccessUnit = 'ENGINEERING' | 'HR' | 'SALES' | 'SUPPORT' | 'OPERATIONS';

export interface UserAccessProfile {
  role: UserRole;
  level: AccessLevel;
  unit: AccessUnit;
}

export interface User {
  keycloakUserId: string;
  username: string;
  email: string;
  fullName?: string;
  avatar?: string;
  phoneNumber?: string;
  status: UserStatus;
  createdAt?: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  fullName?: string;
  phoneNumber?: string;
  avatar?: string;
}

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

export interface LoginResponse {
  success: boolean;
  message?: string;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
}



const DEFAULT_API_URL = 'http://localhost:8088';
const MOCK_USERS_KEY = 'admin_mock_users';
const MOCK_GROUPS_KEY = 'admin_mock_groups';
const USER_ACCESS_KEY = 'admin_user_access_profiles';


const defaultUsers: User[] = [
  {
    keycloakUserId: 'kc-admin',
    username: 'admin',
    email: 'admin@secretchat.local',
    fullName: 'System Administrator',
    status: 'ACTIVE',
    createdAt: '2026-01-01T08:00:00',
  },
  {
    keycloakUserId: 'kc-user-01',
    username: 'minh.nguyen',
    email: 'minh.nguyen@secretchat.local',
    fullName: 'Nguyen Minh',
    phoneNumber: '0912345678',
    status: 'ACTIVE',
    createdAt: '2026-02-14T09:30:00',
  },
];

const defaultGroups: Group[] = [
  {
    id: 1,
    name: 'Engineering',
    description: 'Trao đổi nội bộ của nhóm kỹ thuật',
    creatorId: 'kc-admin',
    isActive: true,
    createdAt: '2026-03-01T10:00:00',
    updatedAt: '2026-03-01T10:00:00',
    members: [
      { groupId: 1, userId: 'kc-admin', role: 'OWNER', joinedAt: '2026-03-01T10:00:00' },
      { groupId: 1, userId: 'kc-user-01', role: 'MEMBER', invitedBy: 'kc-admin', joinedAt: '2026-03-01T10:05:00' },
    ],
  },
];

export function getApiBaseUrl(): string {
  if (typeof window === 'undefined') return DEFAULT_API_URL;
  return localStorage.getItem('API_BASE_URL') || DEFAULT_API_URL;
}

export function setApiBaseUrl(url: string): void {
  if (typeof window !== 'undefined') {
    localStorage.setItem('API_BASE_URL', url.replace(/\/+$/, ''));
  }
}

export function getUseMockData(): boolean {
  return typeof window !== 'undefined' && localStorage.getItem('USE_MOCK_DATA') === 'true';
}

export function setUseMockData(value: boolean): void {
  if (typeof window !== 'undefined') localStorage.setItem('USE_MOCK_DATA', String(value));
}

function readMock<T>(key: string, initialValue: T): T {
  if (typeof window === 'undefined') return initialValue;
  const stored = localStorage.getItem(key);
  if (stored) return JSON.parse(stored) as T;
  localStorage.setItem(key, JSON.stringify(initialValue));
  return initialValue;
}

function writeMock(key: string, value: unknown): void {
  if (typeof window !== 'undefined') localStorage.setItem(key, JSON.stringify(value));
}

function mockUsers(): User[] {
  return readMock(MOCK_USERS_KEY, defaultUsers);
}

function mockGroups(): Group[] {
  return readMock(MOCK_GROUPS_KEY, defaultGroups);
}

function defaultAccessProfiles(): Record<string, UserAccessProfile> {
  return {
    'kc-admin': { role: 'ADMIN', level: 'DIRECTOR', unit: 'ENGINEERING' },
    'kc-user-01': { role: 'USER', level: 'STAFF', unit: 'ENGINEERING' },
  };
}

function mockAccessProfiles(): Record<string, UserAccessProfile> {
  return readMock(USER_ACCESS_KEY, defaultAccessProfiles());
}



async function parseResponse<T = void>(response: Response): Promise<T> {
  if (response.status === 401) {
    throw new Error('Phiên đăng nhập đã hết hạn hoặc không hợp lệ.');
  }
  if (response.status === 403) {
    throw new Error('Tài khoản không có quyền quản trị.');
  }
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    try {
      const parsed = JSON.parse(body) as { message?: string; error?: string; detail?: string };
      throw new Error(parsed.message || parsed.error || parsed.detail || `Yêu cầu thất bại (HTTP ${response.status}).`);
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new Error(body || `Yêu cầu thất bại (HTTP ${response.status}).`);
      }
      throw error;
    }
  }
  if (response.status === 204) return undefined as T;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json')
    ? await response.json() as T
    : await response.text() as unknown as T;
}

async function request<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
  headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${getApiBaseUrl()}${path}`, { ...init, headers });
  if (response.status === 401) {
    throw new Error(path.endsWith('/auth/login')
      ? 'Username hoặc mật khẩu không chính xác.'
      : 'Phiên đăng nhập đã hết hạn hoặc không hợp lệ.');
  }
  return parseResponse<T>(response);
}

export const api = {
  async login(username: string, password: string): Promise<LoginResponse> {
    if (getUseMockData()) {
      if (username !== 'admin' || password !== 'admin') {
        throw new Error('Tài khoản mock: admin / admin');
      }
      const result = { success: true, accessToken: 'mock-token', refreshToken: 'mock-refresh', expiresIn: 3600 };
      localStorage.setItem('accessToken', result.accessToken);
      localStorage.setItem('refreshToken', result.refreshToken);
      return result;
    }
    const result = await request<LoginResponse>('/api/users/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    if (!result.success || !result.accessToken) {
      throw new Error(result.message || 'Đăng nhập thất bại.');
    }
    localStorage.setItem('accessToken', result.accessToken);
    if (result.refreshToken) localStorage.setItem('refreshToken', result.refreshToken);
    return result;
  },

  async logout(): Promise<void> {
    const refreshToken = typeof window !== 'undefined' ? localStorage.getItem('refreshToken') : null;
    try {
      if (!getUseMockData() && refreshToken) {
        await request('/api/users/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        });
      }
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }
  },

  async getAllUsers(): Promise<User[]> {
    return getUseMockData() ? mockUsers() : request<User[]>('/api/users');
  },

  async getUserById(id: string): Promise<User> {
    if (!getUseMockData()) return request<User>(`/api/users/${encodeURIComponent(id)}`);
    const user = mockUsers().find((item) => item.keycloakUserId === id);
    if (!user) throw new Error('Không tìm thấy người dùng.');
    return user;
  },

  async getUserByUsername(username: string): Promise<User> {
    if (!getUseMockData()) return request<User>(`/api/users/username/${encodeURIComponent(username)}`);
    const user = mockUsers().find((item) => item.username === username);
    if (!user) throw new Error('Không tìm thấy username.');
    return user;
  },

  async getUserByEmail(email: string): Promise<User> {
    if (!getUseMockData()) return request<User>(`/api/users/email/${encodeURIComponent(email)}`);
    const user = mockUsers().find((item) => item.email === email);
    if (!user) throw new Error('Không tìm thấy email.');
    return user;
  },

  async createUser(data: CreateUserRequest): Promise<User> {
    if (!getUseMockData()) {
      return request<User>('/api/users', { method: 'POST', body: JSON.stringify(data) });
    }
    const users = mockUsers();
    if (users.some((item) => item.username === data.username || item.email === data.email)) {
      throw new Error('Username hoặc email đã tồn tại.');
    }
    const created: User = {
      keycloakUserId: `kc-${Date.now()}`,
      username: data.username,
      email: data.email,
      fullName: data.fullName || '',
      phoneNumber: data.phoneNumber,
      avatar: data.avatar,
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
    };
    writeMock(MOCK_USERS_KEY, [...users, created]);
    return created;
  },

  async deleteUser(id: string): Promise<void> {
    if (!getUseMockData()) {
      await request(`/api/users/${encodeURIComponent(id)}`, { method: 'DELETE' });
      return;
    }
    writeMock(MOCK_USERS_KEY, mockUsers().filter((item) => item.keycloakUserId !== id));
  },

  async changeRole(id: string, role: UserRole): Promise<void> {
    if (getUseMockData()) {
      const profiles = mockAccessProfiles();
      profiles[id] = { role, level: profiles[id]?.level || 'STAFF', unit: profiles[id]?.unit || 'ENGINEERING' };
      writeMock(USER_ACCESS_KEY, profiles);
      return;
    }
    await request(`/api/users/${encodeURIComponent(id)}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    });
  },

  async changeStatus(id: string, status: Exclude<UserStatus, 'DELETED'>): Promise<User> {
    if (!getUseMockData()) {
      return request<User>(`/api/users/${encodeURIComponent(id)}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
    }
    const users = mockUsers();
    const index = users.findIndex((item) => item.keycloakUserId === id);
    if (index < 0) throw new Error('Không tìm thấy người dùng.');
    users[index] = { ...users[index], status };
    writeMock(MOCK_USERS_KEY, users);
    return users[index];
  },

  async getUserAccessProfiles(): Promise<Record<string, UserAccessProfile>> {
    return mockAccessProfiles();
  },

  async saveUserAccessProfile(id: string, profile: UserAccessProfile): Promise<void> {
    if (!getUseMockData()) {
      await request(`/api/users/${encodeURIComponent(id)}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role: profile.role }),
      });
    }
    const profiles = mockAccessProfiles();
    profiles[id] = profile;
    writeMock(USER_ACCESS_KEY, profiles);
  },

  async getAllGroups(): Promise<Group[]> {
    if (!getUseMockData()) return request<Group[]>('/api/groups');
    return mockGroups();
  },

  async getGroupDetails(id: number): Promise<Group> {
    if (!getUseMockData()) return request<Group>(`/api/groups/${id}`);
    const group = mockGroups().find((item) => item.id === id);
    if (!group) throw new Error(`Không tìm thấy nhóm #${id}.`);
    return group;
  },

  async createGroup(data: CreateGroupRequest): Promise<Group> {
    if (!getUseMockData()) {
      return request<Group>('/api/groups', { method: 'POST', body: JSON.stringify(data) });
    }
    const groups = mockGroups();
    const id = groups.length ? Math.max(...groups.map((item) => item.id)) + 1 : 1;
    const now = new Date().toISOString();
    const memberIds = [...new Set(data.memberIds || [])].filter((memberId) => memberId !== data.creatorId);
    const created: Group = {
      id,
      name: data.name,
      description: data.description,
      creatorId: data.creatorId,
      avatarUrl: data.avatarUrl,
      isActive: true,
      createdAt: now,
      updatedAt: now,
      members: [
        { groupId: id, userId: data.creatorId, role: 'OWNER', joinedAt: now },
        ...memberIds.map((userId) => ({ groupId: id, userId, role: 'MEMBER' as const, invitedBy: data.creatorId, joinedAt: now })),
      ],
    };
    writeMock(MOCK_GROUPS_KEY, [...groups, created]);
    return created;
  },

  async updateGroup(id: number, data: UpdateGroupRequest): Promise<Group> {
    if (!getUseMockData()) {
      return request<Group>(`/api/groups/${id}`, { method: 'PUT', body: JSON.stringify(data) });
    }
    const groups = mockGroups();
    const index = groups.findIndex((item) => item.id === id);
    if (index < 0) throw new Error('Nhóm không tồn tại.');
    groups[index] = { ...groups[index], ...data, updatedAt: new Date().toISOString() };
    writeMock(MOCK_GROUPS_KEY, groups);
    return groups[index];
  },

  async deleteGroup(id: number, ownerId: string): Promise<void> {
    if (!getUseMockData()) {
      await request(`/api/groups/${id}?userId=${encodeURIComponent(ownerId)}`, { method: 'DELETE' });
      return;
    }
    writeMock(MOCK_GROUPS_KEY, mockGroups().filter((item) => item.id !== id));
  },

  async addGroupMember(groupId: number, userId: string, invitedBy: string): Promise<Group> {
    if (!getUseMockData()) {
      return request<Group>(`/api/groups/${groupId}/members`, {
        method: 'POST',
        body: JSON.stringify({ userId, invitedBy, role: 'MEMBER' }),
      });
    }
    const group = mockGroups().find((item) => item.id === groupId);
    if (!group) throw new Error('Nhóm không tồn tại.');
    if (group.members.some((member) => member.userId === userId)) throw new Error('Người dùng đã ở trong nhóm.');
    group.members.push({ groupId, userId, invitedBy, role: 'MEMBER', joinedAt: new Date().toISOString() });
    group.updatedAt = new Date().toISOString();
    const groups = mockGroups().map((item) => item.id === groupId ? group : item);
    writeMock(MOCK_GROUPS_KEY, groups);
    return group;
  },

  async removeGroupMember(groupId: number, userId: string): Promise<void> {
    if (!getUseMockData()) {
      await request(`/api/groups/${groupId}/members/${encodeURIComponent(userId)}`, { method: 'DELETE' });
      return;
    }
    const groups = mockGroups();
    const group = groups.find((item) => item.id === groupId);
    if (group) group.members = group.members.filter((member) => member.userId !== userId);
    writeMock(MOCK_GROUPS_KEY, groups);
  },

  async updateGroupMemberNickname(groupId: number, userId: string, nickname: string): Promise<GroupMember> {
    if (!getUseMockData()) {
      return request<GroupMember>(`/api/groups/${groupId}/members/${encodeURIComponent(userId)}/nickname`, {
        method: 'PUT',
        body: JSON.stringify({ nickname }),
      });
    }
    const groups = mockGroups();
    const member = groups.find((item) => item.id === groupId)?.members.find((item) => item.userId === userId);
    if (!member) throw new Error('Không tìm thấy thành viên.');
    member.nickname = nickname;
    writeMock(MOCK_GROUPS_KEY, groups);
    return member;
  },

  async updateGroupMemberRole(groupId: number, userId: string, role: Exclude<GroupRole, 'OWNER'>): Promise<GroupMember> {
    if (!getUseMockData()) {
      return request<GroupMember>(`/api/groups/${groupId}/members/${encodeURIComponent(userId)}/role`, {
        method: 'PUT',
        body: JSON.stringify({ role }),
      });
    }
    const groups = mockGroups();
    const member = groups.find((item) => item.id === groupId)?.members.find((item) => item.userId === userId);
    if (!member) throw new Error('Không tìm thấy thành viên.');
    member.role = role;
    writeMock(MOCK_GROUPS_KEY, groups);
    return member;
  },

  async transferGroupOwnership(groupId: number, currentOwnerId: string, newOwnerId: string): Promise<Group> {
    if (!getUseMockData()) {
      return request<Group>(`/api/groups/${groupId}/owner?currentOwnerId=${encodeURIComponent(currentOwnerId)}&newOwnerId=${encodeURIComponent(newOwnerId)}`, {
        method: 'PUT',
      });
    }
    const groups = mockGroups();
    const group = groups.find((item) => item.id === groupId);
    if (!group) throw new Error('Nhóm không tồn tại.');
    group.members.forEach((member) => {
      if (member.userId === currentOwnerId) member.role = 'MEMBER';
      if (member.userId === newOwnerId) member.role = 'OWNER';
    });
    group.creatorId = newOwnerId;
    writeMock(MOCK_GROUPS_KEY, groups);
    return group;
  },
};
