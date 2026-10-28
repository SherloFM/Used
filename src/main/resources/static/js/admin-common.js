/* ==================================================
   ADMIN PORTAL — shared helpers for every admin page

   Pages are plain HTML; this file injects the sidebar,
   enforces the admin-only guard (real backend check,
   not just hiding UI), and wraps the admin API calls.
   ================================================== */

const ADMIN_TOKEN_KEY = 'used_jwt_token';

function adminToken() {
    return localStorage.getItem(ADMIN_TOKEN_KEY);
}

/** fetch() wrapper that always sends the JWT and turns API JSON errors
 *  into a JavaScript Error with the server's friendly "message". */
async function adminFetch(url, options = {}) {
    const token = adminToken();
    if (!token) {
        window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname);
        throw new Error('Not logged in');
    }

    const response = await fetch(url, {
        ...options,
        headers: {
            ...(options.headers || {}),
            'Authorization': 'Bearer ' + token,
            ...(options.body ? { 'Content-Type': 'application/json' } : {})
        }
    });

    if (!response.ok) {
        let message = 'Request failed (' + response.status + ').';
        try {
            const body = await response.json();
            if (body && typeof body.message === 'string' && body.message.trim()) message = body.message;
        } catch (ignored) { /* non-JSON body */ }

        // Session expired or no longer an admin -> bounce out of the portal
        if (response.status === 401 || response.status === 403) {
            adminToast(message, 'error');
            setTimeout(() => window.location.href = '/', 800);
        }
        throw new Error(message);
    }

    return response.status === 204 ? null : await response.json();
}

/** Real backend guard: the profile endpoint decides "who am I".
 *  Admins pass; everyone else is routed home. Returns the profile. */
async function requireAdmin() {
    const token = adminToken();
    if (!token) window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname);

    try {
        const res = await fetch('/api/profile', { headers: { 'Authorization': 'Bearer ' + token } });
        if (res.status === 401 || res.status === 403) {
            window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname);
            return null;
        }
        const profile = await res.json();
        if (profile.role !== 'ADMIN') {
            window.location.href = '/';
            return null;
        }
        return profile;
    } catch (err) {
        window.location.href = '/login?next=' + encodeURIComponent(window.location.pathname);
        return null;
    }
}

// ---------- Sidebar injection ----------
const ADMIN_SIDEBAR_HTML = `
    <aside class="admin-sidebar">
        <div class="admin-brand" onclick="location.href='/admin'">
            <i class="fas fa-recycle"></i> Used <span style="font-size:0.75rem;font-weight:normal;">Admin</span>
        </div>
        <nav class="admin-nav">
            <a href="/admin" data-nav="admin"><i class="fa fa-gauge"></i> Dashboard</a>
            <a href="/admin/users" data-nav="admin-users"><i class="fa fa-users"></i> Users</a>
            <a href="/admin/listings" data-nav="admin-listings"><i class="fa fa-box"></i> Listings</a>
            <a href="/admin/reports" data-nav="admin-reports"><i class="fa fa-flag"></i> Reports</a>
        </nav>
        <div class="admin-sidebar-footer">
            <a href="/"><i class="fa fa-arrow-left"></i> Back to Marketplace</a>
            <a href="#" class="logout" onclick="adminLogout(event)"><i class="fa fa-sign-out-alt"></i> Logout</a>
        </div>
    </aside>`;

function buildAdminSidebar() {
    document.querySelectorAll('.admin-sidebar-slot').forEach(slot => {
        slot.outerHTML = ADMIN_SIDEBAR_HTML;
    });
    // highlight the current page
    const path = window.location.pathname;
    document.querySelectorAll('.admin-nav a').forEach(link => {
        if (link.getAttribute('href') === path) link.classList.add('active');
    });
}

function adminLogout(e) {
    if (e) e.preventDefault();
    if (!confirm('Log out of the admin portal?')) return;
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem('used_role');
    window.location.href = '/login';
}

// ---------- Small UI building blocks ----------
function adminToast(message, type = 'info') {
    let container = document.getElementById('adminToastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'adminToastContainer';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = 'admin-toast ' + (type === 'info' ? '' : type);
    const icon = type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-times-circle' : 'fa-info-circle';
    toast.innerHTML = '<i class="fas ' + icon + '"></i> ' + escapeHtml(message);
    container.appendChild(toast);
    setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity 0.4s'; }, 3200);
    setTimeout(() => toast.remove(), 3700);
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    const div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
}

function fmtDate(value) {
    if (!value) return '--';
    const date = new Date(value);
    if (isNaN(date.getTime())) return '--';
    return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) +
        ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/** Fixed badge CSS class for known workflow statuses. */
function statusBadge(status) {
    const map = {
        'ACTIVE': 'b-active', 'SOLD': 'b-sold', 'DELISTED': 'b-delisted',
        'PENDING': 'b-pending', 'UNDER_REVIEW': 'b-under-review',
        'DISMISSED': 'b-dismissed', 'ACTION_TAKEN': 'b-action-taken',
        'USER': 'b-user', 'ADMIN': 'b-admin'
    };
    return '<span class="badge ' + (map[status] || 'b-dismissed') + '">' + escapeHtml(status || 'UNKNOWN') + '</span>';
}

/** Human readable report reason: SUSPECTED_SCAM -> Suspected scam */
function humanize(value) {
    if (!value) return '--';
    return value
        .toLowerCase()
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}

/** Spinner/empty/error placeholders inside a container. */
function showSpinner(el, text) {
    el.innerHTML = '<div class="state-box"><i class="fas fa-spinner spinner"></i><p>' + escapeHtml(text || 'Loading...') + '</p></div>';
}
function showEmpty(el, text) {
    el.innerHTML = '<div class="state-box"><i class="fas fa-inbox"></i><p>' + escapeHtml(text || 'Nothing here yet.') + '</p></div>';
}
function showError(el, message) {
    el.innerHTML = '<div class="state-box error"><i class="fas fa-exclamation-triangle"></i><p>' + escapeHtml(message) + '</p></div>';
}

// Run on every admin page load
document.addEventListener('DOMContentLoaded', () => {
    buildAdminSidebar();
    requireAdmin().then(profile => {
        if (!profile) return; // redirected
        localStorage.setItem('used_role', profile.role); // remember role for the login redirect
        const who = document.querySelector('[data-admin-username]');
        if (who) who.textContent = profile.username || profile.email;
    });
});
