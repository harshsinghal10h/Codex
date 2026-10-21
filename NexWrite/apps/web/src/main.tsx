// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import React from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/inter';
import '@fontsource-variable/space-grotesk';
import '@fontsource-variable/jetbrains-mono';
import '@nexwrite/design-tokens/css';
import './style.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(<App />);

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  // Failure leaves the network app functional; no document data is sent here.
  navigator.serviceWorker.register('/sw.js').catch(() => {});
}
