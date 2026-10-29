// TOKEN_KEY is declared by each page that includes this script.
let notificationEventSource = null;
let notificationUnreadCount = 0;
const seenNotificationIds = new Set();

function isLoggedIn() {
    return !!localStorage.getItem(TOKEN_KEY);
}

function getAuthHeaders() {
    const token = localStorage.getItem(TOKEN_KEY);
    return token ? { 'Authorization': 'Bearer ' + token } : {};
}

function logoutUser() {
    if (notificationEventSource) notificationEventSource.close();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('used_role');
    window.location.href = '/login';
}

window.handleNavSearch = function () {
    if (typeof window.performGlobalSearch === 'function') {
        window.performGlobalSearch();
        return;
    }
    // Not the home page: take the search to the home page, preserving
    // whatever the user typed (keyword) and selected (category tags).
    const params = new URLSearchParams();
    const keywordInput = document.getElementById('keywordInput');
    const kw = ((keywordInput || {}).value || '').trim();
    if (kw) params.set('q', kw);
    const tags = Array.from(document.querySelectorAll('#searchTagsList .tag-chip > span'))
        .map(span => span.textContent.trim())
        .filter(Boolean);
    if (tags.length) params.set('tags', tags.join(','));
    window.location.href = '/?' + params.toString();
};

// Enter in the keyword box searches too (the home page defines its own
// Enter handling, so the listener is only added elsewhere).
if (typeof window.performGlobalSearch !== 'function') {
    const keywordInput = document.getElementById('keywordInput');
    if (keywordInput) {
        keywordInput.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                window.handleNavSearch();
            }
        });
    }
}

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
        syncNotifications();
    }
};

async function loadNotifications() {
    await syncNotifications();
}

async function syncNotifications() {
    const list = document.getElementById('notifList');
    const token = localStorage.getItem(TOKEN_KEY);
    if (!list || !token) return;

    try {
        const headers = getAuthHeaders();
        const [historyResponse, countResponse] = await Promise.all([
            fetch('/api/notifications?page=0&size=50', { headers }),
            fetch('/api/notifications/unread-count', { headers })
        ]);
        if (!historyResponse.ok || !countResponse.ok) {
            throw new Error(`Notification sync failed (${historyResponse.status}/${countResponse.status}).`);
        }
        const [items, unread] = await Promise.all([
            historyResponse.json(),
            countResponse.json()
        ]);
        list.replaceChildren();
        if (!items.length) {
            showNotificationEmptyState(list);
        } else {
            items.slice().reverse().forEach(notification => {
                if (notification.id) seenNotificationIds.add(String(notification.id));
                renderNotification(notification);
            });
        }
        notificationUnreadCount = unread.count;
        updateNotifBadge(notificationUnreadCount);
    } catch (error) {
        console.error('Notification sync failed', error);
        list.replaceChildren();
        const message = document.createElement('p');
        message.style.cssText = 'padding:12px;color:#999;text-align:center;';
        message.textContent = 'Could not load notifications';
        list.appendChild(message);
    }
}

function showNotificationEmptyState(list) {
    const message = document.createElement('p');
    message.style.cssText = 'padding:18px;color:#999;text-align:center;';
    message.textContent = 'No notifications yet';
    list.appendChild(message);
}

function renderNotification(notification) {
    const list = document.getElementById('notifList');
    if (!list) return;

    const item = document.createElement('div');
    item.className = 'notif-item';
    item.style.cssText = `padding:10px 14px;border-bottom:1px solid #f1f1f1;cursor:pointer;background:${notification.read ? 'white' : '#f0f7ff'};`;

    const title = document.createElement('div');
    title.style.cssText = 'font-weight:600;font-size:0.9rem;color:#2c3e50;';
    title.textContent = notification.title || notification.type || 'Notification';
    const message = document.createElement('div');
    message.style.cssText = 'font-size:0.85rem;color:#555;margin-top:2px;';
    message.textContent = notification.message || '';
    const createdAt = document.createElement('div');
    createdAt.style.cssText = 'font-size:0.75rem;color:#999;margin-top:4px;';
    createdAt.textContent = new Date(notification.createdAt || Date.now()).toLocaleString();
    item.append(title, message, createdAt);
    if (notification.id) {
        item.addEventListener('click', () => markNotifRead(notification.id));
    }

    if (list.firstElementChild && list.firstElementChild.tagName === 'P') {
        list.replaceChildren();
    }
    list.prepend(item);
}

function handleIncomingNotification(data, eventId) {
    const id = data.notificationId || eventId;
    if (id && seenNotificationIds.has(String(id))) return;
    if (id) seenNotificationIds.add(String(id));

    notificationUnreadCount++;
    updateNotifBadge(notificationUnreadCount);
    showNotificationToast(data);
    renderNotification(id ? { ...data, id, read: false } : data);
}

function showNotificationToast(data) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.style.cssText = 'background:#2c3e50;color:white;padding:12px 16px;border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,.2);max-width:360px;transition:opacity .3s;';
    const title = document.createElement('strong');
    title.textContent = data.title || 'New Notification';
    const message = document.createElement('div');
    message.style.marginTop = '4px';
    message.textContent = data.message || '';
    toast.append(title, message);
    container.appendChild(toast);
    window.setTimeout(() => {
        toast.style.opacity = '0';
        window.setTimeout(() => toast.remove(), 350);
    }, 4000);
}

function initNotificationStream() {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token || !window.EventSource) return;
    if (notificationEventSource) notificationEventSource.close();

    notificationEventSource = new EventSource(
        `/api/notifications/stream?token=${encodeURIComponent(token)}`);
    notificationEventSource.onopen = () => syncNotifications();
    notificationEventSource.onerror = error => {
        console.error('Notification stream disconnected; EventSource will reconnect.', error);
    };
    notificationEventSource.addEventListener('NOTIFICATION', event => {
        try {
            handleIncomingNotification(JSON.parse(event.data), event.lastEventId);
        } catch (error) {
            console.error('Could not parse notification event.', error);
        }
    });
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
        const response = await fetch('/api/notifications/' + id + '/read', {
            method: 'PUT',
            headers: getAuthHeaders()
        });
        if (!response.ok) throw new Error(`Could not mark notification read (${response.status}).`);
        await syncNotifications();
    } catch (error) {
        console.error('Could not mark notification read.', error);
    }
};

window.clearNotifications = async function () {
    try {
        const response = await fetch('/api/notifications/read-all', {
            method: 'PUT',
            headers: getAuthHeaders()
        });
        if (!response.ok) throw new Error(`Could not mark notifications read (${response.status}).`);
        await syncNotifications();
    } catch (error) {
        console.error('Could not mark notifications read.', error);
    }
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

document.addEventListener('DOMContentLoaded', () => {
    initNavbar();
    if (isLoggedIn()) initNotificationStream();
});
