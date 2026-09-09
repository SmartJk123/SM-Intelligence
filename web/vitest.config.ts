import { defineConfig } from 'vitest/config';
// Worker threads avoid slow child-process startup on Windows development machines.
export default defineConfig({ test: { pool: 'threads', maxWorkers: 1 } });
