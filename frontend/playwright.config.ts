import {defineConfig} from '@playwright/test';

export default defineConfig({
  testDir: 'tests',
  use: {
    baseURL: 'http://127.0.0.1:4300',
    browserName: 'chromium',
    locale: 'en-US',
    timezoneId: 'UTC',
    reducedMotion: 'reduce',
  },
  webServer: [
    {
      command: 'pnpm exec ng serve registration --host 127.0.0.1 --port 4300',
      url: 'http://127.0.0.1:4300',
      reuseExistingServer: false,
    },
    {
      command: 'pnpm exec ng serve catalogue --host 127.0.0.1 --port 4301',
      url: 'http://127.0.0.1:4301',
      reuseExistingServer: false,
    },
  ],
  snapshotPathTemplate: '{testDir}/baselines/{arg}{ext}',
});
