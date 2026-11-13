export function applyAdminVisibility(root, admin) {
  root.querySelectorAll('[data-admin-only]').forEach(element => {
    element.hidden = !admin;
  });
}
