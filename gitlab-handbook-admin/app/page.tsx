'use client';

import React, { useSyncExternalStore } from 'react';
import Login from './components/Login';
import Dashboard from './components/Dashboard';

const AUTH_EVENT = 'secret-chat-auth-change';

function subscribeToAuth(callback: () => void) {
  window.addEventListener('storage', callback);
  window.addEventListener(AUTH_EVENT, callback);
  return () => {
    window.removeEventListener('storage', callback);
    window.removeEventListener(AUTH_EVENT, callback);
  };
}

function getAuthSnapshot() {
  return Boolean(localStorage.getItem('accessToken'));
}

export default function Page() {
  const isAuthenticated = useSyncExternalStore(subscribeToAuth, getAuthSnapshot, () => false);

  const handleLoginSuccess = () => {
    window.dispatchEvent(new Event(AUTH_EVENT));
  };

  const handleLogout = () => {
    window.dispatchEvent(new Event(AUTH_EVENT));
  };

  if (isAuthenticated) {
    return <Dashboard onLogout={handleLogout} />;
  }

  return <Login onLoginSuccess={handleLoginSuccess} />;
}
