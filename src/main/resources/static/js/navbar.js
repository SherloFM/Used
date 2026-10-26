// TOKEN_KEY is declared by each page that includes this script.
function isLoggedIn() {
    return !!localStorage.getItem(TOKEN_KEY);
}

function getAuthHeaders() {
    const token = localStorage.getItem(TOKEN_KEY);
    return token ? { 'Authorization': 'Bearer ' + token } : {};
}

function logoutUser() {
    localStorage.removeItem(TOKEN_KEY);
    window.location.href = '/login';
}

window.handleNavSearch = function () {
    if (typeof window.performGlobalSearch === 'function') {
        window.performGlobalSearch();
        return;
    }
    const kw = (document.getElementById('keywordInput') || {}).value || '';
    window.location.href = '/?q=' + encodeURIComponent(kw);
};

function handleLockedInteraction() {
    const modal = document.getElementById('lockModal');
    if (modal) modal.style.display = 'flex';
}

function toggleUserMenu(e) {
    if (e) e.stopPropagation();
    if (!isLoggedIn()) { handleLockedInteraction(); return; }
    const menu = document.getElementById('userDropdown');
    if (menu) {
        if (menu.style.display === 'block') menu.style.display = 'none';
        else menu.style.display = 'block';
    }
}

window.toggleNotifDropdown = function (e) {
    if (e) e.stopPropagation();
    if (!isLoggedIn()) { handleLockedInteraction(); return; }
    const dropdown = document.getElementById('notifDropdown');
    if (!dropdown) return;
    if (dropdown.style.display === 'block') {
        dropdown.style.display = 'none';
    } else {
        dropdown.style.display = 'block';
        loadNotifications();
    }
};

async function loadNotifications() {
    const list = document.getElementById('notifList');
    if (!list) return;
    try {
        const res = await fetch('/api/notifications?size=50', { headers: getAuthHeaders() });
        if (!res.ok) { list.innerHTML = '<p style="padding:12px;color:#999;text-align:center;">Could not load notifications</p>'; return; }
        const items = await res.json();
        if (!items || items.length === 0) {
            list.innerHTML = '<p style="padding:12px;color:#999;text-align:center;">No notifications yet</p>';
            updateNotifBadge(0);
            return;
        }
        const unread = items.filter(n => !n.read).length;
        updateNotifBadge(unread);
        list.innerHTML = items.map(n => `
            <div class="notif-item" onclick="markNotifRead(${n.id})" style="padding:10px 14px;border-bottom:1px solid #f1f1f1;cursor:pointer;background:${n.read ? 'white' : '#f0f7ff'};">
                <div style="font-weight:600;font-size:0.9rem;color:#2c3e50;">${escapeHtml(n.title || n.type)}</div>
                <div style="font-size:0.85rem;color:#555;margin-top:2px;">${escapeHtml(n.message || '')}</div>
                <div style="font-size:0.75rem;color:#999;margin-top:4px;">${new Date(n.createdAt).toLocaleString()}</div>
            </div>`).join('');
    } catch (e) {
        list.innerHTML = '<p style="padding:12px;color:#999;text-align:center;">Could not load notifications</p>';
    }
}

function updateNotifBadge(count) {
    const badge = document.getElementById('notifBadge');
    if (!badge) return;
    if (count > 0) {
        badge.textContent = count > 9 ? '9+' : count;
        badge.style.display = 'inline-block';
    } else {
        badge.style.display = 'none';
    }
}

window.markNotifRead = async function (id) {
    try {
        await fetch('/api/notifications/' + id + '/read', { method: 'PUT', headers: getAuthHeaders() });
        loadNotifications();
    } catch (e) {}
};

window.clearNotifications = async function () {
    try {
        await fetch('/api/notifications/read-all', { method: 'PUT', headers: getAuthHeaders() });
        loadNotifications();
    } catch (e) {}
};

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

document.addEventListener('click', (e) => {
    const userMenu = document.getElementById('userDropdown');
    if (userMenu && !e.target.closest('.user-menu-wrapper')) {
        userMenu.style.display = 'none';
    }
    const notifDropdown = document.getElementById('notifDropdown');
    if (notifDropdown && !e.target.closest('.notification-wrapper')) {
        notifDropdown.style.display = 'none';
    }
    const lockModal = document.getElementById('lockModal');
    if (lockModal && e.target === lockModal) {
        lockModal.style.display = 'none';
    }
});

async function initNavbar() {
    if (!isLoggedIn()) {
        const header = document.getElementById('userDropdownHeader');
        if (header) header.textContent = 'Guest';
        return;
    }

    try {
        const res = await fetch('/api/profile', { headers: getAuthHeaders() });
        if (!res.ok) return;
        const data = await res.json();
        const header = document.getElementById('userDropdownHeader');
        if (header) {
            const name = ((data.firstName || '') + ' ' + (data.lastName || '')).trim() || data.username || 'Account';
            header.textContent = name;
        }

        const img = document.getElementById('navAvatarImg');
        const icon = document.querySelector('#navAvatar i');
        if (data.imgUrl) {
            if (img) { img.src = data.imgUrl; img.style.display = 'block'; }
            if (icon) icon.style.display = 'none';
        } else if (data.img && data.imgtype) {
            if (img) { img.src = 'data:' + data.imgtype + ';base64,' + data.img; img.style.display = 'block'; }
            if (icon) icon.style.display = 'none';
        }
    } catch (e) {
        console.error(e);
    }
}

document.addEventListener('DOMContentLoaded', initNavbar);
