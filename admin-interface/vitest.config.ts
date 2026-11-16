import { defineConfig } from 'vitest/config';
// A separate process avoids thread-worker startup timeouts on Windows.
export default defineConfig({ test: { pool: 'forks', maxWorkers: 1 } });
