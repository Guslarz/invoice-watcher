import {test, expect} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('REG-009: sign-in is accessible and password controls are absent', async ({
  page,
}) => {
  await page.route('**/api/session', r =>
    r.fulfill({json: {authenticated: false}}),
  );
  await page.goto('/');
  await expect(
    page.getByRole('button', {name: 'Sign in with Google'}),
  ).toBeVisible();
  await expect(page.locator('input[type=password]')).toHaveCount(0);
  expect((await new AxeBuilder({page}).analyze()).violations).toEqual([]);
});
test('REG-003 REG-005 REG-009: existing date is read-only and folder edits retain identity', async ({
  page,
}) => {
  const configuration = {
    email: 'user@example.com',
    version: 2,
    connected: true,
    reconnectionRequired: false,
    folders: [{id: 'f1', name: 'INBOX', startDate: '2026-01-01T00:00:00Z'}],
  };
  await page.route('**/api/session', r =>
    r.fulfill({json: {authenticated: true, email: configuration.email}}),
  );
  await page.route('**/api/csrf', r =>
    r.fulfill({json: {headerName: 'X-CSRF-TOKEN', token: 'fixture'}}),
  );
  await page.route('**/api/v1/me', r => r.fulfill({json: configuration}));
  await page.route('**/api/v1/me/configuration', async r => {
    expect(r.request().postDataJSON().folders[0].id).toBe('f1');
    expect(r.request().headers()['x-csrf-token']).toBe('fixture');
    await r.fulfill({json: {...configuration, version: 3}});
  });
  await page.goto('/');
  await expect(page.getByLabel('Start date')).toHaveAttribute('readonly', '');
  await page.getByRole('button', {name: 'Save folders'}).click();
  await expect(
    page.getByRole('status').filter({hasText: 'saved'}),
  ).toBeVisible();
  expect((await new AxeBuilder({page}).analyze()).violations).toEqual([]);
});
test('RELEASE-040: library states, focus, encapsulation and visual baseline', async ({
  page,
}) => {
  await page.goto('http://127.0.0.1:4301');
  await page.getByRole('button', {name: 'Save folders'}).focus();
  await expect(page.getByRole('button', {name: 'Save folders'})).toBeFocused();
  await expect(page.getByRole('button', {name: 'Save folders'})).toHaveCSS(
    'outline-offset',
    '2px',
  );
  await expect(page.getByRole('button', {name: 'Save folders'})).toHaveCSS(
    'outline-width',
    '1px',
  );
  await expect(page.getByRole('button', {name: 'Loading…'})).toBeDisabled();
  await page.addStyleTag({
    content: 'button { color: rgb(255, 0, 0) !important; }',
  });
  await expect(page.getByRole('button', {name: 'Save folders'})).not.toHaveCSS(
    'color',
    'rgb(255, 0, 0)',
  );
  expect((await new AxeBuilder({page}).analyze()).violations).toEqual([]);
  await expect(page).toHaveScreenshot('ui-desktop.png', {
    fullPage: true,
    animations: 'disabled',
  });
  await page.setViewportSize({width: 390, height: 844});
  await expect(page).toHaveScreenshot('ui-mobile.png', {
    fullPage: true,
    animations: 'disabled',
  });
  const folderInput = page.getByRole('textbox', {
    name: 'Folder name',
    exact: true,
  });
  await folderInput.focus();
  await expect(folderInput).toHaveCSS('outline-offset', '2px');
  await expect(folderInput).toHaveCSS('outline-width', '1px');
});

test('REG-002 REG-009 REG-010: initial Gmail authorization leads to explicit folder registration', async ({
  page,
}) => {
  await page.route('**/api/session', r =>
    r.fulfill({json: {authenticated: true, email: 'user@example.com'}}),
  );
  await page.route('**/api/v1/me', r => r.fulfill({status: 404, json: {}}));
  await page.route('**/api/csrf', r =>
    r.fulfill({json: {headerName: 'X-CSRF-TOKEN', token: 'fixture'}}),
  );
  await page.route('**/api/v1/registration', async r => {
    expect(r.request().postDataJSON()).toEqual({
      folders: [{name: 'INBOX', startDate: ''}],
    });
    await r.fulfill({
      status: 201,
      json: {
        email: 'user@example.com',
        version: 0,
        connected: true,
        reconnectionRequired: false,
        folders: [{id: 'f1', name: 'INBOX', startDate: ''}],
      },
    });
  });
  await page.goto('/?connection=connected');
  await expect(page.getByText(/Development environment:/)).toContainText(
    'permanently deletes',
  );
  await page.getByLabel('Start date').evaluate(element => {
    const input = element as HTMLInputElement;
    input.setCustomValidity('Incomplete date');
    input.dispatchEvent(new Event('input', {bubbles: true}));
  });
  await expect(
    page.getByRole('button', {name: 'Register', exact: true}),
  ).toBeDisabled();
  await page.getByLabel('Start date').evaluate(element => {
    const input = element as HTMLInputElement;
    input.setCustomValidity('');
    input.dispatchEvent(new Event('input', {bubbles: true}));
  });
  await page.getByRole('button', {name: 'Register', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Save folders'})).toBeVisible();
  await expect(
    page.getByRole('status').filter({hasText: 'folders are saved'}),
  ).toBeVisible();
});

test('REG-007 REG-014: deletion is confirmed and failed revocation remains visible after sign-out', async ({
  page,
}) => {
  await page.route('**/api/session', r =>
    r.fulfill({json: {authenticated: true, email: 'user@example.com'}}),
  );
  await page.route('**/api/v1/me', r =>
    r.fulfill({
      json: {
        email: 'user@example.com',
        version: 1,
        connected: true,
        reconnectionRequired: false,
        folders: [{id: 'f1', name: 'INBOX', startDate: ''}],
      },
    }),
  );
  await page.route('**/api/csrf', r =>
    r.fulfill({json: {headerName: 'X-CSRF-TOKEN', token: 'fixture'}}),
  );
  let removed = false;
  await page.route('**/api/v1/registration', async r => {
    removed = true;
    expect(r.request().method()).toBe('DELETE');
    await r.fulfill({json: {revoked: false}});
  });
  await page.route('**/api/logout', r => r.fulfill({status: 204}));
  await page.goto('/');
  await page.getByRole('button', {name: 'Delete registration'}).click();
  expect(removed).toBe(false);
  await page.getByRole('button', {name: 'Cancel', exact: true}).click();
  await expect(
    page.getByRole('button', {name: 'Confirm delete', exact: true}),
  ).toHaveCount(0);
  expect(removed).toBe(false);
  await page.getByRole('button', {name: 'Delete registration'}).click();
  await page.getByRole('button', {name: 'Confirm delete', exact: true}).click();
  await expect(page.getByRole('alert')).toContainText(
    'Google revocation failed',
  );
  expect(removed).toBe(true);
});
